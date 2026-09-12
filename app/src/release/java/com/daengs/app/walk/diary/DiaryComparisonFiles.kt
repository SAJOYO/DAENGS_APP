package com.daengs.app.walk.diary

import android.content.Context

/** Release has no local comparison file access. */
internal object DiaryComparisonFiles {
    suspend fun prepare(context: Context, snapshot: DiaryComparisonSnapshot): Unit = error("Debug only")
    suspend fun read(context: Context, snapshot: DiaryComparisonSnapshot): DiaryPlaceComparison = error("Debug only")
}
