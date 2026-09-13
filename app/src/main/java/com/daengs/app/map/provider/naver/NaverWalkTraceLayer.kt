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
 * Each world-aligned tile already contains the composed sheets' final alpha and display RGB.
 * GroundOverlay adds no opacity scaling, density weighting, or camera-dependent recalculation.
 */
@Composable
internal fun NaverWalkTraceLayer(map: NaverMap?, tiles: List<TraceRasterTile>, globalZ: Int = NaverWalkLayerOrder.TRACE_SHEETS) {
    val diagnostics = LocalWalkMapDiagnostics.current
    LaunchedEffect(map, tiles, globalZ, diagnostics) {
        if (map == null || tiles.isEmpty()) return@LaunchedEffect
        val prepared = withContext(Dispatchers.Default) {
            tiles.map { tile ->
                ensureActive()
                val rgb = tile.rgb
                require(rgb == null || rgb.size == tile.alpha.size)
                val pixels = IntArray(tile.alpha.size) { index ->
                    val alpha = (tile.alpha[index].coerceIn(0f, 1f) * 255).roundToInt()
                    (alpha shl 24) or ((rgb?.get(index) ?: TRACE_PIGMENT_RGB) and 0xFFFFFF)
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
                    // Every sheet stays below the route, including after asynchronous reloads.
                    applyNativeWalkOrder(globalZ, { globalZIndex = it }, { globalZIndex })
                }
                overlays += overlay
                overlay.map = map
                diagnostics?.attached(overlay, NativeWalkLayerReading("셀로판", overlay.globalZIndex,
                    "SDK 알파 ${overlay.alpha} · 픽셀 최대 ${tile.alpha.maxOrNull() ?: 0f}"))
            }
            awaitCancellation()
        } finally {
            overlays.forEach { it.map = null; diagnostics?.detached(it) }
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
