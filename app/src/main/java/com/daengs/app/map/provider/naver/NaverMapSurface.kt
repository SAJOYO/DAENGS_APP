package com.daengs.app.map.provider.naver

import android.graphics.Color
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.graphics.toArgb
import com.daengs.app.BuildConfig
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.shell.MapScene
import com.daengs.app.ui.theme.CreamBg
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraAnimation
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.LocationOverlay
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage

@Composable
fun NaverMapSurface(
    scene: MapScene,
    searchOrigin: GeoPoint?,
    followDevice: Boolean,
    onCameraIdle: (GeoPoint) -> Unit,
    onCameraGesture: () -> Unit,
    onSelectPlace: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(context) }
    var naverMap by remember { mutableStateOf<NaverMap?>(null) }
    val latestCameraCallback by rememberUpdatedState(onCameraIdle)
    val latestGestureCallback by rememberUpdatedState(onCameraGesture)
    // Idle fires for our own moveCamera calls too. Without the reason, following the device
    // would look exactly like the user panning the map.
    val lastCameraReason = remember { mutableIntStateOf(CameraUpdate.REASON_DEVELOPER) }

    DisposableEffect(mapView, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = {
            mapView.apply {
                getMapAsync { map ->
                    naverMap = map
                    map.uiSettings.isLocationButtonEnabled = false
                    map.uiSettings.isZoomControlEnabled = false
                    // Nothing in this app is answered by a view wider than a city: search caps at
                    // a 10km radius and a walk is a few km. The floor also keeps marker count sane.
                    map.minZoom = MIN_ZOOM
                    map.extent = KOREA_EXTENT
                    applyDaengsStyle(map)
                    map.addOnCameraChangeListener { reason, _ ->
                        lastCameraReason.intValue = reason
                        if (reason == CameraUpdate.REASON_GESTURE) latestGestureCallback()
                    }
                    map.addOnCameraIdleListener {
                        if (!lastCameraReason.intValue.isUserDriven()) return@addOnCameraIdleListener
                        val target = map.cameraPosition.target
                        latestCameraCallback(GeoPoint(target.latitude, target.longitude))
                    }
                }
            }
        },
        modifier = modifier,
    )

    LaunchedEffect(naverMap, searchOrigin) {
        val map = naverMap ?: return@LaunchedEffect
        val origin = searchOrigin ?: return@LaunchedEffect
        map.moveCamera(
            CameraUpdate.scrollAndZoomTo(LatLng(origin.latitude, origin.longitude), 14.5)
                .animate(CameraAnimation.Easing),
        )
    }

    LaunchedEffect(naverMap, scene.currentPosition, followDevice) {
        val map = naverMap ?: return@LaunchedEffect
        val point = scene.currentPosition ?: return@LaunchedEffect
        if (followDevice) map.moveCamera(CameraUpdate.scrollTo(point.toLatLng()))
    }

    DisposableEffect(naverMap) {
        val overlay: LocationOverlay? = naverMap?.locationOverlay
        onDispose { overlay?.isVisible = false }
    }

    LaunchedEffect(naverMap, scene.currentPosition) {
        val overlay = naverMap?.locationOverlay ?: return@LaunchedEffect
        val point = scene.currentPosition
        if (point == null) {
            overlay.isVisible = false
        } else {
            overlay.position = point.toLatLng()
            overlay.isVisible = true
        }
    }

    DisposableEffect(naverMap, scene.places) {
        val map = naverMap
        val markers = if (map == null) emptyList() else scene.places.map { place ->
            Marker().apply {
                position = place.point.toLatLng()
                captionText = place.label
                captionMinZoom = 13.0
                width = if (place.selected) 84 else 64
                height = if (place.selected) 105 else 80
                // Selection is size and stacking order. Keeping the group icon means the
                // selected pin still says what kind of place it is.
                icon = OverlayImage.fromResource(place.iconGroup.marker)
                zIndex = if (place.selected) SELECTED_MARKER_Z else 0
                isHideCollidedMarkers = true
                setOnClickListener {
                    onSelectPlace(place.id)
                    true
                }
                this.map = map
            }
        }
        onDispose { markers.forEach { it.map = null } }
    }

}


private fun Int.isUserDriven(): Boolean =
    this == CameraUpdate.REASON_GESTURE || this == CameraUpdate.REASON_CONTROL

private fun GeoPoint.toLatLng(): LatLng = LatLng(latitude, longitude)

