package com.daengs.app.location

import kotlinx.coroutines.flow.Flow

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

data class LocationSample(
    val point: GeoPoint,
    val capturedAtMillis: Long,
    val elapsedRealtimeNanos: Long? = null,
    val accuracyMeters: Float? = null,
    val speedMetersPerSecond: Float? = null,
    val isMock: Boolean = false,
    /** True-north travel course, not the direction the phone is facing. */
    val bearingDegrees: Float? = null,
    val bearingAccuracyDegrees: Float? = null,
    val speedAccuracyMetersPerSecond: Float? = null,
    val provider: String? = null,
)

data class LocationUpdateConfig(
    val intervalMillis: Long = 1_500,
    val minIntervalMillis: Long = 750,
    val minDistanceMeters: Float = 1f,
)

interface LocationSource {
    suspend fun currentLocation(): LocationSample

    /** Recording bypasses Flow buffers so numbering happens at the platform callback. */
    fun subscribeRecording(config: LocationUpdateConfig, onSample: (LocationSample) -> Unit,
        onFailure: (Throwable) -> Unit): LocationSubscription =
        throw UnsupportedOperationException("Recording subscription unavailable")

    fun locationUpdates(config: LocationUpdateConfig = LocationUpdateConfig()): Flow<LocationSample>
}


interface LocationSubscription {
    /** Wait for registration/removal; the caller closes its ingress gate before calling this. */
    suspend fun close()
}
