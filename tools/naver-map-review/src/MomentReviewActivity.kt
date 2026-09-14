package com.daengs.app.map.review

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.daengs.app.R
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.moments.MomentMarkerState
import com.daengs.app.map.layers.trail.TrailLayerState
import com.daengs.app.map.provider.naver.*
import com.daengs.app.map.shell.MapCameraSnapshot
import com.daengs.app.map.shell.MapScene
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.WalkMomentType
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.PathOverlay
import java.io.File

/** Fixture-only native map; production Surface and layers, no recording or account data. */
class MomentReviewActivity : ComponentActivity() {
    var generation by mutableIntStateOf(0)
    var showMap by mutableStateOf(true)
    var moments by mutableStateOf(emptyList<MomentMarkerState>())
    var callbackVersion by mutableIntStateOf(0)
    var appliedCallbackVersion = -1; private set
    var lastClick by mutableStateOf("")
    internal val diagnostics = WalkMapDiagnostics()
    lateinit var bluePhoto: File; private set
    private var observedView: MapView? = null
    private var observedMap: NaverMap? = null
    private lateinit var trail: TrailLayerState
    private fun point(x: Double, y: Double) = GeoPoint(37.56661 + y, 126.978388 + x)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val red = fixturePhoto("red", Color.RED)
        bluePhoto = fixturePhoto("blue", Color.BLUE)
        moments = listOf(
            MomentMarkerState("bark", point(-.002, .001), "짖기", behaviors = setOf(WalkMomentType.BARKING)),
            MomentMarkerState("sniff", point(0.0, .001), "킁킁", behaviors = setOf(WalkMomentType.SNIFFING)),
            MomentMarkerState("excretion", point(.002, .001), "배설", behaviors = setOf(WalkMomentType.EXCRETION)),
            MomentMarkerState("note", point(-.002, -.001), "메모", behaviors = setOf(WalkMomentType.NOTE)),
            MomentMarkerState("photo", point(0.0, -.001), "사진", photoFile = red),
            MomentMarkerState("missing", point(.002, -.001), "누락 사진", photoFile = File(cacheDir, "absent.png")),
        )
        trail = TrailLayerState(paths = listOf(moments.map { it.point }))
        setContent { DaengsTheme { CompositionLocalProvider(LocalWalkMapDiagnostics provides diagnostics) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Text("행동·사진 핀 검증 · 합성 위치", Modifier.padding(12.dp))
                Text("선택: $lastClick", Modifier.padding(horizontal = 12.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (showMap) key(generation) {
                        val version = callbackVersion
                        SideEffect { appliedCallbackVersion = version }
                        NaverMapSurface(MapScene(currentPosition = point(0.0, 0.0), moments = moments, trail = trail),
                            searchOrigin = null, followDevice = false, avatarRes = R.drawable.ic_location_paw,
                            initialCamera = MapCameraSnapshot(point(0.0, 0.0), 16.0, 0.0, 0.0),
                            onCameraIdle = {}, onCameraGesture = {}, onSelectPlace = {},
                            onSelectMoment = { id ->
                                lastClick = "$version:$id"
                                moments = moments.map { it.copy(selected = it.id == id) }
                            }, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        } } }
    }

    private fun fixturePhoto(name: String, color: Int): File = File(cacheDir, "moment-$name.png").also { file ->
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }
    fun mapView() = descendants(window.decorView).filterIsInstance<MapView>().firstOrNull()
    fun map(): NaverMap? {
        val view = mapView()
        if (view !== observedView) {
            observedView = view; observedMap = null
            view?.getMapAsync { if (observedView === view) observedMap = it }
        }
        return observedMap
    }
    fun markers() = diagnostics.overlays.keys.filterIsInstance<Marker>()
    fun route() = diagnostics.overlays.keys.filterIsInstance<PathOverlay>().singleOrNull()
    fun marker(id: String): Marker? {
        val p = moments.single { it.id == id }.point
        return markers().singleOrNull { it.position.latitude == p.latitude && it.position.longitude == p.longitude }
    }
}
