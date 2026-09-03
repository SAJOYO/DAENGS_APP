package com.daengs.app.ui.places

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daengs.app.journey.openNaverHandoff
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.features.places.PlaceDiscoveryPanel
import com.daengs.app.map.features.places.PlaceSearchState
import com.daengs.app.map.features.places.canonicalPlaceKeysByMarker
import com.daengs.app.map.features.places.canonicalPlaceMarkers
import com.daengs.app.map.features.places.placeMarkerId
import com.daengs.app.map.features.places.selectedPlaceKind
import com.daengs.app.map.shell.MapHost
import com.daengs.app.map.shell.MapScene
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.pet.Pet
import com.daengs.app.place.PlaceFailure
import com.daengs.app.place.PlaceKey
import com.daengs.app.ui.common.DaengsFloatingButton
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkFaint
import kotlin.math.abs

/** 앱 권한·외부 인텐트와 화면 상태 소유자를 연결하는 시설 검색 진입점이다. */
@Composable
fun PlacesRoute(
    onBack: () -> Unit,
    primaryPet: Pet?,
    modifier: Modifier = Modifier,
    viewModel: PlacesViewModel = viewModel(factory = PlacesViewModel.factory(LocalContext.current)),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var permissionRequested by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        permissionRequested = true
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.updatePermission(
            granted = granted,
            permanentlyDenied = !granted && !canRequestLocationPermissionAgain(context),
        )
    }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val granted = hasLocationPermission(context)
        viewModel.updatePermission(
            granted = granted,
            permanentlyDenied = !granted,
        )
    }

    LaunchedEffect(Unit) {
        val granted = hasLocationPermission(context)
        viewModel.activate(granted)
        if (!granted && !permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(LOCATION_PERMISSIONS)
        }
    }
    LaunchedEffect(primaryPet) {
        viewModel.updateDogContext(primaryPet.toPlaceDogContext())
    }
    DisposableEffect(viewModel) {
        onDispose(viewModel::deactivate)
    }

    PlacesScreen(
        state = state,
        avatarBreed = primaryPet?.breedArt,
        onBack = onBack,
        onRequestPermission = {
            permissionRequested = true
            permissionLauncher.launch(LOCATION_PERMISSIONS)
        },
        onOpenSettings = { settingsLauncher.launch(appSettingsIntent(context)) },
        onAction = viewModel::onAction,
        onOpenHandoff = { openNaverHandoff(context, it) },
        onCall = { dial(context, it) },
        modifier = modifier,
    )
}

