package com.daengs.app.map.provider.naver

import androidx.compose.runtime.staticCompositionLocalOf

/** Optional, screen-owned counters. Production has no collector or logging. */
internal class TerritoryOverlayProbe {
    var created = 0
    var removed = 0
    var updated = 0
    val syncNanos = mutableListOf<Long>()
    fun reset() { created = 0; removed = 0; updated = 0; syncNanos.clear() }
}

internal val LocalTerritoryOverlayProbe = staticCompositionLocalOf<TerritoryOverlayProbe?> { null }
