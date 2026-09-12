package com.daengs.app.ui.walk.review

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.TrailRecorder
import com.daengs.app.walk.TrailDecision
import com.daengs.app.walk.WalkPace
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Diagnostics of the actual historical recorder, never an alternative measurement policy. */
internal fun legacyReviewTrace(
    fixes: List<RecordedFix>,
    speedLimit: Double = WalkPace.MAX_METERS_PER_SECOND,
    minDistance: Double = 3.0,
): JSONObject {
    val recorder = TrailRecorder(maxSamples = Int.MAX_VALUE,
        maxSpeedMetersPerSecond = speedLimit, minDistanceMeters = minDistance)
    var decision: TrailDecision? = null
    recorder.onDecision = { decision = it }
    recorder.start()
    var chain: Int? = null
    val decisions = JSONArray()
    for (fix in fixes.sortedBy { it.clientSeq }) {
        val row = JSONObject().put("seq", fix.clientSeq).put("chain", fix.chainIndex).put("at_ms", fix.atMillis)
        if (fix.recordingEligible == false) {
            decisions.put(row.put("disposition", "recording_ineligible"))
            continue
        }
        if (chain != null && chain != fix.chainIndex) { recorder.pause(); recorder.resume() }
        chain = fix.chainIndex
        val sample = LocationSample(GeoPoint(fix.lat, fix.lng), fix.atMillis,
            accuracyMeters = fix.accuracyM, isMock = fix.isMock)
        val before = recorder.snapshot()
        val after = recorder.add(sample)
        val assessed = checkNotNull(decision).also { check(it.sample === sample) }
        val disposition = assessed.disposition.name.lowercase(Locale.ROOT)
        decisions.put(row.put("disposition", disposition)
            .put("distance_delta_m", after.distanceMeters - before.distanceMeters))
    }
    return JSONObject().put("distance_m", recorder.snapshot().distanceMeters)
        .put("retained_point_count", recorder.snapshot().sampleCount).put("decisions", decisions)
}
