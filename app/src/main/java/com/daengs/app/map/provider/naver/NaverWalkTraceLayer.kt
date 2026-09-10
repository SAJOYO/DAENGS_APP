package com.daengs.app.map.provider.naver

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.tooling.preview.Preview
import com.daengs.app.map.layers.traces.TraceRasterTile
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.GroundOverlay
import com.naver.maps.map.overlay.OverlayImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Each world-aligned tile already contains the final source-over alpha for visible walks.
 * GroundOverlay adds no opacity scaling, density weighting, or camera-dependent recalculation.
 */
@Composable
internal fun NaverWalkTraceLayer(map: NaverMap?, tiles: List<TraceRasterTile>) {
    LaunchedEffect(map, tiles) {
        if (map == null || tiles.isEmpty()) return@LaunchedEffect
        val prepared = withContext(Dispatchers.Default) {
            tiles.map { tile ->
                ensureActive()
                val pixels = IntArray(tile.alpha.size) { index ->
                    val alpha = (tile.alpha[index].coerceIn(0f, 1f) * 255).roundToInt()
                    (alpha shl 24) or TRACE_PIGMENT_RGB
                }
                tile to OverlayImage.fromBitmap(
                    Bitmap.createBitmap(pixels, tile.size, tile.size, Bitmap.Config.ARGB_8888),
                )
            }
        }
        val overlays = mutableListOf<GroundOverlay>()
        try {
            prepared.forEach { (tile, image) ->
                val overlay = GroundOverlay().apply {
                    bounds = LatLngBounds(
                        LatLng(tile.southWest.latitude, tile.southWest.longitude),
                        LatLng(tile.northEast.latitude, tile.northEast.longitude),
                    )
                    this.image = image
                    alpha = 1f
                    // Keep map labels, pins, and route endpoints legible over the paint.
                    globalZIndex = -100
                }
                overlays += overlay
                overlay.map = map
            }
            awaitCancellation()
        } finally {
            overlays.forEach { it.map = null }
            // OverlayImage owns its bitmap. Do not recycle it while the SDK may still read it.
        }
    }
}

private const val TRACE_PIGMENT_RGB = 0xB82758

@Preview
@Composable
private fun NaverWalkTraceLayerPreview() {
    NaverWalkTraceLayer(map = null, tiles = emptyList())
}
