package com.daengs.app.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.location.LocationUpdateConfig

/** Experimental phone-observation thresholds, not a diagnosis of a dog's action. */
data class StayStampPolicy(
    val enterMeters: Double = 6.0,
    val exitMeters: Double = 10.0,
    val confirmMillis: Long = 30_000,
    val exitMillis: Long = 6_000,
    val maxGapMillis: Long = 10_000,
    val mergeMeters: Double = 6.0,
    val maxAccuracyMeters: Float = 10f,
) {
    init {
        require(enterMeters.isFinite() && enterMeters > 0)
        require(exitMeters.isFinite() && exitMeters > enterMeters)
        require(mergeMeters.isFinite() && mergeMeters > 0 && mergeMeters <= exitMeters)
        require(confirmMillis > 0 && exitMillis > 0 && maxGapMillis > 0)
        require(maxAccuracyMeters.isFinite() && maxAccuracyMeters > 0)
    }
}

/** One immutable observation window per place. No cumulative duration or visit count. */
data class StayStamp(
    val id: Int,
    val origin: GeoPoint,
    val startedAtMillis: Long,
    val confirmedAtMillis: Long,
)

// Request stationary observations only while recording a walk. Delivery is not guaranteed.
internal val WALK_OBSERVATION_CONFIG = LocationUpdateConfig(minDistanceMeters = 0f)

/**
 * Receives raw fixes before distance-based display pruning. Uses only fields kept
 * in RecordedFix so live and saved replay have the same evidence. Memory grows
 * with distinct places, never with the length of a stay. No wall-clock ticker.
 */
class StayStampRecorder(private val policy: StayStampPolicy = StayStampPolicy()) {
    private var stamps: List<StayStamp> = emptyList()
    private var candidate: LocationSample? = null
    private var activeOrigin: GeoPoint? = null
    private var outsideAt: Long? = null
    private var previous: LocationSample? = null
    private var latestAt = Long.MIN_VALUE

    fun snapshot(): List<StayStamp> = stamps

    fun reset() {
        stamps = emptyList()
        activeOrigin = null
        latestAt = Long.MIN_VALUE
        breakContinuity()
    }

    /** Pause/unknown reception breaks evidence, but is never proof of departure. */
    fun breakContinuity() {
        candidate = null
        previous = null
        outsideAt = null
    }

    fun add(sample: LocationSample) {
        val at = sample.capturedAtMillis
        if (at <= latestAt) { breakContinuity(); return }
        latestAt = at
        val accuracy = sample.accuracyMeters
        if (!sample.point.valid() || sample.isMock || accuracy == null ||
            !accuracy.isFinite() || accuracy < 0 || accuracy > policy.maxAccuracyMeters) {
            breakContinuity(); return
        }
        previous?.let { prior ->
            if (at - prior.capturedAtMillis > policy.maxGapMillis) breakContinuity()
        }
        previous?.let { prior ->
            val speed = prior.point.distanceTo(sample.point) / ((at - prior.capturedAtMillis) / 1000.0)
            if (speed > WalkPace.MAX_METERS_PER_SECOND) { breakContinuity(); return }
        }
        previous = sample
        activeOrigin?.let { origin ->
            if (origin.distanceTo(sample.point) <= policy.exitMeters) { outsideAt = null; return }
            val outside = outsideAt ?: at.also { outsideAt = it }
            if (at - outside < policy.exitMillis) return
            activeOrigin = null
            outsideAt = null
        }
        if (candidate == null || candidate!!.point.distanceTo(sample.point) > policy.enterMeters) {
            candidate = sample
        }
        val start = checkNotNull(candidate)
        if (at - start.capturedAtMillis < policy.confirmMillis) return
        if (stamps.none { it.origin.distanceTo(start.point) <= policy.mergeMeters }) {
            stamps = stamps + StayStamp(stamps.size + 1, start.point, start.capturedAtMillis, at)
        }
        activeOrigin = start.point
        candidate = null
    }
}

private fun GeoPoint.valid(): Boolean = latitude.isFinite() && longitude.isFinite() &&
    latitude in -90.0..90.0 && longitude in -180.0..180.0

/** Same state machine for completion and history. Chain changes mean pause/resume. */
fun detectStayStamps(fixes: List<RecordedFix>): List<StayStamp> {
    val recorder = StayStampRecorder()
    var chain: Int? = null
    for (fix in fixes.filter { it.recordingEligible != false }.sortedBy { it.clientSeq }) {
        if (chain != null && fix.chainIndex != chain) recorder.breakContinuity()
        chain = fix.chainIndex
        recorder.add(LocationSample(GeoPoint(fix.lat, fix.lng), fix.atMillis,
            accuracyMeters = fix.accuracyM, isMock = fix.isMock))
    }
    return recorder.snapshot()
}
