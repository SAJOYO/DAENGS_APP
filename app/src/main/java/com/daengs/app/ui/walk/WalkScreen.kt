package com.daengs.app.ui.walk

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.map.features.territory.TerritoryBoardState
import com.daengs.app.map.features.territory.TerritoryGameState
import com.daengs.app.map.shell.MapHost
import com.daengs.app.map.shell.MapPurpose
import com.daengs.app.miniroom.OutsideSnapshot
import com.daengs.app.miniroom.OutsideWeather
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.pet.Pet
import com.daengs.app.territory.userMessage
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.common.DaengsFloatingButton
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.PinkSoft
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import com.daengs.app.walk.TrackingState
import com.daengs.app.walk.TrailSnapshot
import com.daengs.app.walk.WalkMoment
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.WalkRoutePoint
import com.daengs.app.walk.MIN_WALK_METERS
import com.daengs.app.walk.MIN_WALK_MILLIS
import com.daengs.app.walk.WalkSummary
import com.daengs.app.walk.countsAsWalk
import com.daengs.app.walk.WalkTrackingState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** 산책 상태를 그리며 모든 사용자 입력을 [WalkAction]으로 올리는 순수 화면이다. */
@Composable
fun WalkScreen(
    state: WalkUiState,
    onAction: (WalkAction) -> Unit,
    modifier: Modifier = Modifier,
    avatarBreed: DogBreed? = null,
    /** 대표 아이가 올린 프로필 사진. 있으면 지도의 내 위치가 그 얼굴이 된다. */
    avatarPhoto: android.graphics.Bitmap? = null,
    outside: OutsideSnapshot = OutsideSnapshot.DEFAULT,
    showMap: Boolean = true,
    /** 그 아이가 올린 프로필 사진. 없으면 견종 그림이다. */
    photoOf: (String) -> ImageBitmap? = { null },
) {
    // **이번 산책에 데려가는 아이의 얼굴.** 대표를 데려가면 null 이라 아래에서 원래
    // 값을 그대로 쓴다 — 지금 동작이 안 바뀌고, 바깥에서 정해 넘기는 값(개발자 패널의
    // 견종 고르기)도 계속 이긴다. 대표를 빼고 나갔을 때만 그 아이로 바꾼다.
    val face = walkFaceOverride(state.selection.pets, state.selection.selectedDogIds)
    // **사진과 견종을 같이 옮긴다.** 사진만 바꾸면, 그 아이가 사진을 안 올렸을 때
    // 대표의 견종 그림이 남아서 반쯤 다른 아이가 된다.
    val faceRes = walkFacePortraitRes(face, avatarBreed)
    val facePhoto = if (face == null) avatarPhoto else photoOf(face.id)?.asAndroidBitmap()

    val mapPresentation = state.toMapPresentation()
    val summary = state.completedSummary
    var bottomInset by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var leftInset by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var rightInset by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var topInset by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    Box(modifier.fillMaxSize()) {
        if (showMap) {
            MapHost(
                scene = mapPresentation.scene,
                searchOrigin = null,
                followDevice = state.location.followDevice && mapPresentation.fitBounds == null,
                bottomPaddingPx = bottomInset, leftPaddingPx = leftInset, rightPaddingPx = rightInset, topPaddingPx = topInset,
                avatarRes = faceRes,
                avatarPhoto = facePhoto,
                centerOn = state.location.centerOn,
                centerZoom = state.location.centerZoom,
                fitBounds = mapPresentation.fitBounds,
                onCameraIdle = { onAction(WalkAction.CameraSettled(it)) },
                onCameraGesture = { onAction(WalkAction.CameraMoved) },
                onSelectPlace = {},
                onSelectTerritorySite = { onAction(WalkAction.SelectTerritorySite(it)) },
                onSelectMoment = { onAction(WalkAction.SelectMoment(it)) },
                onSelectRouteEndpoint = { onAction(WalkAction.SelectRouteEndpoint(it)) },
                onMapTap = { onAction(WalkAction.MapTapped(it)) },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(PinkFaint))
        }

        WalkGameOverlay(
            tracking = state.tracking,
            summary = summary,
            outside = outside,
            locationGranted = state.location.permissionGranted,
            preciseLocation = state.location.precisePermission,
            locating = state.location.locating,
            locationSample = state.location.sample,
            locationError = state.location.errorMessage,
            pets = state.selection.pets,
            selectedDogIds = state.selection.selectedDogIds,
            photoOf = photoOf,
            moments = state.displayedMoments,
            momentNotice = state.momentNotice,
            selectedRoutePoint = state.selectedRoutePoint,
            resultExpanded = state.completion.resultExpanded,
            mapPurpose = state.map.purpose,
            territory = state.territory,
            territoryGame = state.territoryGame,
            onInsets = { left, top, right, bottom -> leftInset = left; topInset = top; rightInset = right; bottomInset = bottom },
            onCloseTerritory = { onAction(WalkAction.ClearTerritory) },
            onSelectClaimingPet = { site, pet -> onAction(WalkAction.SelectClaimingPet(site, pet)) },
            onOpenEntries = { onAction(WalkAction.OpenEntries) },
            onOpenDiaryList = { onAction(WalkAction.OpenDiaryList) },
            onPhotographWalk = { onAction(WalkAction.PhotographWalk) },
            onMarkTerritory = { onAction(WalkAction.MarkTerritory(it)) },
            onPhotographTerritory = { onAction(WalkAction.PhotographTerritory(it)) },
            onRefreshClaimAccess = { onAction(WalkAction.RefreshClaimAccess) },
            onToggleDog = { onAction(WalkAction.ToggleDog(it)) },
            onHome = { onAction(WalkAction.Home) },
            onMapPurposeChange = { onAction(WalkAction.ChangeMapPurpose(it)) },
            onRetryTerritory = { onAction(WalkAction.RetryTerritory) },
            onRequestOrientation = { onAction(WalkAction.RequestOrientation(it)) },
            onOpenSettings = { onAction(WalkAction.OpenAppSettings) },
            onLocate = { onAction(WalkAction.Locate) },
            onStart = { onAction(WalkAction.StartRequested) },
            onPause = { onAction(WalkAction.Pause) },
            onResume = { onAction(WalkAction.Resume) },
            onStop = { onAction(WalkAction.Stop) },
            onAddMoment = { onAction(WalkAction.AddMoment(it)) },
            onClearRoutePoint = { onAction(WalkAction.ClearRoutePoint) },
            onReviewMap = { onAction(WalkAction.ReviewMap) },
            onShowResult = { onAction(WalkAction.ShowResult) },
            onCloseResult = { onAction(WalkAction.CloseResult) },
        )
    }
}

