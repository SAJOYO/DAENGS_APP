package com.daengs.app.walk.pin

import com.daengs.app.location.GeoPoint
import kotlin.math.abs

/**
 * Deterministic per-tap calculation. No GPS acquisition, clock reads, storage, scheduling or attestation.
 * The caller persists begin() immediately and CAS-applies at most one finish() to the same action.
 */
class ActionPinEstimator {
    /**
     * [directFix] is supplied only when the existing normal-GPS selector accepted that raw observation.
     * Null explicitly selects fallback; the estimator never upgrades a fallback to OBSERVED itself.
     * Even delayed initial computation cannot see observations taken after the tap.
     */
    fun begin(
        request: ActionPinRequest,
        observations: List<ActionPinObservation>,
        directFix: ActionPinSourceRef? = null,
        computedAtMillis: Long = request.targetAtMillis,
    ): ActionPinResolution {
        require(computedAtMillis >= request.targetAtMillis)
        val evidence = evidence(request, observations, request.targetAtMillis)
        val direct = evidence.singleOrNull {
            it.ref == directFix && it.chainIndex == request.chainIndex &&
                request.targetAtMillis - it.atMillis <= PinPolicyV1.DIRECT_MAX_AGE_MILLIS &&
                it.modelAccuracy() <= PinPolicyV1.DIRECT_ACCURACY_METERS
        }
        if (direct != null) {
            return ActionPinResolution(
                request, ActionPinState.RESOLVED, ActionPinMethod.OBSERVED, direct.point,
                computedAtMillis, request.targetAtMillis, listOf(direct.ref),
                direct.accuracyMeters!!.toDouble(), ActionPinUncertaintyBasis.PROVIDER_ACCURACY,
                ActionPinReason.DIRECT_FIX,
            )
        }
        val candidate = estimate(request, evidence)
        return resolution(
            request, candidate, ActionPinState.PROVISIONAL, computedAtMillis,
            request.targetAtMillis + PinPolicyV1.WAIT_MILLIS, ActionPinReason.AWAITING_OBSERVATIONS,
        )
    }

    /**
     * Feed the saved resolution and raw evidence again after recovery. [observationCutoffMillis] is
     * the actual collection/session end, which may precede computation. Deadline never extends.
     * REFINED closes early only with supported post-tap refinement; otherwise returns the initial pin.
     * ESTIMATOR_FAILED deliberately keeps the initial result without touching potentially bad input.
     */
    fun finish(
        initial: ActionPinResolution,
        observations: List<ActionPinObservation>,
        computedAtMillis: Long,
        reason: ActionPinReason = ActionPinReason.DEADLINE,
        observationCutoffMillis: Long = computedAtMillis,
    ): ActionPinResolution {
        if (initial.state != ActionPinState.PROVISIONAL) return initial
        require(computedAtMillis >= initial.computedAtMillis)
        require(observationCutoffMillis >= initial.request.targetAtMillis)
        require(reason in setOf(
            ActionPinReason.REFINED, ActionPinReason.DEADLINE, ActionPinReason.SESSION_ENDED,
            ActionPinReason.RECOVERED, ActionPinReason.ESTIMATOR_FAILED,
        ))
        require(reason != ActionPinReason.DEADLINE || computedAtMillis >= initial.resolveByMillis)
        val retained = Candidate(initial.point, initial.method, initial.sourceRefs)
        val candidate = if (reason == ActionPinReason.ESTIMATOR_FAILED) retained else {
            val cutoff = minOf(computedAtMillis, observationCutoffMillis, initial.resolveByMillis)
            estimate(initial.request, evidence(initial.request, observations, cutoff))
        }
        val improves = candidate.supported && candidate.refs.any { it.atMillis > initial.request.targetAtMillis }
        if (reason == ActionPinReason.REFINED && !improves) return initial
        // An isolated/poor future fix must not displace an already available historical estimate.
        val selected = if (initial.point == null || improves) candidate else retained
        return resolution(
            initial.request, selected,
            if (selected.point == null) ActionPinState.UNLOCATED else ActionPinState.RESOLVED,
            computedAtMillis, initial.resolveByMillis, reason,
        )
    }

