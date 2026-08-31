package com.daengs.app.map.provider.naver

import android.graphics.Color
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
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.shell.MapScene
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
    // idle 은 **우리가 부른 moveCamera 에도** 뜬다. 이유를 같이 안 보면, 기기를 따라
    // 카메라가 움직인 것과 사용자가 지도를 민 것이 똑같아 보인다.
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
                    // 이 앱에는 도시보다 넓게 봐서 답이 나오는 화면이 없다 — 검색은
                    // 반경 10km 가 상한이고 산책은 몇 km 다. 하한을 두면 마커 수도 안 터진다.
                    map.minZoom = MIN_ZOOM
                    map.extent = KOREA_EXTENT
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
                // 선택은 크기와 앞뒤 순서로만 표현한다. 묶음 아이콘을 그대로 두어야
                // 고른 핀도 여전히 "어떤 곳인지"를 말해 준다.
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

/** 고른 핀은 이웃 위에 그린다. 마커가 겹칠 때 고른 것이 가려지면 안 된다. */
private const val SELECTED_MARKER_Z = 100

/** 줌 하한. 이보다 멀어지면 검색 반경 상한(10km)이 이미 화면 밖이고 마커 수가 치솟는다. */
private const val MIN_ZOOM = 11.0

/** 카메라가 데이터가 덮는 나라를 벗어나지 못하게 한다 — 출처가 전부 국내다. */
private val KOREA_EXTENT = LatLngBounds(LatLng(32.9, 124.0), LatLng(38.7, 132.0))