@Composable
private fun WalkGameOverlay(
    tracking: WalkTrackingState,
    summary: WalkSummary?,
    outside: OutsideSnapshot,
    locationGranted: Boolean,
    preciseLocation: Boolean,
    locating: Boolean,
    locationError: String?,
    locationSample: LocationSample? = null,
    pets: List<Pet>,
    selectedDogIds: Set<String>,
    /** 그 아이가 올린 프로필 사진. 없으면 견종 그림이다. */
    photoOf: (String) -> ImageBitmap? = { null },
    moments: List<WalkMoment>,
    momentNotice: String?,
    selectedRoutePoint: WalkRoutePoint?,
    resultExpanded: Boolean,
    mapPurpose: MapPurpose = MapPurpose.WALK,
    territory: TerritoryBoardState = TerritoryBoardState(),
    territoryGame: TerritoryGameState = TerritoryGameState(),
    onInsets: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    onCloseTerritory: () -> Unit = {},
    onSelectClaimingPet: (String, String) -> Unit = { _, _ -> },
    onOpenEntries: () -> Unit = {},
    /**
     * 산책 기록 **목록**으로 나간다. [onOpenEntries] 와 다른 자리다 — 저쪽은 지금
     * 걷는 산책 한 건에 남긴 것이고, 이쪽은 지난 산책들의 목록이다. 걷는 중인
     * 산책은 아직 목록에 없어서(끝나야 들어간다) 둘을 하나로 합칠 수 없다.
     */
    onOpenDiaryList: () -> Unit = {},
    onPhotographWalk: () -> Unit = {},
    onMarkTerritory: (String) -> Unit = {},
    onPhotographTerritory: (String) -> Unit = {},
    onRefreshClaimAccess: () -> Unit = {},
    onToggleDog: (String) -> Unit,
    onHome: () -> Unit,
    onMapPurposeChange: (MapPurpose) -> Unit = {},
    onRetryTerritory: () -> Unit = {},
    onRequestOrientation: (WalkOrientation) -> Unit,
    onOpenSettings: () -> Unit,
    onLocate: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onAddMoment: (WalkMomentType) -> Unit,
    onClearRoutePoint: () -> Unit,
    onReviewMap: () -> Unit,
    onShowResult: () -> Unit,
    onCloseResult: () -> Unit,
) {
    var momentsOpen by rememberSaveable { mutableStateOf(false) }
    var pausedBrowsing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(tracking.trail.state) { if (tracking.trail.state != TrackingState.PAUSED) pausedBrowsing = false }
    LaunchedEffect(territory.selectedSiteId) { if (territory.selectedSiteId != null) momentsOpen = false }
    var wallClockMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var realtimeMillis by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(territoryGame.enabled, mapPurpose) {
        while (true) {
            wallClockMillis = System.currentTimeMillis()
            realtimeMillis = SystemClock.elapsedRealtime()
            delay(1_000L)
            if (territoryGame.enabled && mapPurpose == MapPurpose.TERRITORY) onRefreshClaimAccess()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val layoutMode = walkLayoutMode(maxWidth.value, maxHeight.value)
        val territoryCardMaxHeight = maxHeight * if (layoutMode == WalkLayoutMode.LANDSCAPE) .7f else .65f
        val territoryCardVisible = summary == null && mapPurpose == MapPurpose.TERRITORY &&
            territory.selectedSiteId != null && territoryGame.enabled && territoryGame.target != null
        val stackMapTools = maxWidth < 380.dp
        val elapsedMillis = summary?.activeDurationMillis ?: tracking.elapsedMillisAt(realtimeMillis)
        val distanceMeters = summary?.distanceMeters ?: tracking.trail.distanceMeters
        val notice = when {
            summary != null -> "경로 가까이를 누르면 그 지점의 기록을 볼 수 있어요"
            !locationGranted -> "산책 경로를 기록하려면 위치 권한이 필요해요."
            locationGranted && !preciseLocation -> "정확한 위치를 켜야 경로를 기록할 수 있어요."
            locationError != null -> locationError
            tracking.errorMessage != null -> tracking.errorMessage
            mapPurpose == MapPurpose.TERRITORY -> territoryStatusLabel(territory)
            tracking.trail.state == TrackingState.RECORDING -> "동선을 기록하고 있어요"
            tracking.trail.state == TrackingState.PAUSED -> "산책이 잠시 멈춰 있어요"
            else -> "산책을 시작하면 지나온 동선이 지도에 남아요"
        }
        val momentEnabled = tracking.canRecordAction

        Box(Modifier.fillMaxSize().systemBarsPadding().padding(12.dp)) {
            val landscape = layoutMode == WalkLayoutMode.LANDSCAPE
            val density = LocalDensity.current
            var panelWidth by remember { androidx.compose.runtime.mutableIntStateOf(0) }
            var panelHeight by remember { androidx.compose.runtime.mutableIntStateOf(0) }
            var dockWidth by remember { androidx.compose.runtime.mutableIntStateOf(0) }
            var gaugeHeight by remember { androidx.compose.runtime.mutableIntStateOf(0) }
            var hudHeight by remember { androidx.compose.runtime.mutableIntStateOf(0) }
            val systemTop = WindowInsets.systemBars.getTop(density)
            val systemBottom = WindowInsets.systemBars.getBottom(density)
            LaunchedEffect(panelWidth, panelHeight, dockWidth, gaugeHeight, hudHeight, systemTop, systemBottom, landscape, tracking.trail.state, summary) {
                val gap = with(density) { 24.dp.roundToPx() }
                onInsets(if (landscape && panelHeight > 0) panelWidth + gap else 0,
                    systemTop + hudHeight + gap,
                    if (landscape && tracking.trail.state != TrackingState.OFF && summary == null) dockWidth + gap else 0,
                    if (landscape) systemBottom + (if (tracking.trail.state != TrackingState.OFF && summary == null) gaugeHeight else 0) + gap else systemBottom + panelHeight + gap)
            }
            Column(Modifier.align(Alignment.TopStart).fillMaxWidth()
                .onSizeChanged { hudHeight = it.height },
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top) {
                Surface(shape = RoundedCornerShape(16.dp), color = CardWhite) {
                    val tools: @Composable () -> Unit = {
                        WalkHomeButton(onHome)
                        WalkRotateButton(layoutMode, onRequestOrientation)
                        WalkMapSettingsButton()
                    }
                    if (stackMapTools) Column(horizontalAlignment = Alignment.CenterHorizontally) { tools() }
                    else Row(verticalAlignment = Alignment.CenterVertically) { tools() }
                }
                WalkTopHud(elapsedMillis, distanceMeters, outside.takeIf { summary == null }, wallClockMillis, summary,
                    modifier = Modifier.widthIn(max = 224.dp),
                    controlContent = {
                        if (summary == null && tracking.trail.state != TrackingState.OFF) {
                            WalkToolButton(if (tracking.trail.state == TrackingState.PAUSED) WalkTool.PLAY else WalkTool.PAUSE,
                                if (tracking.trail.state == TrackingState.PAUSED) "산책 재개 메뉴" else "잠시 멈춤",
                                { if (tracking.trail.state == TrackingState.PAUSED) pausedBrowsing = false else onPause() },
                                enabled = tracking.finishingSessionId == null,
                                // **여기만 강조한다.** 시간 카드 안에서 유일하게 누르는
                                // 것인데 나머지 도구와 같은 모양이라 눈에 안 걸렸다.
                                // 종료가 이 버튼 뒤에만 있어서(`PauseCard`) 못 찾으면
                                // 산책을 끝낼 방법이 없다.
                                emphasis = true,
                                caption = if (tracking.trail.state == TrackingState.PAUSED) "재개" else "멈춤")
                        }
                    }, gpsContent = {
                    val gps = walkGpsPresentation(locationGranted, preciseLocation, locationError,
                        locationSample, realtimeMillis * 1_000_000L)
                    WalkGpsDot(gps.good, gps.unavailable, gps.detail, onOpenSettings)
                })
                }
                if (tracking.trail.state != TrackingState.OFF || summary != null) {
                    if (summary != null) WalkSpeedLegend(Modifier.align(Alignment.End))
                    else if (!landscape) MotionSpeedometer(
                        display = tracking.motionDisplay,
                        modifier = Modifier.align(Alignment.End))
                }
            }
            val dock: @Composable () -> Unit = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = CardWhite) { WalkMapModeButton(mapPurpose, onMapPurposeChange) }
                // **걷는 중에 쓰는 도크라 한 칸을 크게 잡는다.** 걸으면서 누르는
                // 자리라 48dp 로는 손가락이 자주 빗나간다.
                Surface(shape = RoundedCornerShape(18.dp), color = CardWhite) {
                    Row(Modifier.padding(6.dp)) {
                        WalkToolButton(WalkTool.CAMERA, "산책 사진 촬영", onPhotographWalk,
                            enabled = tracking.trail.state == TrackingState.RECORDING && tracking.finishingSessionId == null,
                            caption = "사진", minSize = DOCK_BUTTON, iconSize = DOCK_ICON)
                        WalkToolButton(WalkTool.RECORD, "행동 기록", {
                            momentsOpen = !momentsOpen; onCloseTerritory()
                        }, enabled = tracking.trail.state == TrackingState.RECORDING, active = momentsOpen,
                            caption = "기록", minSize = DOCK_BUTTON, iconSize = DOCK_ICON)
                        WalkToolButton(WalkTool.ENTRIES, "이 산책에 남긴 것", onOpenEntries,
                            caption = "일기", minSize = DOCK_BUTTON, iconSize = DOCK_ICON)
                        WalkToolButton(WalkTool.LOCATE, "내 위치", onLocate,
                            enabled = locationGranted && !locating,
                            caption = "내 위치", minSize = DOCK_BUTTON, iconSize = DOCK_ICON)
                    }
                }
            }
            }
            Column(Modifier.align(if (landscape) Alignment.BottomStart else Alignment.BottomCenter)
                .widthIn(max = if (landscape) 300.dp else 360.dp)
                .then(if (landscape) Modifier else Modifier.fillMaxWidth())
                .onSizeChanged { panelWidth = it.width; panelHeight = it.height },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (momentsOpen && tracking.trail.state == TrackingState.RECORDING) WalkMomentDock(
                    layoutMode = WalkLayoutMode.PORTRAIT, enabled = momentEnabled,
                    onAddMoment = { momentsOpen = false; onAddMoment(it) })
                if (territoryCardVisible) {
                    val ownerPet = territoryGame.target?.takeIf { it.occupancyKnown && it.isOwnedByMe == true }
                        ?.claim?.occupancy?.ownerPetId?.let { id -> pets.firstOrNull { it.id == id } }
                    TerritoryActionCard(territoryGame, onMarkTerritory, onPhotograph = onPhotographTerritory,
                        modifier = Modifier.fillMaxWidth().heightIn(max = territoryCardMaxHeight),
                        onClose = onCloseTerritory, onSelectPet = onSelectClaimingPet,
                        ownerPhoto = ownerPet?.id?.let(photoOf), ownerBreed = ownerPet?.breedArt,
                        onPrepareWalk = onCloseTerritory)
                }
                if (tracking.errorMessage != null || (mapPurpose == MapPurpose.TERRITORY &&
                    (territory.failure != null || territory.sites.isEmpty()))) {
                    StatusPill(notice, tracking.errorMessage != null || territory.failure != null,
                        if (territory.failure != null) "다시 시도" else null, onRetryTerritory)
                }
                if (tracking.trail.state == TrackingState.OFF && summary == null) {
                    // **산책 전에는 목록으로 간다.** 도크의 `일기` 는 지금 걷는 산책에
                    // 묶여 있어서(`activeSessionId ?: completedSessionId`), 걷기 전에
                    // 누르면 묶일 산책이 없어 늘 빈 창이 떴다. 여기서 사람이 보고 싶은
                    // 것은 지난 산책이다 — 홈의 `지난 산책` 과 같은 화면으로 보낸다.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(12.dp), color = CardWhite) { WalkMapModeButton(mapPurpose, onMapPurposeChange) }
                        Surface(shape = RoundedCornerShape(12.dp), color = CardWhite) {
                            WalkToolButton(WalkTool.ENTRIES, "산책 기록", onOpenDiaryList,
                                caption = "산책 기록", captionBeside = true)
                        }
                    }
                }
                if ((tracking.trail.state == TrackingState.OFF && !territoryCardVisible) || summary != null) WalkPrimaryControl(
                    tracking, resultExpanded, pets, selectedDogIds, locationGranted && preciseLocation,
                    onToggleDog, onStart, onPause, onShowResult, photoOf = photoOf)
                else if (!landscape && tracking.trail.state != TrackingState.OFF) dock()
                if (summary != null) TextButton(onClick = onOpenEntries) { Text("기록 ${tracking.savedEntryCount}") }
            }
            if (landscape && tracking.trail.state != TrackingState.OFF && summary == null) {
                MotionSpeedometer(display = tracking.motionDisplay,
                    modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { gaugeHeight = it.height })
                Box(Modifier.align(Alignment.BottomEnd).onSizeChanged { dockWidth = it.width }) { dock() }
            }

            if (selectedRoutePoint != null) {
                WalkRoutePointCard(
                    point = selectedRoutePoint,
                    onClose = onClearRoutePoint,
                    modifier = Modifier
                        .align(
                            if (layoutMode == WalkLayoutMode.LANDSCAPE) {
                                Alignment.BottomCenter
                            } else {
                                Alignment.Center
                            },
                        )
                        .widthIn(max = 470.dp)
                        .fillMaxWidth(),
                )
            } else momentNotice?.let {
                StatusPill(
                    label = it,
                    error = false,
                    actionLabel = null,
                    onAction = {},
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        val modalVisible = tracking.completedSessionId != null && resultExpanded ||
            tracking.finishingSessionId != null || (tracking.trail.state == TrackingState.PAUSED && !pausedBrowsing)
        when {
            tracking.completedSessionId != null && resultExpanded -> ModalScrim {
                if (summary == null) SavingCard("결과를 준비하고 있어요")
                else WalkResultCard(
                    summary = summary,
                    layoutMode = layoutMode,
                    momentGroupCount = moments.size,
                    momentActionCount = moments.sumOf { it.actions.size },
                    onReviewMap = onReviewMap,
                    onClose = onCloseResult,
                )
            }
            tracking.finishingSessionId != null -> ModalScrim { SavingCard("산책을 저장하고 있어요") }
            tracking.trail.state == TrackingState.PAUSED && !pausedBrowsing -> ModalScrim {
                PauseCard(
                    onResume = onResume,
                    onStop = onStop,
                    onBrowse = { pausedBrowsing = true },
                    // 서비스가 저장 뒤에 재는 것과 **같은 규칙**이다. 여기서 미리 재
                    // 두면 "종료 → 사라짐 → 왜 없지" 가 아니라 누르기 전에 알 수 있다.
                    tooShort = !countsAsWalk(distanceMeters, elapsedMillis) && tracking.savedEntryCount == 0,
                )
            }
        }
        if (modalVisible) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .systemBarsPadding()
                    .padding(12.dp)
                    .zIndex(30f),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WalkHomeButton(onHome)
                WalkRotateButton(layoutMode, onRequestOrientation)
                WalkMapSettingsButton()
            }
        }
    }
}

