package com.daengs.app.walk.diary

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.WalkMeasurementDetail
import com.daengs.app.walk.measurementRef
import com.daengs.app.walk.WalkSummary
import com.daengs.app.walk.distanceTo
import org.json.JSONObject
import java.time.Instant

/** The actual uploaded observation chosen by the server, never a route-distance interpolation. */
data class StoryboardObservation(
    val clientSeq: Int, val chainIndex: Int, val atMillis: Long, val point: GeoPoint,
) {
    companion object {
        fun parse(obj: JSONObject): StoryboardObservation {
            obj.exactKeys("client_seq", "chain_index", "at", "lat", "lng")
            val lat = obj.getDouble("lat"); val lng = obj.getDouble("lng")
            require(lat.isFinite() && lat in -90.0..90.0 && lng.isFinite() && lng in -180.0..180.0)
            return StoryboardObservation(obj.strictLong("client_seq", 0, Int.MAX_VALUE.toLong()).toInt(),
                obj.strictLong("chain_index", 0, Int.MAX_VALUE.toLong()).toInt(),
                Instant.parse(obj.getString("at")).toEpochMilli(), GeoPoint(lat, lng))
        }
    }
}

/** A one-session index. Same-place revisits must resolve to their own sequence and timestamp. */
internal class StoryboardObservationIndex(walk: WalkSummary, observations: List<RecordedFix>,
    private val measurement: WalkMeasurementDetail? = null) {
    private val sessionId = walk.sessionId
    private val start = walk.startedAtMillis
    private val end = walk.endedAtMillis
    private val fixes = observations.groupBy { it.clientSeq }

    fun resolve(anchor: StoryboardObservation?): GeoPoint? {
        anchor ?: return null
        val fix = fixes[anchor.clientSeq]?.singleOrNull() ?: return null
        if (end == null || fix.isMock ||
            fix.chainIndex != anchor.chainIndex || fix.atMillis != anchor.atMillis ||
            !fix.lat.isFinite() || !fix.lng.isFinite()) return null
        if (measurement == null) {
            if (anchor.atMillis !in start..end) return null
        } else {
            // The adopted measurement already checked epoch/monotonic recording membership.
            // Wall-clock correction must not erase a source address before scene binding.
            if (measurement.ownerId.isBlank() || fix.sourceEpoch == null || fix.clockEpochId == null ||
                fix.elapsedRealtimeNanos == null || fix.recordingEligible != true ||
                fix.measurementRef(sessionId) !in measurement.usableSources) return null
        }
        val point = GeoPoint(fix.lat, fix.lng)
        // Server chunk coordinates are quantized. Allow at most 1 m of serialization difference.
        return point.takeIf { it.distanceTo(anchor.point) <= 1.0 }
    }
}
