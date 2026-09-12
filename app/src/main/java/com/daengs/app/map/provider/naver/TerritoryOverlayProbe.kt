package com.daengs.app.map.provider.naver

import androidx.compose.runtime.staticCompositionLocalOf

/** Optional, screen-owned counters. Production has no collector or logging. */
internal class TerritoryOverlayProbe {
    var created = 0
    var removed = 0
    var updated = 0
    var framePasses = 0
    var handleFrames = 0
    var maxFrameTargets = 0
    fun recordFrame(targets: Int) {
        framePasses++; handleFrames += targets; maxFrameTargets = maxOf(maxFrameTargets, targets)
    }
    val syncNanos = mutableListOf<Long>()
    fun reset() { created = 0; removed = 0; updated = 0; syncNanos.clear(); framePasses = 0; handleFrames = 0; maxFrameTargets = 0 }
}

internal val LocalTerritoryOverlayProbe = staticCompositionLocalOf<TerritoryOverlayProbe?> { null }
