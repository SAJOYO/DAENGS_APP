package com.daengs.app.map.provider.naver

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.daengs.app.map.layers.spatial.SpatialDiaryPaintCell
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.PolygonOverlay

/** Provider adapter only. Distribution scaling and hex geometry stay outside the SDK. */
@Composable
internal fun NaverSpatialDiaryLayer(map: NaverMap?, cells: List<SpatialDiaryPaintCell>) {
    DisposableEffect(map, cells) {
        val overlays = if (map == null) emptyList() else cells.map { cell ->
            PolygonOverlay().apply {
                coords = cell.boundary.map { LatLng(it.latitude, it.longitude) }
                color = Color.argb((cell.alpha * 255).toInt(), 198, 65, 113)
                outlineWidth = 0
                zIndex = 1
                this.map = map
            }
        }
        onDispose { overlays.forEach { it.map = null } }
    }
}
