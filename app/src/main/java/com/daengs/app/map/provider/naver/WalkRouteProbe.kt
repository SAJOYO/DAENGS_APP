package com.daengs.app.map.provider.naver

import androidx.compose.runtime.staticCompositionLocalOf

/** Opt-in debug measurements. Counts our work, not native draw calls or FPS. */
internal class WalkRouteProbe {
    var created = 0
    var removed = 0
    var updated = 0
    var prepared = 0
    var pointsPrepared = 0
    val prepareNanos = mutableListOf<Long>()
    val sdkNanos = mutableListOf<Long>()
    fun reset() {
        created = 0; removed = 0; updated = 0; prepared = 0; pointsPrepared = 0
        prepareNanos.clear(); sdkNanos.clear()
    }
}
internal val LocalWalkRouteProbe = staticCompositionLocalOf<WalkRouteProbe?> { null }
