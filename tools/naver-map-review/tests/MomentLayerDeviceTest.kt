package com.daengs.app.map.review

import android.graphics.Color
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.daengs.app.R
import com.naver.maps.map.CameraPosition
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage
import com.naver.maps.map.overlay.PathOverlay
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MomentLayerDeviceTest {
    private fun await(s: ActivityScenario<MomentReviewActivity>, message: String,
        condition: (MomentReviewActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 20_000
        while (SystemClock.uptimeMillis() < deadline) {
            var done = false
            s.onActivity { done = condition(it) }
            if (done) return
            Thread.sleep(50)
        }
        fail(message)
    }
    private fun ready(s: ActivityScenario<MomentReviewActivity>) = await(s, "Six native pins and route") {
        val map = it.map()
        map != null && !map.isCameraIdlePending && it.markers().size == 6 &&
            it.markers().all { marker -> marker.map === map } && it.route()?.map === map &&
            it.marker("photo")?.icon?.getBitmap(it)?.width == 104
    }
    private fun tap(s: ActivityScenario<MomentReviewActivity>, id: String) {
        var x = 0f; var y = 0f
        s.onActivity {
            val marker = requireNotNull(it.marker(id))
            val point = requireNotNull(it.map()).projection.toScreenLocation(marker.position)
            val view = requireNotNull(it.mapView())
            val offset = IntArray(2); view.getLocationOnScreen(offset)
            // Center of the visible icon, accounting for bottom-anchored photo/fallback pins.
            x = offset[0] + point.x; y = offset[1] + point.y + (.5f - marker.anchor.y) * marker.height
            assertTrue("Fixture tap must be inside the visible map: $id ($x, $y)",
                x > offset[0] && x < offset[0] + view.width && y > offset[1] && y < offset[1] + view.height)
        }
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(now, now + 80, MotionEvent.ACTION_UP, x, y, 0)
        down.source = InputDevice.SOURCE_TOUCHSCREEN; up.source = InputDevice.SOURCE_TOUCHSCREEN
        try {
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            assertTrue(automation.injectInputEvent(down, true)); assertTrue(automation.injectInputEvent(up, true))
        } finally { down.recycle(); up.recycle() }
    }

    @Test fun nativeTapPhotoReplacementAndFallbackKeepRouteAndCamera() {
        ActivityScenario.launch(MomentReviewActivity::class.java).use { s ->
            ready(s)
            lateinit var route: PathOverlay
            lateinit var camera: CameraPosition
            lateinit var pins: List<Marker>
            s.onActivity {
                route = requireNotNull(it.route()); camera = requireNotNull(it.map()).cameraPosition
                pins = it.markers(); it.callbackVersion = 7
            }
            await(s, "Callback recomposed") { it.appliedCallbackVersion == 7 }
            s.onActivity { assertEquals(pins.toSet(), it.markers().toSet()) }
            tap(s, "sniff")
            await(s, "Updated native click callback") { it.lastClick == "7:sniff" }
            s.onActivity {
                assertSame(route, it.route()); assertEquals(camera, requireNotNull(it.map()).cameraPosition)
                assertTrue(pins.all { marker -> marker.map == null })
                it.moments = it.moments.map { p -> if (p.id == "photo") p.copy(photoFile = it.bluePhoto) else p }
            }
            await(s, "Blue replacement photo") {
                val bitmap = it.marker("photo")?.icon?.getBitmap(it)
                bitmap?.width == 104 && bitmap.getPixel(52, 52) == Color.BLUE
            }
            tap(s, "missing")
            await(s, "Missing photo remains clickable") { it.lastClick == "7:missing" }
            s.onActivity {
                assertEquals(OverlayImage.fromResource(R.drawable.ic_walk_moment), it.marker("missing")?.icon)
                assertSame(route, it.route()); assertEquals(camera, requireNotNull(it.map()).cameraPosition)
            }
        }
    }

    @Test fun backgroundMapReplacementAndReentryReleaseOldPins() {
        ActivityScenario.launch(MomentReviewActivity::class.java).use { s ->
            ready(s)
            lateinit var map: NaverMap
            lateinit var pins: List<Marker>
            s.onActivity { map = requireNotNull(it.map()); pins = it.markers() }
            s.moveToState(Lifecycle.State.CREATED); s.moveToState(Lifecycle.State.RESUMED)
            ready(s)
            s.onActivity {
                assertSame(map, it.map()); assertEquals(pins.toSet(), it.markers().toSet())
                it.generation++
            }
            await(s, "New map prepared") { it.map()?.let { current -> current !== map } == true && it.markers().size == 6 }
            ready(s)
            s.onActivity {
                assertTrue(map.isDestroyed); assertTrue(pins.all { marker -> marker.map == null })
                map = requireNotNull(it.map()); pins = it.markers(); it.showMap = false
            }
            await(s, "Map and overlays removed") { it.mapView() == null && it.markers().isEmpty() && it.route() == null }
            s.onActivity {
                assertTrue(map.isDestroyed); assertTrue(pins.all { marker -> marker.map == null }); it.showMap = true
            }
            ready(s)
        }
    }
}
