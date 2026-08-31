package com.daengs.app.walk

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WalkTrackingTest {
    @Test
    fun `store publishes the complete state atomically`() {
        val store = WalkTrackingStore()
        val expected = WalkTrackingState(
            trail = TrailSnapshot(state = TrackingState.PAUSED),
            errorMessage = "위치를 받을 수 없습니다.",
        )

        store.publish(expected)

        assertSame(expected, store.state.value)
    }

    @Test
    fun `elapsed time adds only the active monotonic interval`() {
        val state = WalkTrackingState(
            activeDurationMillis = 3_000L,
            activeSinceRealtimeMillis = 10_000L,
        )

        assertEquals(5_500L, state.elapsedMillisAt(12_500L))
        assertEquals(3_000L, state.elapsedMillisAt(9_000L))
        assertEquals(3_000L, state.copy(activeSinceRealtimeMillis = null).elapsedMillisAt(99_000L))
    }

    @Test
    fun `controller maps controls to explicit service actions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val application = context.applicationContext as Application
        val controller = ForegroundWalkTrackingController(context, WalkTrackingStore())

        controller.start()
        assertEquals(WalkTrackingService.ACTION_START, shadowOf(application).nextStartedService.action)
        controller.pause()
        assertEquals(WalkTrackingService.ACTION_PAUSE, shadowOf(application).nextStartedService.action)
        controller.resume()
        assertEquals(WalkTrackingService.ACTION_RESUME, shadowOf(application).nextStartedService.action)
        controller.stop()
        assertEquals(WalkTrackingService.ACTION_STOP, shadowOf(application).nextStartedService.action)
    }
}
