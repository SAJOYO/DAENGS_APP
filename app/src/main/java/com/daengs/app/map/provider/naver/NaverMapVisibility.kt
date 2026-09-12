package com.daengs.app.map.provider.naver

import androidx.compose.runtime.*
import androidx.compose.ui.unit.IntSize
import com.daengs.app.map.shell.*
import com.daengs.app.map.layers.moments.MomentMarkerState
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.NaverMap

/** Read-only projection. It cannot issue camera updates. */
@Composable
internal fun NaverMapVisibility(map: NaverMap?, size: IntSize, density: Float,
    query: MapVisibilityQuery?, moments: List<MomentMarkerState>, onResult: (MapVisibilityResult) -> Unit) {
    val latestResult by rememberUpdatedState(onResult)
    var settled by remember(map) { mutableStateOf(false) }
    val badgeSizes = remember(moments, density) { moments.mapNotNull { marker -> marker.sequenceLabel?.let { label ->
        val (width, height) = diaryPinSize(label, density)
        marker.point to MapScreenSize(width.toFloat(), height.toFloat())
    } }.toMap() }
    DisposableEffect(map, size, density, query, badgeSizes) {
        fun report(moving: Boolean = false) {
            if (query == null) return
            val ids = if (map == null || moving || !settled) null else visibleMapLocationIds(query, size.width, size.height, density,
                badgeSize = { badgeSizes[it] ?: MapScreenSize(32 * density, 37 * density) }) {
                val p = map.projection.toScreenLocation(LatLng(it.latitude, it.longitude))
                MapScreenPoint(p.x, p.y)
            }
            latestResult(MapVisibilityResult(query, ids))
        }
        val idle = NaverMap.OnCameraIdleListener { settled = true; report() }
        val moving = NaverMap.OnCameraChangeListener { _, _ -> settled = false; report(moving = true) }
        if (query != null) { map?.addOnCameraIdleListener(idle); map?.addOnCameraChangeListener(moving); report() }
        onDispose { map?.removeOnCameraIdleListener(idle); map?.removeOnCameraChangeListener(moving) }
    }
}
