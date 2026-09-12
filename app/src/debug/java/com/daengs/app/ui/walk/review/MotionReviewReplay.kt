package com.daengs.app.ui.walk.review

import com.daengs.app.walk.motion.MotionPolicies
import com.daengs.app.walk.motion.MotionPolicySelection
import com.daengs.app.walk.motion.replayRecordedMotion
import com.daengs.app.walk.sync.WalkMotionContract
import com.daengs.app.walk.sync.WalkPrecisionContract
import org.json.JSONArray
import org.json.JSONObject

/** Actual Kotlin motion engine, on validated backup input; never the legacy route evaluator. */
internal fun motionReviewReplay(plan: WalkMotionContract.Plan): JSONObject {
    val input = plan.input
    val steps = JSONArray()
    val policy = (MotionPolicies.resolveJson(input.session.id, input.session.motionPolicyJson)
        as MotionPolicySelection.Supported).policy
    replayRecordedMotion(policy, input.epochs, input.fixes.asSequence()) { step ->
        val estimate = step.estimate
        val decision = step.decision
        // Control callbacks have no observation estimate. Their times remain in the manifest.
        if (estimate != null && decision != null) steps.put(JSONObject()
            .put("ingress_seq", estimate.ref?.ingressSeq ?: JSONObject.NULL)
            .put("source_epoch", estimate.ref?.sourceEpoch ?: JSONObject.NULL)
            .put("clock_epoch_id", estimate.ref?.clockEpochId ?: JSONObject.NULL)
            .put("position_quality", estimate.positionQuality.name)
            .put("estimate_reasons", JSONArray(estimate.reasons.map { it.name }.sorted()))
            .put("from_seq", decision.fromRef?.ingressSeq ?: JSONObject.NULL)
            .put("to_seq", decision.toRef?.ingressSeq ?: JSONObject.NULL)
            .put("connection", decision.connection.name).put("distance_use", decision.distanceUse.name)
            .put("distance_delta_m", decision.distanceDeltaM)
            .put("reasons", JSONArray(decision.reasons.map { it.name }.sorted()))
            .put("cumulative_distance_m", step.snapshot.eligibleDistanceM))
    }
    return JSONObject().put("format", "walk-motion-kotlin-replay-v1")
        .put("input_origin", "server-backup").put("independent_device_input_verified", false)
        .put("summary", WalkPrecisionContract.replay(plan)).put("steps", steps)
}
