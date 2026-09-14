package com.daengs.app.map.provider.naver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.R
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.moments.MomentMarkerState
import com.daengs.app.walk.WalkMomentType
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.Overlay
import com.naver.maps.map.overlay.OverlayImage
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import org.robolectric.shadow.api.Shadow

/** Real Compose effects and photo decoding; SDK shadows replace only native overlay calls. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], instrumentedPackages = ["com.naver.maps"], shadows = [
    NaverMomentLayerTest.MapShadow::class, NaverMomentLayerTest.OverlayShadow::class,
    NaverMomentLayerTest.MarkerShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NaverMomentLayerTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val pin = MomentMarkerState("note", GeoPoint(37.5, 127.0), "메모")
    private val diagnostics = WalkMapDiagnostics()
    private fun map() = Shadow.newInstanceOf(NaverMap::class.java)
    private fun marker() = diagnostics.overlays.keys.single() as Marker
    private fun state(marker: Marker) = Shadow.extract<MarkerShadow>(marker)

    @Test fun `late map callback replacement and leaving keep the correct marker lifetime`() {
        var activeMap by mutableStateOf<NaverMap?>(null)
        var mounted by mutableStateOf(true)
        var clicked = ""
        var callback by mutableStateOf<(String) -> Unit>({ clicked = "old:$it" })
        compose.setContent {
            if (mounted) NaverMomentLayer(activeMap, listOf(pin), callback, diagnostics, 200_000)
        }
        val first = map()
        compose.runOnIdle { assertTrue(diagnostics.overlays.isEmpty()); activeMap = first }
        lateinit var old: Marker
        compose.runOnIdle {
            old = marker()
            assertSame(first, state(old).owner)
            assertEquals(LatLng(37.5, 127.0), state(old).coordinate)
            callback = { clicked = "new:$it" }
        }
        compose.runOnIdle {
            assertSame(old, marker())
            assertTrue(requireNotNull(state(old).click).onClick(old))
            assertEquals("new:note", clicked)
            activeMap = map()
        }
        lateinit var replacement: Marker
        compose.runOnIdle {
            assertNull(state(old).owner)
            assertEquals(1, state(old).detachments)
            replacement = marker()
            assertNotSame(old, replacement)
            assertSame(activeMap, state(replacement).owner)
            mounted = false
        }
        compose.runOnIdle {
            assertNull(state(replacement).owner)
            assertEquals(1, state(replacement).detachments)
            assertTrue(diagnostics.overlays.isEmpty())
        }
    }

    @Test fun `selection and hiding retain sizes anchors and layer ordering`() {
        val map = map()
        var moments by mutableStateOf(listOf(pin))
        compose.setContent { NaverMomentLayer(map, moments, {}, diagnostics, 200_000) }
        lateinit var initial: Marker
        compose.runOnIdle {
            initial = marker()
            assertEquals(64, state(initial).width)
            assertEquals(64, state(initial).height)
            assertEquals(PointF(.5f, .933f), state(initial).anchor)
            assertEquals(50, state(initial).localZ)
            assertEquals(200_000, state(initial).globalZ)
            moments = listOf(pin.copy(selected = true, aboveRouteEndpoints = true))
        }
        compose.runOnIdle {
            assertNull(state(initial).owner)
            assertEquals(82, state(marker()).width)
            assertEquals(140, state(marker()).localZ)
            assertEquals("메모", state(marker()).caption)
            moments = emptyList()
        }
        compose.runOnIdle { assertTrue(diagnostics.overlays.isEmpty()) }
    }

    @Test fun `behavior art takes priority over ordinal badges and keeps a centered anchor`() {
        val map = map()
        var moments by mutableStateOf(listOf(pin.copy(behaviors = setOf(WalkMomentType.SNIFFING), sequenceLabel = "2")))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                NaverMomentLayer(map, moments, {}, diagnostics, 200_000)
            }
        }
        compose.runOnIdle {
            assertEquals(PointF(.5f, .5f), state(marker()).anchor)
            assertEquals(40, state(marker()).width)
            assertEquals("메모", state(marker()).caption)
            moments = listOf(pin.copy(sequenceLabel = "2"))
        }
        compose.runOnIdle {
            assertEquals(PointF(.5f, 1f), state(marker()).anchor)
            assertEquals("", state(marker()).caption)
        }
    }

    @Test fun `photo replacement uses new pixels and missing file keeps a clickable fallback`() {
        fun photo(color: Int): File {
            val file = File.createTempFile("moment-layer-", ".png", context.cacheDir)
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            return file
        }
        val red = photo(Color.RED); val blue = photo(Color.BLUE)
        val missing = File(context.cacheDir, "missing-${System.nanoTime()}.png")
        val map = map()
        var moments by mutableStateOf(listOf(pin.copy(photoFile = red)))
        var clicked: String? = null
        fun center() = state(marker()).image?.getBitmap(context)?.let {
            if (it.width == 104) it.getPixel(52, 52) else null
        }
        try {
            compose.setContent { NaverMomentLayer(map, moments, { clicked = it }, diagnostics, 200_000) }
            compose.waitUntil(10_000) { compose.runOnIdle { diagnostics.overlays.size == 1 && center() == Color.RED } }
            compose.runOnIdle { moments = listOf(pin.copy(photoFile = blue)) }
            compose.waitUntil(10_000) { compose.runOnIdle { diagnostics.overlays.size == 1 && center() == Color.BLUE } }
            compose.runOnIdle { moments = listOf(pin.copy(photoFile = missing)) }
            compose.waitUntil(10_000) {
                compose.runOnIdle {
                    diagnostics.overlays.size == 1 && state(marker()).image == OverlayImage.fromResource(R.drawable.ic_walk_moment)
                }
            }
            compose.runOnIdle {
                val current = marker()
                assertTrue(requireNotNull(state(current).click).onClick(current))
                assertEquals(pin.id, clicked)
            }
        } finally { red.delete(); blue.delete() }
    }

    @Implements(value = NaverMap::class, isInAndroidSdk = false, callThroughByDefault = false)
    class MapShadow

    @Implements(value = Overlay::class, isInAndroidSdk = false, callThroughByDefault = false)
    open class OverlayShadow {
        var owner: NaverMap? = null
        var detachments = 0
        var globalZ = 0
        var localZ = 0
        var click: Overlay.OnClickListener? = null
        @Implementation fun getMap() = owner
        @Implementation open fun setMap(value: NaverMap?) { if (owner != null && value == null) detachments++; owner = value }
        @Implementation fun getGlobalZIndex() = globalZ
        @Implementation open fun setGlobalZIndex(value: Int) { globalZ = value }
        @Implementation fun setZIndex(value: Int) { localZ = value }
        @Implementation fun setOnClickListener(value: Overlay.OnClickListener?) { click = value }
        companion object { @JvmStatic @Implementation fun __staticInitializer__() = Unit }
    }

    @Implements(value = Marker::class, isInAndroidSdk = false, callThroughByDefault = false)
    class MarkerShadow : OverlayShadow() {
        var coordinate = LatLng(0.0, 0.0)
        @JvmField var width = 0
        @JvmField var height = 0
        @JvmField var anchor = PointF()
        var caption = ""
        var image: OverlayImage? = null
        @Implementation fun setPosition(value: LatLng) { coordinate = value }
        @Implementation fun setWidth(value: Int) { width = value }
        @Implementation fun setHeight(value: Int) { height = value }
        @Implementation fun setAnchor(value: PointF) { anchor = value }
        // Marker overrides these SDK methods; Robolectric dispatches at their declaring class.
        @Implementation override fun setMap(value: NaverMap?) = super.setMap(value)
        @Implementation override fun setGlobalZIndex(value: Int) = super.setGlobalZIndex(value)
        @Implementation fun setCaptionText(value: String) { caption = value }
        @Implementation fun setIcon(value: OverlayImage) { image = value }
        companion object { @JvmStatic @Implementation fun __staticInitializer__() = Unit }
    }
}