/** 시설 검색의 순수 화면. 권한·API·위치 객체를 만들거나 소유하지 않는다. */
@Composable
fun PlacesScreen(
    state: PlacesUiState,
    onBack: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onAction: (PlacesAction) -> Unit,
    onOpenHandoff: (String) -> Unit,
    onCall: (String) -> Unit,
    modifier: Modifier = Modifier,
    avatarBreed: DogBreed? = null,
    showMap: Boolean = true,
) {
    val discovery = state.discovery
    var cameraCandidate by remember { mutableStateOf<GeoPoint?>(null) }
    var followDevice by remember { mutableStateOf(true) }
    var panelHeightPx by remember { mutableIntStateOf(0) }
    var centerOn by remember { mutableStateOf<GeoPoint?>(null) }

    fun focusOn(key: PlaceKey) {
        followDevice = false
        centerOn = canonicalPlaceMarkers(discovery)
            .firstOrNull { it.id == placeMarkerId(key) }
            ?.point
        onAction(PlacesAction.Select(key))
    }

    BackHandler(onBack = onBack)

    val selectedKind = selectedPlaceKind(discovery)
    val markerKeys = canonicalPlaceKeysByMarker(discovery)
    val movedFromOrigin = cameraMovedFrom(cameraCandidate, discovery.origin)
    val location = state.location
    val permissionAction = when (location) {
        PlaceLocationState.PermissionRequired -> "위치 권한" to onRequestPermission
        PlaceLocationState.PermissionPermanentlyDenied -> "설정 열기" to onOpenSettings
        else -> null
    }
    val locationMessage = location.userMessage()?.takeUnless {
        location is PlaceLocationState.Unsupported &&
            (discovery.search as? PlaceSearchState.Failed)?.failure ==
            PlaceFailure.UnsupportedLocation
    }

    Box(modifier.fillMaxSize()) {
        if (showMap) {
            MapHost(
                scene = MapScene(
                    currentPosition = location.currentPosition,
                    places = canonicalPlaceMarkers(discovery),
                ),
                searchOrigin = discovery.origin,
                followDevice = followDevice,
                avatarRes = avatarBreed?.portraitRes,
                bottomPaddingPx = panelHeightPx,
                centerOn = centerOn,
                onCameraIdle = { cameraCandidate = it },
                onCameraGesture = { followDevice = false },
                onSelectPlace = { id -> markerKeys[id]?.let(::focusOn) },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(PinkFaint))
        }

        DaengsFloatingButton(
            label = "← 홈",
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(start = 12.dp, top = 68.dp, end = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (movedFromOrigin) {
                    DaengsFloatingButton(
                        label = "이 지역 검색",
                        enabled = !discovery.loading && !location.locating,
                        onClick = {
                            cameraCandidate?.let { point ->
                                followDevice = false
                                onAction(
                                    PlacesAction.SearchAt(
                                        point,
                                        selectedKind,
                                        discovery.preferParking,
                                    ),
                                )
                            }
                        },
                    )
                }
                DaengsFloatingButton(
                    label = if (location.locating) "찾는 중" else "내 위치",
                    enabled = location !is PlaceLocationState.PermissionRequired &&
                        location !is PlaceLocationState.PermissionPermanentlyDenied &&
                        !discovery.loading &&
                        !location.locating,
                    onClick = {
                        centerOn = null
                        followDevice = true
                        onAction(PlacesAction.Locate(selectedKind, discovery.preferParking))
                    },
                )
                permissionAction?.let { (label, action) ->
                    DaengsFloatingButton(label = label, onClick = action)
                }
            }

            locationMessage?.let { message ->
                Surface(color = DaengsColors.ErrorSoft, shape = RoundedCornerShape(12.dp)) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(message, color = DaengsColors.Error, fontSize = 12.sp)
                        if (location is PlaceLocationState.Failed) {
                            DaengsFloatingButton(
                                label = "위치 다시 확인",
                                onClick = {
                                    onAction(
                                        PlacesAction.Locate(
                                            selectedKind,
                                            discovery.preferParking,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        PlaceDiscoveryPanel(
            state = discovery,
            journey = state.journey,
            onSearch = { kind, preferParking ->
                onAction(PlacesAction.Search(kind, preferParking))
            },
            onRetry = { onAction(PlacesAction.RetrySearch) },
            onSelect = ::focusOn,
            onJourney = { onAction(PlacesAction.LoadJourney(it)) },
            onRetryJourney = { onAction(PlacesAction.RetryJourney) },
            onOpenHandoff = onOpenHandoff,
            onCall = onCall,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .onSizeChanged { panelHeightPx = it.height },
        )
    }
}

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

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

private fun canRequestLocationPermissionAgain(context: Context): Boolean {
    val activity = context.findActivity() ?: return false
    return LOCATION_PERMISSIONS.any { permission ->
        ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun appSettingsIntent(context: Context): Intent = Intent(
    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    Uri.fromParts("package", context.packageName, null),
)

private fun dial(context: Context, phone: String) {
    val safe = phone.filter { it.isDigit() || it in "+*#," }
    if (safe.isNotBlank()) context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$safe".toUri()))
}

@Preview(device = "spec:width=411dp,height=891dp", showBackground = true)
@Composable
private fun PlacesScreenPreview() {
    DaengsTheme {
        PlacesScreen(
            state = PlacesUiState(),
            onBack = {},
            onRequestPermission = {},
            onOpenSettings = {},
            onAction = {},
            onOpenHandoff = {},
            onCall = {},
            showMap = false,
        )
    }
}