@Composable
private fun WalkMapModeButton(
    purpose: MapPurpose,
    onChange: (MapPurpose) -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = if (purpose == MapPurpose.TERRITORY) MapPurpose.WALK else MapPurpose.TERRITORY
    WalkToolButton(WalkTool.POLE, if (purpose == MapPurpose.TERRITORY) "점령지 숨기기" else "점령지 보기",
        { onChange(target) }, modifier, active = purpose == MapPurpose.TERRITORY,
        caption = if (purpose == MapPurpose.TERRITORY) "점령지 숨기기" else "점령지 보기", captionBeside = true)
}

internal fun territoryStatusLabel(state: TerritoryBoardState): String = when {
    state.failure != null -> state.failure.userMessage()
    state.loading && state.sites.isEmpty() -> "점령지를 불러오는 중이에요"
    state.selectedSiteId != null -> "점령지를 선택했어요"
    state.loading -> "점령지 ${state.sites.size}곳 · 새 지역을 불러오는 중이에요"
    state.sites.isEmpty() && state.loadedOrigin != null -> "이 주변에는 점령지가 없어요"
    state.sites.isEmpty() -> "현재 위치가 잡히면 주변 점령지를 보여드려요"
    state.truncated -> "점령지 ${state.sites.size}곳 · 일부만 표시하고 있어요"
    else -> "점령지 ${state.sites.size}곳"
}

