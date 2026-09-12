package com.daengs.app.map.review

import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.daengs.app.location.GeoPoint
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraPosition
import com.naver.maps.map.NaverMap
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapFirstFrameTest {
    private fun await(s: ActivityScenario<MapReviewActivity>, condition: (MapReviewActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            s.onActivity { ready = condition(it) }
            if (ready) return
            // Only observe. Invalidating, relaying out or sending input would mask the regression.
            Thread.sleep(50)
        }
        var detail = ""
        s.onActivity { detail = "generation=${it.generation}, shown=${it.showMap}, surface=${it.surfaceReady()}, map=${it.map()}" }
        fail("Native map did not become ready: $detail")
    }

    @Test fun replacementAndReentryRenderWithoutAdditionalInput() {
        ActivityScenario.launch(MapReviewActivity::class.java).use { s ->
            await(s) { it.surfaceReady() && it.map()?.locationOverlay?.isVisible == true }
            repeat(3) {
                lateinit var previous: NaverMap
                s.onActivity { previous = requireNotNull(it.map()); it.generation++ }
                await(s) { it.surfaceReady() && it.map()?.let { map -> map !== previous && map.locationOverlay.isVisible } == true }
                s.onActivity { assertTrue(previous.isDestroyed); previous = requireNotNull(it.map()); it.showMap = false }
                await(s) { it.mapView() == null }
                s.onActivity { assertTrue(previous.isDestroyed); it.showMap = true }
                await(s) { it.surfaceReady() && it.map()?.let { map -> map !== previous && map.locationOverlay.isVisible } == true }
            }
        }
    }

    @Test fun resumeRotationAndLocationKeepWorking() {
        ActivityScenario.launch(MapReviewActivity::class.java).use { s ->
            await(s) { it.surfaceReady() && it.map()?.locationOverlay?.isVisible == true }
            lateinit var original: NaverMap
            s.onActivity { original = requireNotNull(it.map()) }
            s.moveToState(Lifecycle.State.CREATED)
            s.moveToState(Lifecycle.State.RESUMED)
            await(s) { it.surfaceReady() && it.map() === original && original.locationOverlay.isVisible }
            s.onActivity {
                val c = original.cameraPosition
                original.cameraPosition = CameraPosition(c.target, c.zoom, c.tilt, 135.0)
            }
            await(s) { kotlin.math.abs(original.locationOverlay.bearing - 135f) < 0.1f }
            s.onActivity { it.point = null }
            await(s) { !original.locationOverlay.isVisible }
            s.onActivity { it.point = GeoPoint(37.56675, 126.9786); it.follow = true }
            await(s) { original.locationOverlay.isVisible && original.cameraPosition.target.let { target ->
                kotlin.math.abs(target.latitude - 37.56675) < 0.000001 &&
                    kotlin.math.abs(target.longitude - 126.9786) < 0.000001
            } }
            s.onActivity {
                assertEquals(LatLng(37.56675, 126.9786), original.locationOverlay.position)
                assertEquals(96, original.locationOverlay.iconWidth)
                assertEquals(96, original.locationOverlay.iconHeight)
            }
        }
    }
}
