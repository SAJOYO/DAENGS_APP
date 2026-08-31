package com.daengs.app.walk

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.daengs.app.DaengsApp
import com.daengs.app.MainActivity
import com.daengs.app.R
import com.daengs.app.location.FeedStatus
import com.daengs.app.location.LocationSample
import com.daengs.app.location.LocationSource
import com.daengs.app.location.LocationTracker
import com.daengs.app.miniroom.OutsideApi
import com.daengs.app.miniroom.OutsideTime
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * 화면 생명주기와 독립적으로 사용자가 시작한 산책 위치를 기록한다.
 *
 * 기기가 보고한 원본 fix는 화면용 필터보다 먼저 Room writer에 넘긴다. 서버 전송과 점수
 * 계산은 이 서비스의 책임이 아니며, 명시적인 종료만 저장 세션을 닫는다.
 */
class WalkTrackingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val recorder = TrailRecorder()

    private lateinit var locationSource: LocationSource
    private lateinit var tracker: LocationTracker
    private lateinit var store: WalkTrackingStore
    private lateinit var writer: WalkFixWriter

    private val sessionLock = Any()
    private var sessionId: String? = null
    private var nextClientSeq = 0
    private var chainIndex = 0
    private var activeDurationMillis = 0L
    private var activeSinceRealtimeMillis: Long? = null

    override fun onCreate() {
        super.onCreate()
        val runtime = (application as DaengsApp).walkRuntime
        locationSource = runtime.locationSource
        store = runtime.store
        writer = runtime.writer
        tracker = LocationTracker(serviceScope)
        createNotificationChannel()

        serviceScope.launch { tracker.updates.collect(::acceptLocation) }
        serviceScope.launch { tracker.status.collect(::acceptFeedStatus) }
        serviceScope.launch { writer.failure.filterNotNull().collect(::acceptStorageFailure) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording(intent.getStringExtra(EXTRA_DOG_ID))
            ACTION_PAUSE -> pauseRecording(startId)
            ACTION_RESUME -> resumeRecording(startId)
            ACTION_STOP -> stopRecording(startId)
            else -> stopIfInactive(startId)
        }
        // 복구 정책 없이 프로세스가 살아난 것만으로 산책을 재개하지 않는다.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        tracker.stop()
        if (recorder.snapshot().state != TrackingState.OFF) {
            pauseTiming()
            store.publish(
                trackingState(
                    trail = recorder.pause(),
                    lastSample = store.state.value.lastSample,
                    errorMessage = "산책 기록 서비스가 종료되었습니다.",
                ),
            )
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startRecording(dogId: String?) {
        if (recorder.snapshot().state != TrackingState.OFF) {
            promote(recorder.snapshot(), store.state.value.errorMessage)
            return
        }
        writer.clearFailure()
        activeDurationMillis = 0L
        activeSinceRealtimeMillis = SystemClock.elapsedRealtime()
        val trail = recorder.start()
        openSession(dogId)
        store.publish(trackingState(trail = trail, lastSample = null))
        // Android 14+는 위치 구독 전에 location 타입 FGS가 승격됐는지 검사한다.
        promote(trail, errorMessage = null)
        tracker.start(locationSource)
    }

    private fun pauseRecording(startId: Int) {
        if (recorder.snapshot().state != TrackingState.RECORDING) {
            stopIfInactive(startId)
            return
        }
        tracker.stop()
        pauseTiming()
        val trail = recorder.pause()
        store.publish(trackingState(trail))
        promote(trail, errorMessage = null)
    }

    private fun resumeRecording(startId: Int) {
        if (recorder.snapshot().state != TrackingState.PAUSED) {
            stopIfInactive(startId)
            return
        }
        val trail = recorder.resume()
        activeSinceRealtimeMillis = SystemClock.elapsedRealtime()
        synchronized(sessionLock) { chainIndex += 1 }
        store.publish(trackingState(trail))
        promote(trail, errorMessage = null)
        tracker.start(locationSource)
    }

    private fun stopRecording(stopStartId: Int) {
        if (recorder.snapshot().state == TrackingState.OFF) {
            stopIfInactive(stopStartId)
            return
        }
        tracker.stop()
        pauseTiming()
        closeSession()
        val trail = recorder.stop()
        store.publish(trackingState(trail))
        serviceScope.launch {
            try {
                writer.flush()
            } finally {
                synchronized(sessionLock) {
                    // flush 중 새 산책이 시작됐다면 새 알림까지 지우지 않는다. 새 startId가
                    // 있으면 Android가 stopSelf 호출을 무시하지만 foreground 제거는 따로
                    // 막아야 한다.
                    if (sessionId == null && recorder.snapshot().state == TrackingState.OFF) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf(stopStartId)
                    }
                }
            }
        }
    }

    /** 잘못 전달된 제어 명령이 비활성 서비스를 시작된 상태로 남기지 않게 한다. */
    private fun stopIfInactive(startId: Int) {
        if (recorder.snapshot().state == TrackingState.OFF) stopSelf(startId)
    }

    private fun acceptLocation(sample: LocationSample) {
        synchronized(sessionLock) {
            sessionId?.let { id ->
                writer.append(
                    id,
                    RecordedFix(
                        clientSeq = nextClientSeq++,
                        chainIndex = chainIndex,
                        atMillis = sample.capturedAtMillis,
                        lat = sample.point.latitude,
                        lng = sample.point.longitude,
                        accuracyM = sample.accuracyMeters,
                        isMock = sample.isMock,
                    ),
                )
            }
        }
        store.publish(trackingState(trail = recorder.add(sample), lastSample = sample))
    }

    private fun openSession(dogId: String?) {
        val id = UUID.randomUUID().toString()
        synchronized(sessionLock) {
            sessionId = id
            nextClientSeq = 0
            chainIndex = 0
            writer.openSession(
                RecordedSession(
                    id = id,
                    dogId = dogId,
                    startedAtMillis = System.currentTimeMillis(),
                ),
            )
        }
        stampWeather(id)
    }

    /**
     * 그날 나갈 때의 날씨를 세션에 박는다.
     *
     * **시작할 때 한 번만 찍는다.** 두 시간 걸으면 날씨가 바뀌는데, 기록에 남길 값은
     * "나갈 때 어땠나"다. 끝날 때 찍으면 비 맞고 걸은 산책이 "맑음"으로 남는다.
     *
     * 못 받으면 아무것도 안 쓴다 — **모르는 것을 "맑음"으로 채우지 않는다.**
     * 좌표가 아직 없을 수도 있어서(첫 fix 전) 마지막으로 알던 위치를 쓴다.
     */
    private fun stampWeather(sessionId: String) {
        serviceScope.launch {
            val point = runCatching { locationSource.currentLocation().point }.getOrNull()
                ?: return@launch
            val now = OutsideApi.fetchNow(point.latitude, point.longitude) ?: return@launch
            writer.stampWeather(
                sessionId,
                RecordedWeather(
                    weatherCode = now.weatherCode,
                    isDay = now.time == OutsideTime.DAY,
                    temperatureC = now.temperatureC,
                ),
            )
        }
    }

    private fun closeSession() = synchronized(sessionLock) {
        sessionId?.let { writer.closeSession(it, System.currentTimeMillis()) }
        sessionId = null
    }

    private fun acceptFeedStatus(status: FeedStatus) {
        when (status) {
            is FeedStatus.Failed -> pauseAfterFeedProblem(
                status.cause.message ?: "위치를 계속 받을 수 없습니다.",
            )
            FeedStatus.Completed -> if (recorder.snapshot().state == TrackingState.RECORDING) {
                pauseAfterFeedProblem("위치 업데이트가 종료되어 산책 기록을 일시정지했습니다.")
            }
            FeedStatus.Idle, FeedStatus.Running -> Unit
        }
    }

    private fun pauseAfterFeedProblem(message: String) {
        // stop 직후 늦게 배달된 feed 실패가 종료된 기록을 다시 foreground로 올리면 안 된다.
        if (recorder.snapshot().state != TrackingState.RECORDING) return
        tracker.stop()
        pauseTiming()
        val trail = recorder.pause()
        store.publish(trackingState(trail, errorMessage = message))
        promote(trail, message)
    }

    private fun acceptStorageFailure(message: String) {
        val trail = recorder.snapshot()
        if (trail.state == TrackingState.OFF) return
        store.publish(trackingState(trail, errorMessage = message))
        promote(trail, message)
    }

    private fun pauseTiming() {
        val startedAt = activeSinceRealtimeMillis ?: return
        activeDurationMillis += (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
        activeSinceRealtimeMillis = null
    }

    private fun trackingState(
        trail: TrailSnapshot,
        lastSample: LocationSample? = store.state.value.lastSample,
        errorMessage: String? = null,
    ): WalkTrackingState = WalkTrackingState(
        trail = trail,
        lastSample = lastSample,
        errorMessage = errorMessage,
        activeDurationMillis = activeDurationMillis,
        activeSinceRealtimeMillis = activeSinceRealtimeMillis,
    )

    private fun promote(trail: TrailSnapshot, errorMessage: String?) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(trail, errorMessage),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
    }

    private fun buildNotification(trail: TrailSnapshot, errorMessage: String?): Notification {
        val text = errorMessage ?: when (trail.state) {
            TrackingState.RECORDING -> "산책 동선을 기록하고 있어요."
            TrackingState.PAUSED -> "산책 기록이 일시정지됐어요."
            TrackingState.OFF -> "산책 기록을 마쳤어요."
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_walk_notification)
            .setContentTitle("댕스 산책 기록")
            .setContentText(text)
            .setContentIntent(openAppPendingIntent())
            .setOnlyAlertOnce(true)
            .setOngoing(trail.state != TrackingState.OFF)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .apply {
                when (trail.state) {
                    TrackingState.RECORDING -> addAction(
                        R.drawable.ic_walk_notification,
                        "일시정지",
                        servicePendingIntent(ACTION_PAUSE, REQUEST_PAUSE),
                    )
                    TrackingState.PAUSED -> addAction(
                        R.drawable.ic_walk_notification,
                        "계속 기록",
                        servicePendingIntent(ACTION_RESUME, REQUEST_RESUME),
                    )
                    TrackingState.OFF -> Unit
                }
                if (trail.state != TrackingState.OFF) {
                    addAction(
                        R.drawable.ic_walk_notification,
                        "종료",
                        servicePendingIntent(ACTION_STOP, REQUEST_STOP),
                    )
                }
            }
            .build()
    }

    private fun openAppPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        REQUEST_OPEN,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            commandIntent(this, action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "산책 기록",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "산책 중 위치 기록 상태를 표시합니다." },
        )
    }

    companion object {
        const val ACTION_START = "com.daengs.app.walk.START"
        const val ACTION_PAUSE = "com.daengs.app.walk.PAUSE"
        const val ACTION_RESUME = "com.daengs.app.walk.RESUME"
        const val ACTION_STOP = "com.daengs.app.walk.STOP"

        private const val CHANNEL_ID = "walk_tracking"
        private const val NOTIFICATION_ID = 4101
        private const val REQUEST_OPEN = 4101
        private const val REQUEST_PAUSE = 4102
        private const val REQUEST_RESUME = 4103
        private const val REQUEST_STOP = 4104

        const val EXTRA_DOG_ID = "dogId"

        fun commandIntent(context: Context, action: String): Intent =
            Intent(context, WalkTrackingService::class.java).setAction(action)
    }
}
