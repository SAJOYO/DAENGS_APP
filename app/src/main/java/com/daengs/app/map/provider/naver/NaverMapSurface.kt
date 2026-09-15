package com.daengs.app.map.provider.naver

import android.graphics.Color
import android.graphics.PointF
import android.util.Log
import androidx.annotation.DrawableRes
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.graphics.toArgb
import com.daengs.app.BuildConfig
import com.daengs.app.R
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.shell.BaseMapStyle
import com.daengs.app.map.layers.completedroute.routeEndpointStamps
import com.daengs.app.map.shell.MapScene
import com.daengs.app.map.shell.MapCameraSnapshot
import com.daengs.app.map.shell.MapVisibilityQuery
import com.daengs.app.map.shell.MapVisibilityResult
import com.daengs.app.map.shell.minimumZoom
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengPinkDeep
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraAnimation
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.CameraPosition
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage
import com.daengs.app.map.style.rememberWalkStyle
import com.naver.maps.map.overlay.CircleOverlay

@Composable
fun NaverMapSurface(
    scene: MapScene,
    searchOrigin: GeoPoint?,
    followDevice: Boolean,
    @DrawableRes avatarRes: Int? = null,
    /** 사용자가 올린 프로필 사진. 있으면 [avatarRes] 보다 이쪽이 앞선다. */
    avatarPhoto: android.graphics.Bitmap? = null,
    /** 아래쪽에서 패널이 가리는 높이(px). 지도의 "가운데"가 그만큼 위로 올라간다. */
    bottomPaddingPx: Int = 0,
    leftPaddingPx: Int = 0,
    topPaddingPx: Int = 0,
    rightPaddingPx: Int = 0,
    /** 여기로 지도를 옮긴다. **사용자가 카드나 마커를 누른 순간에만** 값이 온다. */
    centerOn: GeoPoint? = null,
    /** [centerOn] 으로 갈 때 쓸 배율. null 이면 지금 배율을 지키되 너무 멀면 당긴다. */
    centerZoom: Double? = null,
    centerMinZoom: Double? = null,
    cameraRequestKey: Int = 0,
    centerYFraction: Float = .5f,
    keepSelectionVisible: Boolean = false,
    /** 이 점들이 **다 보이게** 화면을 맞춘다. 지난 산책의 경로처럼 범위가 정해진 것에 쓴다. */
    fitBounds: List<GeoPoint>? = null,
    onCameraIdle: (GeoPoint) -> Unit,
    onCameraGesture: () -> Unit,
    onSelectPlace: (String) -> Unit,
    onSelectTerritorySite: (String) -> Unit = {},
    onSelectMoment: (String) -> Unit = {},
    onSelectMomentGroup: (List<String>) -> Unit = { ids -> ids.firstOrNull()?.let(onSelectMoment) },
    onSelectRouteEndpoint: (String) -> Unit = {},
    onSelectRecordContext: (String) -> Unit = {},
    onMapTap: (GeoPoint) -> Unit = {},
    modifier: Modifier = Modifier,
    initialCamera: MapCameraSnapshot? = null,
    onCameraSnapshot: ((MapCameraSnapshot) -> Unit)? = null,
    onRouteDirectionCount: (Int) -> Unit = {},
    visibilityQuery: MapVisibilityQuery? = null,
    onVisibility: (MapVisibilityResult) -> Unit = {},
) {
    if (androidx.compose.ui.platform.LocalInspectionMode.current) {
        androidx.compose.foundation.layout.Box(modifier) {
            androidx.compose.material3.Text("지도 · ${scene.moments.size}개 장면")
        }
        return
    }
    val walkAppearance = scene.walkPresentation?.route ?: run {
        val selected by rememberWalkStyle()
        com.daengs.app.map.style.WalkRouteAppearance(selected.policy, selected.themeId)
    }
    val diagnostics = LocalWalkMapDiagnostics.current
    val layerOrder = NaverWalkLayerOrder.resolve(scene.walkPresentation?.stack)
    val drawTraces = diagnostics?.showTraces != false
    val drawRoute = diagnostics?.showRoute != false
    val drawMarkers = diagnostics?.showMarkers != false
    val visibleMoments = if (drawMarkers) scene.moments.filter { it.recordPin == null && it.diaryPin == null } else emptyList()
    val recordMoments = if (drawMarkers) scene.moments.filter { it.recordPin != null } else emptyList()
    val diaryMoments = if (drawMarkers) scene.moments.filter { it.diaryPin != null } else emptyList()
    var recordMarkerBounds by remember { mutableStateOf<List<com.daengs.app.map.layout.MarkerRect>>(emptyList()) }
    var diaryMarkerBounds by remember { mutableStateOf<List<com.daengs.app.map.layout.MarkerRect>>(emptyList()) }
    val visibleGaps = if (drawRoute) scene.completedRoute.gapEndpoints else emptyList()
    val visibleSelectedPoint = scene.completedRoute.selectedPoint.takeIf { drawMarkers }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(context) }
    val cameraToRestore = remember(mapView) { initialCamera }
    val initialCameraRequest = remember(mapView) { cameraRequestKey }
    var cameraRestored by remember(mapView) { mutableStateOf(false) }
    var restoredRecordRequest by remember(mapView) { mutableStateOf<Int?>(null) }
    var reportCamera by remember(mapView) { mutableStateOf(false) }
    var naverMap by remember { mutableStateOf<NaverMap?>(null) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current.density
    val latestCameraCallback by rememberUpdatedState(onCameraIdle)
    val latestGestureCallback by rememberUpdatedState(onCameraGesture)
    val latestMapTapCallback by rememberUpdatedState(onMapTap)
    val latestSnapshotCallback by rememberUpdatedState(onCameraSnapshot)
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
                // Compose가 draw 중 새 AndroidView를 배치하면 SurfaceView의 pre-draw는
                // 이미 지나갔을 수 있다. 첫 배치 뒤 한 프레임을 더 요청해 Surface를 만든다.
                doOnLayout { view -> view.rootView.postInvalidateOnAnimation() }
                getMapAsync { map ->
                    naverMap = map
                    map.uiSettings.isLocationButtonEnabled = false
                    map.uiSettings.isZoomControlEnabled = false
                    // 이 앱에는 도시보다 넓게 봐서 답이 나오는 화면이 없다 — 검색은
                    // 반경 10km 가 상한이고 산책은 몇 km 다. 하한을 두면 마커 수도 안 터진다.
                    map.minZoom = MIN_ZOOM
                    map.extent = KOREA_EXTENT
                    applyDaengsStyle(map)
                    map.addOnCameraChangeListener { reason, _ ->
                        lastCameraReason.intValue = reason
                        if (reason == CameraUpdate.REASON_GESTURE) latestGestureCallback()
                    }
                    map.addOnCameraIdleListener {
                        if (reportCamera) {
                            val camera = map.cameraPosition
                            latestSnapshotCallback?.invoke(MapCameraSnapshot(
                                GeoPoint(camera.target.latitude, camera.target.longitude),
                                camera.zoom, camera.bearing, camera.tilt,
                            ))
                        }
                        if (!lastCameraReason.intValue.isUserDriven()) return@addOnCameraIdleListener
                        val target = map.cameraPosition.target
                        latestCameraCallback(GeoPoint(target.latitude, target.longitude))
                    }
                }
            }
        },
        modifier = modifier.onSizeChanged { viewportSize = it },
    )

    DisposableEffect(naverMap, diagnostics?.showBase) {
        val map = naverMap
        val original = map?.mapType
        if (diagnostics?.showBase == false) map?.mapType = NaverMap.MapType.None
        onDispose { if (original != null) map?.mapType = original }
    }

    if (!scene.detachedRecordPins && !scene.detachedDiaryPins && scene.moments.none { it.diaryPin != null })
        NaverMapVisibility(naverMap, viewportSize, density, visibilityQuery, scene.moments, onVisibility)

    LaunchedEffect(naverMap, searchOrigin) {
        val map = naverMap ?: return@LaunchedEffect
        val origin = searchOrigin ?: return@LaunchedEffect
        map.moveCamera(
            CameraUpdate.scrollAndZoomTo(LatLng(origin.latitude, origin.longitude), 14.5)
                .animate(CameraAnimation.Easing),
        )
    }

    LaunchedEffect(naverMap, scene.baseMapStyle, scene.allowRegionalOverview) {
        val map = naverMap ?: return@LaunchedEffect
        // 점령지는 3km 원 하나만 읽는다. 더 멀리 축소하면 화면은 넓어지는데 데이터는
        // 늘지 않아 빈 곳처럼 거짓말하게 되므로, 그 모드에서만 도시 단위 줌을 막는다.
        map.minZoom = scene.minimumZoom()
    }

    LaunchedEffect(naverMap, scene.currentPosition, followDevice) {
        val map = naverMap ?: return@LaunchedEffect
        val point = scene.currentPosition ?: return@LaunchedEffect
        if (followDevice) map.moveCamera(CameraUpdate.scrollTo(point.toLatLng()))
    }

    NaverLocationLayer(naverMap, scene.currentPosition, avatarRes, avatarPhoto)

    // 화면 아래를 패널이 덮고 있다. 그걸 알려주지 않으면 지도가 **패널 뒤를 가운데로**
    // 삼아서, 고른 장소로 움직여도 그 장소가 패널에 가려 안 보인다.
    LaunchedEffect(naverMap, bottomPaddingPx, leftPaddingPx, topPaddingPx, rightPaddingPx, keepSelectionVisible) {
        // The diary explicitly reframes below. Do not also shift its target to preserve the old
        // visible map area: that padding-induced camera move can displace the new fit/selection.
        naverMap?.setContentPadding(leftPaddingPx, topPaddingPx, rightPaddingPx, bottomPaddingPx, keepSelectionVisible)
    }

    // 지나온 길 전체가 한눈에 들어오게 맞춘다. 첫 좌표로 가는 것과 다르다 —
    // 한 시간 걸은 산책은 시작점만 보면 어디를 돌았는지 알 수 없다.
    val recordsOverview = scene.walkPresentation?.stack == com.daengs.app.map.shell.WalkLayerStack.RECORDS
    LaunchedEffect(naverMap, fitBounds, cameraRequestKey, if (keepSelectionVisible) centerOn else null,
        if (keepSelectionVisible) viewportSize else IntSize.Zero,
        if (recordsOverview) emptyList<Int>() else listOf(bottomPaddingPx, leftPaddingPx, topPaddingPx, rightPaddingPx)) {
        val map = naverMap ?: return@LaunchedEffect
        if (!cameraRestored && cameraToRestore != null && initialCameraRequest == cameraRequestKey &&
            centerOn == null && searchOrigin == null) {
            cameraRestored = true
            if (recordsOverview) restoredRecordRequest = cameraRequestKey
            reportCamera = true
            map.moveCamera(CameraUpdate.toCameraPosition(CameraPosition(
                cameraToRestore.target.toLatLng(), cameraToRestore.zoom,
                cameraToRestore.tilt, cameraToRestore.bearing,
            )))
            return@LaunchedEffect
        }
        // Late trace bounds are data arrival, not permission to replace a restored overview camera.
        if (recordsOverview && restoredRecordRequest == cameraRequestKey) return@LaunchedEffect
        // A selection made while getMapAsync was pending takes precedence over the old snapshot.
        cameraRestored = true
        // Opening a clustered-record drawer must not zoom back out and regroup the inspected area.
        if ((keepSelectionVisible || recordsOverview) && centerOn != null) return@LaunchedEffect
        if (keepSelectionVisible && viewportSize == IntSize.Zero) return@LaunchedEffect
        val points = fitBounds?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        reportCamera = true
        val bounds = LatLngBounds.Builder().apply {
            points.forEach { include(it.toLatLng()) }
        }.build()
        // 한 점뿐이면 경계가 넓이 0 이라 SDK 가 거부한다. 그때는 그 점으로 간다.
        if (bounds.southWest == bounds.northEast) {
            map.moveCamera(CameraUpdate.scrollAndZoomTo(bounds.southWest, SELECTED_PLACE_MIN_ZOOM))
        } else if (keepSelectionVisible) {
            // Leave space for the ordinal badge above a coordinate, endpoints below it,
            // and the settings button. Overlay gestures do not change this viewport.
            val visibleHeight = viewportSize.height - topPaddingPx - bottomPaddingPx
            val padding = minOf((48 * density).toInt(), visibleHeight / 5).coerceAtLeast(0)
            map.moveCamera(CameraUpdate.fitBounds(bounds, padding, padding * 2, padding, padding))
        } else {
            map.moveCamera(CameraUpdate.fitBounds(bounds, FIT_PADDING_PX))
        }
    }

    // 카드를 누르면 **그 장소가 지도 한가운데** 오게 한다. 목록에서 고른 곳이 화면
    // 밖에 있으면 무엇을 고른 것인지 알 수 없다.
    //
    // **선택 상태가 아니라 "누른 순간"을 본다.** 검색이 끝나면 첫 결과가 저절로
    // 선택되는데, 선택을 보고 움직이면 "내 위치" 를 눌러도 지도가 곧바로 그 첫
    // 결과로 도로 끌려간다.
    LaunchedEffect(naverMap, centerOn, cameraRequestKey,
        if (keepSelectionVisible) viewportSize else IntSize.Zero,
        if (keepSelectionVisible) listOf(bottomPaddingPx, leftPaddingPx, topPaddingPx, rightPaddingPx) else emptyList<Int>()) {
        val map = naverMap ?: return@LaunchedEffect
        if (keepSelectionVisible && viewportSize == IntSize.Zero) return@LaunchedEffect
        val point = centerOn ?: return@LaunchedEffect
        // 너무 멀리서 보고 있었으면 당겨 준다. 이미 가까우면 배율은 안 건드린다 —
        // 사용자가 맞춰 놓은 화면을 마음대로 바꾸지 않는다.
        val zoom = centerZoom ?: maxOf(map.cameraPosition.zoom, centerMinZoom ?: SELECTED_PLACE_MIN_ZOOM)
        map.moveCamera(
            CameraUpdate.scrollAndZoomTo(point.toLatLng(), zoom)
                .apply { if (centerYFraction != .5f) pivot(PointF(.5f, centerYFraction.coerceIn(0f, 1f))) }
                .animate(CameraAnimation.Easing),
        )
    }

    DisposableEffect(naverMap, scene.places) {
        val map = naverMap
        val markers = if (map == null) emptyList() else scene.places.map { place ->
            Marker().apply {
                position = place.point.toLatLng()
                captionText = place.label
                captionMinZoom = 13.0
                // **정사각이어야 한다.** 그림(`ic_facility_*.xml`)의 뷰포트가 24x24 인데
                // 예전엔 64x80 으로 그려서 세로로 1.25배 늘어났다 — 핀이 홀쭉해 보이고
                // 동그란 머리가 타원이 됐다.
                width = if (place.selected) MARKER_PX_SELECTED else MARKER_PX
                height = if (place.selected) MARKER_PX_SELECTED else MARKER_PX
                // 핀 끝이 그림의 맨 아래가 아니라 93% 지점이라, 기본 기준점(1.0)으로 두면
                // 핀이 장소보다 조금 위에 뜬다.
                anchor = MARKER_ANCHOR
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
        onDispose { markers.forEach { it.map = null; diagnostics?.detached(it) } }
    }

    NaverWalkTraceLayer(naverMap, if (drawTraces) scene.traceTiles else emptyList(), layerOrder.traces)
    NaverTravelHeadingLayer(naverMap, scene.currentPosition, scene.travelHeading)
    NaverSpatialDiaryLayer(naverMap, scene.spatialCells)

    // 점령지는 시설 검색 핀을 재사용하지 않는다. 원천 종류가 무엇이든 앱에서는 같은
    // 게임 지점이고, 장소 검색이 갱신돼도 이 레이어의 생애에는 영향을 주지 않는다.
    NaverTerritoryLayer(naverMap, scene.territorySites, onSelectTerritorySite)
    NaverStayStampLayer(naverMap, scene.stayStamps)

    DisposableEffect(naverMap) {
        val map = naverMap
        map?.setOnMapClickListener { _, coordinate ->
            latestMapTapCallback(GeoPoint(coordinate.latitude, coordinate.longitude))
        }
        onDispose { map?.setOnMapClickListener(null) }
    }

    // 행동 책갈피는 시설 검색 결과와 다른 레이어다. 같은 장소 핀 목록에 섞으면 검색을
    // 새로 할 때 산책 중 사용자가 남긴 순간까지 사라진다.
    val fixedMarkerFootprints = scene.routeEndpointStamps().map { endpoint ->
        val art = requireNotNull(context.getDrawable(endpoint.kind.iconRes))
        val dimensions = if (endpoint.compact) diaryPinSize(endpoint.label,density,endpoint=true) else art.intrinsicWidth to art.intrinsicHeight
        endpoint.point to com.daengs.app.map.layout.MarkerFootprint(dimensions.first/density.toDouble(),dimensions.second/density.toDouble(),
            .5,if(endpoint.abovePoint) 1.0 else if(endpoint.compact) 0.0 else .5)
    }
    if (scene.detachedDiaryPins || scene.detachedRecordPins) {
        val obstacles = fixedMarkerFootprints + scene.stayStamps.map {
            it.point to com.daengs.app.map.layout.MarkerFootprint(56.0,34.0)
        } + scene.sessionExplorer?.recordContext?.markers.orEmpty().map {
            val dimensions = if (it.gapBoundary) (34 * density) to (39 * density)
                else diaryPinSize(it.label, density, endpoint = true).let { size -> size.first.toFloat() to size.second.toFloat() }
            it.point to com.daengs.app.map.layout.MarkerFootprint(dimensions.first/density.toDouble(),dimensions.second/density.toDouble(),.5,0.0)
        }
        NaverDetachedMomentLayer(naverMap, recordMoments + diaryMoments, viewportSize, density, layerOrder,
            onSelectMomentGroup, (scene.markerAvoidancePaths + scene.completedRoute.paths +
                scene.sessionExplorer?.observedParts.orEmpty().map { it.path }).distinct(),
            visibilityQuery, bottomPaddingPx, obstacles,
            onBounds = { diaryMarkerBounds = it; recordMarkerBounds = emptyList() }, onVisibility = onVisibility,
            topInset = if (scene.detachedRecordPins) topPaddingPx else 0)
    } else {
    NaverGroupedMomentLayer(naverMap, recordMoments, viewportSize, density, layerOrder, onSelectMomentGroup,
        topInset = topPaddingPx, bottomInset = bottomPaddingPx, onBounds = { recordMarkerBounds = it }, fixedMarkers = fixedMarkerFootprints)
    NaverGroupedMomentLayer(naverMap, diaryMoments, viewportSize, density, layerOrder, onSelectMomentGroup,
        query = visibilityQuery.takeIf { scene.moments.any { it.diaryPin != null } },
        onVisibility = onVisibility, onBounds = { diaryMarkerBounds = it }, fixedMarkers = fixedMarkerFootprints)
    }

    NaverMomentLayer(naverMap, visibleMoments, onSelectMoment, diagnostics, layerOrder.markers)

    NaverWalkRouteLayer(naverMap, if (drawRoute) scene.trail else com.daengs.app.map.layers.trail.TrailLayerState(),
        if (drawRoute) scene.completedRoute else com.daengs.app.map.layers.completedroute.CompletedRouteLayerState(), walkAppearance.speedPolicy, walkAppearance.themeId,
        dimCompleted = scene.sessionExplorer?.emphasisPaths?.isNotEmpty() == true, stroke = walkAppearance.stroke, globalZ = layerOrder.route)

    NaverRouteEndpointLayer(naverMap, if (drawMarkers) scene.routeEndpointStamps() else emptyList(), onSelectRouteEndpoint, layerOrder.markers)
    NaverRecordContextLayer(naverMap, scene.sessionExplorer?.recordContext, density, onSelectRecordContext)
    NaverSessionRouteExplorer(naverMap, scene.sessionExplorer, scene.completedRoute.paths,
        scene.moments.filter { it.diaryPin == null && it.recordPin == null }.map { it.point } + scene.routeEndpointStamps().map { it.point } +
            scene.sessionExplorer?.recordContext?.markers.orEmpty().map { it.point },
        viewportSize, bottomPaddingPx, density, onRouteDirectionCount, recordMarkerBounds + diaryMarkerBounds,
        cursorAvatarRes = avatarRes, cursorAvatarPhoto = avatarPhoto)

    DisposableEffect(naverMap, visibleGaps) {
        val map = naverMap
        val gapDots = if (map == null) {
            emptyList()
        } else {
            visibleGaps.map { point ->
                CircleOverlay().apply {
                    center = point.toLatLng()
                    radius = ROUTE_GAP_RADIUS_METERS
                    color = ROUTE_GAP_COLOR
                    outlineWidth = ROUTE_GAP_OUTLINE_WIDTH
                    outlineColor = TRAIL_OUTLINE_COLOR
                    zIndex = ROUTE_GAP_Z
                    this.map = map
                }
            }
        }
        onDispose { gapDots.forEach { it.map = null } }
    }

    DisposableEffect(naverMap, visibleSelectedPoint) {
        val map = naverMap
        val selectedDot = if (map == null) {
            null
        } else {
            visibleSelectedPoint?.let { point ->
                CircleOverlay().apply {
                    center = point.toLatLng()
                    radius = ROUTE_SELECTED_RADIUS_METERS
                    color = ROUTE_SELECTED_COLOR
                    outlineWidth = ROUTE_SELECTED_OUTLINE_WIDTH
                    outlineColor = TRAIL_OUTLINE_COLOR
                    zIndex = SELECTED_MARKER_Z
                    this.map = map
                }
            }
        }
        onDispose { selectedDot?.map = null }
    }
}


private fun Int.isUserDriven(): Boolean =
    this == CameraUpdate.REASON_GESTURE || this == CameraUpdate.REASON_CONTROL

private fun GeoPoint.toLatLng(): LatLng = LatLng(latitude, longitude)

/** 고른 핀은 이웃 위에 그린다. 마커가 겹칠 때 고른 것이 가려지면 안 된다. */
private const val SELECTED_MARKER_Z = 100


/**
 * 산책 경로.
 *
 * 예전엔 보라(`rgb(125, 84, 180)`)였다. 지도 자체가 원색이던 시절에는 그래야 눈에
 * 띄었는데, 지도를 크림으로 빼고 마커를 앱 팔레트로 맞춘 뒤로는 **경로선만 남의 색**이
 * 됐다. 상세 화면의 주인공이 이 선이라 제일 눈에 띈다.
 */
private val TRAIL_COLOR = DaengPinkDeep.toArgb()

/**
 * 흰 테두리.
 *
 * 장식이 아니다. 연한 지도 위에서는 분홍 선이 바탕에 녹아 어디를 지나갔는지 흐려진다 —
 * 마커 핀에 흰 테두리를 두른 것과 같은 이유다. 지하철 노선처럼 색이 있는 선과 겹칠
 * 때도 테두리가 둘을 갈라 준다.
 */

private val TRAIL_OUTLINE_COLOR = Color.WHITE

/** 줌 하한. 이보다 멀어지면 검색 반경 상한(10km)이 이미 화면 밖이고 마커 수가 치솟는다. */
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

/** 마커 한 변(px). 그림 비율이 1:1 이라 가로세로가 같아야 안 늘어난다. */
private const val MARKER_PX = 72

private const val MARKER_PX_SELECTED = 92

private const val ROUTE_GAP_Z = 40

private const val ROUTE_GAP_RADIUS_METERS = 4.0

private val ROUTE_GAP_COLOR = Color.argb(220, 112, 108, 105)

private const val ROUTE_GAP_OUTLINE_WIDTH = 3

private const val ROUTE_SELECTED_RADIUS_METERS = 6.0

private val ROUTE_SELECTED_COLOR = DaengPink.toArgb()

private const val ROUTE_SELECTED_OUTLINE_WIDTH = 4

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun NaverSceneMapPreview() {
    NaverMapSurface(MapScene(moments = listOf(
        com.daengs.app.map.layers.moments.MomentMarkerState("scene", GeoPoint(37.5, 127.0), "1 · 7",
            aboveRouteEndpoints = true))), null, false, onCameraIdle = {}, onCameraGesture = {}, onSelectPlace = {})
}

/** 핀 끝의 세로 위치. 그림에서 뾰족한 끝이 22.4/24 = 0.933 지점에 있다. */
private val MARKER_ANCHOR = PointF(0.5f, 0.933f)

/** 경로를 다 담을 때 가장자리에 남기는 여백(px). 선이 화면 끝에 붙으면 잘려 보인다. */
private const val FIT_PADDING_PX = 80

/** 고른 장소로 갈 때 최소한 이만큼은 당겨서 본다. */
private const val SELECTED_PLACE_MIN_ZOOM = 16.0

private const val MIN_ZOOM = 11.0

/** 3km 조회 한 장이 화면을 의미 있게 덮는 실험 시작값. 실기기에서 최종 조정한다. */
private const val TERRITORY_MIN_ZOOM = 13.0

/** 카메라가 데이터가 덮는 나라를 벗어나지 못하게 한다 — 출처가 전부 국내다. */
private val KOREA_EXTENT = LatLngBounds(LatLng(32.9, 124.0), LatLng(38.7, 132.0))
