package com.daengs.app.map.provider.naver

import android.graphics.PointF
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.completedroute.SessionRouteExplorerLayerState
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.NaverMap
import com.naver.maps.map.Projection
import com.naver.maps.map.overlay.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import org.robolectric.shadow.api.Shadow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], instrumentedPackages = ["com.naver.maps"], shadows = [
    NaverSessionRouteExplorerTest.MapShadow::class, NaverSessionRouteExplorerTest.ProjectionShadow::class,
    NaverSessionRouteExplorerTest.OverlayShadow::class, NaverSessionRouteExplorerTest.MarkerShadow::class])
class NaverSessionRouteExplorerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `camera and viewport redraw retain anchors while cursor updates and disposal respect ownership`() {
        val map = Shadow.newInstanceOf(NaverMap::class.java)
        val native = Shadow.extract<MapShadow>(map)
        var mounted by mutableStateOf(true)
        var explorer by mutableStateOf(SessionRouteExplorerLayerState())
        var size by mutableStateOf(IntSize(400, 320))
        var count = -1
        val paths = listOf(listOf(GeoPoint(14.0, .04), GeoPoint(14.0, .36)))
        compose.setContent {
            if (mounted) NaverSessionRouteExplorer(map, explorer, paths, emptyList(), size, 0, 1f, { count = it })
        }
        var original = emptyList<LatLng>()
        compose.runOnIdle {
            assertTrue(count in 2..5)
            original = native.markers.map { it.coordinate }
            assertTrue(native.markers.all { it.iconWidth == 18 })
            native.moving.forEach { it.onCameraChange(0, false) }
            assertTrue(native.markers.all { !it.visible })
            Shadow.extract<ProjectionShadow>(native.projection).shift = 3f
            native.idle.forEach { it.onCameraIdle() }
            assertEquals(original, native.markers.map { it.coordinate })
            assertTrue(native.markers.all { it.visible })
            size = IntSize(406, 320)
        }
        compose.runOnIdle {
            assertEquals(original, native.markers.map { it.coordinate })
            assertEquals(1, native.idle.size)
            assertEquals(1, native.moving.size)
            explorer = explorer.copy(cursor = GeoPoint(14.0, .2))
        }
        compose.runOnIdle {
            assertEquals(original.size + 1, native.markers.size)
            assertEquals(original, native.markers.filter { it.iconWidth == 18 }.map { it.coordinate })
            mounted = false
        }
        compose.runOnIdle {
            assertTrue(native.markers.isEmpty())
            assertTrue(native.idle.isEmpty())
            assertTrue(native.moving.isEmpty())
        }
    }

    @Implements(value = NaverMap::class, isInAndroidSdk = false, callThroughByDefault = false)
    class MapShadow {
        @JvmField val projection = Shadow.newInstanceOf(Projection::class.java)
        val markers = mutableListOf<MarkerShadow>()
        val idle = mutableSetOf<NaverMap.OnCameraIdleListener>()
        val moving = mutableSetOf<NaverMap.OnCameraChangeListener>()
        @Implementation fun getProjection() = projection
        @Implementation fun addOnCameraIdleListener(value: NaverMap.OnCameraIdleListener) { check(idle.add(value)) }
        @Implementation fun removeOnCameraIdleListener(value: NaverMap.OnCameraIdleListener) { check(idle.remove(value)) }
        @Implementation fun addOnCameraChangeListener(value: NaverMap.OnCameraChangeListener) { check(moving.add(value)) }
        @Implementation fun removeOnCameraChangeListener(value: NaverMap.OnCameraChangeListener) { check(moving.remove(value)) }
    }
    @Implements(value = Projection::class, isInAndroidSdk = false, callThroughByDefault = false)
    class ProjectionShadow {
        var shift = 0f
        @Implementation fun toScreenLocation(p: LatLng) = PointF((p.longitude * 1000).toFloat() + shift, (p.latitude * 10).toFloat())
        @Implementation fun fromScreenLocation(p: PointF) = LatLng(p.y / 10.0, (p.x - shift) / 1000.0)
    }
    @Implements(value = Overlay::class, isInAndroidSdk = false, callThroughByDefault = false)
    open class OverlayShadow {
        @JvmField var visible = true
        @Implementation fun setVisible(value: Boolean) { visible = value }
        companion object { @JvmStatic @Implementation fun __staticInitializer__() = Unit }
    }
    @Implements(value = Marker::class, isInAndroidSdk = false, callThroughByDefault = false)
    class MarkerShadow : OverlayShadow() {
        var coordinate = LatLng(0.0, 0.0)
        var iconWidth = 0
        var owner: NaverMap? = null
        @Implementation fun __constructor__() = Unit
        @Implementation fun __constructor__(point: LatLng, image: OverlayImage) { coordinate = point }
        @Implementation fun setPosition(value: LatLng) { coordinate = value }
        @Implementation fun setWidth(value: Int) { iconWidth = value }
        @Implementation fun setMap(value: NaverMap?) {
            owner?.let { Shadow.extract<MapShadow>(it).markers.remove(this) }
            owner = value
            value?.let { Shadow.extract<MapShadow>(it).markers.add(this) }
        }
        companion object { @JvmStatic @Implementation fun __staticInitializer__() = Unit }
    }
}
