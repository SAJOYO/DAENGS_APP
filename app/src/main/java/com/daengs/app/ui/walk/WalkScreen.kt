package com.daengs.app.ui.walk

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
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
import com.daengs.app.walk.WalkSummary
import com.daengs.app.walk.WalkTrackingState
import com.daengs.app.walk.isFreshEnoughForMoment
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
    outside: OutsideSnapshot = OutsideSnapshot.DEFAULT,
    showMap: Boolean = true,
    /** 그 아이가 올린 프로필 사진. 없으면 견종 그림이다. */
    photoOf: (String) -> ImageBitmap? = { null },
) {
    val mapPresentation = state.toMapPresentation { formatClock(it) }
    val summary = state.completedSummary

    Box(modifier.fillMaxSize()) {
        if (showMap) {
            MapHost(
                scene = mapPresentation.scene,
                searchOrigin = null,
                followDevice = state.location.followDevice,
                avatarRes = avatarBreed?.portraitRes,
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
    var wallClockMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var realtimeMillis by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            wallClockMillis = System.currentTimeMillis()
            realtimeMillis = SystemClock.elapsedRealtime()
            delay(1_000L)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val layoutMode = walkLayoutMode(maxWidth.value, maxHeight.value)
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
        val momentEnabled = tracking.latestMomentFix
            ?.isFreshEnoughForMoment(realtimeMillis * 1_000_000L) == true

        Box(Modifier.fillMaxSize().systemBarsPadding().padding(12.dp)) {
            if (layoutMode == WalkLayoutMode.LANDSCAPE) {
                Row(
                    Modifier.align(Alignment.TopStart).zIndex(10f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WalkHomeButton(onHome)
                    WalkStatsHud(
                        elapsedMillis = elapsedMillis,
                        distanceMeters = distanceMeters,
                    )
                    if (summary == null) {
                        WalkMapModeButton(mapPurpose, onMapPurposeChange)
                    }
                }
                Row(
                    Modifier.align(Alignment.TopEnd),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WalkRotateButton(layoutMode, onRequestOrientation)
                    if (summary == null) {
                        MomentHud(
                            nowMillis = wallClockMillis,
                            outside = outside,
                            gpsLabel = gpsLabel(locationGranted, preciseLocation, tracking.lastSample),
                        )
                    } else CompletedWalkHud(summary)
                }
                Column(
                    Modifier.align(Alignment.BottomStart),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusPill(
                        label = notice,
                        error = !preciseLocation || locationError != null ||
                            tracking.errorMessage != null ||
                            (mapPurpose == MapPurpose.TERRITORY && territory.failure != null),
                        actionLabel = when {
                            !locationGranted || !preciseLocation -> "설정"
                            mapPurpose == MapPurpose.TERRITORY && territory.failure != null -> "다시 시도"
                            else -> null
                        },
                        onAction = if (
                            mapPurpose == MapPurpose.TERRITORY && territory.failure != null &&
                            locationGranted && preciseLocation
                        ) onRetryTerritory else onOpenSettings,
                    )
                    if (summary == null) {
                        DaengsFloatingButton(
                            label = if (locating) "찾는 중" else "◎ 내 위치",
                            enabled = locationGranted && !locating,
                            onClick = onLocate,
                        )
                    }
                }
                if (
                    tracking.trail.state == TrackingState.RECORDING &&
                    mapPurpose == MapPurpose.WALK
                ) {
                    WalkMomentDock(
                        layoutMode = layoutMode,
                        enabled = momentEnabled,
                        onAddMoment = onAddMoment,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
                WalkPrimaryControl(
                    tracking = tracking,
                    resultExpanded = resultExpanded,
                    pets = pets,
                    selectedDogIds = selectedDogIds,
                    locationReady = locationGranted && preciseLocation,
                    onToggleDog = onToggleDog,
                    onStart = onStart,
                    onPause = onPause,
                    onShowResult = onShowResult,
                    modifier = Modifier.align(Alignment.BottomEnd),
                    photoOf = photoOf,
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().align(Alignment.TopStart),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WalkHomeButton(onHome)
                    if (summary == null) {
                        WalkMapModeButton(mapPurpose, onMapPurposeChange)
                    }
                    WalkRotateButton(layoutMode, onRequestOrientation)
                }
                Column(
                    Modifier.align(Alignment.TopCenter).padding(top = 52.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WalkStatsHud(
                        elapsedMillis = elapsedMillis,
                        distanceMeters = distanceMeters,
                    )
                    if (summary == null) {
                        MomentHud(
                            nowMillis = wallClockMillis,
                            outside = outside,
                            gpsLabel = gpsLabel(locationGranted, preciseLocation, tracking.lastSample),
                        )
                    } else CompletedWalkHud(summary)
                }
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(max = 380.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (
                        tracking.trail.state == TrackingState.RECORDING &&
                        mapPurpose == MapPurpose.WALK
                    ) {
                        WalkMomentDock(
                            layoutMode = layoutMode,
                            enabled = momentEnabled,
                            onAddMoment = onAddMoment,
                        )
                    }
                    StatusPill(
                        label = notice,
                        error = !preciseLocation || locationError != null ||
                            tracking.errorMessage != null ||
                            (mapPurpose == MapPurpose.TERRITORY && territory.failure != null),
                        actionLabel = when {
                            !locationGranted || !preciseLocation -> "설정"
                            mapPurpose == MapPurpose.TERRITORY && territory.failure != null -> "다시 시도"
                            else -> null
                        },
                        onAction = if (
                            mapPurpose == MapPurpose.TERRITORY && territory.failure != null &&
                            locationGranted && preciseLocation
                        ) onRetryTerritory else onOpenSettings,
                    )
                    if (summary == null) {
                        DaengsFloatingButton(
                            label = if (locating) "찾는 중" else "◎ 내 위치",
                            enabled = locationGranted && !locating,
                            onClick = onLocate,
                        )
                    }
                    WalkPrimaryControl(
                        tracking = tracking,
                        resultExpanded = resultExpanded,
                        pets = pets,
                        selectedDogIds = selectedDogIds,
                        locationReady = locationGranted && preciseLocation,
                        onToggleDog = onToggleDog,
                        onStart = onStart,
                        onPause = onPause,
                        onShowResult = onShowResult,
                        photoOf = photoOf,
                    )
                }
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
            tracking.finishingSessionId != null || tracking.trail.state == TrackingState.PAUSED
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
            tracking.trail.state == TrackingState.PAUSED -> ModalScrim {
                PauseCard(onResume = onResume, onStop = onStop)
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WalkHomeButton(onHome)
                WalkRotateButton(layoutMode, onRequestOrientation)
            }
        }
    }
}

@Composable
private fun WalkMapModeButton(
    purpose: MapPurpose,
    onChange: (MapPurpose) -> Unit,
) {
    val target = if (purpose == MapPurpose.TERRITORY) MapPurpose.WALK else MapPurpose.TERRITORY
    DaengsFloatingButton(
        label = if (purpose == MapPurpose.TERRITORY) "산책 지도" else "점령 지도",
        onClick = { onChange(target) },
        modifier = Modifier.semantics { contentDescription = "지도 모드 전환" },
    )
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
) {
    DaengsFloatingButton(
        label = if (layoutMode == WalkLayoutMode.PORTRAIT) "가로 보기" else "세로 보기",
        onClick = { onRequestOrientation(layoutMode.oppositeOrientation) },
        modifier = Modifier.semantics { contentDescription = "산책 화면 회전" },
    )
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
            enabled = locationReady,
            onStart = onStart,
            modifier = modifier,
            photoOf = photoOf,
        )
    }
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

/** 어느 상태에서도 한 번에 홈으로 갈 수 있는 고정 출구. 기록 중이면 서비스는 계속 돈다. */
@Composable
private fun WalkHomeButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(CardWhite.copy(alpha = 0.96f))
            .border(1.dp, DaengsColors.BorderNeutral, CircleShape)
            .semantics { contentDescription = "홈으로" }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        DaengsIconView(DaengsIcon.Home, Modifier.size(21.dp), tint = DaengPinkDeep, filled = true)
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
                WalkMomentButton(type = type, enabled = enabled, onClick = { onAddMoment(type) })
            }
        }
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            WalkMomentType.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { type ->
                        WalkMomentButton(type = type, enabled = enabled, onClick = { onAddMoment(type) })
                    }
                }
            }
        }
    }
}

