package com.daengs.app.map.provider.naver

import android.graphics.PointF
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layout.*
import com.daengs.app.map.layers.moments.*
import com.daengs.app.map.shell.*
import com.daengs.app.ui.theme.*
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.*
import kotlin.math.roundToInt

/** One packing pass for both families. Group membership and click identities remain separate. */
@Composable
internal fun NaverDetachedMomentLayer(map: NaverMap?, moments: List<MomentMarkerState>, size: IntSize,
    density: Float, order: NativeWalkStack, onSelect: (List<String>) -> Unit,
    paths: List<List<GeoPoint>>, query: MapVisibilityQuery?, bottomInset: Int,
    fixedMarkers: List<Pair<GeoPoint, MarkerFootprint>>, onBounds: (List<MarkerRect>) -> Unit,
    onVisibility: (MapVisibilityResult) -> Unit,
) {
    val context=LocalContext.current
    val select by rememberUpdatedState(onSelect)
    val visibility by rememberUpdatedState(onVisibility)
    val bounds by rememberUpdatedState(onBounds)
    val offsets=remember(map) { mutableMapOf<String,MarkerPoint>() }
    DisposableEffect(map,moments,size,density,order,paths,query,bottomInset,fixedMarkers) {
        val overlays=mutableListOf<Overlay>()
        fun clear() { overlays.forEach { it.map=null }; overlays.clear() }
        fun redraw() {
            if (map==null || size.width==0 || size.height==0 || density<=0) return
            val viewport=markerViewport(size,density,0,query?.bottomOcclusionPx ?: bottomInset)
            fun project(point: GeoPoint,id: String=""): MarkerPoint {
                val p=map.projection.toScreenLocation(LatLng(point.latitude,point.longitude))
                return MarkerPoint(id,p.x/density.toDouble(),p.y/density.toDouble())
            }
            fun coordinate(p: MarkerPoint)=map.projection.fromScreenLocation(PointF((p.x*density).toFloat(),(p.y*density).toFloat()))
            val exclusions=markerExclusions(size,density,query)+fixedMarkers.map { (p,f)->f.at(project(p)) }
            val edges=paths.flatMap { path->path.zipWithNext().map { (a,b)->MarkerRouteEdge(project(a),project(b)) } }
            val source=moments.associateBy { it.id }
            val points=moments.map { project(it.point,it.id) }.filter {
                it.x in viewport.left-96..viewport.right+96 && it.y in viewport.top-96..viewport.bottom+96
            }
            data class Art(val icon: OverlayImage,val width: Int,val height: Int,val footprint: MarkerFootprint)
            val artCache=mutableMapOf<String,Art>()
            fun art(group: MarkerGroup): Art=artCache.getOrPut(group.key) {
                val members=group.points.map { source.getValue(it.id) }
                val chosen=members.firstOrNull { it.selected } ?: members.minBy { it.diaryPin?.ordinal ?: Int.MAX_VALUE }
                val selected=members.any { it.selected || it.diaryPin?.inspected==true }
                val bitmap=if (chosen.diaryPin!=null) diaryGroupPinBitmap(chosen.diaryPin.ordinal,members.size,selected,density,detached=true)
                    else diaryActionPinBitmap(context,members.flatMap { it.behaviors }.toSet(),
                        members.sumOf { it.recordPin?.count ?: 1 },selected,density)
                // Reserve the largest selected artwork, including any representative ordinal.
                val reserved=if (chosen.diaryPin!=null) diaryGroupPinBitmap(members.maxOf { it.diaryPin!!.ordinal },members.size,true,density,detached=true)
                    else diaryActionPinBitmap(context,members.flatMap { it.behaviors }.toSet(),
                        members.sumOf { it.recordPin?.count ?: 1 },true,density)
                Art(OverlayImage.fromBitmap(bitmap),bitmap.width,bitmap.height,
                    MarkerFootprint(maxOf(bitmap.width,reserved.width)/density.toDouble(),maxOf(bitmap.height,reserved.height)/density.toDouble())).also { reserved.recycle() }
            }
            fun pack(diameter: Double): List<MarkerPlacement> {
                val groups=points.groupBy { source.getValue(it.id).diaryPin!=null }.values.flatMap { clusterMapMarkers(it,diameter) }
                return placeDetachedMarkers(groups.map { MarkerGlyph(it,art(it).footprint,momentGroupPriority(it.points.map { p->source.getValue(p.id) })) },
                    viewport,exclusions,edges,offsets)
            }
            var result=pack(44.0)
            for (diameter in listOf(60.0,80.0)) {
                if (result.none { it.crowded }) break
                val alternative=pack(diameter)
                if (alternative.filter { it.crowded }.sumOf { it.glyph.group.points.size } < result.filter { it.crowded }.sumOf { it.glyph.group.points.size }) result=alternative
            }
            val visible=result.filterNot { it.crowded }
            val missing=result.filter { it.crowded }.flatMap { it.glyph.group.points }.map { it.id }.toSet()
            // Publish explicit access through the existing lists; never draw the overlapping fallback.
            bounds(visible.map { it.bounds })
            query?.let { visibility(MapVisibilityResult(it,if(map.isCameraIdlePending) null else visible.flatMap { p->p.glyph.group.points.map { it.id } }.toSet(), missing)) }
            offsets.keys.retainAll(result.map { it.glyph.group.key }.toSet())
            clear()
            for (placement in visible) {
                val group=placement.glyph.group; val a=art(group)
                offsets[group.key]=MarkerPoint(group.key,placement.point.x-group.anchor.x,placement.point.y-group.anchor.y)
                val start=coordinate(group.anchor); val end=coordinate(placement.point)
                if (placement.point.distance(group.anchor)>1) {
                    overlays+=PolylineOverlay().apply {
                        coords=listOf(start,end); width=density.roundToInt().coerceAtLeast(1)
                        color=WalkTraceShadow.color.copy(alpha=.55f).toArgb(); globalZIndex=order.markers-1; this.map=map
                    }
                }
                overlays+=Marker(start,OverlayImage.fromResource(com.daengs.app.R.drawable.ic_walk_replay_cursor)).apply {
                    width=(7*density).roundToInt(); height=width; anchor=PointF(.5f,.5f)
                    globalZIndex=order.markers-1; this.map=map
                }
                overlays+=Marker(end,a.icon).apply {
                    width=a.width; height=a.height; anchor=PointF(.5f,.5f)
                    globalZIndex=order.markers; zIndex=120+placement.glyph.priority*10; isHideCollidedMarkers=false
                    val members=group.points.map { source.getValue(it.id) }
                    alpha=if(members.all { it.diaryPin?.dimmed==true }) .42f else 1f
                    setOnClickListener { select(group.points.map { it.id }); true }; this.map=map
                }
            }
        }
        val idle=NaverMap.OnCameraIdleListener { redraw() }
        val moving=NaverMap.OnCameraChangeListener { _,_->query?.let { visibility(MapVisibilityResult(it,null)) } }
        map?.addOnCameraIdleListener(idle); map?.addOnCameraChangeListener(moving); redraw()
        onDispose { map?.removeOnCameraIdleListener(idle); map?.removeOnCameraChangeListener(moving); clear() }
    }
}

@Preview(showBackground=true)
@Composable
private fun DetachedActionArtPreview() { ActionMarkersPreview() }
