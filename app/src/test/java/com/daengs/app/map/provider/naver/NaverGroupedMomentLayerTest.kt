package com.daengs.app.map.provider.naver

import android.graphics.PointF
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.moments.DiaryPinAppearance
import com.daengs.app.map.layers.moments.MomentMarkerState
import com.daengs.app.map.shell.*
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.NaverMap
import com.naver.maps.map.Projection
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow

/** Exercise the actual listener lifecycle; the projected scene is outside the native overlay area. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], instrumentedPackages = ["com.naver.maps"], shadows = [
    NaverGroupedMomentLayerTest.MapShadow::class, NaverGroupedMomentLayerTest.ProjectionShadow::class])
class NaverGroupedMomentLayerTest {
    @get:Rule val compose = createComposeRule()
    private val point = GeoPoint(37.5, 127.0)
    private val moment = MomentMarkerState("scene", point, "1", diaryPin = DiaryPinAppearance(1))
    private val query = MapVisibilityQuery("revision", listOf(MapLocationTarget("scene", listOf(point))), 80)

    @Test fun `late scenes use the idle camera observed while there were no markers or query`() {
        val map = Shadow.newInstanceOf(NaverMap::class.java)
        val state = Shadow.extract<MapShadow>(map)
        var moments by mutableStateOf(emptyList<MomentMarkerState>())
        var activeQuery by mutableStateOf<MapVisibilityQuery?>(null)
        var result: MapVisibilityResult? = null
        var mounted by mutableStateOf(true)
        compose.setContent {
            if (mounted) NaverGroupedMomentLayer(map, moments, IntSize(360, 640), 1f,
                NaverWalkLayerOrder.resolve(null), {}, query = activeQuery, onVisibility = { result = it })
        }
        compose.runOnIdle {
            state.finishMoving()
            moments = listOf(moment); activeQuery = query
        }
        compose.runOnIdle {
            assertEquals(emptySet<String>(), result?.visibleIds)
            state.startMoving()
            assertNull(result?.visibleIds)
            state.finishMoving()
            assertEquals(emptySet<String>(), result?.visibleIds)
            mounted = false
        }
        compose.runOnIdle { assertTrue(state.idle.isEmpty()); assertTrue(state.moving.isEmpty()) }
    }

    @Test fun `mounting on an already idle map reports visibility without a new gesture`() {
        val map = Shadow.newInstanceOf(NaverMap::class.java)
        Shadow.extract<MapShadow>(map).pending = false
        var result: MapVisibilityResult? = null
        compose.setContent {
            NaverGroupedMomentLayer(map, listOf(moment), IntSize(360, 640), 1f,
                NaverWalkLayerOrder.resolve(null), {}, query = query, onVisibility = { result = it })
        }
        compose.runOnIdle { assertEquals(emptySet<String>(), result?.visibleIds) }
    }

    @Implements(value = NaverMap::class, isInAndroidSdk = false, callThroughByDefault = false)
    class MapShadow {
        var pending = true
        val idle = mutableSetOf<NaverMap.OnCameraIdleListener>()
        val moving = mutableSetOf<NaverMap.OnCameraChangeListener>()
        private val projection = Shadow.newInstanceOf(Projection::class.java)
        @Implementation fun getProjection() = projection
        @Implementation fun isCameraIdlePending() = pending
        @Implementation fun addOnCameraIdleListener(listener: NaverMap.OnCameraIdleListener) { check(idle.add(listener)) }
        @Implementation fun removeOnCameraIdleListener(listener: NaverMap.OnCameraIdleListener) { check(idle.remove(listener)) }
        @Implementation fun addOnCameraChangeListener(listener: NaverMap.OnCameraChangeListener) { check(moving.add(listener)) }
        @Implementation fun removeOnCameraChangeListener(listener: NaverMap.OnCameraChangeListener) { check(moving.remove(listener)) }
        fun startMoving() { pending = true; moving.toList().forEach { it.onCameraChange(0, false) } }
        fun finishMoving() { pending = false; idle.toList().forEach { it.onCameraIdle() } }
    }

    @Implements(value = Projection::class, isInAndroidSdk = false, callThroughByDefault = false)
    class ProjectionShadow {
        @Implementation fun toScreenLocation(point: LatLng) = PointF(-1000f, -1000f)
    }
}
