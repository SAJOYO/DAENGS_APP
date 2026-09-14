package com.daengs.app.map.provider.naver

import androidx.compose.runtime.*

/** Opt-in renderer readback. The debug lab supplies this; normal screens never allocate it. */
internal class WalkMapDiagnostics {
    var showBase by mutableStateOf(true)
    var showTraces by mutableStateOf(true)
    var showRoute by mutableStateOf(true)
    var showMarkers by mutableStateOf(true)
    val overlays = mutableStateMapOf<Any, NativeWalkLayerReading>()
    val details = mutableStateMapOf<String, String>()
    fun attached(token: Any, reading: NativeWalkLayerReading) { overlays[token] = reading }
    fun detached(token: Any) { overlays.remove(token) }
}

internal data class NativeWalkLayerReading(
    val role: String,
    val globalZ: Int,
    val description: String,
)

internal val LocalWalkMapDiagnostics = staticCompositionLocalOf<WalkMapDiagnostics?> { null }

/** Apply the provider policy and return what the target actually accepted, for diagnostics. */
internal fun applyNativeWalkOrder(order: Int, write: (Int) -> Unit, read: () -> Int): Int {
    write(order)
    return read()
}
