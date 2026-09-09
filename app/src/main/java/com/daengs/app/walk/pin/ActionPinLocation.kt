package com.daengs.app.walk.pin

import com.daengs.app.location.GeoPoint
import java.util.UUID

/** Captured at the tap; never replace this scope with the currently logged-in owner/session. */
data class ActionPinRequest(
    val resolutionId: UUID,
    val ownerId: String?,
    val sessionId: String,
    val chainIndex: Int,
    val targetAtMillis: Long,
) {
    init {
        require(sessionId.isNotBlank())
        require(chainIndex >= 0)
        require(targetAtMillis in 0..Long.MAX_VALUE - PinPolicyV1.WAIT_MILLIS)
    }
}

/** An immutable copy of a persisted raw fix, not TrailRecorder's display path. */
data class ActionPinObservation(
    val ownerId: String?,
    val sessionId: String,
    val clientSeq: Int,
    val chainIndex: Int,
    val atMillis: Long,
    val point: GeoPoint,
    val accuracyMeters: Float?,
    val isMock: Boolean = false,
) {
    val ref: ActionPinSourceRef get() = ActionPinSourceRef(clientSeq, chainIndex, atMillis)
}

data class ActionPinSourceRef(val clientSeq: Int, val chainIndex: Int, val atMillis: Long)

enum class ActionPinState { PROVISIONAL, RESOLVED, UNLOCATED }
enum class ActionPinMethod { OBSERVED, ESTIMATED, LAST_KNOWN, NONE }
enum class ActionPinUncertaintyBasis { PROVIDER_ACCURACY, UNKNOWN }
enum class ActionPinReason {
    DIRECT_FIX, AWAITING_OBSERVATIONS, REFINED, DEADLINE, SESSION_ENDED,
    RECOVERED, ESTIMATOR_FAILED, NO_EVIDENCE,
}

/** Internal calculation result, not the v2 JSON/Room schema. Only the estimator constructs it. */
class ActionPinResolution internal constructor(
    val request: ActionPinRequest,
    val state: ActionPinState,
    val method: ActionPinMethod,
    val point: GeoPoint?,
    val computedAtMillis: Long,
    val resolveByMillis: Long,
    sourceRefs: List<ActionPinSourceRef>,
    val uncertaintyMeters: Double?,
    val uncertaintyBasis: ActionPinUncertaintyBasis,
    val reason: ActionPinReason,
) {
    val sourceRefs: List<ActionPinSourceRef> = java.util.Collections.unmodifiableList(sourceRefs.toList())
    val policyVersion: String = PinPolicyV1.VERSION
    val algorithmVersion: String = "action-pin-local-v1"
}

/** Versioned experiment parameters; changes require a new policy/algorithm, not runtime mutation. */
internal object PinPolicyV1 {
    const val VERSION = "action-pin-policy-v1"
    const val WAIT_MILLIS = 8_000L
    const val HISTORY_MILLIS = 30_000L
    const val DIRECT_MAX_AGE_MILLIS = 10_000L
    const val DIRECT_ACCURACY_METERS = 15.0
    const val MODEL_ACCURACY_METERS = 50.0
    const val MAX_SPEED_MPS = 3.0
    const val MAX_BRACKET_MILLIS = 20_000L
    const val PREDICTION_MILLIS = 5_000L
    const val FIT_WINDOW_MILLIS = 8_000L
    const val MIN_FIT_SPAN_MILLIS = 3_000L
    const val MAX_PREDICTION_METERS = 10.0
    const val MAX_FIT_RESIDUAL_METERS = 6.0
    const val SPIKE_RESIDUAL_METERS = 6.0
    const val SPIKE_BRACKET_MILLIS = 8_000L
    const val STATIONARY_SPEED_MPS = 0.35
    const val STATIONARY_RADIUS_METERS = 6.0
}
