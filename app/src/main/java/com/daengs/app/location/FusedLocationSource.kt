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

    /**
     * 지금 위치 한 번.
     *
     * **새 좌표를 못 구하면 마지막으로 알던 곳이라도 준다.** `getCurrentLocation` 은
     * 실내이거나 대략적 위치 권한만 있을 때 그냥 null 을 돌려주는데, 그때 실패로
     * 끝내면 지도가 **네이버 기본 카메라(서울시청)** 에 앉은 채로 남는다 —
     * 사용자에게는 앱이 자기를 시청에 데려다 놓은 것으로 보인다.
     *
     * 오래된 좌표라도 "내가 아는 마지막 자리"가 시청보다 낫다. 얼마나 오래됐는지는
     * [LocationSample.capturedAtMillis] 에 그대로 실려 간다.
     */
    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): LocationSample = suspendCancellableCoroutine { continuation ->
        val cancellation = CancellationTokenSource()

        fun fallbackToLastKnown() {
            client.lastLocation
                .addOnSuccessListener { last ->
                    if (!continuation.isActive) return@addOnSuccessListener
                    if (last == null) {
                        continuation.resumeWithException(
                            IllegalStateException(
                                "현재 위치를 가져오지 못했습니다. 위치 설정을 확인해주세요.",
                            ),
                        )
                    } else {
                        continuation.resume(last.toSample())
                    }
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
        }

        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
            .addOnSuccessListener { location ->
                if (!continuation.isActive) return@addOnSuccessListener
                if (location == null) fallbackToLastKnown() else continuation.resume(location.toSample())
            }
            .addOnFailureListener {
                if (continuation.isActive) fallbackToLastKnown()
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
        // 주면 "invalid null looper" 로 거절한다. 수집 코루틴의 dispatcher가 바뀌어도
        // 위치 콜백의 실행 위치가 함께 바뀌지 않도록 메인 Looper를 계약으로 고정한다.
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