    private data class Candidate(
        val point: GeoPoint?,
        val method: ActionPinMethod,
        val refs: List<ActionPinSourceRef>,
        val supported: Boolean = false,
    )

    private fun resolution(
        request: ActionPinRequest, candidate: Candidate, state: ActionPinState,
        computedAt: Long, resolveBy: Long, reason: ActionPinReason,
    ) = ActionPinResolution(
        request, state, candidate.method, candidate.point, computedAt, resolveBy,
        candidate.refs.distinct().sortedWith(compareBy({ it.atMillis }, { it.clientSeq })),
        null, ActionPinUncertaintyBasis.UNKNOWN, reason,
    )

    private fun evidence(
        request: ActionPinRequest, observations: List<ActionPinObservation>, cutoff: Long,
    ): List<ActionPinObservation> = observations
        .filter { it.ownerId == request.ownerId && it.sessionId == request.sessionId }
        // Repeated identical delivery is harmless; conflicting raw identity is not new evidence.
        .groupBy { it.clientSeq }.values.mapNotNull { copies -> copies.distinct().singleOrNull() }
        .filter {
            it.clientSeq >= 0 && it.chainIndex in 0..request.chainIndex &&
                it.atMillis in 0..cutoff && !it.isMock && it.point.isUsablePinPoint() &&
                (it.chainIndex == request.chainIndex || it.atMillis <= request.targetAtMillis)
        }
        .sortedWith(compareBy({ it.atMillis }, { it.clientSeq }))

    private fun estimate(request: ActionPinRequest, evidence: List<ActionPinObservation>): Candidate {
        val target = request.targetAtMillis
        // One sample/second bounds the O(n²) consensus to <=39 samples in the 30+8 second window.
        val recent = evidence.filter {
            it.chainIndex == request.chainIndex &&
                target - it.atMillis <= PinPolicyV1.HISTORY_MILLIS &&
                it.modelAccuracy() <= PinPolicyV1.MODEL_ACCURACY_METERS
        }.groupBy { it.atMillis / 1_000 }.values.map { bucket ->
            bucket.minWith(compareBy({ it.modelAccuracy() }, { abs(it.atMillis - target) }, { it.clientSeq }))
        }.sortedBy { it.atMillis }
        // A rough fix exactly at t=0 must not outrank several better surrounding observations.
        val precise = recent.filter { it.modelAccuracy() <= PinPolicyV1.DIRECT_ACCURACY_METERS }
        val precisePath = consistentPath(precise)
        val path = trimSpikes(if (precisePath.size >= 3) precisePath else consistentPath(recent))
        if (path.size >= 3) {
            val before = path.lastOrNull { it.atMillis <= target }
            val after = path.firstOrNull { it.atMillis >= target }
            val local = path.filter { abs(it.atMillis - target) <= PinPolicyV1.FIT_WINDOW_MILLIS }
            val stationary = fitPinMotion(local, target)
            if (stationary != null && stationary.velocity.length() <= PinPolicyV1.STATIONARY_SPEED_MPS &&
                stationary.residual <= PinPolicyV1.STATIONARY_RADIUS_METERS &&
                path.minOf { abs(it.atMillis - target) } <= PinPolicyV1.PREDICTION_MILLIS
            ) {
                // Fit intercept estimates the tap time, also for slow movement; not a provider fix.
                stationary.atTarget.point()?.let { return estimated(it, path, supported = true) }
            }
            if (before != null && after != null && after.atMillis - before.atMillis <= PinPolicyV1.MAX_BRACKET_MILLIS) {
                if (before.atMillis == after.atMillis) return estimated(before.point, path, supported = true)
                val fraction = (target - before.atMillis).toDouble() / (after.atMillis - before.atMillis)
                val point = (PinVector.from(before.point) * (1 - fraction) + PinVector.from(after.point) * fraction).point()
                if (point != null) return estimated(point, path, supported = true)
            }
            // One-sided movement: use only the segment nearest the tap, with a bounded horizon.
            val anchor = before ?: after
            if (anchor != null && abs(anchor.atMillis - target) <= PinPolicyV1.PREDICTION_MILLIS) {
                val segment = path.filter {
                    abs(it.atMillis - anchor.atMillis) <= PinPolicyV1.FIT_WINDOW_MILLIS &&
                        if (before != null) it.atMillis <= target else it.atMillis >= target
                }
                val fit = fitPinMotion(segment, target)
                if (fit != null && fit.velocity.length() <= PinPolicyV1.MAX_SPEED_MPS &&
                    fit.residual <= PinPolicyV1.MAX_FIT_RESIDUAL_METERS
                ) {
                    fit.atTarget.point()?.let { point ->
                        if (pinDistance(anchor.point, point) <= PinPolicyV1.MAX_PREDICTION_METERS) {
                            return estimated(point, path, supported = true)
                        }
                    }
                }
            }
        }
        // Prefer the screened path to a most-recent jump. No age/accuracy gate can erase an action.
        val last = path.takeIf { it.size >= 3 }?.lastOrNull { it.atMillis <= target }
            ?: evidence.lastOrNull { it.atMillis <= target }
        if (last != null) return Candidate(last.point, ActionPinMethod.LAST_KNOWN, listOf(last.ref))
        // Only a future coordinate exists: a zero-motion estimate of the earlier tap, with UNKNOWN error.
        val firstAfter = path.firstOrNull() ?: evidence.firstOrNull()
        return if (firstAfter != null) estimated(firstAfter.point, listOf(firstAfter))
        else Candidate(null, ActionPinMethod.NONE, emptyList())
    }

