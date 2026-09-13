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
import com.daengs.app.map.layers.moments.MomentMarkerState
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.WalkTraceShadow
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.*
import kotlin.math.roundToInt

/** Records-only native layer. Diary markers never opt into this clustering/quiet-caption policy. */
@Composable
internal fun NaverRecordActionPinLayer(map: NaverMap?, moments: List<MomentMarkerState>, size: IntSize,
    density: Float, order: NativeWalkStack, onSelect: (List<String>) -> Unit) {
    val context = LocalContext.current
    val select by rememberUpdatedState(onSelect)
    val diagnostics = LocalWalkMapDiagnostics.current
    DisposableEffect(map, moments, size, density, order, diagnostics) {
        val overlays = mutableListOf<Overlay>()
        // Bounded cache survives camera gestures, but never retains previous query result sets.
        val icons = object : LinkedHashMap<String, OverlayImage>(64, .75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, OverlayImage>?) = this.size > 64
        }
        fun clear() { overlays.forEach { it.map = null; diagnostics?.detached(it) }; overlays.clear() }
        fun redraw() {
            clear()
            if (map == null || size.width == 0 || size.height == 0 || moments.isEmpty()) return
            val source = moments.associateBy { it.id }
            fun project(item: MomentMarkerState): RecordPinScreenPoint {
                val point = map.projection.toScreenLocation(LatLng(item.point.latitude, item.point.longitude))
                return RecordPinScreenPoint(item.id, point.x.toDouble() / density, point.y.toDouble() / density)
            }
            val projected = moments.map(::project).filter { it.x in -100.0..(size.width / density + 100.0) && it.y in -100.0..(size.height / density + 100.0) }
            val results = clusterRecordPins(projected.filter { source.getValue(it.id).recordPin?.background == false })
            val background = clusterRecordPins(projected.filter { source.getValue(it.id).recordPin?.background == true }, 30.0)
            val placements = placeRecordPinBadges(results)
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
                val count = members.sumOf { it.recordPin!!.count }
                val selected = members.any { it.selected }
                val behaviors = members.flatMap { it.behaviors }.toSet()
                val key = "${behaviors.sortedBy { it.ordinal }}:$count:$selected"
                val bitmap = if (key !in icons) actionMarkerBitmap(context, behaviors, selected, count.takeIf { it > 1 }?.toString(), density, recordFocusRing = true) else null
                val icon = icons.getOrPut(key) { OverlayImage.fromBitmap(requireNotNull(bitmap)) }
                val iconDp = if (selected) 36 else 32
                val placement = placements[index]
                if (placement.distance(group.anchor) > 1) line(listOf(coordinate(group.anchor), coordinate(placement)), false)
                if (selected && group.points.size > 1) {
                    val left = group.points.minOf { it.x } - 8; val right = group.points.maxOf { it.x } + 8
                    val top = group.points.minOf { it.y } - 8; val bottom = group.points.maxOf { it.y } + 8
                    line(listOf(left to top, right to top, right to bottom, left to bottom, left to top)
                        .map { (x,y) -> coordinate(RecordPinScreenPoint("",x,y)) }, true)
                }
                overlays += Marker(coordinate(placement), icon).apply {
                    width = ((iconDp * behaviors.size + 8) * density).roundToInt(); height = ((iconDp + 8)*density).roundToInt()
                    anchor = PointF(.5f,.5f); globalZIndex = order.markers; zIndex = if (selected) 140 else 120
                    isHideCollidedMarkers = false
                    setOnClickListener { select(members.map { it.id }); true }
                    this.map = map
                    diagnostics?.attached(this, NativeWalkLayerReading("액션 핀", globalZIndex, "${count}건 · 아이콘+배지"))
                }
            }
        }
        val idle = NaverMap.OnCameraIdleListener { redraw() }
        // Native markers move with the map; regroup only at rest, avoiding flicker during gestures.
        if (moments.isNotEmpty()) map?.addOnCameraIdleListener(idle)
        redraw()
        onDispose { map?.removeOnCameraIdleListener(idle); clear(); icons.clear() }
    }
}

@Preview(showBackground = true)
@Composable
private fun RecordPinBadgesPreview() { ActionMarkersPreview() }
