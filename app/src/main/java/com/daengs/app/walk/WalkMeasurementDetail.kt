package com.daengs.app.walk

import com.daengs.app.location.GeoPoint

/** One adopted measurement supplies route, metrics and endpoint semantics together. */
data class WalkMeasurementBoundary(val sourceEpoch: String, val clockEpochId: String,
    val seq: Int?, val controlKind: String?, val atMillis: Long, val point: GeoPoint?)

data class WalkMeasurementDetail(val id: String, val resultDigest: String,
    val boundaries: Map<String, WalkMeasurementBoundary?>,
    val observedRuns: List<List<RecordedFix>>)
