package com.daengs.app.ui.walk

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.daengs.app.location.FeedStatus
import com.daengs.app.location.FusedLocationSource
import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.location.LocationTracker
import com.daengs.app.map.layers.trail.toTrailLayerState
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.map.shell.MapHost
import com.daengs.app.map.shell.MapScene
import com.daengs.app.ui.common.DaengsFloatingButton
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.walk.TrackingState
import com.daengs.app.walk.TrailSnapshot
import com.daengs.app.walk.WalkTrackingController
import com.daengs.app.walk.WalkTrackingState
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 산책. **미니룸의 문으로 들어온다.**
 *
 * 처음에는 산책 제어가 장소 화면 안에 있었다. 한 탭에서 "주변 카페 찾기"와 "산책
 * 시작"을 같이 하니 그 탭이 무엇을 하는 곳인지 이름으로 말할 수 없었다. 둘을 갈랐다 —
 * **장소는 탭, 산책은 문**이다.
 *
 * 지도는 장소 화면과 같은 [MapHost] 를 쓰지만 **장소 마커도 검색 패널도 없다.**
 * 걷는 동안 필요한 것은 내가 지금 어디 있고 어디를 지나왔는가뿐이다.
 *
 * 기록은 이 화면이 아니라 [WalkTrackingController] 와 그 뒤의 Foreground Service 가
 * 한다. 그래서 **화면을 나가도 기록은 이어진다** — 여기서 상태를 들고 있으면 안 된다.
 */
@Composable
fun WalkScreen(
    onBack: () -> Unit,
    walkController: WalkTrackingController,
    modifier: Modifier = Modifier,
    /** 산책하는 아이. 내 위치에 그 얼굴이 선다 — 걷는 건 사람이 아니라 강아지다. */
    avatarBreed: DogBreed? = null,
) {
    val context = LocalContext.current
    val inspectionMode = LocalInspectionMode.current
    val scope = rememberCoroutineScope()
    val source = remember(context) { FusedLocationSource(context.applicationContext) }
    val locationTracker = remember(scope) { LocationTracker(scope) }

    val tracking by walkController.state.collectAsState()
    val trackingActive = tracking.trail.state != TrackingState.OFF

    var granted by remember { mutableStateOf(inspectionMode || hasLocationPermission(context)) }
    var currentPosition by remember { mutableStateOf<GeoPoint?>(null) }
    var followDevice by remember { mutableStateOf(true) }
    var locationError by remember { mutableStateOf<String?>(null) }

    fun acceptLocation(sample: LocationSample) {
        currentPosition = sample.point
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // 알림을 거부해도 안드로이드는 작업 관리자에 FGS 를 띄우고 기록 자체는 된다.
        walkController.start()
    }

    fun startWalk() {
        followDevice = true
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            walkController.start()
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
    LaunchedEffect(tracking.lastSample) {
        tracking.lastSample?.let(::acceptLocation)
    }
    // **기록 중에는 이 화면이 위치를 따로 받지 않는다.** 서비스가 이미 받고 있어서,
    // 둘이 같이 받으면 같은 것을 두 번 켜는 셈이다.
    LaunchedEffect(granted, inspectionMode, trackingActive) {
        if (inspectionMode) return@LaunchedEffect
        if (granted && !trackingActive) locationTracker.start(source) else locationTracker.stop()
    }
    DisposableEffect(locationTracker) {
        onDispose(locationTracker::stop)
    }

    BackHandler(onBack = onBack)

    Box(modifier.fillMaxSize()) {
        if (inspectionMode) {
            Box(Modifier.fillMaxSize().background(PinkFaint))
        } else {
            MapHost(
                scene = MapScene(
                    currentPosition = currentPosition,
                    trail = tracking.trail.toTrailLayerState(),
                ),
                searchOrigin = null,
                followDevice = followDevice,
                avatarRes = avatarBreed?.portraitRes,
                onCameraIdle = {},
                onCameraGesture = { followDevice = false },
                onSelectPlace = {},
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 제어 카드는 **아래**다. 위에 두면 "방으로" 버튼과 겹치고, 걸으면서 한 손으로
        // 누르는 것이라 엄지가 닿는 자리여야 한다.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            locationError?.let { error ->
                Surface(color = DaengsColors.ErrorSoft, shape = RoundedCornerShape(12.dp)) {
                    Text(
                        error,
                        color = DaengsColors.Error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            WalkControlCard(
                state = tracking,
                locationGranted = granted,
                onStart = ::startWalk,
                onPause = walkController::pause,
                onResume = {
                    followDevice = true
                    walkController.resume()
                },
                onStop = walkController::stop,
            )
        }

        // 방으로 돌아가도 **기록은 멈추지 않는다.** 산책은 서비스가 들고 있어서
        // 문으로 다시 들어오면 걷던 상태 그대로다. 여기서 멈추면 방을 한 번 들여다본
        // 것만으로 산책이 끊긴다.
        DaengsFloatingButton(
            label = "← 방으로",
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp),
        )
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

@Preview(device = "spec:width=411dp,height=891dp", showBackground = true)
@Composable
private fun WalkScreenPreview() {
    DaengsTheme {
        WalkScreen(onBack = {}, walkController = PreviewWalkTrackingController())
    }
}

private class PreviewWalkTrackingController : WalkTrackingController {
    override val state = MutableStateFlow(
        WalkTrackingState(
            trail = TrailSnapshot(state = TrackingState.PAUSED, distanceMeters = 842.4),
            activeDurationMillis = 754_000L,
        ),
    )

    override fun start() = Unit

    override fun pause() = Unit

    override fun resume() = Unit

    override fun stop() = Unit
}