    private fun estimated(point: GeoPoint, samples: List<ActionPinObservation>, supported: Boolean = false) =
        Candidate(point, ActionPinMethod.ESTIMATED, samples.map { it.ref }, supported)

    /** Remove the worst isolated spike first, so its neighbours are not all rejected with it. */
    private fun trimSpikes(samples: List<ActionPinObservation>): List<ActionPinObservation> {
        val kept = samples.toMutableList()
        while (kept.size > 3) {
            var worst = -1
            var worstResidual = PinPolicyV1.SPIKE_RESIDUAL_METERS
            for (i in 1 until kept.lastIndex) {
                val before = kept[i - 1]
                val after = kept[i + 1]
                val span = after.atMillis - before.atMillis
                if (span > PinPolicyV1.SPIKE_BRACKET_MILLIS) continue
                val fraction = (kept[i].atMillis - before.atMillis).toDouble() / span
                val expected = (PinVector.from(before.point) * (1 - fraction) + PinVector.from(after.point) * fraction).point() ?: continue
                val residual = pinDistance(expected, kept[i].point)
                if (residual > worstResidual) {
                    worst = i
                    worstResidual = residual
                }
            }
            if (worst < 0) break
            kept.removeAt(worst)
        }
        return kept
    }

    /** Longest plausible temporal subsequence; jump points can be skipped without poisoning later fixes. */
    private fun consistentPath(samples: List<ActionPinObservation>): List<ActionPinObservation> {
        if (samples.isEmpty()) return emptyList()
        val lengths = IntArray(samples.size) { 1 }
        val costs = DoubleArray(samples.size) { samples[it].modelAccuracy() }
        val previous = IntArray(samples.size) { -1 }
        for (i in samples.indices) {
            for (j in 0 until i) {
                val seconds = (samples[i].atMillis - samples[j].atMillis) / 1_000.0
                val noise = (samples[i].modelAccuracy() + samples[j].modelAccuracy()).coerceIn(6.0, 15.0)
                if (pinDistance(samples[i].point, samples[j].point) > PinPolicyV1.MAX_SPEED_MPS * seconds + noise) continue
                val length = lengths[j] + 1
                val cost = costs[j] + samples[i].modelAccuracy()
                if (length > lengths[i] || length == lengths[i] && cost < costs[i]) {
                    lengths[i] = length
                    costs[i] = cost
                    previous[i] = j
                }
            }
        }
        var index = samples.indices.maxWith(compareBy<Int>({ lengths[it] }, { -costs[it] }, { samples[it].atMillis }))
        val selected = mutableListOf<ActionPinObservation>()
        while (index >= 0) {
            selected.add(samples[index])
            index = previous[index]
        }
        return selected.asReversed()
    }

    private fun ActionPinObservation.modelAccuracy(): Double =
        accuracyMeters?.toDouble()?.takeIf { it.isFinite() && it > 0 } ?: Double.POSITIVE_INFINITY
}