/** Selected place pins draw above their neighbours so the choice stays visible when markers collide. */
private const val SELECTED_MARKER_Z = 100

/** Zoom floor. Below this the search radius cap (10km) is already off-screen and marker count spikes. */
/**
 * 지도를 앱 화풍에 맞춘다.
 *
 * 두 층이다. **콘솔에서 만든 스타일**([BuildConfig.NAVER_MAP_STYLE_ID])이 타일 자체의
 * 색(땅·물·도로·건물)을 정하고, 여기 표시 옵션이 그 위에서 밀도를 정한다.
 * 스타일이 없어도 표시 옵션은 그대로 먹으므로 **키가 없는 사람도 절반은 받는다.**
 *
 * ⚠️ 간이 모드(`isLiteModeEnabled`)를 켜면 **아래가 전부 무시된다.** 켜지 말 것.
 * ⚠️ 네이버 로고는 가리거나 지우지 않는다 (SDK 이용 조건).
 */
private fun applyDaengsStyle(map: NaverMap) {
    // 타일이 오기 전에 깔리는 바탕. 기본값이 회색이라 지도가 뜰 때마다 **회색이
    // 한 번 번쩍인다** — 크림으로 두면 앱 배경에서 지도가 자라나는 것처럼 보인다.
    map.backgroundColor = CreamBg.toArgb()

    // 건물을 2D 로. 기울이지 않는 화면이라 3D 가 정보를 주지 않고,
    // 입체 그림자가 우리 마커와 겹쳐서 마커가 건물에 파묻힌다.
    map.buildingHeight = 0f

    // 지도가 앱보다 진해서 아주 살짝 뺀다. 오버레이에는 안 먹으므로 **마커·경로는
    // 그대로 선명하다** — 그래서 지도만 뒤로 물러난다.
    //
    // 스타일이 붙으면 0 이다. 스타일이 이미 밝은 색으로 칠해져 있어서 여기서 또
    // 밝히면 물·녹지가 바래서 안 보인다. 실기기에서 0.35 로 해보니 그랬다.
    map.lightness = if (BuildConfig.NAVER_MAP_STYLE_ID.isBlank()) LIGHTNESS_PLAIN else 0f

    // `symbolScale` 은 **건드리지 않는다.** 지도 상호명이 우리 마커와 경쟁해서 줄여
    // 봤는데(0.5), 글자가 작아진 자리에 **라벨이 더 많이 들어차서 오히려 복잡해졌다.**
    // 반대로 키우면(1.25) 개수는 줄지만 글자가 우리 마커만큼 커진다. 기본값이 낫다.
    //
    // 지하철 노선 색도 여기서 못 뺀다. `LAYER_GROUP_TRANSIT` 을 꺼도 그대로인 걸 보면
    // **기본 타일에 그려져 있다** — 그건 스타일 에디터에서 색을 줘야 한다.

    if (BuildConfig.NAVER_MAP_STYLE_ID.isNotBlank()) {
        map.setCustomStyleId(
            BuildConfig.NAVER_MAP_STYLE_ID,
            object : NaverMap.OnCustomStyleLoadCallback {
                override fun onCustomStyleLoaded() = Unit

                // **삼키면 안 된다.** 실패해도 기본 지도가 멀쩡히 뜨기 때문에,
                // 로그가 없으면 "스타일을 안 만든 건지 ID 가 틀린 건지" 구분이 안 된다.
                override fun onCustomStyleLoadFailed(exception: Exception) {
                    Log.w(
                        TAG,
                        "지도 스타일(${BuildConfig.NAVER_MAP_STYLE_ID})을 불러오지 못했다. " +
                            "기본 지도로 표시한다.",
                        exception,
                    )
                }
            },
        )
    }
}

private const val TAG = "DaengsMap"

/**
 * 스타일이 없을 때만 쓰는 보정.
 *
 * 0.35 는 너무 바랬고(도로가 배경에 녹았다), 0.15 가 **지도는 물러나되 길은 읽히는**
 * 자리였다. 실기기에서 세 값을 세워 보고 정했다.
 */
private const val LIGHTNESS_PLAIN = 0.15f

private const val MIN_ZOOM = 11.0

/** The camera cannot leave the country the data covers — every source is domestic. */
private val KOREA_EXTENT = LatLngBounds(LatLng(32.9, 124.0), LatLng(38.7, 132.0))