@Composable
private fun WalkMomentButton(
    type: WalkMomentType,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (enabled) DaengPinkDeep else TextMuted
    Row(
        Modifier
            .width(116.dp)
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
        Text(type.label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

private val WalkMomentType.walkIcon: DaengsIcon
    get() = when (this) {
        WalkMomentType.EXPLORE -> DaengsIcon.Pin
        WalkMomentType.TOILET_MARKING -> DaengsIcon.Paw
        WalkMomentType.SOCIAL -> DaengsIcon.Heart
        WalkMomentType.SPECIAL -> DaengsIcon.Book
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
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("산책을 시작할까요?", color = TextDark, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            if (pets.isNotEmpty()) {
                DogPickRow(pets, selectedDogIds, onToggleDog, photoOf = photoOf)
            } else {
                Text("등록한 강아지가 없어도 산책은 기록할 수 있어요.", color = TextMuted, fontSize = 11.sp)
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
                .background(Color.Black.copy(alpha = 0.34f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        )
        Box(Modifier.align(Alignment.Center).padding(horizontal = 16.dp)) { content() }
    }
}

@Composable
private fun PauseCard(onResume: () -> Unit, onStop: () -> Unit) {
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
            Text("산책을 잠시 멈췄어요", color = TextDark, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("종료는 이 화면에서만 할 수 있어요.", color = TextMuted, fontSize = 12.sp)
            WalkWideAction("계속 걷기", onClick = onResume)
            WalkWideAction("산책 종료", accent = false, onClick = onStop)
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
            color = when {
                !enabled -> TextMuted
                accent -> CardWhite
                else -> DaengPinkDeep
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

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
