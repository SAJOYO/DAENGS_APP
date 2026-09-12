package com.daengs.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Build
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
    override fun subscribeRecording(config: LocationUpdateConfig, onSample: (LocationSample) -> Unit,
        onFailure: (Throwable) -> Unit): LocationSubscription {
        val closed = java.util.concurrent.atomic.AtomicBoolean(false)
        val executor = java.util.concurrent.ThreadPoolExecutor(1, 1, 0L,
            java.util.concurrent.TimeUnit.MILLISECONDS, java.util.concurrent.ArrayBlockingQueue(64),
            java.util.concurrent.ThreadFactory { task -> Thread(task, "walk-location").apply { isDaemon = true } },
            java.util.concurrent.RejectedExecutionHandler { _, _ ->
                if (!closed.get()) onFailure(IllegalStateException("SOURCE_DELIVERY_FAILURE"))
            })
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (closed.get()) return
                try { result.locations.forEach { onSample(it.toSample()) } }
                catch (error: Exception) { onFailure(error) }
            }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, config.intervalMillis)
            .setMinUpdateIntervalMillis(config.minIntervalMillis)
            .setMinUpdateDistanceMeters(config.minDistanceMeters).build()
        val registration = try { client.requestLocationUpdates(request, executor, callback) }
        catch (error: Exception) { executor.shutdown(); throw error }
        registration.addOnFailureListener { if (!closed.get()) onFailure(it) }
        // A late registration after close/timeout must not resurrect the subscription.
        registration.addOnSuccessListener { if (closed.get()) client.removeLocationUpdates(callback) }
        return object : LocationSubscription {
            override suspend fun close() {
                closed.set(true)
                try {
                    registration.awaitCompletion()
                    client.removeLocationUpdates(callback).awaitCompletion()
                } finally { executor.shutdown() }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun locationUpdates(config: LocationUpdateConfig): Flow<LocationSample> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, config.intervalMillis)
            .setMinUpdateIntervalMillis(config.minIntervalMillis)
            .setMinUpdateDistanceMeters(config.minDistanceMeters)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach {
                    if (!trySend(it.toSample()).isSuccess) {
                        close(IllegalStateException("Location delivery buffer unavailable"))
                        return
                    }
                }
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

internal fun Location.toSample(): LocationSample = LocationSample(
    point = GeoPoint(latitude = latitude, longitude = longitude),
    capturedAtMillis = time,
    elapsedRealtimeNanos = elapsedRealtimeNanos,
    accuracyMeters = accuracy.takeIf { hasAccuracy() },
    speedMetersPerSecond = speed.takeIf { hasSpeed() },
    speedAccuracyMetersPerSecond = speedAccuracyMetersPerSecond.takeIf { hasSpeedAccuracy() },
    provider = provider,
    bearingDegrees = bearing.takeIf { hasBearing() },
    bearingAccuracyDegrees = bearingAccuracyDegrees.takeIf { hasBearingAccuracy() },
    // 일부 AVD의 `adb emu geo fix`는 플랫폼 mock 표식 없이 내려온다. 그 값만 믿으면
    // 검증 산책이 실제 기기 증거로 저장·업로드되므로 실행 환경까지 함께 판정한다.
    isMock = isMockEvidence(
        platformReportedMock = LocationCompat.isMock(this),
        fingerprint = Build.FINGERPRINT,
        model = Build.MODEL,
        manufacturer = Build.MANUFACTURER,
        device = Build.DEVICE,
        product = Build.PRODUCT,
    ),
)

internal fun isMockEvidence(
    platformReportedMock: Boolean,
    fingerprint: String,
    model: String,
    manufacturer: String,
    device: String,
    product: String,
): Boolean = platformReportedMock ||
    fingerprint.startsWith("generic", ignoreCase = true) ||
    model.contains("emulator", ignoreCase = true) ||
    model.startsWith("sdk_", ignoreCase = true) ||
    manufacturer.contains("genymotion", ignoreCase = true) ||
    device.startsWith("emu", ignoreCase = true) ||
    product.startsWith("sdk_", ignoreCase = true)


private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitCompletion(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
        addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
        addOnCanceledListener { continuation.cancel() }
    }
