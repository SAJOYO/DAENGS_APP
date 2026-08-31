package com.daengs.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import androidx.core.location.LocationCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine

class FusedLocationSource(context: Context) : LocationSource {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): LocationSample = suspendCancellableCoroutine { continuation ->
        val cancellation = CancellationTokenSource()
        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
            .addOnSuccessListener { location ->
                if (!continuation.isActive) return@addOnSuccessListener
                if (location == null) {
                    continuation.resumeWithException(
                        IllegalStateException("현재 위치를 가져오지 못했습니다. 위치 설정을 확인해주세요."),
                    )
                } else {
                    continuation.resume(location.toSample())
                }
            }
            .addOnFailureListener { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        continuation.invokeOnCancellation { cancellation.cancel() }
    }

    @SuppressLint("MissingPermission")
    override fun locationUpdates(config: LocationUpdateConfig): Flow<LocationSample> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, config.intervalMillis)
            .setMinUpdateIntervalMillis(config.minIntervalMillis)
            .setMinUpdateDistanceMeters(config.minDistanceMeters)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { trySend(it.toSample()) }
            }
        }
        // **null 을 넘기면 안 된다.** 콜백은 전달받을 Looper 가 필요한데, GMS 는 null 을
        // 주면 "invalid null looper" 로 거절한다 — *부르는 스레드* 가 자기 Looper 를
        // 갖고 있을 때만 봐준다. 이 flow 는 산책 서비스의 Dispatchers.Default 에서
        // 모으는데 거기엔 Looper 가 없다. 그래서 구독이 즉시 실패하고, 산책마다
        // 위치를 한 번도 못 받은 채 스스로 멈췄다.
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnFailureListener { close(it) }
        awaitClose { client.removeLocationUpdates(callback) }
    }
}

private fun Location.toSample(): LocationSample = LocationSample(
    point = GeoPoint(latitude = latitude, longitude = longitude),
    capturedAtMillis = time,
    elapsedRealtimeNanos = elapsedRealtimeNanos,
    accuracyMeters = accuracy.takeIf { hasAccuracy() },
    speedMetersPerSecond = speed.takeIf { hasSpeed() },
    isMock = LocationCompat.isMock(this),
)