@Composable
private fun WalkRotateButton(
    layoutMode: WalkLayoutMode,
    onRequestOrientation: (WalkOrientation) -> Unit,
    modifier: Modifier = Modifier,
) {
    WalkToolButton(WalkTool.ROTATE, if (layoutMode == WalkLayoutMode.PORTRAIT) "가로 보기" else "세로 보기",
        { onRequestOrientation(layoutMode.oppositeOrientation) }, modifier)
}

@Composable
private fun WalkPrimaryControl(
    tracking: WalkTrackingState,
    resultExpanded: Boolean,
    pets: List<Pet>,
    selectedDogIds: Set<String>,
    locationReady: Boolean,
    onToggleDog: (String) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onShowResult: () -> Unit,
    modifier: Modifier = Modifier,
    photoOf: (String) -> ImageBitmap? = { null },
) {
    when {
        tracking.completedSessionId != null && !resultExpanded -> DaengsFloatingButton(
            label = "결과 다시 보기",
            onClick = onShowResult,
            modifier = modifier,
        )
        tracking.completedSessionId == null &&
            tracking.finishingSessionId == null &&
            tracking.trail.state == TrackingState.RECORDING -> WalkRoundButton(
            label = "Ⅱ",
            caption = "잠시 멈춤",
            onClick = onPause,
            modifier = modifier,
        )
        tracking.completedSessionId == null &&
            tracking.finishingSessionId == null &&
            tracking.trail.state == TrackingState.OFF -> ReadyCard(
            pets = pets,
            selectedDogIds = selectedDogIds,
            onToggleDog = onToggleDog,
            // **위치만 준비돼서는 부족하다.** 강아지 앱이라 아이 없이는 안 나간다
            // (`WalkDogPick.canStartWalk`). 둘러보기(목록이 빔)는 예외다.
            enabled = locationReady && canStartWalk(pets, selectedDogIds),
            blockedReason = walkStartBlockedReason(pets, selectedDogIds),
            onStart = onStart,
            modifier = modifier,
            photoOf = photoOf,
        )
    }
}

