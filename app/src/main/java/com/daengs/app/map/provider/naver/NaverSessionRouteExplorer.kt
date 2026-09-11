package com.daengs.app.map.provider.naver

import android.graphics.Color
import android.graphics.PointF
import androidx.compose.runtime.*
import androidx.compose.ui.unit.IntSize
import com.daengs.app.R
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.completedroute.*
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage
import com.naver.maps.map.overlay.PathOverlay

/** Own SDK markers. A replay point never touches the device LocationOverlay. */
@Composable
internal fun NaverSessionRouteExplorer(
    map: NaverMap?, state: SessionRouteExplorerLayerState?,
    paths: List<List<GeoPoint>>, obstacles: List<GeoPoint>,
    size: IntSize, bottomPadding: Int, density: Float,
    onDirectionCount: (Int) -> Unit,
) {
    val arrowIcon = remember { OverlayImage.fromResource(R.drawable.ic_walk_external_direction) }
    val cursorIcon = remember { OverlayImage.fromResource(R.drawable.ic_walk_replay_cursor) }
    val latestCount by rememberUpdatedState(onDirectionCount)
    val highlight = state?.highlightPaths.orEmpty()
    DisposableEffect(map, state != null, paths, obstacles, size, bottomPadding, density, highlight) {
        val arrows = mutableListOf<Marker>()
        var sides = emptyMap<String, Int>()
        fun clear() { arrows.forEach { it.map = null }; arrows.clear() }
        fun redraw() {
            clear()
            if (map == null || state == null || size.width == 0 || size.height == 0) return
            val projection = map.projection
            val visible = RouteScreenRect(12.0 * density, 12.0 * density,
                size.width - 12.0 * density, size.height - bottomPadding - 32.0 * density)
            fun project(p: GeoPoint): RouteScreenPoint {
                val xy = projection.toScreenLocation(LatLng(p.latitude, p.longitude))
                return RouteScreenPoint(xy.x.toDouble(), xy.y.toDouble())
            }
            val source = highlight.ifEmpty { paths }
            fun projectEdges(input: List<List<GeoPoint>>) = input.flatMapIndexed { segment, points ->
                points.map(::project).zipWithNext().mapIndexed { index, (a, b) ->
                    RouteScreenEdge("$segment:$index", a, b)
                }
            }
            val edges = projectEdges(source)
            val exclusions = obstacles.map(::project).filter { it.valid }.map {
                RouteScreenRect(it.x - 28 * density, it.y - 46 * density,
                    it.x + 28 * density, it.y + 14 * density)
            } + RouteScreenRect(size.width - 68.0 * density, 0.0, size.width.toDouble(), 68.0 * density)
            val placements = placeRouteDirections(edges, visible, exclusions, density.toDouble(), sides,
                distinguishPasses = highlight.isNotEmpty(),
                collisionEdges = if (highlight.isEmpty()) edges else projectEdges(paths))
            sides = placements.associate { it.id to it.side }
            for (placement in placements) {
                val coordinate = projection.fromScreenLocation(PointF(placement.center.x.toFloat(), placement.center.y.toFloat()))
                if (!coordinate.isValid) continue
                arrows += Marker(coordinate, arrowIcon).apply {
                    width = (24 * density).toInt(); height = width
                    anchor = PointF(.5f, .5f); angle = placement.angle
                    isFlat = false; zIndex = 110
                    this.map = map
                }
            }
            latestCount(arrows.size)
        }
        val idle = NaverMap.OnCameraIdleListener { redraw() }
        val moving = NaverMap.OnCameraChangeListener { _, _ -> arrows.forEach { it.isVisible = false } }
        if (map != null && state != null) {
            map.addOnCameraIdleListener(idle); map.addOnCameraChangeListener(moving)
            redraw()
        }
        onDispose {
            map?.removeOnCameraIdleListener(idle); map?.removeOnCameraChangeListener(moving)
            clear()
        }
    }
    DisposableEffect(map, highlight) {
        val lines = if (map == null) emptyList() else highlight.filter { it.size >= 2 }.map { points ->
            PathOverlay().apply {
                coords = points.map { LatLng(it.latitude, it.longitude) }
                width = (6 * density).toInt(); color = Color.rgb(194, 54, 103)
                outlineWidth = (2 * density).toInt(); outlineColor = Color.WHITE
                zIndex = 10; this.map = map
            }
        }
        onDispose { lines.forEach { it.map = null } }
    }
    val cursor = remember(map, state != null) {
        if (map == null || state == null) null else Marker().apply {
            icon = cursorIcon; anchor = PointF(.5f, .5f); zIndex = 160
            width = (24 * density).toInt(); height = width
        }
    }
    SideEffect {
        val point = state?.cursor
        if (cursor != null) {
            if (point == null) cursor.map = null else {
                cursor.position = LatLng(point.latitude, point.longitude); cursor.map = map
            }
        }
    }
    DisposableEffect(cursor) { onDispose { cursor?.map = null } }
}
