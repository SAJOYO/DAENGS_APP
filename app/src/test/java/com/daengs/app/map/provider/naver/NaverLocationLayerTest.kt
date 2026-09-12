package com.daengs.app.map.provider.naver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.R
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.shell.BaseMapStyle
import com.daengs.app.map.shell.MapScene
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraPosition
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.LocationOverlay
import com.naver.maps.map.overlay.Overlay
import com.naver.maps.map.overlay.OverlayImage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow

/** Executes real Compose effects against SDK shadows; does not render native map tiles. */
@RunWith(RobolectricTestRunner::class)
// Instrument the Android SDK jar too: its optimized classes omit JVM stack-map frames.
@Config(sdk = [35], instrumentedPackages = ["com.naver.maps"], shadows = [NaverLocationLayerTest.MapShadow::class,
    NaverLocationLayerTest.OverlayShadow::class, NaverLocationLayerTest.LocationShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NaverLocationLayerTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val point = GeoPoint(37.5, 127.0)
    private fun map() = Shadow.newInstanceOf(NaverMap::class.java)
    private fun state(map: NaverMap): MapShadow = Shadow.extract(map)
    private fun location(map: NaverMap): LocationShadow = Shadow.extract(map.locationOverlay)
    private fun photo() = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
    private fun OverlayImage.bitmap() = requireNotNull(getBitmap(context))

    @Test fun `late map and location updates keep one rotation listener until leaving the screen`() {
        var activeMap by mutableStateOf<NaverMap?>(null)
        var scene by mutableStateOf(MapScene())
        var visible by mutableStateOf(true)
        compose.setContent {
            if (visible) NaverLocationLayer(activeMap, scene.currentPosition, null, null)
        }
        val map = map()
        state(map).rotate(60.0)
        compose.runOnIdle { activeMap = map }
        compose.runOnIdle {
            assertEquals(1, state(map).listeners.size)
            assertFalse(location(map).visible)
            assertEquals(60f, location(map).rotation, 0f)
            scene = scene.copy(currentPosition = point)
        }
        compose.runOnIdle {
            assertTrue(location(map).visible)
            assertEquals(LatLng(point.latitude, point.longitude), location(map).coordinate)
            state(map).rotate(135.0)
            assertEquals(135f, location(map).rotation, 0f)
            scene = scene.copy(currentPosition = GeoPoint(37.6, 127.1))
        }
        compose.runOnIdle {
            assertEquals(LatLng(37.6, 127.1), location(map).coordinate)
            scene = scene.copy(baseMapStyle = BaseMapStyle.SEARCH_DETAIL)
        }
        compose.runOnIdle {
            assertEquals(1, state(map).registrations)
            assertEquals(0, state(map).removals)
            scene = scene.copy(currentPosition = null)
        }
        compose.runOnIdle {
            assertFalse(location(map).visible)
            scene = scene.copy(currentPosition = point)
        }
        compose.runOnIdle {
            assertTrue(location(map).visible)
            visible = false
        }
        compose.runOnIdle {
            assertFalse(location(map).visible)
            assertTrue(state(map).listeners.isEmpty())
            assertEquals(1, state(map).removals)
        }
    }

    @Test fun `photo changes preserve the overlay and clearing the avatar restores original SDK dimensions`() {
        val map = map()
        val originalIcon = location(map).image
        var avatarRes by mutableStateOf<Int?>(R.drawable.ic_location_paw)
        var avatarPhoto by mutableStateOf<Bitmap?>(null)
        var currentPosition by mutableStateOf(point)
        compose.setContent { NaverLocationLayer(map, currentPosition, avatarRes, avatarPhoto) }
        lateinit var paw: OverlayImage
        compose.runOnIdle {
            paw = location(map).image
            assertNotSame(originalIcon, paw)
            assertEquals(96, location(map).width)
            assertEquals(96, location(map).height)
            avatarPhoto = photo()
        }
        lateinit var portrait: OverlayImage
        compose.runOnIdle {
            portrait = location(map).image
            assertEquals(Color.RED, portrait.bitmap().getPixel(48, 48))
            assertEquals(Color.WHITE, portrait.bitmap().getPixel(48, 1))
            currentPosition = GeoPoint(37.6, 127.1)
        }
        compose.runOnIdle {
            assertSame(portrait, location(map).image)
            avatarPhoto = null
        }
        compose.runOnIdle {
            assertTrue(paw.bitmap().sameAs(location(map).image.bitmap()))
            avatarRes = null
        }
        compose.runOnIdle {
            assertSame(originalIcon, location(map).image)
            assertEquals(41, location(map).width)
            assertEquals(43, location(map).height)
            assertEquals(1, state(map).registrations)
            assertEquals(0, state(map).removals)
        }
    }

    @Test fun `switching maps releases the old overlay and remembers each maps own SDK defaults`() {
        val first = map()
        val second = map()
        location(second).width = 47
        location(second).height = 53
        val secondDefault = location(second).image
        var activeMap by mutableStateOf<NaverMap?>(first)
        var avatarPhoto by mutableStateOf<Bitmap?>(photo())
        compose.setContent { NaverLocationLayer(activeMap, point, null, avatarPhoto) }
        compose.runOnIdle {
            assertTrue(location(first).visible)
            activeMap = second
        }
        compose.runOnIdle {
            assertFalse(location(first).visible)
            assertTrue(state(first).listeners.isEmpty())
            assertEquals(1, state(first).removals)
            assertTrue(location(second).visible)
            assertEquals(1, state(second).listeners.size)
            assertEquals(Color.RED, location(second).image.bitmap().getPixel(48, 48))
            avatarPhoto = null
        }
        compose.runOnIdle {
            assertSame(secondDefault, location(second).image)
            assertEquals(47, location(second).width)
            assertEquals(53, location(second).height)
            activeMap = null
        }
        compose.runOnIdle {
            assertFalse(location(second).visible)
            assertTrue(state(second).listeners.isEmpty())
            assertEquals(1, state(second).removals)
        }
    }

    @Implements(value = NaverMap::class, isInAndroidSdk = false, callThroughByDefault = false)
    class MapShadow {
        val overlay: LocationOverlay = Shadow.newInstanceOf(LocationOverlay::class.java)
        val listeners = mutableSetOf<NaverMap.OnCameraChangeListener>()
        var registrations = 0
        var removals = 0
        private var bearing = 0.0
        @Implementation fun getLocationOverlay() = overlay
        @Implementation fun getCameraPosition() = CameraPosition(LatLng(37.5, 127.0), 14.0, 0.0, bearing)
        @Implementation fun addOnCameraChangeListener(listener: NaverMap.OnCameraChangeListener) {
            check(listeners.add(listener)); registrations++
        }
        @Implementation fun removeOnCameraChangeListener(listener: NaverMap.OnCameraChangeListener) {
            check(listeners.remove(listener)); removals++
        }
        fun rotate(degrees: Double) {
            bearing = degrees
            listeners.toList().forEach { it.onCameraChange(0, false) }
        }
    }

    @Implements(value = Overlay::class, isInAndroidSdk = false, callThroughByDefault = false)
    open class OverlayShadow {
        @JvmField var visible = false
        @Implementation fun isVisible() = visible
        @Implementation fun setVisible(value: Boolean) { visible = value }
        companion object {
            // Native Android libraries cannot load in the Windows JVM test process.
            @JvmStatic @Implementation fun __staticInitializer__() = Unit
        }
    }

    @Implements(value = LocationOverlay::class, isInAndroidSdk = false, callThroughByDefault = false)
    class LocationShadow : OverlayShadow() {
        var coordinate = LatLng(0.0, 0.0)
        var rotation = 0f
        var image = OverlayImage.fromResource(R.drawable.ic_location_paw)
        var width = 41
        var height = 43
        @Implementation fun getPosition() = coordinate
        @Implementation fun setPosition(value: LatLng) { coordinate = value }
        @Implementation fun getBearing() = rotation
        @Implementation fun setBearing(value: Float) { rotation = value }
        @Implementation fun getIcon() = image
        @Implementation fun setIcon(value: OverlayImage) { image = value }
        @Implementation fun getIconWidth() = width
        @Implementation fun setIconWidth(value: Int) { width = value }
        @Implementation fun getIconHeight() = height
        @Implementation fun setIconHeight(value: Int) { height = value }
    }
}