/**
 * 왼쪽 위에서 시간과 일시정지를 묶고 거리·날씨·GPS를 보조 줄에 표시한다.
 *
 * @param outside 날씨. 산책이 끝난 뒤에는 null 이고 [summary] 자리가 대신 온다
 */
@Composable
private fun WalkTopHud(
    elapsedMillis: Long,
    distanceMeters: Double,
    outside: OutsideSnapshot?,
    nowMillis: Long,
    summary: WalkSummary?,
    modifier: Modifier = Modifier,
    controlContent: @Composable () -> Unit = {},
    gpsContent: @Composable () -> Unit = {},
) {
    HudSurface(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HudMetric("산책 시간", formatDuration(elapsedMillis))
            controlContent()
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(formatDistance(distanceMeters), color = TextDark, fontSize = 13.sp,
                modifier = Modifier.semantics { contentDescription = "이동 거리 ${formatDistance(distanceMeters)}" })
            when {
                summary != null -> {
                    HudDivider()
                    Text(
                        "${formatClock(summary.startedAtMillis)} 시작",
                        color = TextDark,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                outside != null -> {
                    HudDivider()
                    Text(
                        if (outside.known) outside.temperatureC?.let { "${it.roundToInt()}°C" } ?: "날씨 —" else "날씨 —",
                        color = TextDark,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            gpsContent()
        }
        }
    }
}

/** 한 줄 안에서 재는 값과 알려 주는 값을 가르는 가는 선. */
@Composable
private fun HudDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(22.dp)
            .background(DaengsColors.BorderNeutral),
    )
}

@Composable
private fun WalkStatsHud(elapsedMillis: Long, distanceMeters: Double) {
    HudSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            HudMetric("산책 시간", formatDuration(elapsedMillis))
            HudMetric("이동 거리", formatDistance(distanceMeters))
        }
    }
}

/** 지도를 내 자리로 되돌린다. **동그란 아이콘 하나**로 큰 버튼 옆에 붙는다. */
@Composable
private fun WalkLocateButton(enabled: Boolean, locating: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(CardWhite.copy(alpha = if (enabled) 0.96f else 0.68f))
            .border(1.dp, DaengsColors.BorderNeutral, CircleShape)
            .semantics { contentDescription = "내 위치로" }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (locating) {
            CircularProgressIndicator(Modifier.size(18.dp), color = DaengPink, strokeWidth = 2.dp)
        } else {
            DaengsIconView(
                DaengsIcon.Pin,
                Modifier.size(20.dp),
                tint = if (enabled) DaengPinkDeep else TextMuted,
            )
        }
    }
}

/** 어느 상태에서도 한 번에 홈으로 갈 수 있는 고정 출구. 기록 중이면 서비스는 계속 돈다. */
@Composable
private fun WalkHomeButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(48.dp)
            .semantics { contentDescription = "홈으로" }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // **순수 검정을 쓰지 않는다.** 이 앱의 어두운 색은 따뜻한 갈색(`TextDark`,
        // `#4A3B36`)이고 「내 주변」에는 순수 검정이 한 군데도 없다. 여기만 검정이라
        // 같은 알약 안에서 옆의 회전·설정 아이콘(둘 다 `TextDark`)과 색이 갈렸고,
        // 산책 화면만 차갑게 보이는 원인이었다.
        DaengsIconView(DaengsIcon.Home, Modifier.size(21.dp), tint = TextDark, filled = true)
    }
}

/**
 * 산책 중 현재 GPS 자리에 행동 책갈피를 꽂는 네 버튼.
 *
 * 가로에서는 오른손 엄지로 누르기 쉽게 오른쪽에 세로로 두고, 세로에서는 지도 폭을
 * 가리지 않도록 하단에 2×2로 둔다. GPS가 아직 없으면 보이되 비활성화해, 버튼이
 * 사라져서 기능을 못 찾는 문제와 엉뚱한 옛 좌표를 쓰는 문제를 함께 막는다.
 */
