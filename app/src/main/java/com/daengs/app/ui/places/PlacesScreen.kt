package com.daengs.app.ui.places

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.daengs.app.BuildConfig
import com.daengs.app.journey.HttpJourneyRepository
import com.daengs.app.journey.JourneyApi
import com.daengs.app.journey.openNaverHandoff
import com.daengs.app.location.FeedStatus
import com.daengs.app.location.FusedLocationSource
import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.location.LocationTracker
import com.daengs.app.map.features.journey.PlaceJourneyController
import com.daengs.app.map.features.places.DEFAULT_PLACE_KIND
import com.daengs.app.map.features.places.PlaceDiscoveryController
import com.daengs.app.map.features.places.PlaceDiscoveryPanel
import com.daengs.app.map.features.places.PlaceOriginMode
import com.daengs.app.map.features.places.canonicalPlaceKeysByMarker
import com.daengs.app.map.features.places.canonicalPlaceMarkers
import com.daengs.app.map.features.places.selectedPlaceKind
import com.daengs.app.map.shell.MapHost
import com.daengs.app.map.shell.MapScene
import com.daengs.app.place.PlaceApi
import com.daengs.app.place.PlaceKind
import com.daengs.app.place.PlaceRepository
import com.daengs.app.ui.theme.DaengsTheme
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * geo Android의 canonical Place 화면을 APP 셸에 연결한다.
 *
 * 검색 종류·요청·결과 카드·마커·journey 표시는 원본 `main@c5f0d5f`의 동작이다.
 * APP에는 사용자 강아지 선택이 없으므로 원본 계약이 지원하는 조건 없는 검색을 보낸다.
 */
