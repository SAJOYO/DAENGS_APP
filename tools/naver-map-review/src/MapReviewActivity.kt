package com.daengs.app.map.review

import android.app.Application
import android.os.Bundle
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.daengs.app.BuildConfig
import com.daengs.app.R
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.provider.naver.NaverMapSurface
import com.daengs.app.map.shell.MapCameraSnapshot
import com.daengs.app.map.shell.MapScene
import com.daengs.app.ui.theme.DaengsTheme
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.NaverMapSdk

/** This opt-in app has no authentication, recording runtime or database initialization. */
class MapReviewApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        check(BuildConfig.APPLICATION_ID.endsWith(".locationreview"))
        require(BuildConfig.NAVER_MAP_NCP_KEY_ID.isNotBlank()) { "Set DAENGS_NAVER_NCP_KEY_ID before building" }
        NaverMapSdk.getInstance(this).client = NaverMapSdk.NcpKeyClient(BuildConfig.NAVER_MAP_NCP_KEY_ID)
    }
}

/** No buttons, animations or timer-driven redraws can hide a stalled native Surface. */
class MapReviewActivity : ComponentActivity() {
    var generation by mutableIntStateOf(0)
    var showMap by mutableStateOf(true)
    var point by mutableStateOf<GeoPoint?>(ORIGIN)
    var follow by mutableStateOf(false)
    private var observedView: MapView? = null
    private var observedMap: NaverMap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DaengsTheme {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Text("네이버 지도 첫 그리기 검증 · 합성 위치", Modifier.padding(12.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (showMap) key(generation) {
                        NaverMapSurface(
                            scene = MapScene(currentPosition = point), searchOrigin = null,
                            followDevice = follow, avatarRes = R.drawable.ic_location_paw,
                            initialCamera = MapCameraSnapshot(ORIGIN, 17.0, 0.0, 0.0),
                            onCameraIdle = {}, onCameraGesture = {}, onSelectPlace = {},
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        } }
    }

    private inline fun <reified T : View> find(view: View): T? = descendants(view).filterIsInstance<T>().firstOrNull()
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }

    fun mapView(): MapView? = find(window.decorView)
    fun surfaceReady(): Boolean = mapView()?.let { find<SurfaceView>(it)?.holder?.surface?.isValid } == true

    fun map(): NaverMap? {
        val view = mapView()
        if (view !== observedView) {
            observedView = view
            observedMap = null
            view?.getMapAsync { if (observedView === view) observedMap = it }
        }
        return observedMap
    }

    companion object { val ORIGIN = GeoPoint(37.56661, 126.978388) }
}
