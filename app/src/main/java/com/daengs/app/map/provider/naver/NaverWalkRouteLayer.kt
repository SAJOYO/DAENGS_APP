package com.daengs.app.map.provider.naver

import android.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.style.WalkSpeedPoint
import com.daengs.app.map.style.paintWalkSpeedPath
import com.daengs.app.map.style.rememberWalkStyle
import com.daengs.app.map.layers.trail.TrailLayerState
import com.daengs.app.map.layers.completedroute.CompletedRouteLayerState
import com.daengs.app.map.style.WalkStylePolicy
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.MultipartPathOverlay
import com.naver.maps.map.overlay.PathOverlay

/** One store per map; SDK mutations run only on the Compose UI thread after composition. */
@Composable
internal fun NaverWalkRouteLayer(naverMap: NaverMap?, trail: TrailLayerState,
    completed: CompletedRouteLayerState, policy: WalkStylePolicy, themeId: String, dimCompleted: Boolean) {
    val probe = LocalWalkRouteProbe.current
    val store = remember(naverMap, probe) {
        naverMap?.let { map -> WalkRouteOverlayStore({ NaverWalkRouteHandle(map, it) }, probe) }
    }
    val sources = remember(trail.paths, trail.speedPaths, completed.paths, completed.speedPaths) {
        walkRouteSources(trail, completed)
    }
    DisposableEffect(store) { onDispose { store?.clear() } }
    SideEffect { store?.sync(sources, policy, themeId, dimCompleted) }
}

/** Parts and coordinates are set before attachment; color-only changes keep native coordinates. */
private class NaverWalkRouteHandle(map: NaverMap, initial: WalkRouteRenderState) : WalkRouteHandle {
    private val multipart = if (initial.key.speed) MultipartPathOverlay() else null
    private val plain = if (initial.key.speed) null else PathOverlay()
    private val overlay get() = multipart ?: plain!!

    init {
        geometry(initial)
        colors(initial)
        multipart?.apply { width = TRAIL_WIDTH; outlineWidth = TRAIL_OUTLINE_WIDTH }
        plain?.apply { width = TRAIL_WIDTH; outlineWidth = TRAIL_OUTLINE_WIDTH; outlineColor = Color.WHITE }
        overlay.map = map
    }

    override fun update(previous: WalkRouteRenderState, next: WalkRouteRenderState) {
        val sameGeometry = previous.parts.size == next.parts.size && previous.parts.indices.all {
            previous.parts[it].points == next.parts[it].points
        }
        if (!sameGeometry) geometry(next)
        if (previous.dimmed != next.dimmed || previous.parts.size != next.parts.size || previous.parts.indices.any {
            previous.parts[it].color != next.parts[it].color
        }) colors(next)
    }

    private fun geometry(state: WalkRouteRenderState) {
        multipart?.coordParts = state.parts.map { part -> part.points.map { LatLng(it.latitude, it.longitude) } }
        plain?.coords = state.parts.single().points.map { LatLng(it.latitude, it.longitude) }
    }

    private fun colors(state: WalkRouteRenderState) {
        multipart?.colorParts = state.parts.map { part ->
            val color = if (state.dimmed) (part.color and 0x00ffffff) or 0x60000000 else part.color
            MultipartPathOverlay.ColorPart(color, Color.WHITE, color, Color.WHITE)
        }
        plain?.color = state.parts.single().color
    }

    override fun remove() { overlay.map = null }
}

private const val TRAIL_WIDTH = 14
private const val TRAIL_OUTLINE_WIDTH = 2

@Preview
@Composable
private fun NaverWalkRouteLayerPreview() {
    val style by rememberWalkStyle()
    val parts = remember(style) {
        listOf(listOf(0 to 0, 40 to 20, 80 to 10), listOf(110 to 40, 150 to 60, 200 to 30)).flatMap { segment ->
            paintWalkSpeedPath(segment.mapIndexed { index, (x, y) ->
                WalkSpeedPoint(GeoPoint(37.5 + y * .0000005, 127.0 + x * .0000005), index * 2000L)
            }, style.policy, style.themeId)
        }
    }
    Canvas(Modifier.size(280.dp, 140.dp)) {
        fun at(point: GeoPoint) = Offset(((point.longitude - 127.0) / .0001 * size.width * .8 + 20).toFloat(),
            ((point.latitude - 37.5) / .00004 * size.height * .7 + 20).toFloat())
        parts.forEach { part ->
            val a = at(part.points.first()); val b = at(part.points.last())
            drawLine(androidx.compose.ui.graphics.Color.White, a, b, 18f)
            drawLine(androidx.compose.ui.graphics.Color(part.color), a, b, 14f)
        }
    }
}
