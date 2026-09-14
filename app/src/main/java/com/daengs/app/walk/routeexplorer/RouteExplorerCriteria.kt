package com.daengs.app.walk.routeexplorer

/** Spatial lookup resolution, not a distance for accepting a user's tap. */
internal object RouteIndexGrid {
    const val CELL_SIZE_METERS = 20.0
}

/** Maximum distance from a tap to the edge that seeds passage analysis. */
internal object RouteTapCriteria {
    const val MAX_EDGE_DISTANCE_METERS = 20.0
}

/** Criteria for counting repeated traversal of the same corridor in the saved walking route. */
internal object RoutePassageCriteria {
    // Inclusive edge bounds; accuracy must additionally be finite and strictly positive.
    const val MIN_EDGE_LENGTH_METERS = .3
    const val MAX_EDGE_LENGTH_METERS = 120.0
    const val MAX_SAMPLE_GAP_MILLIS = 15_000L
    const val MAX_ACCURACY_METERS = 12f

    const val CANDIDATE_SEARCH_METERS = 30.0
    // Absolute cosine permits both forward and reverse traversals, but excludes crossings.
    const val MIN_DIRECTION_ALIGNMENT = .9
    const val CORRIDOR_HALF_LENGTH_METERS = 24.0
    const val CORRIDOR_HALF_WIDTH_METERS = 4.0
    // A traversal must reach both sides of the anchor and cover the minimum longitudinal span.
    const val MIN_CENTER_CROSSING_METERS = 6.0
    const val MIN_TRAVERSAL_SPAN_METERS = 18.0
    const val MAX_LATERAL_SPREAD_METERS = 2.0
}

/** Legacy route replay only; passage eligibility and measured-observation timelines are separate. */
internal object RouteReplayCriteria {
    // Both active time and recorded wall time must advance by 1..this limit.
    const val MAX_INTERPOLATION_GAP_MILLIS = 15_000L
}