@Composable
private fun WalkMomentDock(
    layoutMode: WalkLayoutMode,
    enabled: Boolean,
    onAddMoment: (WalkMomentType) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (layoutMode == WalkLayoutMode.LANDSCAPE) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            WalkMomentType.entries.forEach { type ->
                WalkMomentButton(type = type, enabled = enabled || type == WalkMomentType.NOTE, onClick = { onAddMoment(type) })
            }
        }
    } else {
        // **한 줄이다.** 두 줄이면 지도 아래가 덩어리 넷(행동·안내·내 위치·멈춤)으로
        // 쌓여서 지도를 절반 가까이 덮었다. 줄이 하나 줄면 그만큼 길이 보인다.
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WalkMomentType.entries.forEach { type ->
                WalkMomentButton(
                    type = type,
                    enabled = enabled || type == WalkMomentType.NOTE,
                    onClick = { onAddMoment(type) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun WalkMomentButton(
    type: WalkMomentType,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (enabled) DaengPinkDeep else TextMuted
    Row(
        modifier
            .then(if (modifier == Modifier) Modifier.width(116.dp) else Modifier)
            .height(44.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) CardWhite.copy(alpha = 0.96f) else CardWhite.copy(alpha = 0.68f))
            .border(1.dp, DaengsColors.BorderNeutral, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DaengsIconView(type.walkIcon, Modifier.size(18.dp), tint = tint)
        Text(
            type.shortLabel,
            color = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/**
 * 한 줄에 넷이 들어가려면 이름이 짧아야 한다.
 *
 * **아이콘이 같이 있어서 줄여도 읽힌다.** 긴 이름(`WalkMomentType.label`)은 기록 목록
 * 처럼 글자만 있는 자리가 그대로 쓴다 — 거기서까지 줄이면 무슨 기록인지 모른다.
 */
private val WalkMomentType.shortLabel: String
    get() = when (this) {
        WalkMomentType.SNIFFING -> "킁킁"
        WalkMomentType.EXCRETION -> "배설"
        WalkMomentType.BARKING -> "짖기"
        WalkMomentType.NOTE -> "순간"
    }

private val WalkMomentType.walkIcon: DaengsIcon
    get() = when (this) {
        WalkMomentType.SNIFFING -> DaengsIcon.Pin
        WalkMomentType.EXCRETION -> DaengsIcon.Paw
        WalkMomentType.BARKING -> DaengsIcon.Sound
        WalkMomentType.NOTE -> DaengsIcon.Book
    }

@Composable
private fun MomentHud(
    nowMillis: Long,
    outside: OutsideSnapshot,
    gpsLabel: String,
    modifier: Modifier = Modifier,
) {
    HudSurface(modifier) {
        Column(horizontalAlignment = Alignment.End) {
            Text(
                momentHeadline(nowMillis, outside),
                color = TextDark,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${formatClock(nowMillis, seconds = true)} · $gpsLabel",
                color = TextMuted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun CompletedWalkHud(summary: WalkSummary, modifier: Modifier = Modifier) {
    HudSurface(modifier) {
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatDate(summary.startedAtMillis),
                color = TextDark,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${formatClock(summary.startedAtMillis)}–${summary.endedAtMillis?.let(::formatClock) ?: "-"} · 저장된 경로",
                color = TextMuted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun WalkRoutePointCard(
    point: WalkRoutePoint,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = CardWhite.copy(alpha = 0.97f),
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "경로 지점 · ${formatClock(point.capturedAtMillis, seconds = true)}",
                    color = TextDark,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "닫기",
                    color = DaengPinkDeep,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onClose).padding(4.dp),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ResultMetric("활동 시간", formatDuration(point.activeElapsedMillis))
                ResultMetric("누적 거리", formatDistance(point.cumulativeDistanceMeters))
                ResultMetric("구간 속도", formatDerivedSpeed(point.derivedSpeedMetersPerSecond))
                ResultMetric("GPS 정확도", point.accuracyMeters?.let { "±${it.roundToInt()} m" } ?: "-")
            }
        }
    }
}

@Composable
private fun HudSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        color = CardWhite.copy(alpha = 0.94f),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 5.dp,
        content = { Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) { content() } },
    )
}

@Composable
private fun HudMetric(label: String, value: String) {
    Column {
        Text(label, color = TextMuted, fontSize = 10.sp)
        Text(value, color = TextDark, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusPill(
    label: String,
    error: Boolean,
    actionLabel: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (error) DaengsColors.ErrorSoft else CardWhite.copy(alpha = 0.94f))
            .border(
                1.dp,
                if (error) DaengsColors.Error.copy(alpha = 0.3f) else DaengsColors.BorderNeutral,
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, color = if (error) DaengsColors.Error else TextDark, fontSize = 11.sp)
        if (actionLabel != null) {
            Text(
                actionLabel,
                color = if (error) DaengsColors.Error else DaengPinkDeep,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onAction).padding(2.dp),
            )
        }
    }
}

@Composable
private fun ReadyCard(
    pets: List<Pet>,
    selectedDogIds: Set<String>,
    onToggleDog: (String) -> Unit,
    enabled: Boolean,
    /** 못 누르는 이유. null 이면 막힌 게 아니다 (위치를 기다리는 중일 수 있다) */
    blockedReason: String? = null,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    photoOf: (String) -> ImageBitmap? = { null },
) {
    Surface(
        modifier.widthIn(max = 300.dp).fillMaxWidth(),
        color = CardWhite,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 7.dp,
    ) {
        // **가운데로 모은다.** 버튼은 가로로 꽉 차는데 글자만 왼쪽에 붙어 있어서,
        // 카드 안에서 두 축이 따로 놀았다.
        Column(
            Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("산책을 시작할까요?", color = TextDark, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            if (pets.isNotEmpty()) {
                // **접어 두지 않는다.** 예전에는 "함께 걷는 강아지 0마리" 버튼 하나만
                // 보였다. 두 마리 이상이면 아무도 안 골라진 채로 열리는데(`defaultWalkDogs`)
                // 골라야 할 아이들이 그 버튼 뒤에 숨어 있어서, 시작이 막힌 이유도
                // 푸는 방법도 화면에 없었다. 아이들을 바로 내놓고 [walkDogPickLabel] 이
                // "누구와 나갈까요?" 라고 말하게 한다.
                DogPickRow(
                    pets,
                    selectedDogIds,
                    onToggleDog,
                    modifier = Modifier.fillMaxWidth(),
                    photoOf = photoOf,
                )
            } else {
                Text(
                    "등록한 강아지가 없어도 산책은 기록할 수 있어요.",
                    color = TextMuted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                )
            }
            // **왜 안 눌리는지 말해 준다.** 흐린 버튼만 두면 고장으로 읽힌다.
            blockedReason?.let {
                Text(it, color = TextMuted, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
            WalkWideAction("산책 시작", enabled = enabled, onClick = onStart)
        }
    }
}

@Composable
private fun WalkRoundButton(
    label: String,
    caption: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(DaengPink)
                .border(4.dp, CardWhite, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = CardWhite, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Text(caption, color = TextDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ModalScrim(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        // 카드 밖 입력을 여기서 끝낸다. 시각적 배경만 두면 일시정지 중에도 뒤의 지도가
        // 드래그되어, 화면 상태와 실제 조작 가능 상태가 서로 어긋난다.
        Box(
            Modifier
                .fillMaxSize()
                // 겹도 순수 검정 대신 앱의 어두운 갈색을 옅게 깐다. 검정이면 방·지도의
                // 따뜻한 색 위에서 회색빛이 돌아 화면이 갑자기 차가워진다.
                .background(TextDark.copy(alpha = 0.34f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        )
        Box(Modifier.align(Alignment.Center).padding(horizontal = 16.dp)) { content() }
    }
}

/**
 * 잠시 멈춤. **종료가 여기에만 있다.**
 *
 * @param tooShort 지금 끝내면 **산책으로 안 남는다.** 50m·1분을 둘 다 넘어야 기록되는데
 *   (`countsAsWalk`), 예전에는 그 말을 종료를 누른 **뒤에야** 했다 — 걷고 온 사람이
 *   기록이 사라진 것을 보고 나서 이유를 읽는 순서였다. 누르기 전에 말해 준다
 */
@Composable
private fun PauseCard(onResume: () -> Unit, onStop: () -> Unit, tooShort: Boolean, onBrowse: () -> Unit = {}) {
    Surface(
        modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
        color = CardWhite,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 10.dp,
    ) {
        Column(
            Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (tooShort) "아직 산책으로 기록되기엔 짧아요" else "산책을 잠시 멈췄어요",
                color = TextDark,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                if (tooShort) {
                    "${MIN_WALK_METERS.toInt()}m 를 ${MIN_WALK_MILLIS / 60_000}분 넘게 걸어야 남아요. " +
                        "지금 그만두면 이 기록은 지워져요."
                } else {
                    "종료는 이 화면에서만 할 수 있어요."
                },
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onBrowse) { Text("지도 둘러보기") }
            WalkWideAction(if (tooShort) "이어서 걷기" else "계속 걷기", onClick = onResume)
            WalkWideAction(
                if (tooShort) "그만두기" else "산책 종료",
                accent = false,
                onClick = onStop,
            )
        }
    }
}

@Composable
private fun SavingCard(label: String) {
    Surface(
        modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
        color = CardWhite,
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 10.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 26.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator(Modifier.size(24.dp), color = DaengPink, strokeWidth = 3.dp)
            Text(label, color = TextDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WalkResultCard(
    summary: WalkSummary,
    layoutMode: WalkLayoutMode,
    momentGroupCount: Int,
    momentActionCount: Int,
    onReviewMap: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        modifier = Modifier.widthIn(max = 470.dp).fillMaxWidth(),
        color = CardWhite,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 12.dp,
    ) {
        Column(
            Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("산책 완료!", color = DaengPinkDeep, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            if (layoutMode == WalkLayoutMode.LANDSCAPE) {
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    ResultMetric("시작", formatClock(summary.startedAtMillis))
                    ResultMetric("종료", summary.endedAtMillis?.let(::formatClock) ?: "-")
                    ResultMetric("산책 시간", formatDuration(summary.activeDurationMillis))
                    ResultMetric("이동 거리", formatDistance(summary.distanceMeters))
                    ResultMetric("평균 속도", formatAverageSpeed(summary))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        ResultMetric("시작", formatClock(summary.startedAtMillis))
                        ResultMetric("종료", summary.endedAtMillis?.let(::formatClock) ?: "-")
                        ResultMetric("산책 시간", formatDuration(summary.activeDurationMillis))
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        ResultMetric("이동 거리", formatDistance(summary.distanceMeters))
                        ResultMetric("평균 속도", formatAverageSpeed(summary))
                    }
                }
            }
            if (momentGroupCount > 0) {
                Text(
                    "지도에 남긴 장소 ${momentGroupCount}곳 · 행동 ${momentActionCount}개",
                    color = TextMuted,
                    fontSize = 12.sp,
                )
            }
            if (layoutMode == WalkLayoutMode.LANDSCAPE) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WalkWideAction(
                        label = "방으로 돌아가기",
                        accent = false,
                        buttonWidth = 190.dp,
                        onClick = onClose,
                    )
                    WalkWideAction(
                        label = "지도 둘러보기",
                        buttonWidth = 190.dp,
                        onClick = onReviewMap,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WalkWideAction("지도 둘러보기", onClick = onReviewMap)
                    WalkWideAction("방으로 돌아가기", accent = false, onClick = onClose)
                }
            }
        }
    }
}

@Composable
private fun ResultMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextMuted, fontSize = 10.sp)
        Text(value, color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WalkWideAction(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    accent: Boolean = true,
    buttonWidth: androidx.compose.ui.unit.Dp = 220.dp,
) {
    Box(
        Modifier
            .width(buttonWidth)
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    !enabled -> PinkFaint
                    accent -> DaengPink
                    else -> PinkSoft
                },
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            // 보조 버튼 글자는 **진한 갈색**이다. 연분홍 바탕에 분홍 글자(`DaengPinkDeep`)
            // 였을 때 대비가 2.49:1 밖에 안 나와서, 실기기에서 「그만두기」가 눌리지
            // 않는 버튼처럼 보였다. 같은 바탕에 이 색이면 8.77:1 이다.
            color = when {
                !enabled -> TextMuted
                accent -> CardWhite
                else -> TextDark
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

/** 아래 도크 버튼 한 칸의 크기. */
private val DOCK_BUTTON = 58.dp

/** 그 안의 그림 크기. */
private val DOCK_ICON = 26.dp

internal fun formatDuration(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d:%02d".format(Locale.US, hours, minutes, seconds)
}

internal fun formatDistance(meters: Double): String =
    if (meters < 1_000.0) "${meters.coerceAtLeast(0.0).roundToInt()} m"
    else "%.2f km".format(Locale.US, meters / 1_000.0)

internal fun formatAverageSpeed(summary: WalkSummary): String {
    if (summary.activeDurationMillis <= 0L) return "-"
    val kmPerHour = summary.distanceMeters / (summary.activeDurationMillis / 1_000.0) * 3.6
    return "%.1f km/h".format(Locale.US, kmPerHour)
}

internal fun formatDerivedSpeed(metersPerSecond: Double?): String =
    metersPerSecond?.takeIf { it.isFinite() && it >= 0.0 }
        ?.let { "%.1f km/h".format(Locale.US, it * 3.6) }
        ?: "-"

private fun momentHeadline(nowMillis: Long, outside: OutsideSnapshot): String {
    val date = DATE_FORMAT.format(Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()))
    if (!outside.known) return "$date · 날씨 확인 중"
    val weather = when (outside.view.weather) {
        OutsideWeather.CLEAR -> "맑음"
        OutsideWeather.CLOUDY -> "흐림"
        OutsideWeather.RAIN -> "비"
        OutsideWeather.SNOW -> "눈"
    }
    val temperature = outside.temperatureC?.let { " · ${it.roundToInt()}°C" }.orEmpty()
    return "$date · $weather$temperature"
}

private fun formatDate(millis: Long): String =
    DATE_FORMAT.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private fun gpsLabel(granted: Boolean, precise: Boolean, sample: LocationSample?): String = when {
    !granted -> "GPS 권한 필요"
    !precise -> "GPS 정확도 낮음"
    sample == null -> "GPS 찾는 중"
    sample.accuracyMeters == null -> "GPS 연결됨"
    sample.accuracyMeters <= 15f -> "GPS 좋음"
    sample.accuracyMeters <= 40f -> "GPS 보통"
    else -> "GPS 약함"
}

private val DATE_FORMAT = DateTimeFormatter.ofPattern("M.d E", Locale.KOREAN)

@Preview(showBackground = true)
@Composable
private fun WalkScreenPreview() {
    DaengsTheme {
        WalkScreen(
            state = WalkUiState(
                location = WalkLocationUiState(
                    permissionGranted = true,
                    precisePermission = true,
                ),
            ),
            onAction = {},
            showMap = false,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WalkMapModeButtonPreview() {
    DaengsTheme {
        Surface(Modifier.padding(16.dp)) {
            WalkMapModeButton(MapPurpose.TERRITORY, onChange = {})
        }
    }
}

@Preview(device = "spec:width=891dp,height=411dp", showBackground = true)
@Preview(device = "spec:width=411dp,height=891dp", showBackground = true)
@Composable
private fun WalkReadyPreview() {
    DaengsTheme {
        Box(Modifier.fillMaxSize().background(PinkFaint)) {
            WalkGameOverlay(
                tracking = WalkTrackingState(), summary = null,
                outside = OutsideSnapshot.DEFAULT, locationGranted = true,
                preciseLocation = true, locating = false, locationError = null,
                pets = emptyList(), selectedDogIds = emptySet(), moments = emptyList(),
                momentNotice = null, selectedRoutePoint = null, resultExpanded = true,
                onToggleDog = {}, onHome = {},
                onRequestOrientation = {},
                onOpenSettings = {}, onLocate = {}, onStart = {}, onPause = {}, onResume = {},
                onStop = {}, onAddMoment = {}, onClearRoutePoint = {},
                onReviewMap = {}, onShowResult = {},
                onCloseResult = {},
            )
        }
    }
}

@Preview(device = "spec:width=891dp,height=411dp", showBackground = true)
@Preview(device = "spec:width=411dp,height=891dp", showBackground = true)
@Composable
private fun WalkRecordingPreview() {
    DaengsTheme {
        Box(Modifier.fillMaxSize().background(PinkFaint)) {
            WalkGameOverlay(
                tracking = WalkTrackingState(
                    trail = TrailSnapshot(state = TrackingState.RECORDING, distanceMeters = 842.4),
                    latestMomentFix = LocationSample(
                        point = GeoPoint(37.5007, 127.0365),
                        capturedAtMillis = 0L,
                    ),
                    activeDurationMillis = 754_000L,
                ),
                summary = null, outside = OutsideSnapshot.DEFAULT,
                locationGranted = true, preciseLocation = true, locating = false,
                locationError = null, pets = emptyList(), selectedDogIds = emptySet(), moments = emptyList(),
                momentNotice = null, selectedRoutePoint = null, resultExpanded = true,
                onToggleDog = {}, onHome = {},
                onRequestOrientation = {},
                onOpenSettings = {}, onLocate = {}, onStart = {}, onPause = {}, onResume = {},
                onStop = {}, onAddMoment = {}, onClearRoutePoint = {},
                onReviewMap = {}, onShowResult = {}, onCloseResult = {},
            )
        }
    }
}

@Preview(device = "spec:width=891dp,height=411dp", showBackground = true)
@Preview(device = "spec:width=411dp,height=891dp", showBackground = true)
@Composable
private fun WalkPausedPreview() {
    DaengsTheme {
        Box(Modifier.fillMaxSize().background(PinkFaint)) {
            WalkGameOverlay(
                tracking = WalkTrackingState(
                    trail = TrailSnapshot(state = TrackingState.PAUSED, distanceMeters = 842.4),
                    activeDurationMillis = 754_000L,
                ),
                summary = null, outside = OutsideSnapshot.DEFAULT,
                locationGranted = true, preciseLocation = true, locating = false,
                locationError = null, pets = emptyList(), selectedDogIds = emptySet(), moments = emptyList(),
                momentNotice = null, selectedRoutePoint = null, resultExpanded = true,
                onToggleDog = {}, onHome = {},
                onRequestOrientation = {},
                onOpenSettings = {}, onLocate = {}, onStart = {}, onPause = {}, onResume = {},
                onStop = {}, onAddMoment = {}, onClearRoutePoint = {},
                onReviewMap = {}, onShowResult = {}, onCloseResult = {},
            )
        }
    }
}

@Preview(device = "spec:width=891dp,height=411dp", showBackground = true)
@Preview(device = "spec:width=411dp,height=891dp", showBackground = true)
@Composable
private fun WalkResultPreview() {
    val summary = WalkSummary(
        sessionId = "preview", dogIds = emptyList(), startedAtMillis = 1_788_324_720_000L,
        endedAtMillis = 1_788_326_220_000L, weather = null, distanceMeters = 1_840.0,
        activeDurationMillis = 1_500_000L, segments = emptyList(), anchor = null,
    )
    DaengsTheme {
        Box(Modifier.fillMaxSize().background(PinkFaint)) {
            WalkGameOverlay(
                tracking = WalkTrackingState(completedSessionId = "preview"), summary = summary,
                outside = OutsideSnapshot.DEFAULT, locationGranted = true,
                preciseLocation = true, locating = false, locationError = null,
                pets = emptyList(), selectedDogIds = emptySet(), moments = emptyList(),
                momentNotice = null, selectedRoutePoint = null, resultExpanded = true,
                onToggleDog = {}, onHome = {},
                onRequestOrientation = {},
                onOpenSettings = {}, onLocate = {}, onStart = {}, onPause = {}, onResume = {},
                onStop = {}, onAddMoment = {}, onClearRoutePoint = {},
                onReviewMap = {}, onShowResult = {},
                onCloseResult = {},
            )
        }
    }
}

@Preview(device = "spec:width=891dp,height=411dp", showBackground = true)
@Preview(device = "spec:width=411dp,height=891dp", showBackground = true)
@Composable
private fun WalkRoutePointCardPreview() {
    DaengsTheme {
        Box(
            Modifier.fillMaxSize().background(PinkFaint).padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            WalkRoutePointCard(
                point = WalkRoutePoint(
                    point = GeoPoint(37.5, 127.0),
                    capturedAtMillis = 1_788_324_820_000L,
                    accuracyMeters = 7f,
                    activeElapsedMillis = 754_000L,
                    cumulativeDistanceMeters = 842.4,
                    derivedSpeedMetersPerSecond = 1.2,
                    segmentIndex = 0,
                    pointIndex = 12,
                ),
                onClose = {},
                modifier = Modifier.widthIn(max = 470.dp).fillMaxWidth(),
            )
        }
    }
}
