package com.daengs.app.walk.trajectory

import com.daengs.app.walk.LegacyAcceptedInterval
import com.daengs.app.walk.LegacyRouteEvidence
import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.WalkSessionDetail

/** Review parameters, not an adopted walking/motion policy or guaranteed GPS error bounds. */
internal data class LegacyConnectionPolicy(
    val maxAccuracyMeters: Double = 25.0,
    val minIntervalMillis: Long = 500,
    val maxIntervalMillis: Long = 10_000,
    val maxAdjustedSpeedMps: Double = 35.0,
    val maxVelocityChangeMps2: Double = 8.0,
    val minConsistentEdges: Int = 3,
    val maxClockDifferenceMillis: Long = 1_000,
) {
    val version: String get() = "legacy-observation-review-v1"
    init {
        require(maxAccuracyMeters.isFinite() && maxAccuracyMeters > 0)
        require(minIntervalMillis > 0 && maxIntervalMillis >= minIntervalMillis)
        require(maxAdjustedSpeedMps.isFinite() && maxAdjustedSpeedMps > 0)
        require(maxVelocityChangeMps2.isFinite() && maxVelocityChangeMps2 > 0)
        require(minConsistentEdges >= 2 && maxClockDifferenceMillis >= 0)
    }
}

internal enum class ObservationQuality { USABLE, UNCERTAIN, INVALID }
internal enum class ObservationConnection { CONNECTED, BROKEN, UNRESOLVED }
internal enum class LegacyWalkingUse { INCLUDED, EXCLUDED, UNRESOLVED }
internal enum class AuxiliaryUse { REVIEW_CANDIDATE, RESERVED_BY_WALKING, OWNERSHIP_CONFLICT, UNAVAILABLE }
internal enum class ObservationClock { LEGACY_WALL_TIME, SCOPED_MONOTONIC, UNRESOLVED }
internal enum class ConnectionReason {
    INVALID_COORDINATE, MOCK_LOCATION, MISSING_ACCURACY, INVALID_ACCURACY, LOW_ACCURACY,
    RECORDING_INELIGIBLE, OUTSIDE_RECORD_WINDOW, CHAIN_BOUNDARY, SOURCE_EPOCH_BOUNDARY, CLOCK_EPOCH_BOUNDARY,
    MISSING_SEQUENCE, NON_INCREASING_TIME, UNTRUSTED_START_TIME, INCOMPLETE_CLOCK, UNSCOPED_MONOTONIC, CLOCK_DISAGREEMENT,
    INTERVAL_TOO_SHORT, OBSERVATION_GAP, POSITION_UNCERTAIN, POSITION_INVALID,
    NONFINITE_DISPLACEMENT, SPEED_BEYOND_REVIEW_LIMIT, NEIGHBOR_VELOCITY_CHANGE, INSUFFICIENT_NEIGHBORS, CONSISTENT_NEIGHBORS,
    LEGACY_OWNER_RANGE, LEGACY_REJECTED_ENDPOINT, NO_ACCOUNTING_OWNER,
    OWNER_CONNECTION_UNRESOLVED, OWNER_CONTAINS_REJECTED_OBSERVATION,
}

internal data class AssessedObservation(val fix: RecordedFix, val quality: ObservationQuality,
    val reasons: List<ConnectionReason>)

/** Accuracy allowance is a heuristic using reported radii, never a statistical confidence bound. */
internal data class ConnectionKinematics(val seconds: Double, val displacementMeters: Double,
    val coordinateSpeedMps: Double, val accuracyAllowanceMeters: Double, val adjustedSpeedMps: Double)

internal data class NeighborConsistency(val fromSeq: Int, val throughSeq: Int, val toSeq: Int,
    val velocityChangeMps: Double, val allowanceMps: Double, val consistent: Boolean)

internal data class AssessedObservationInterval(
    val fromSeq: Int,
    val toSeq: Int,
    val connection: ObservationConnection,
    val clock: ObservationClock,
    val reasons: List<ConnectionReason>,
    val kinematics: ConnectionKinematics?,
    /** INCLUDED means source-range ownership, not inclusion of each raw leg's geometry. */
    val walkingUse: LegacyWalkingUse,
    val ownerToSeq: Int?,
    val accountingReasons: List<ConnectionReason>,
    val auxiliaryUse: AuxiliaryUse,
)

/** References original neighbors only. No resampling, bridging, distance total or scene ownership. */
internal data class AuxiliaryObservationRun(val fromSeq: Int, val toSeq: Int, val walkingUse: LegacyWalkingUse,
    val sourceSeqs: List<Int>)

internal data class ConnectionTransition(val atSeq: Int,
    val beforeConnection: ObservationConnection, val afterConnection: ObservationConnection,
    val beforeWalkingUse: LegacyWalkingUse, val afterWalkingUse: LegacyWalkingUse,
    val beforeAuxiliaryUse: AuxiliaryUse, val afterAuxiliaryUse: AuxiliaryUse)

/** Bound to one complete read. Results have no authority to change its summary or map. */
internal class LegacyConnectivityReview internal constructor(
    private val basis: LegacyRouteEvidence?,
    val policy: LegacyConnectionPolicy,
    val unsupportedReason: String?,
    val observations: List<AssessedObservation>,
    val owners: List<LegacyAcceptedInterval>,
    val intervals: List<AssessedObservationInterval>,
    val neighbors: List<NeighborConsistency>,
    val auxiliaryRuns: List<AuxiliaryObservationRun>,
    val transitions: List<ConnectionTransition>,
) {
    val stage: String get() = "review_candidate"
    fun matches(detail: WalkSessionDetail): Boolean = unsupportedReason == null && basis?.matches(detail) == true
}
