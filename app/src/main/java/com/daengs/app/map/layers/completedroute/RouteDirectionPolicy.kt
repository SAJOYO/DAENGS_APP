package com.daengs.app.map.layers.completedroute

/** Completed-route screen-space presentation only; never used to classify GPS or count passes. */
internal object RouteDirectionPolicy {
    const val iconSizeDp = 18
    const val offsetDp = 16.0
    const val spacingDp = 100.0
    const val maxArrows = 5
    const val sampleStepDp = 12.0
    const val maxCandidates = 160
    const val tangentHalfWindowDp = 14.0
    const val minimumStraightness = .985
    const val maxBendDp = 2.0
    const val routeClearanceDp = 3.0
    // Existing ambiguity guard: crossing/opposite observations do not assert one direction.
    const val ambiguityDistanceDp = 7.0
    const val ambiguityDirectionCosine = .7
}
