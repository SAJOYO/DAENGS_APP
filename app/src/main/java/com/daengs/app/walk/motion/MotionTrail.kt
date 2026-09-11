package com.daengs.app.walk.motion

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.TrailSnapshot
import com.daengs.app.walk.TrackingState

/** Renders the engine's accepted edges, without another distance filter or a second raw index. */
internal class MotionTrail(private val maxSamples: Int = 5_000) {
    private val segments = ArrayDeque<ArrayDeque<LocationSample>>()
    private var lastRef: MotionRef? = null
    private var sampleCount = 0
    private var start: LocationSample? = null
    private var dirty = false
    private var rendered: List<List<LocationSample>> = emptyList()
    private var lowAccuracy = 0
    private var tooFast = 0

    init { require(maxSamples > 0) }

    fun accept(fix: RecordedFix, step: MotionStep) {
        val decision = step.decision ?: return
        lowAccuracy = if (MotionReason.POSITION_UNCERTAIN in decision.reasons) lowAccuracy + 1 else 0
        tooFast = if (MotionReason.HIGH_SPEED in decision.reasons || MotionReason.REENTRY_PENDING in decision.reasons)
            tooFast + 1 else 0
        if (decision.connection == Connection.SKIP) return
        val ref = requireNotNull(decision.toRef)
        check(ref.ingressSeq == fix.ingressSeq && ref.sourceEpoch == fix.sourceEpoch &&
            ref.clockEpochId == fix.clockEpochId && ref.chainIndex == fix.chainIndex)
        if (decision.connection == Connection.START_NEW) segments.addLast(ArrayDeque())
        else check(decision.distanceUse == DistanceUse.INCLUDE && decision.fromRef == lastRef)
        val sample = LocationSample(GeoPoint(fix.lat, fix.lng), fix.atMillis,
            elapsedRealtimeNanos = fix.elapsedRealtimeNanos, accuracyMeters = fix.accuracyM,
            speedMetersPerSecond = fix.speedMps, isMock = fix.isMock)
        if (start == null) start = sample
        segments.last().addLast(sample)
        lastRef = ref
        sampleCount++
        while (sampleCount > maxSamples) {
            segments.first().removeFirst()
            sampleCount--
            if (segments.first().isEmpty()) segments.removeFirst()
        }
        dirty = true
    }

    fun snapshot(state: TrackingState, distanceM: Double): TrailSnapshot {
        if (dirty) { rendered = segments.map { it.toList() }; dirty = false }
        return TrailSnapshot(state, rendered, distanceM, lowAccuracy, tooFast, start)
    }
}
