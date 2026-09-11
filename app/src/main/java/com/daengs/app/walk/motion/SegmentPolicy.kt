package com.daengs.app.walk.motion

import kotlin.math.max

/** Keeps one noise anchor and one recent valid point, independently from speed estimation. */
internal class SegmentPolicy(private val config: MotionConfig, private val version: String) {
    private var anchor: MotionPoint? = null
    private var lastUsable: MotionPoint? = null
    private val barriers = linkedSetOf<MotionReason>()
    private var needsReentry = false
    private var reentryCount = 0
    private var reentrySince: Long? = null
    var distanceM = 0.0
        private set
    var segmentCount = 0L
        private set

    fun breakConnection(reason: MotionReason, highSpeed: Boolean = false) {
        anchor = null
        lastUsable = null
        barriers += reason
        needsReentry = needsReentry || highSpeed
        reentryCount = 0
        reentrySince = null
    }

    fun decide(point: MotionPoint, estimate: MotionEstimate): SegmentDecision {
        if (estimate.speedMps != null && estimate.speedMps > config.maxWalkingSpeedMps) {
            breakConnection(MotionReason.HIGH_SPEED, highSpeed = true)
            return skip(point.ref, estimate.reasons + MotionReason.HIGH_SPEED)
        }
        if (estimate.positionQuality != PositionQuality.USABLE) {
            reentryCount = 0
            reentrySince = null
            return skip(point.ref, estimate.reasons)
        }
        val last = lastUsable
        if (last != null && seconds(last.time, point.time) > config.maxGapSeconds) {
            breakConnection(MotionReason.OBSERVATION_GAP)
        }
        lastUsable = point
        if (needsReentry) {
            val plausible = estimate.speedMps != null && estimate.speedQuality != SpeedQuality.UNVERIFIED
            if (!plausible) {
                reentryCount = 0; reentrySince = null
            } else {
                if (reentryCount == 0) reentrySince = point.time
                reentryCount = minOf(config.reentrySamples, reentryCount + 1)
            }
            if (!plausible || reentryCount < config.reentrySamples ||
                seconds(requireNotNull(reentrySince), point.time) < config.reentrySeconds) {
                return skip(point.ref, estimate.reasons + MotionReason.REENTRY_PENDING)
            }
            needsReentry = false
        }

        val from = anchor
        if (from == null) {
            anchor = point
            segmentCount++
            val reasons = if (barriers.isEmpty()) setOf(MotionReason.FIRST_POINT) else barriers.toSet()
            barriers.clear()
            return SegmentDecision(segmentCount - 1, null, point.ref, DistanceUse.EXCLUDE, 0.0,
                Connection.START_NEW, null, estimate.reasons + reasons, version)
        }

        val delta = from.distanceTo(point)
        val dt = seconds(from.time, point.time)
        val segmentSpeed = delta / dt
        // The segment may be fast even when the new point reports an instantaneous speed of zero.
        val lowerSpeed = (delta - from.accuracy - point.accuracy).coerceAtLeast(0.0) / dt
        if (lowerSpeed > config.maxWalkingSpeedMps) {
            breakConnection(MotionReason.HIGH_SPEED, highSpeed = true)
            return skip(point.ref, estimate.reasons + MotionReason.HIGH_SPEED, segmentSpeed)
        }
        if (delta > config.maxJumpM) {
            breakConnection(MotionReason.JUMP)
            return skip(point.ref, estimate.reasons + MotionReason.JUMP, segmentSpeed)
        }
        val floor = max(config.minDistanceM, (from.accuracy + point.accuracy) * config.noiseRadiusFactor)
        if (delta <= 0.0 || delta < floor) return skip(point.ref, estimate.reasons + MotionReason.BELOW_NOISE_FLOOR, segmentSpeed)
        anchor = point
        distanceM += delta
        return SegmentDecision(segmentCount - 1, from.ref, point.ref, DistanceUse.INCLUDE, delta,
            Connection.CONTINUE, segmentSpeed, estimate.reasons, version)
    }

    fun skip(ref: MotionRef?, reasons: Set<MotionReason>, speed: Double? = null) =
        SegmentDecision(null, null, ref, DistanceUse.EXCLUDE, 0.0, Connection.SKIP, speed, reasons + barriers, version)
}
