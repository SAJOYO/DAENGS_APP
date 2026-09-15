package com.daengs.app.map.provider.naver

import android.graphics.PointF
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.R
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.completedroute.LIVE_ROUTE_START_ID
import com.daengs.app.map.layers.completedroute.RouteEndpointKind
import com.daengs.app.map.layers.completedroute.RouteEndpointMarkerState
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage

/** Small centered stamps. Selection changes stacking, never size or coordinates. */
@Composable
internal fun NaverRouteEndpointLayer(
    map: NaverMap?,
    endpoints: List<RouteEndpointMarkerState>,
    onSelect: (String) -> Unit,
    globalZ: Int = NaverWalkLayerOrder.MARKERS,
) {
    val density = LocalDensity.current.density
    if (LocalInspectionMode.current) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            endpoints.forEach {
                if (it.compact) Image(diaryPinBitmap(it.label, it.selected, density, endpoint = true,
                    detached = it.abovePoint).asImageBitmap(), it.label)
                else Image(painterResource(it.kind.iconRes), it.label)
            }
        }
        return
    }
    val diagnostics = LocalWalkMapDiagnostics.current
    val context = LocalContext.current
    val latestSelect by rememberUpdatedState(onSelect)
    DisposableEffect(map, endpoints, context.resources.configuration.densityDpi, density, diagnostics, globalZ) {
        val markers = if (map == null) emptyList() else endpoints.flatMap { endpoint ->
            val resource = endpoint.kind.iconRes
            // Vector intrinsic dimensions are dp-aware and are also used by Preview.
            val art = requireNotNull(context.getDrawable(resource))
            val compact = if (endpoint.compact) diaryPinBitmap(endpoint.label, endpoint.selected, density, endpoint = true, detached = endpoint.abovePoint) else null
            val marker = Marker().apply {
                position = LatLng(endpoint.point.latitude, endpoint.point.longitude)
                width = compact?.width ?: art.intrinsicWidth
                height = compact?.height ?: art.intrinsicHeight
                anchor = PointF(0.5f, if (endpoint.abovePoint) 1.2f else if (compact == null) 0.5f else 0f)
                icon = compact?.let(OverlayImage::fromBitmap) ?: OverlayImage.fromResource(resource)
                captionText = endpoint.label.takeIf { endpoint.selected && !endpoint.compact }.orEmpty()
                captionMinZoom = 0.0
                zIndex = if (endpoint.selected) 100 else 80
                isHideCollidedMarkers = false
                setOnClickListener {
                    if (!endpoint.compact) captionText = if (captionText.isEmpty()) endpoint.label else ""
                    if (endpoint.id != LIVE_ROUTE_START_ID && !endpoint.abovePoint) latestSelect(endpoint.id)
                    true
                }
                applyNativeWalkOrder(globalZ, { globalZIndex = it }, { globalZIndex })
                this.map = map
                diagnostics?.attached(this, NativeWalkLayerReading("마커", globalZIndex, "출발·도착"))
            }
            if (!endpoint.abovePoint) listOf(marker) else listOf(marker, Marker().apply {
                position = marker.position; icon = OverlayImage.fromResource(R.drawable.ic_walk_replay_cursor)
                width = (7*density).toInt(); height = width; anchor = PointF(.5f,.5f)
                globalZIndex = globalZ; this.map = map
            })
        }
        onDispose { markers.forEach { it.map = null; diagnostics?.detached(it) } }
    }
}

internal val RouteEndpointKind.iconRes: Int get() = when (this) {
    RouteEndpointKind.START -> R.drawable.ic_walk_start
    RouteEndpointKind.END -> R.drawable.ic_walk_finish
    RouteEndpointKind.START_END -> R.drawable.ic_walk_start_finish
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFAF4)
@Composable
private fun RouteEndpointStampsPreview() {
    val point = GeoPoint(37.5, 127.0)
    NaverRouteEndpointLayer(null, listOf(
        RouteEndpointMarkerState("start", point, "출발", RouteEndpointKind.START),
        RouteEndpointMarkerState("end", point, "도착", RouteEndpointKind.END),
        RouteEndpointMarkerState("both", point, "출발 · 도착", RouteEndpointKind.START_END),
    ), {})
}

@Preview(showBackground = true)
@Composable
private fun FixedDiaryEndpointsPreview() {
    val point = GeoPoint(37.5, 127.0)
    NaverRouteEndpointLayer(null, listOf(
        RouteEndpointMarkerState("start", point, "산책 시작", RouteEndpointKind.START, compact = true, abovePoint = true),
        RouteEndpointMarkerState("end", point, "산책 끝", RouteEndpointKind.END, compact = true, abovePoint = true),
    ), {})
}
