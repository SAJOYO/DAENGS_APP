package com.daengs.app.map.provider.naver

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.createBitmap
import com.daengs.app.map.features.records.*
import com.daengs.app.map.layout.*
import com.daengs.app.map.shell.*
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.moments.MomentMarkerState
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.WalkTraceShadow
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.*
import kotlin.math.roundToInt

/** Shared projection/layout lifecycle. Each caller supplies one semantic family, never mixed groups. */
@Composable
internal fun NaverGroupedMomentLayer(map: NaverMap?, moments: List<MomentMarkerState>, size: IntSize,
    density: Float, order: NativeWalkStack, onSelect: (List<String>) -> Unit,
    topInset: Int = 0, bottomInset: Int = 0, query: MapVisibilityQuery? = null,
    onVisibility: (MapVisibilityResult) -> Unit = {}, onBounds: (List<MarkerRect>) -> Unit = {},
    fixedMarkers: List<Pair<GeoPoint, MarkerFootprint>> = emptyList()) {
    val context = LocalContext.current
    val select by rememberUpdatedState(onSelect)
    val report by rememberUpdatedState(onVisibility)
    val reportBounds by rememberUpdatedState(onBounds)
    val diagnostics = LocalWalkMapDiagnostics.current
    var settled by remember(map) { mutableStateOf(false) }
    DisposableEffect(map, moments, size, density, order, diagnostics, topInset, bottomInset, query, fixedMarkers) {
        val overlays = mutableListOf<Overlay>()
        // Bounded cache survives camera gestures, but never retains previous query result sets.
        val icons = object : LinkedHashMap<String, OverlayImage>(64, .75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, OverlayImage>?) = this.size > 64
        }
        fun clear() { overlays.forEach { it.map = null; diagnostics?.detached(it) }; overlays.clear() }
        fun redraw() {
            clear()
            if (map == null || size.width == 0 || size.height == 0 || density <= 0) {
                reportBounds(emptyList()); query?.let { report(MapVisibilityResult(it, null)) }; return
            }
            val diary = moments.firstOrNull()?.diaryPin != null
            val viewport = markerViewport(size, density, topInset, query?.bottomOcclusionPx ?: bottomInset)
            val covers = markerExclusions(size, density, query)
            val exclusions = covers + fixedMarkers.map { (point, footprint) ->
                val p = map.projection.toScreenLocation(LatLng(point.latitude,point.longitude))
                footprint.at(MarkerPoint("",p.x/density.toDouble(),p.y/density.toDouble()))
            }
            val source = moments.associateBy { it.id }
            fun project(item: MomentMarkerState): RecordPinScreenPoint {
                val point = map.projection.toScreenLocation(LatLng(item.point.latitude, item.point.longitude))
                return RecordPinScreenPoint(item.id, point.x.toDouble() / density, point.y.toDouble() / density)
            }
            val projected = moments.map(::project).filter { it.x in -100.0..(size.width / density + 100.0) && it.y in -100.0..(size.height / density + 100.0) }
            val results = clusterMapMarkers(projected.filter { source.getValue(it.id).recordPin?.background == false || source.getValue(it.id).diaryPin != null })
            val background = clusterMapMarkers(projected.filter { source.getValue(it.id).recordPin?.background == true }, 30.0)
            data class Art(val icon: OverlayImage, val width: Int, val height: Int, val selected: Boolean, val alpha: Float)
            val art = results.map { group ->
                val members = group.points.map { source.getValue(it.id) }
                val chosen = members.firstOrNull { it.selected } ?: members.minBy { it.diaryPin?.ordinal ?: Int.MAX_VALUE }
                val selected = members.any { it.selected || it.diaryPin?.inspected == true }
                val count = if (diary) members.size else members.sumOf { it.recordPin!!.count }
                val behaviors = members.flatMap { it.behaviors }.toSet()
                val key = "${chosen.diaryPin?.ordinal}:${behaviors.sortedBy { it.ordinal }}:$count:$selected"
                val bitmap = if (diary) diaryGroupPinBitmap(chosen.diaryPin!!.ordinal, count, selected, density)
                    else actionMarkerBitmap(context, behaviors, selected, count.takeIf { it > 1 }?.toString(), density, recordFocusRing = true)
                val cached = icons[key]
                val icon = icons.getOrPut(key) { OverlayImage.fromBitmap(bitmap) }
                Art(icon, bitmap.width, bitmap.height, selected, if (diary && members.all { it.diaryPin!!.dimmed }) .42f else 1f)
                    .also { if (cached != null) bitmap.recycle() }
            }
            val placements = placeMapMarkers(results.mapIndexed { i, group ->
                MarkerGlyph(group, MarkerFootprint(art[i].width/density.toDouble(), art[i].height/density.toDouble(), .5, if (diary) 1.0 else .5),
                    if (art[i].selected) 1 else 0)
            }, viewport, exclusions)
            val visiblePlacements = placements.filter { it.bounds.intersects(viewport) && covers.none { cover -> cover.contains(it.bounds) } }
            reportBounds(visiblePlacements.map { it.bounds })
            query?.let { report(MapVisibilityResult(it, if (settled) visiblePlacements.flatMap { p -> p.glyph.group.points.map { it.id } }.toSet() else null)) }
            fun coordinate(p: RecordPinScreenPoint) = map.projection.fromScreenLocation(PointF((p.x * density).toFloat(), (p.y * density).toFloat()))
            fun line(points: List<LatLng>, dashed: Boolean) {
                if (points.distinct().size < 2) return
                overlays += PolylineOverlay().apply {
                    coords = points; width = density.roundToInt().coerceAtLeast(1)
                    color = WalkTraceShadow.color.copy(alpha = .45f).toArgb()
                    if (dashed) setPattern((3 * density).roundToInt(), (3 * density).roundToInt())
                    globalZIndex = order.markers - 1; this.map = map
                }
            }
            background.forEach { group ->
                if (results.any { it.anchor.distance(group.anchor) < 12 }) return@forEach
                val opacity = group.points.maxOf { source.getValue(it.id).recordPin!!.alpha }
                val key = "background:$opacity"
                val icon = icons.getOrPut(key) {
                    val bitmap = createBitmap((10 * density).roundToInt().coerceAtLeast(1), (10 * density).roundToInt().coerceAtLeast(1))
                    val canvas = Canvas(bitmap); val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                    paint.color = CreamBg.copy(alpha = .65f).toArgb()
                    canvas.drawCircle(5*density, 5*density, 4*density, paint)
                    paint.color = WalkTraceShadow.color.copy(alpha = opacity).toArgb()
                    canvas.drawCircle(5*density, 5*density, 3*density, paint)
                    OverlayImage.fromBitmap(bitmap)
                }
                overlays += Marker(coordinate(group.anchor), icon).apply {
                    width = (10*density).roundToInt(); height = width; anchor = PointF(.5f,.5f)
                    globalZIndex = order.route - 1; isHideCollidedMarkers = false
                    // No click listener: background context cannot hijack the active search.
                    this.map = map
                    diagnostics?.attached(this, NativeWalkLayerReading("배경 핀", globalZIndex, "농도 $opacity"))
                }
            }
            results.forEachIndexed { index, group ->
                val members = group.points.map { source.getValue(it.id) }
                val count = if (diary) members.size else members.sumOf { it.recordPin!!.count }
                val selected = art[index].selected
                val placement = placements[index].point
                if (placement.distance(group.anchor) > 1) line(listOf(coordinate(group.anchor), coordinate(placement)), false)
                if (!diary && selected && group.points.size > 1) {
                    val left = group.points.minOf { it.x } - 8; val right = group.points.maxOf { it.x } + 8
                    val top = group.points.minOf { it.y } - 8; val bottom = group.points.maxOf { it.y } + 8
                    line(listOf(left to top, right to top, right to bottom, left to bottom, left to top)
                        .map { (x,y) -> coordinate(RecordPinScreenPoint("",x,y)) }, true)
                }
                overlays += Marker(coordinate(placement), art[index].icon).apply {
                    width = art[index].width; height = art[index].height; alpha = art[index].alpha
                    anchor = PointF(.5f,if (diary) 1f else .5f); globalZIndex = order.markers; zIndex = if (selected) 140 else 120
                    isHideCollidedMarkers = false
                    setOnClickListener { select(members.map { it.id }); true }
                    this.map = map
                    diagnostics?.attached(this, NativeWalkLayerReading(if (diary) "장면 묶음" else "액션 핀", globalZIndex,
                        "${count}건 · ${if (placements[index].crowded) "밀집 한계" else "공통 배치"}"))
                }
            }
        }
        val idle = NaverMap.OnCameraIdleListener { settled = true; redraw() }
        val moving = NaverMap.OnCameraChangeListener { _, _ -> settled = false; query?.let { report(MapVisibilityResult(it, null)) } }
        // Native markers move with the map; regroup only at rest, avoiding flicker during gestures.
        if (moments.isNotEmpty()) map?.addOnCameraIdleListener(idle)
        if (query != null) map?.addOnCameraChangeListener(moving)
        redraw()
        onDispose { map?.removeOnCameraIdleListener(idle); map?.removeOnCameraChangeListener(moving); clear(); icons.clear() }
    }
}

@Preview(showBackground = true)
@Composable
private fun RecordPinBadgesPreview() { ActionMarkersPreview() }
