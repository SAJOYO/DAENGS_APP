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
import com.daengs.app.location.LocationSample
import com.daengs.app.location.LocationSource
import com.daengs.app.miniroom.OutsideApi
import com.daengs.app.miniroom.OutsideTime
import com.daengs.app.walk.sync.WalkDeliveryScheduler
import com.daengs.app.walk.display.DisplayLifecycle
import com.daengs.app.walk.display.MotionDisplay
import com.daengs.app.walk.display.WalkSpeedRuntime
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.sync.withLock
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
    private var ingress: WalkIngress? = null
    private var subscription: com.daengs.app.location.LocationSubscription? = null
    private var projectionJob: kotlinx.coroutines.Job? = null
    private var speedRuntime: WalkSpeedRuntime? = null
    private var speedTickJob: kotlinx.coroutines.Job? = null
    private var boundaryJob: kotlinx.coroutines.Job? = null
    private var transitioning = false
    private var pendingStopStartId: Int? = null
    private val ingressSequence = java.util.concurrent.atomic.AtomicLong(0)
    private val clockEpochId = UUID.randomUUID().toString()
    private var projectionCursor = -1L
    private val projectionMutex = kotlinx.coroutines.sync.Mutex()
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
        createNotificationChannel()

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
        ingress?.seal("INTERRUPTED")
        speedRuntime?.onLifecycle(DisplayLifecycle.FINISHED, SystemClock.elapsedRealtimeNanos())
        val closingSubscription = subscription
        (application as DaengsApp).walkRuntime.recordingScope.launch {
            runCatching { kotlinx.coroutines.withTimeout(10_000) { closingSubscription?.close() } }
        }
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
        if (transitioning || sessionId != null) return
        if (recorder.snapshot().state != TrackingState.OFF) {
            promote(recorder.snapshot(), store.state.value.errorMessage)
            return
        }
        sessionOwnerId = log.ownerId
        writer.clearFailure()
        activeDurationMillis = 0L
        activeSinceRealtimeMillis = SystemClock.elapsedRealtime()
        stayRecorder.reset()
        transitioning = true
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
        transitioning = true
        boundaryJob = serviceScope.launch {
            try { writer.flush(); beginIngress() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                pauseTiming()
                speedRuntime?.onLifecycle(DisplayLifecycle.PAUSED, SystemClock.elapsedRealtimeNanos())
                store.publish(trackingState(recorder.pause(), errorMessage = error.message ?: "위치 기록을 시작하지 못했어요."))
            } finally { transitioning = false; publishTransition() }
        }
    }

    private fun pauseRecording(startId: Int) {
        if (transitioning) return
        if (recorder.snapshot().state != TrackingState.RECORDING) { stopIfInactive(startId); return }
        transitionRecording(stop = false, startId = startId)
    }

    private fun resumeRecording(startId: Int) {
        if (transitioning) return
        if (recorder.snapshot().state != TrackingState.PAUSED) { stopIfInactive(startId); return }
        if (ingress?.progress?.value?.failureReason != null) return
        transitioning = true
        boundaryJob = serviceScope.launch {
            try {
                writer.flush()
                chainIndex += 1
                directPinRef = null
                activeSinceRealtimeMillis = SystemClock.elapsedRealtime()
                recorder.resume()
                store.publish(trackingState(recorder.snapshot(), latestMomentFix = null))
                beginIngress()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                pauseTiming()
                speedRuntime?.onLifecycle(DisplayLifecycle.PAUSED, SystemClock.elapsedRealtimeNanos())
                store.publish(trackingState(recorder.pause(), errorMessage = error.message ?: "위치 기록을 재개하지 못했어요."))
            } finally { transitioning = false; publishTransition() }
        }
    }

    private fun stopRecording(stopStartId: Int) {
        if (transitioning) { pendingStopStartId = stopStartId; return }
        if (recorder.snapshot().state == TrackingState.OFF) { stopIfInactive(stopStartId); return }
        transitionRecording(stop = true, startId = stopStartId)
    }

    private fun beginIngress() {
        val id = checkNotNull(sessionId)
        val runtime = (application as DaengsApp).walkRuntime
        val epoch = RecordingEpoch(UUID.randomUUID().toString(), id, clockEpochId, chainIndex,
            System.currentTimeMillis(), SystemClock.elapsedRealtimeNanos(), ingressSequence.get())
        speedRuntime?.begin(epoch, SystemClock.elapsedRealtimeNanos())
        publishSpeed()
        val stream = WalkIngress(epoch, ingressSequence, runtime.recordingScope,
            SystemClock::elapsedRealtimeNanos, System::currentTimeMillis,
            persist = { writer.appendObserved(id, it).await() },
            saveEpoch = { writer.saveRecordingEpoch(it).await() })
        ingress = stream
        try {
            subscription = locationSource.subscribeRecording(WALK_OBSERVATION_CONFIG,
                onSample = { stream.offer(it) }, onFailure = { stream.fail("SOURCE_DELIVERY_FAILURE") })
        } catch (error: Exception) {
            stream.fail("SOURCE_START_FAILURE")
            throw error
        }
        projectionJob = serviceScope.launch {
            try {
                stream.progress.collect { progress ->
                    projectStored(id)
                    if (progress.failureReason != null && !transitioning) {
                        pauseAfterFeedProblem("위치 기록 전달에 문제가 생겼어요. 원본 저장 상태를 확인해 주세요.")
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) {
                stream.fail("PROJECTION_FAILURE")
                pauseAfterFeedProblem(error.message ?: "저장한 위치를 읽지 못했어요.")
            }
        }
    }

    private suspend fun projectStored(id: String) = projectionMutex.withLock {
        while (sessionId == id) {
            val fixes = log.observationsAfter(id, projectionCursor, 128)
            if (fixes.isEmpty()) break
            // Include cached/ineligible observations so engine references remain contiguous.
            // This read-only projection catches its own faults; raw and legacy recording continue.
            speedRuntime?.observations(fixes, SystemClock.elapsedRealtimeNanos())
            for (fix in fixes) {
                if (fix.recordingEligible != false) acceptCommittedLocation(fix)
                projectionCursor = requireNotNull(fix.ingressSeq)
            }
            publishSpeed()
        }
    }

    private fun transitionRecording(stop: Boolean, startId: Int) {
        val id = sessionId ?: return
        transitioning = true
        pauseTiming()
        val stream = ingress
        val boundary = stream?.seal(if (stop) "STOP" else "PAUSE")
        speedRuntime?.onLifecycle(if (stop) DisplayLifecycle.FINISHED else DisplayLifecycle.PAUSED,
            SystemClock.elapsedRealtimeNanos())
        if (stop) speedTickJob?.cancel()
        val cutoff = if (stop && recorder.snapshot().state == TrackingState.PAUSED) System.currentTimeMillis()
            else boundary?.endedAtMillis ?: System.currentTimeMillis()
        store.publish(trackingState(recorder.snapshot(), finishingSessionId = id.takeIf { stop }))
        boundaryJob = serviceScope.launch {
            try {
                try { kotlinx.coroutines.withTimeout(10_000) { subscription?.close() } }
                catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException) throw error
                    stream?.fail("SOURCE_CLOSE_FAILURE")
                    throw error
                } finally { subscription = null }
                val receipt = kotlinx.coroutines.withTimeout(30_000) { stream?.drain() }
                projectionJob?.cancelAndJoin()
                projectStored(id)
                receipt?.let { speedRuntime?.drained(it) }
                check(receipt == null || receipt.failureReason == null) { "원본 전달에 실패한 산책은 완료할 수 없어요." }
                writer.ordered { (application as DaengsApp).actionPins.finishSession(id, cutoff) }.await()
                writer.flush()
                if (!stop) {
                    stayRecorder.breakContinuity()
                    recorder.pause()
                    store.publish(trackingState(recorder.snapshot()))
                    promote(recorder.snapshot(), null)
                } else {
                    // Stopping an already paused recording creates an explicit STOP marker.
                    if (receipt != null && receipt.endKind == "PAUSE") {
                        val atNanos = SystemClock.elapsedRealtimeNanos()
                        writer.saveRecordingEpoch(RecordingEpoch(UUID.randomUUID().toString(), id,
                            clockEpochId, chainIndex + 1, cutoff, atNanos, ingressSequence.get(),
                            endedAtMillis = cutoff, endedElapsedNanos = atNanos, endKind = "STOP",
                            targetIngressSeq = ingressSequence.get() - 1, drained = true)).await()
                    }
                    checkRecordingComplete(log.recordingEpochs(id))
                    // Await this transaction, not a global failure flag changed by later work.
                    writer.ordered { log.closeSession(id, cutoff) }.await()
                    sessionId = null
                    sessionDogIds = emptyList()
                    recorder.stop()
                    // Completion is already durable. Downstream preparation cannot invalidate it.
                    try {
                        if (finishOrDiscard(id)) {
                            (application as DaengsApp).walkDiaryPublication.start(id)
                            delivery.enqueue(id)
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        Log.w(TAG, "완료한 산책의 결과 준비는 다음 시작에서 재시도한다.", error)
                        publishCompletionFailure("산책은 저장됐어요. 결과를 다시 준비할게요.")
                    }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
                stream?.fail("DRAIN_TIMEOUT")
                (application as DaengsApp).walkRuntime.recordingScope.launch {
                    runCatching { stream?.drain() }
                }
                publishInterrupted(stop, startId, "원본 저장이 늦어 산책을 완료하지 못했어요. 저장된 원본은 유지돼요.")
            } catch (error: Exception) {
                stream?.fail("FINALIZATION_FAILURE")
                // Persist a late close/finalization failure even if the raw consumer already finished.
                runCatching { kotlinx.coroutines.withTimeout(30_000) { stream?.drain() } }
                publishInterrupted(stop, startId, error.message ?: "산책 기록을 완료하지 못했어요. 저장된 원본은 유지돼요.")
            } finally { transitioning = false; publishTransition() }
        }
    }

    private fun publishInterrupted(stop: Boolean, startId: Int, message: String) {
        if (stop) {
            projectionJob?.cancel()
            // Release the active session without closing/deleting its recoverable journal.
            sessionId = null
            sessionDogIds = emptyList()
            store.publish(trackingState(recorder.stop(), errorMessage = message))
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        } else {
            store.publish(trackingState(recorder.pause(), errorMessage = message))
            promote(recorder.snapshot(), message)
        }
    }

    private fun publishTransition() {
        store.publish(store.state.value.copy(recordingTransition = transitioning,
            ingressProgress = ingress?.progress?.value))
        if (!transitioning) pendingStopStartId?.let {
            pendingStopStartId = null
            stopRecording(it)
            return
        }
        if (!transitioning && recorder.snapshot().state == TrackingState.RECORDING &&
            ingress?.progress?.value?.failureReason != null) pauseAfterFeedProblem("원본 전달 실패")
    }

    /** 잘못 전달된 제어 명령이 비활성 서비스를 시작된 상태로 남기지 않게 한다. */
    private fun stopIfInactive(startId: Int) {
        if (recorder.snapshot().state == TrackingState.OFF) stopSelf(startId)
    }

    private fun recordMoment(type: WalkMomentType?, startId: Int) {
        if (transitioning || type == null || recorder.snapshot().state != TrackingState.RECORDING) {
            stopIfInactive(startId)
            return
        }
        if (type == WalkMomentType.NOTE) return // Notes use the editor, without a GPS gate.
        if (com.daengs.app.walk.pin.ActionPinRollout.legacyCreation) {
            recordLegacyMoment(type)
            return
        }
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

    private fun recordLegacyMoment(type: WalkMomentType) {
        val activeSession = sessionId ?: return
        val action = com.daengs.app.walk.pin.legacyWalkAction(
            store.state.value.latestMomentFix, UUID.randomUUID().toString(), activeSession,
            type, System.currentTimeMillis(), SystemClock.elapsedRealtimeNanos(),
        )
        if (action == null) {
            store.publish(WalkEvent.MomentLocationUnavailable)
            return
        }
        val capturedOwner = sessionOwnerId.orEmpty()
        val stored = writer.ordered {
            check(log.ownerId == capturedOwner) { "계정이 변경됐어요." }
            log.appendAction(action)
        }
        serviceScope.launch {
            try {
                stored.await()
                store.publish(WalkEvent.MomentRecorded(type, WalkMomentOutcome.CREATED, "moment-${action.id}"))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) { /* writer.failure reports storage failure. */ }
        }
    }

    private fun acceptCommittedLocation(fix: RecordedFix) {
        val sample = com.daengs.app.location.LocationSample(
            com.daengs.app.location.GeoPoint(fix.lat, fix.lng), fix.atMillis,
            elapsedRealtimeNanos = fix.elapsedRealtimeNanos, accuracyMeters = fix.accuracyM,
            speedMetersPerSecond = fix.speedMps, isMock = fix.isMock,
            bearingDegrees = fix.bearingDegrees, bearingAccuracyDegrees = fix.bearingAccuracyDegrees,
            speedAccuracyMetersPerSecond = fix.speedAccuracyMps, provider = fix.provider)
        nextClientSeq = fix.clientSeq + 1
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
            ingress = null
            ingressSequence.set(0)
            projectionCursor = -1L
            chainIndex = 0
            speedRuntime = WalkSpeedRuntime(id) { error -> Log.w(TAG, "속도 표시 계산을 중단합니다. 원본 기록은 계속합니다.", error) }
            speedTickJob?.cancel()
            speedTickJob = serviceScope.launch {
                while (sessionId == id) {
                    kotlinx.coroutines.delay(1_000)
                    speedRuntime?.tick(SystemClock.elapsedRealtimeNanos())
                    publishSpeed()
                }
            }
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

    private fun pauseAfterFeedProblem(message: String) {
        if (transitioning || recorder.snapshot().state != TrackingState.RECORDING) return
        store.publish(trackingState(recorder.snapshot(), errorMessage = message))
        ingress?.fail("SOURCE_DELIVERY_FAILURE")
        transitionRecording(stop = false, startId = 0)
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

    private fun publishSpeed() {
        val display = speedRuntime?.display?.value ?: return
        if (store.state.value.motionDisplay != display) store.publish(store.state.value.copy(motionDisplay = display))
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
        recordingTransition = transitioning,
        ingressProgress = ingress?.progress?.value,
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
        motionDisplay = speedRuntime?.display?.value ?: MotionDisplay(),
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
