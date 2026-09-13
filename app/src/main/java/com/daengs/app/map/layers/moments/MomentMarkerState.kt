package com.daengs.app.map.layers.moments

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.WalkMomentType

/** 지도 공급자와 무관한 산책 행동 책갈피 표시 상태. */
data class MomentMarkerState(
    val id: String,
    val point: GeoPoint,
    val label: String,
    val selected: Boolean = false,
    val photoFile: java.io.File? = null,
    val aboveRouteEndpoints: Boolean = false,
    /** Optional ordinal badge for a diary. label retains the complete same-position sequence. */
    val sequenceLabel: String? = null,
    /** Explicit behavior identity; translated captions are never parsed to choose an icon. */
    val behaviors: Set<WalkMomentType> = emptySet(),
    /** Opt-in policy for the records overview; legacy diary markers retain their own presentation. */
    val recordPin: RecordPinAppearance? = null,
    /** Diary ordinals and inspection membership never share action counts or behavior styling. */
    val diaryPin: DiaryPinAppearance? = null,
)

data class RecordPinAppearance(val count: Int, val background: Boolean = false, val alpha: Float = .18f)
data class DiaryPinAppearance(val ordinal: Int, val inspected: Boolean = false, val dimmed: Boolean = false)