@Composable
fun PlacesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val inspectionMode = LocalInspectionMode.current
    val scope = rememberCoroutineScope()
    val source = remember(context) { FusedLocationSource(context.applicationContext) }
    val locationTracker = remember(scope) { LocationTracker(scope) }
    val placeDiscovery = remember(scope) {
        PlaceDiscoveryController(
            repository = PlaceRepository(PlaceApi(baseUrl = { BuildConfig.API_BASE_URL })),
            dogContext = null,
            scope = scope,
        )
    }
    val placeJourney = remember(scope) {
        PlaceJourneyController(
            repository = HttpJourneyRepository(JourneyApi(baseUrl = { BuildConfig.API_BASE_URL })),
            dogId = "",
            scope = scope,
        )
    }
    val discovery by placeDiscovery.state.collectAsState()
    val journey by placeJourney.state.collectAsState()

    var granted by remember { mutableStateOf(inspectionMode || hasLocationPermission(context)) }
    var currentPosition by remember { mutableStateOf<GeoPoint?>(null) }
    var devicePosition by remember { mutableStateOf<GeoPoint?>(null) }
    var cameraCandidate by remember { mutableStateOf<GeoPoint?>(null) }
    var followDevice by remember { mutableStateOf(true) }
    var locating by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var initialPlaceSearchStarted by remember { mutableStateOf(false) }

    fun acceptLocation(sample: LocationSample) {
        currentPosition = sample.point
        if (!sample.isMock) devicePosition = sample.point
    }

    fun beginSearch(
        origin: GeoPoint,
        kind: PlaceKind,
        preferParking: Boolean,
        originMode: PlaceOriginMode = PlaceOriginMode.DEVICE,
    ) {
        placeJourney.clear()
        locationError = null
        placeDiscovery.search(origin, listOf(kind), preferParking, originMode)
    }

    fun locateAndSearch(kind: PlaceKind, preferParking: Boolean) {
        if (!granted) return
        scope.launch {
            locating = true
            locationError = null
            runCatching { source.currentLocation() }
                .onSuccess { sample ->
                    if (sample.isMock) {
                        locationError = "가상 위치로는 주변 장소를 검색할 수 없어요."
                    } else {
                        acceptLocation(sample)
                        followDevice = true
                        beginSearch(sample.point, kind, preferParking)
                    }
                }
                .onFailure { error ->
                    locationError = error.message ?: "현재 위치를 확인하지 못했습니다."
                }
            locating = false
        }
    }

    fun searchAtCurrentOrigin(kind: PlaceKind, preferParking: Boolean) {
        val pinned = discovery.origin?.takeIf { discovery.originMode == PlaceOriginMode.PINNED }
        when {
            pinned != null -> beginSearch(pinned, kind, preferParking, PlaceOriginMode.PINNED)
            devicePosition != null -> beginSearch(devicePosition!!, kind, preferParking)
            else -> locateAndSearch(kind, preferParking)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        granted = result.values.any { it }
        if (granted && !initialPlaceSearchStarted) {
            initialPlaceSearchStarted = true
            locateAndSearch(DEFAULT_PLACE_KIND, false)
        }
    }

    LaunchedEffect(Unit) {
        if (!inspectionMode && !granted) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    LaunchedEffect(locationTracker) {
        locationTracker.updates.collect(::acceptLocation)
    }
    LaunchedEffect(locationTracker) {
        locationTracker.status.collect { status ->
            if (status is FeedStatus.Failed) {
                locationError = status.cause.message ?: "위치 업데이트를 이어가지 못했습니다."
            }
        }
    }
    LaunchedEffect(granted, inspectionMode) {
        if (inspectionMode) return@LaunchedEffect
        if (granted) {
            locationTracker.start(source)
            if (!initialPlaceSearchStarted) {
                initialPlaceSearchStarted = true
                locateAndSearch(DEFAULT_PLACE_KIND, false)
            }
        } else {
            locationTracker.stop()
        }
    }
    DisposableEffect(locationTracker) {
        onDispose(locationTracker::stop)
    }

    BackHandler(onBack = onBack)

    val selectedKind = selectedPlaceKind(discovery)
    val markerKeys = canonicalPlaceKeysByMarker(discovery)
    val movedFromOrigin = cameraMovedFrom(cameraCandidate, discovery.origin)

    Box(modifier.fillMaxSize()) {
        if (inspectionMode) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
        } else {
            MapHost(
                scene = MapScene(
                    currentPosition = currentPosition,
                    places = canonicalPlaceMarkers(discovery),
                ),
                searchOrigin = discovery.origin,
                followDevice = followDevice,
                onCameraIdle = { cameraCandidate = it },
                onCameraGesture = { followDevice = false },
                onSelectPlace = { id -> markerKeys[id]?.let(placeDiscovery::select) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        FilledTonalButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp),
        ) { Text("← 홈") }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (movedFromOrigin) {
                    FilledTonalButton(
                        enabled = !discovery.loading && !locating,
                        onClick = {
                            val origin = cameraCandidate
                            if (origin == null) {
                                locationError = "지도를 이동한 뒤 이 지역을 검색해주세요."
                            } else {
                                followDevice = false
                                beginSearch(
                                    origin,
                                    selectedKind,
                                    discovery.preferParking,
                                    PlaceOriginMode.PINNED,
                                )
                            }
                        },
                    ) { Text("이 지역 검색") }
                }
                FilledTonalButton(
                    enabled = granted && !discovery.loading && !locating,
                    onClick = { locateAndSearch(selectedKind, discovery.preferParking) },
                ) { Text(if (locating) "찾는 중" else "내 위치") }
            }
            locationError?.let { error ->
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Text(error, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }

        PlaceDiscoveryPanel(
            state = discovery,
            journey = journey,
            onSearch = ::searchAtCurrentOrigin,
            onRetry = placeDiscovery::retry,
            onSelect = placeDiscovery::select,
            onJourney = { place ->
                val origin = devicePosition
                if (origin == null) {
                    placeJourney.reject(
                        place.key,
                        "현재 위치를 확인한 뒤 길찾기를 다시 눌러주세요.",
                    )
                } else {
                    placeJourney.load(origin, place)
                }
            },
            onRetryJourney = placeJourney::retry,
            onOpenHandoff = { url -> openNaverHandoff(context, url) },
            onCall = { phone -> dial(context, phone) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }
}

private fun cameraMovedFrom(candidate: GeoPoint?, origin: GeoPoint?): Boolean {
    candidate ?: return false
    origin ?: return false
    return abs(candidate.latitude - origin.latitude) > 0.0005 ||
        abs(candidate.longitude - origin.longitude) > 0.0005
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun dial(context: Context, phone: String) {
    val safe = phone.filter { it.isDigit() || it in "+*#," }
    if (safe.isNotBlank()) context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$safe".toUri()))
}

@Preview(device = "spec:width=411dp,height=891dp", showBackground = true)
@Composable
private fun PlacesScreenPreview() {
    DaengsTheme { PlacesScreen(onBack = {}) }
}
