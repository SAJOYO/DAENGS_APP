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
import android.util.Log
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
import com.daengs.app.walk.sync.WalkDeliveryScheduler
import com.daengs.app.walk.sync.completeAndEnqueueWalk
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
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
    // 위치와 사용자 명령을 한 스레드에서 직렬 처리한다. 액션을 추가하는 순간 위치 갱신이
    // 이전 momentGroups 상태를 덮는 경쟁을 만들지 않게 하는 세션 경계다.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val recorder = TrailRecorder()
    private val stayRecorder = StayStampRecorder()

    private lateinit var locationSource: LocationSource
    private lateinit var tracker: LocationTracker
    private lateinit var store: WalkTrackingStore
    private lateinit var writer: WalkFixWriter
    private lateinit var delivery: WalkDeliveryScheduler

    /** 판정하려면 방금 쓴 것을 되읽어야 한다. writer 는 쓰기 전용이라 따로 든다. */
    private lateinit var log: WalkFixLog

    private val sessionLock = Any()
    private var sessionId: String? = null
    private var sessionStartedAtMillis: Long? = null
    private var sessionOwnerId: String? = null
    private var sessionDogIds: List<String> = emptyList()
    private var nextClientSeq = 0
    private var chainIndex = 0
    private var directPinRef: com.daengs.app.walk.pin.ActionPinSourceRef? = null
    private var activeDurationMillis = 0L
    private var activeSinceRealtimeMillis: Long? = null

    override fun onCreate() {
        super.onCreate()
        val runtime = (application as DaengsApp).walkRuntime
        locationSource = runtime.locationSource
        store = runtime.store
        writer = runtime.writer
        delivery = runtime.delivery
        log = runtime.log
        tracker = LocationTracker(serviceScope)
        createNotificationChannel()

        serviceScope.launch { tracker.updates.collect(::acceptLocation) }
        serviceScope.launch { tracker.status.collect(::acceptFeedStatus) }
        serviceScope.launch { writer.failure.filterNotNull().collect(::acceptStorageFailure) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START ->
                startRecording(intent.getStringArrayListExtra(EXTRA_DOG_IDS).orEmpty())
            ACTION_PAUSE -> pauseRecording(startId)
            ACTION_RESUME -> resumeRecording(startId)
            ACTION_STOP -> stopRecording(startId)
            ACTION_RECORD_MOMENT -> recordMoment(
                intent.getStringExtra(EXTRA_MOMENT_TYPE)
                    ?.let { code -> WalkMomentType.entries.firstOrNull { it.behaviorCode == code } },
                startId,
            )
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

    private fun startRecording(dogIds: List<String>) {
        if (recorder.snapshot().state != TrackingState.OFF) {
            promote(recorder.snapshot(), store.state.value.errorMessage)
            return
        }
        sessionOwnerId = log.ownerId
        writer.clearFailure()
        activeDurationMillis = 0L
        activeSinceRealtimeMillis = SystemClock.elapsedRealtime()
        stayRecorder.reset()
        val trail = recorder.start()
        openSession(dogIds)
        store.publish(
            trackingState(
                trail = trail,
                lastSample = null,
                latestMomentFix = null,
                momentGroups = emptyList(),
            ),
        )
        // Android 14+는 위치 구독 전에 location 타입 FGS가 승격됐는지 검사한다.
        promote(trail, errorMessage = null)
        tracker.start(locationSource, WALK_OBSERVATION_CONFIG)
    }

    private fun pauseRecording(startId: Int) {
        if (recorder.snapshot().state != TrackingState.RECORDING) {
            stopIfInactive(startId)
            return
        }
        tracker.stop()
        val cutoff = System.currentTimeMillis()
        sessionId?.let { id -> writer.ordered { (application as DaengsApp).actionPins.finishSession(id, cutoff) } }
        pauseTiming()
        stayRecorder.breakContinuity()
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
        synchronized(sessionLock) { chainIndex += 1; directPinRef = null }
        // 일시정지 전에 받은 좌표로 재개 직후 행동을 찍지 않는다. 새 fix가 올 때까지
        // 행동은 fallback으로 저장한다.
        store.publish(trackingState(trail, latestMomentFix = null))
        promote(trail, errorMessage = null)
        tracker.start(locationSource, WALK_OBSERVATION_CONFIG)
    }

    private fun stopRecording(stopStartId: Int) {
        if (recorder.snapshot().state == TrackingState.OFF) {
            stopIfInactive(stopStartId)
            return
        }
        tracker.stop()
        pauseTiming()
        // 지우려면 어느 세션인지 알아야 하는데, closeSession() 이 비워 버린다.
        val finished = synchronized(sessionLock) { sessionId }
        val cutoff = System.currentTimeMillis()
        finished?.let { id -> writer.ordered { (application as DaengsApp).actionPins.finishSession(id, cutoff) } }
        closeSession()
        val trail = recorder.stop()
        store.publish(trackingState(trail, finishingSessionId = finished))
        serviceScope.launch {
            try {
                completeAndEnqueueWalk(
                    sessionId = finished,
                    complete = {
                        writer.flush()
                        // **flush 뒤에 판정한다.** 좌표가 다 저장되기 전에 재면 방금 걸은
                        // 거리가 0 으로 보여서, 멀쩡한 산책을 지운다.
                        finished?.let { finishOrDiscard(it) } == true
                    },
                    enqueue = delivery::enqueue,
                    onCompletionFailure = { error ->
                        publishCompletionFailure(error.message ?: "산책을 저장하지 못했어요.")
                    },
                    onEnqueueFailure = { error ->
                        Log.w(
                            TAG,
                            "산책 전달 작업을 예약하지 못했다. 다음 시작에서 다시 찾는다.",
                            error,
                        )
                    },
                )
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

    private fun recordMoment(type: WalkMomentType?, startId: Int) {
        if (type == null || recorder.snapshot().state != TrackingState.RECORDING) {
            stopIfInactive(startId)
            return
        }
        if (type == WalkMomentType.NOTE) return // Notes use the editor, without a GPS gate.
        val activeSession = sessionId ?: return
        val capturedOwner = sessionOwnerId.orEmpty()
        val actionId = UUID.randomUUID().toString()
        val at = System.currentTimeMillis()
        val request = com.daengs.app.walk.pin.ActionPinRequest(UUID.randomUUID(), capturedOwner,
            activeSession, chainIndex, at)
        val direct = directPinRef.takeIf {
            store.state.value.latestMomentFix?.isFreshEnoughForMoment(SystemClock.elapsedRealtimeNanos()) == true
        }
        val app = application as DaengsApp
        val stored = writer.ordered {
            val pin = app.actionPins.create(actionId, request, type, direct) ?: return@ordered
            store.publish(WalkEvent.MomentRecorded(type, WalkMomentOutcome.CREATED, "moment-$actionId"))
            if (pin.state == "provisional") {
                // Scheduling failure must not undo the persisted action. Startup recovery finds it.
                try { app.actionPinScheduler.schedule(actionId, pin.resolveByMillis) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { Log.w(TAG, "행동 핀 예약은 다음 시작에서 복구합니다.") }
            }
        }
        serviceScope.launch {
            try { stored.await() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* writer.failure reports storage failure. */ }
        }
    }

    private fun acceptLocation(sample: LocationSample) {
        // A queued fix after pause/stop must not enter either raw history or the detector.
        if (recorder.snapshot().state != TrackingState.RECORDING) return
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
        if (sample.isAccurateEnoughForMoment() && !sample.isMock &&
            sample.accuracyMeters?.let { it.isFinite() && it > 0 } == true) {
            directPinRef = com.daengs.app.walk.pin.ActionPinSourceRef(nextClientSeq - 1, chainIndex, sample.capturedAtMillis)
        } else {
            // Keep exactly the identity accepted by latestMomentFix, never a display coordinate.
            if (sample.isAccurateEnoughForMoment()) directPinRef = null
        }
        stayRecorder.add(sample)
        val latestMomentFix = if (sample.isAccurateEnoughForMoment()) {
            sample
        } else {
            store.state.value.latestMomentFix
        }
        store.publish(
            trackingState(
                trail = recorder.add(sample),
                lastSample = sample,
                latestMomentFix = latestMomentFix,
            ),
        )
    }

    private fun openSession(dogIds: List<String>) {
        val id = UUID.randomUUID().toString()
        synchronized(sessionLock) {
            sessionId = id
            sessionStartedAtMillis = System.currentTimeMillis()
            sessionDogIds = dogIds.toList()
            nextClientSeq = 0
            chainIndex = 0
            directPinRef = null
            writer.openSession(
                RecordedSession(
                    id = id,
                    dogIds = dogIds,
                    startedAtMillis = checkNotNull(sessionStartedAtMillis),
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

    /**
     * 너무 짧으면 **산책으로 치지 않고 지운다.**
     *
     * 문을 눌렀다가 그냥 닫은 것, 시작을 실수로 누른 것이 0m 짜리 기록으로 쌓이면
     * 목록이 지저분해지고 홈의 "오늘 몇 회" 가 거짓이 된다.
     *
     * **지우고 나서 그렇다고 말한다.** 말없이 사라지면 기록이 유실된 것으로 읽힌다 —
     * 걷고 왔는데 목록에 없으면 앱이 잘못한 것처럼 보인다. 카드가 이미 그리고 있는
     * `errorMessage` 자리를 쓴다.
     */
    private suspend fun finishOrDiscard(sessionId: String): Boolean {
        val session = log.session(sessionId)
        if (session == null) {
            publishCompletionFailure("저장한 산책을 다시 읽지 못했어요.")
            return false
        }
        val summary = summarize(session, log.fixes(sessionId))
        if (!summary.countsAsWalk && !log.hasEntries(sessionId)) {
            log.deleteSession(sessionId)
            // **잰 값도 같이 지운다.** 안 지우면 방금 걸은 시간이 화면에 그대로 남아,
            // 지웠다고 말해 놓고 숫자는 계속 보여 주는 꼴이 된다. 거리는 `recorder.stop()`
            // 이 이미 0 으로 만들지만 시간은 이 서비스가 들고 있다.
            activeDurationMillis = 0L
            activeSinceRealtimeMillis = null
            publishCompletionFailure(
                "이동 거리나 시간이 너무 짧아서 산책으로 기록하지 않았어요.",
                clearMoments = true,
            )
            return false
        }
        publishIfStillInactive(
            trackingState(
                trail = recorder.snapshot(),
                completedSessionId = sessionId,
            ),
        )
        return true
    }

    private fun publishCompletionFailure(message: String, clearMoments: Boolean = false) {
        publishIfStillInactive(
            trackingState(
                trail = recorder.snapshot(),
                errorMessage = message,
                latestMomentFix = if (clearMoments) null else store.state.value.latestMomentFix,
                momentGroups = if (clearMoments) emptyList() else store.state.value.momentGroups,
            ),
        )
    }

    /** 저장하는 사이 새 산책이 시작됐다면 이전 산책 결과로 현재 상태를 덮지 않는다. */
    private fun publishIfStillInactive(state: WalkTrackingState) {
        synchronized(sessionLock) {
            if (sessionId == null && recorder.snapshot().state == TrackingState.OFF) {
                store.publish(state)
            }
        }
    }

    private fun closeSession() = synchronized(sessionLock) {
        sessionId?.let { writer.closeSession(it, System.currentTimeMillis()) }
        sessionId = null
        sessionDogIds = emptyList()
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
        val cutoff = System.currentTimeMillis()
        sessionId?.let { id -> writer.ordered { (application as DaengsApp).actionPins.finishSession(id, cutoff) } }
        pauseTiming()
        stayRecorder.breakContinuity()
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
        latestMomentFix: LocationSample? = store.state.value.latestMomentFix,
        momentGroups: List<WalkMoment> = store.state.value.momentGroups,
        errorMessage: String? = null,
        finishingSessionId: String? = null,
        completedSessionId: String? = null,
    ): WalkTrackingState = WalkTrackingState(
        activeSessionId = sessionId,
        activeSessionStartedAtMillis = sessionStartedAtMillis.takeIf { sessionId != null },
        ownerId = sessionOwnerId,
        activeDogIds = sessionDogIds,
        trail = trail,
        lastSample = lastSample,
        latestMomentFix = latestMomentFix,
        momentGroups = momentGroups,
        errorMessage = errorMessage,
        activeDurationMillis = activeDurationMillis,
        activeSinceRealtimeMillis = activeSinceRealtimeMillis,
        finishingSessionId = finishingSessionId,
        completedSessionId = completedSessionId,
        stayStamps = stayRecorder.snapshot(),
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
        private const val TAG = "WalkTrackingService"
        const val ACTION_START = "com.daengs.app.walk.START"
        const val ACTION_PAUSE = "com.daengs.app.walk.PAUSE"
        const val ACTION_RESUME = "com.daengs.app.walk.RESUME"
        const val ACTION_STOP = "com.daengs.app.walk.STOP"
        const val ACTION_RECORD_MOMENT = "com.daengs.app.walk.RECORD_MOMENT"

        private const val CHANNEL_ID = "walk_tracking"
        private const val NOTIFICATION_ID = 4101
        private const val REQUEST_OPEN = 4101
        private const val REQUEST_PAUSE = 4102
        private const val REQUEST_RESUME = 4103
        private const val REQUEST_STOP = 4104

        const val EXTRA_DOG_IDS = "dogIds"
        const val EXTRA_MOMENT_TYPE = "momentType"

        fun commandIntent(context: Context, action: String): Intent =
            Intent(context, WalkTrackingService::class.java).setAction(action)
    }
}
