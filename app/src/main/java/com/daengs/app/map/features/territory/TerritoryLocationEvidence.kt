package com.daengs.app.map.features.territory

import com.daengs.app.location.LocationSample
import com.daengs.app.territory.TerritorySite
import com.daengs.app.territory.evaluateTerritoryProximity
import com.daengs.app.walk.TrackingState
import com.daengs.app.walk.WalkTrackingState

internal data class TerritoryLocationEvidence(val sample: LocationSample?, val trusted: Boolean) {
    fun proximity(site: TerritorySite, radiusMeters: Double) = evaluateTerritoryProximity(
        trusted,
        sample?.point?.distanceMetersTo(site.point) ?: Double.NaN,
        sample?.accuracyMeters?.toDouble() ?: Double.NaN,
        radiusMeters,
    )
}

/** A screen fix can explain browsing/paused proximity, but cannot rescue a rejected recording fix. */
internal fun territoryLocationEvidence(
    tracking: WalkTrackingState,
    permitted: Boolean,
    nowNanos: Long,
    maxFixAgeNanos: Long,
    screenSample: LocationSample? = null,
): TerritoryLocationEvidence {
    val recording = tracking.trail.state == TrackingState.RECORDING
    val sample = if (recording) tracking.latestMomentFix else screenSample ?: tracking.latestMomentFix
    val age = sample?.elapsedRealtimeNanos?.let { nowNanos - it }
    val coordinatesValid = sample?.point?.let {
        it.latitude.isFinite() && it.latitude in -90.0..90.0 &&
            it.longitude.isFinite() && it.longitude in -180.0..180.0
    } == true
    val recordingQuality = !recording || (tracking.lastSample?.isMock != true &&
        tracking.trail.skippedTooFast == 0 && tracking.trail.skippedLowAccuracy == 0 &&
        tracking.errorMessage == null)
    return TerritoryLocationEvidence(sample, permitted && sample != null && !sample.isMock &&
        coordinatesValid && age != null && age in 0..maxFixAgeNanos && recordingQuality)
}
