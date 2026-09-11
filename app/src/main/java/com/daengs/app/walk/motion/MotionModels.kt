package com.daengs.app.walk.motion

import com.daengs.app.walk.RecordedFix

data class MotionRef(val sessionId: String, val ingressSeq: Long, val sourceEpoch: String,
    val clockEpochId: String, val chainIndex: Int)

data class MotionEpoch(val sourceEpoch: String, val clockEpochId: String, val chainIndex: Int,
    val startedElapsedNanos: Long, val firstIngressSeq: Long, val endExclusiveNanos: Long? = null) {
    init {
        require(sourceEpoch.isNotBlank() && clockEpochId.isNotBlank())
        require(chainIndex >= 0 && startedElapsedNanos >= 0 && firstIngressSeq >= 0)
        require(endExclusiveNanos == null || endExclusiveNanos >= startedElapsedNanos)
    }
}

/** Ordered events, not callback batches. The caller persists/control-orders them before replay. */
sealed interface MotionEvent {
    data class Begin(val epoch: MotionEpoch) : MotionEvent
    data class Observation(val sessionId: String, val fix: RecordedFix) : MotionEvent
    data class End(val afterIngressSeq: Long, val elapsedNanos: Long, val kind: EndKind) : MotionEvent
    data class Loss(val reason: LossReason) : MotionEvent
}
enum class EndKind { PAUSE, STOP, INTERRUPTED }
enum class LossReason { INGRESS_OVERFLOW, SOURCE_DELIVERY_FAILURE, STORAGE_FAILURE }
enum class MotionLifecycle { IDLE, RECORDING, PAUSED, STOPPED, INTERRUPTED }
enum class PositionQuality { USABLE, UNCERTAIN, INVALID, MOCK }
enum class SpeedSource { DEVICE, COORDINATE_WINDOW, UNKNOWN }
enum class SpeedQuality { TRUSTED, ESTIMATED, UNVERIFIED, UNKNOWN }
enum class Movement { UNKNOWN, STILL, MOVING, HIGH_SPEED_SUSPECTED }
enum class DistanceUse { INCLUDE, EXCLUDE }
enum class Connection { CONTINUE, START_NEW, SKIP }
enum class MotionReason {
    FIRST_POINT, BELOW_NOISE_FLOOR, POSITION_UNCERTAIN, INVALID_POSITION, MOCK,
    SPEED_MISSING, SPEED_UNCERTAIN, SPEED_INVALID, SPEED_CONFLICT, HIGH_SPEED,
    REENTRY_PENDING, OBSERVATION_GAP, JUMP, OUT_OF_ORDER, DUPLICATE, SAME_TIME_CONFLICT,
    INVALID_TIME, MISSING_METADATA, OUTSIDE_ACTIVE_INTERVAL, SOURCE_CHANGED, CLOCK_DOMAIN_CHANGED,
    CHAIN_CHANGED, PAUSE, INGRESS_GAP, INGRESS_OVERFLOW, SOURCE_DELIVERY_FAILURE, STORAGE_FAILURE,
}

data class MotionEstimate(val ref: MotionRef?, val positionQuality: PositionQuality,
    val speedMps: Double?, val speedSource: SpeedSource, val speedQuality: SpeedQuality,
    val observedElapsedNanos: Long?, val movement: Movement, val reasons: Set<MotionReason>)

data class SegmentDecision(val segmentId: Long?, val fromRef: MotionRef?, val toRef: MotionRef?,
    val distanceUse: DistanceUse, val distanceDeltaM: Double, val connection: Connection,
    val estimatedSegmentSpeedMps: Double?, val reasons: Set<MotionReason>, val policyVersion: String)

/** Processing STOP is not a durable completion receipt; recording integrity remains #283's job. */
data class MotionSnapshot(val lifecycle: MotionLifecycle, val processedThroughJournalSeq: Long,
    val processedObservationCount: Long, val lastIngressSeq: Long, val segmentCount: Long,
    val eligibleDistanceM: Double, val closedRecordingDurationNanos: Long, val hasKnownLoss: Boolean,
    val retainedObservationCount: Int, val policyVersion: String, val configHash: String)

data class MotionStep(val estimate: MotionEstimate?, val decision: SegmentDecision?, val snapshot: MotionSnapshot)
data class MotionJournalEntry(val journalSeq: Long, val event: MotionEvent)
