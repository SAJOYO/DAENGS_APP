package com.daengs.app.location

/** Ephemeral display data; never persisted as walk evidence. */
data class TravelHeading(val degrees: Float, val elapsedRealtimeNanos: Long) {
    fun remainingMillis(nowElapsedRealtimeNanos: Long): Long {
        if (elapsedRealtimeNanos <= 0 || nowElapsedRealtimeNanos < elapsedRealtimeNanos) return 0
        val ageMillis = (nowElapsedRealtimeNanos - elapsedRealtimeNanos) / 1_000_000
        return (10_000L - ageMillis).coerceAtLeast(0)
    }
}

/** Do not turn missing course into north, or stationary GPS drift into a direction. */
fun LocationSample.travelHeading(): TravelHeading? {
    val bearing = bearingDegrees ?: return null
    val speed = speedMetersPerSecond ?: return null
    val accuracy = accuracyMeters ?: return null
    val elapsed = elapsedRealtimeNanos?.takeIf { it > 0 } ?: return null
    if (!bearing.isFinite() || !speed.isFinite() || speed < 0.5f || accuracy !in 0f..25f) return null
    if (bearingAccuracyDegrees != null && bearingAccuracyDegrees !in 0f..45f) return null
    return TravelHeading(((bearing % 360f) + 360f) % 360f, elapsed)
}
