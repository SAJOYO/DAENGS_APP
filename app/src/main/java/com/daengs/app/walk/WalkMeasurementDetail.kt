package com.daengs.app.walk

import com.daengs.app.location.GeoPoint

/** One adopted measurement supplies route, metrics and endpoint semantics together. */
data class WalkMeasurementBoundary(val sourceEpoch: String, val clockEpochId: String,
    val seq: Int?, val controlKind: String?, val atMillis: Long, val point: GeoPoint?)

data class WalkMeasurementDetail(val id: String, val resultDigest: String,
    val boundaries: Map<String, WalkMeasurementBoundary?>,
    val observedRuns: List<List<RecordedFix>>,
    val ownerId: String = "",
    val walkingSections: List<MeasurementWalkingSection> = emptyList(),
    val usableSources: Set<MeasurementSourceRef> = emptySet())

/** Stable source identities; display segment/vertex indices are never a saved binding address. */
data class MeasurementSourceRef(val sessionId: String, val sourceEpoch: String, val clockEpochId: String,
    val clientSeq: Int? = null, val controlKind: String? = null)

data class MeasurementWalkingSection(val id: String, val points: List<MeasurementSourceRef>)

internal fun RecordedFix.measurementRef(sessionId: String) = MeasurementSourceRef(sessionId,
    requireNotNull(sourceEpoch), requireNotNull(clockEpochId), clientSeq)
