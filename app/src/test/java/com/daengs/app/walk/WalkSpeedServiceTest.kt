package com.daengs.app.walk

import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.DaengsApp
import com.daengs.app.location.*
import com.daengs.app.walk.display.*
import com.daengs.app.walk.store.RoomWalkFixLog
import com.daengs.app.walk.store.WalkDatabase
import com.daengs.app.walk.sync.*
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import org.robolectric.util.ReflectionHelpers

/** Actual Service -> ingress -> Room -> projection -> store, without unrelated app SDK/network startup. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DaengsApp::class, shadows = [WalkSpeedServiceTest.ShadowSpeedApplication::class])
class WalkSpeedServiceTest {
    private lateinit var app: DaengsApp
    private lateinit var service: ServiceController<WalkTrackingService>
    private val source = TestLocationSource()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var db: WalkDatabase
    private val main get() = shadowOf(Looper.getMainLooper())
    private val state get() = app.walkRuntime.store.state.value

    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        shadowOf(app).grantPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION)
        db = androidx.room.Room.inMemoryDatabaseBuilder(app, WalkDatabase::class.java).build()
        val dao = db.walkDao()
        val log = RoomWalkFixLog(dao)
        val store = WalkTrackingStore()
        val delivery = object : WalkDeliveryScheduler {
            override suspend fun enqueue(sessionId: String) = Unit
            override suspend fun enqueuePending() = Unit
        }
        val runtime = WalkRuntime(scope, source, store, ForegroundWalkTrackingController(app, store),
            WalkFixWriter(log, scope), log, WalkHistory(log), WalkSync(log), delivery)
        ReflectionHelpers.setField(app, "walkRuntime", runtime)
        app.actionPins = com.daengs.app.walk.pin.ActionPinStore(dao, { "" }, activeSessionId = { store.state.value.activeSessionId })
        ReflectionHelpers.setField(app, "walkDiaryPublication",
            com.daengs.app.walk.diary.WalkDiaryPublication(dao, { "" }, scope, sync = {}))
        service = Robolectric.buildService(WalkTrackingService::class.java).create()
    }

    @After fun cleanup() {
        try {
            if (::service.isInitialized) {
                val activeId = state.activeSessionId
                service.destroy()
                if (activeId != null) awaitState { runBlocking {
                    app.walkRuntime.log.recordingEpochs(activeId).lastOrNull()?.drained == true
                } }
                main.idle()
                runBlocking { app.walkRuntime.writer.flush() }
            }
        } finally {
            scope.cancel()
            if (::db.isInitialized) db.close()
        }
    }

    private fun command(action: String) {
        service.get().onStartCommand(Intent(app, WalkTrackingService::class.java).setAction(action), 0, 1)
        main.idle()
    }

    private fun awaitState(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!predicate() && System.nanoTime() < deadline) { main.idle(); Thread.sleep(10) }
        main.idle()
        assertTrue("Timed out: $state", predicate())
    }

    @Test fun serviceTicksWithoutScreenAndPauseResumeUsesOneSubscriptionAtATime() {
        command(WalkTrackingService.ACTION_START)
        awaitState { source.callback != null && !state.recordingTransition }
        val id = state.activeSessionId!!
        source.emit(1.5f)
        awaitState { state.motionDisplay.speedMps == 1.5 }
        assertEquals(1, runBlocking { app.walkRuntime.log.fixes(id).size })
        main.idleFor(Duration.ofSeconds(11))
        assertEquals(DisplayFreshness.STALE, state.motionDisplay.freshness)
        assertEquals(1.5, state.motionDisplay.speedMps, 0.0)
        command(WalkTrackingService.ACTION_PAUSE)
        awaitState { state.trail.state == TrackingState.PAUSED && !state.recordingTransition }
        assertEquals(DisplayFreshness.PAUSED, state.motionDisplay.freshness)
        val closed = runBlocking { app.walkRuntime.log.recordingEpochs(id).single() }
        assertTrue(closed.drained)
        assertEquals("PAUSE", closed.endKind)
        assertNull(source.callback)
        command(WalkTrackingService.ACTION_RESUME)
        awaitState { source.callback != null && !state.recordingTransition }
        assertEquals(1.5, state.motionDisplay.speedMps, 0.0)
        assertEquals(DisplaySignal.WAITING, state.motionDisplay.signal)
        main.idleFor(Duration.ofSeconds(1))
        source.emit(0f)
        awaitState { state.motionDisplay.speedMps == 0.0 && state.motionDisplay.freshness == DisplayFreshness.LIVE }
        val fixes = runBlocking { app.walkRuntime.log.fixes(id) }
        assertEquals(listOf(0L, 1L), fixes.map { it.ingressSeq })
        assertNotEquals(fixes.first().sourceEpoch, fixes.last().sourceEpoch)
        assertEquals(2, source.starts)
        assertEquals(1, source.maxActive)
        command(WalkTrackingService.ACTION_STOP)
        awaitState { state.trail.state == TrackingState.OFF && !state.recordingTransition }
        assertEquals(DisplayFreshness.FINAL, state.motionDisplay.freshness)
    }

    @Test fun pausedStopThenNewWalkResetsSpeedEvenIfServiceInstanceIsReused() {
        command(WalkTrackingService.ACTION_START)
        awaitState { source.callback != null && !state.recordingTransition }
        val firstId = state.activeSessionId
        source.emit(2f)
        awaitState { state.motionDisplay.speedMps == 2.0 }
        command(WalkTrackingService.ACTION_PAUSE)
        awaitState { state.trail.state == TrackingState.PAUSED && !state.recordingTransition }
        command(WalkTrackingService.ACTION_STOP)
        awaitState { state.trail.state == TrackingState.OFF && !state.recordingTransition }
        assertEquals(DisplayFreshness.FINAL, state.motionDisplay.freshness)
        command(WalkTrackingService.ACTION_START)
        awaitState { source.callback != null && !state.recordingTransition }
        assertNotEquals(firstId, state.activeSessionId)
        assertEquals(MotionDisplay(), state.motionDisplay)
        assertEquals(1, source.maxActive)
    }

    private class TestLocationSource : LocationSource {
        var callback: ((LocationSample) -> Unit)? = null
        var starts = 0
        var maxActive = 0
        private var active = 0
        override suspend fun currentLocation(): LocationSample = error("Weather is not part of this test")
        override fun locationUpdates(config: LocationUpdateConfig): Flow<LocationSample> = error("No screen GPS subscription expected")
        override fun subscribeRecording(config: LocationUpdateConfig, onSample: (LocationSample) -> Unit,
            onFailure: (Throwable) -> Unit): LocationSubscription {
            starts++; active++; maxActive = maxOf(active, maxActive)
            callback = onSample
            return object : LocationSubscription { override suspend fun close() { callback = null; active-- } }
        }
        fun emit(speed: Float) = checkNotNull(callback).invoke(LocationSample(GeoPoint(37.5, 127.0),
            System.currentTimeMillis(), SystemClock.elapsedRealtimeNanos(), 3f, speed,
            speedAccuracyMetersPerSecond = 0.2f, provider = "fused"))
    }

    /** DaengsApp is needed by the service cast; assemble only its recording dependencies above. */
    @Implements(value = DaengsApp::class, isInAndroidSdk = false)
    class ShadowSpeedApplication : org.robolectric.shadows.ShadowApplication() {
        @Implementation fun onCreate() = Unit
    }
}
