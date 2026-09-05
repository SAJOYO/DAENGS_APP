package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.daengs.app.walk.isFreshEnoughForMoment
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import com.daengs.app.map.features.territory.TerritoryCaptureTarget
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daengs.app.miniroom.OutsideSnapshot
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.pet.Pet
import com.daengs.app.walk.WalkHistory
import com.daengs.app.walk.WalkTrackingController

/** 앱 권한·설정·화면 이동과 산책 상태 소유자를 연결하는 진입점이다. */
@Composable
fun WalkRoute(
    onBack: () -> Unit,
    onRequestOrientation: (WalkOrientation) -> Unit,
    walkController: WalkTrackingController,
    history: WalkHistory,
    modifier: Modifier = Modifier,
    avatarBreed: DogBreed? = null,
    /** 대표 아이가 올린 프로필 사진. 있으면 지도의 내 위치가 그 얼굴이 된다. */
    avatarPhoto: android.graphics.Bitmap? = null,
    pets: List<Pet> = emptyList(),
    /** 그 아이가 올린 프로필 사진. 없으면 견종 그림이다. */
    photoOf: (String) -> ImageBitmap? = { null },
    outside: OutsideSnapshot = OutsideSnapshot.DEFAULT,
    viewModel: WalkViewModel = viewModel(
        factory = WalkViewModel.factory(LocalContext.current, walkController, history),
    ),
) {
    val context = LocalContext.current
    val app = context.applicationContext as com.daengs.app.DaengsApp
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val editScope = androidx.compose.runtime.rememberCoroutineScope()
    var editorOpen by remember { mutableStateOf(false) }
    var initialEntry by remember { mutableStateOf<com.daengs.app.walk.WalkEntry?>(null) }
    var entryError by remember { mutableStateOf<String?>(null) }
    var entryBusy by remember { mutableStateOf(false) }
    val observedState by viewModel.state.collectAsState()
    val state = if (observedState.tracking.ownerId != null &&
        observedState.tracking.ownerId != app.tokenStore.load()?.appUserId.orEmpty())
        WalkUiState(selection = WalkSelectionState(pets = pets)) else observedState
    val entrySessionId = state.tracking.activeSessionId ?: state.tracking.completedSessionId
    val entryFlow = remember(entrySessionId) { app.walkEntries.observe(entrySessionId.orEmpty()) }
    val observedEntries by entryFlow.collectAsState(initial = emptyList())
    val entries = observedEntries.filter { it.sessionId == entrySessionId }
    val photoFlow = remember(entrySessionId) { app.walkPhotos.observe(entrySessionId.orEmpty()) }
    val observedDiaryPhotos by photoFlow.collectAsState(initial = emptyList())
    val diaryPhotos = observedDiaryPhotos.filter { it.sessionId == entrySessionId }
    var diaryCaptureSession by remember { mutableStateOf<String?>(null) }
    var selectedPhotoId by remember { mutableStateOf<String?>(null) }
    val renderedState = state.copy(
        diaryPhotos = diaryPhotos,
        tracking = state.tracking.copy(momentGroups = entries.entryMoments(), savedEntryCount = entries.size + diaryPhotos.size),
        completion = state.completion.copy(detail = state.completion.detail?.copy(moments = entries.entryMoments())),
    )
    fun saveEntry(entry: com.daengs.app.walk.WalkEntry?, delete: Boolean = false) {
        entry ?: return
        entryBusy = true
        editScope.launch {
            try {
                app.walkRuntime.writer.flush()
                if (delete) app.walkEntries.deleteAndEnqueue(entry.id, app.walkRuntime.delivery::enqueue)
                else {
                    app.walkEntries.save(entry)
                    app.walkRuntime.delivery.enqueue(entry.sessionId)
                }
                editorOpen = false
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                entryError = e.message ?: "기록을 저장하지 못했어요."
            } finally { entryBusy = false }
        }
    }
    val photos by viewModel.territoryPhotos.collectAsState()
    var captureTarget by remember { mutableStateOf<TerritoryCaptureTarget?>(null) }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        permissionRequested = true
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.updatePermission(
            granted = granted,
            precise = result[Manifest.permission.ACCESS_FINE_LOCATION] == true,
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // 알림 권한을 거절해도 Android가 허용하는 방식으로 기록 시작은 계속한다.
        viewModel.onAction(WalkAction.StartConfirmed)
    }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.updatePermission(
            granted = hasLocationPermission(context),
            precise = hasPreciseLocation(context),
        )
    }

    LaunchedEffect(Unit) {
        val granted = hasLocationPermission(context)
        viewModel.activate(granted, hasPreciseLocation(context))
        if (!granted && !permissionRequested) {
            permissionRequested = true
            locationPermissionLauncher.launch(LOCATION_PERMISSIONS)
        }
    }
    LaunchedEffect(pets) {
        viewModel.updatePets(pets)
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                WalkEffect.NavigateHome -> onBack()
                WalkEffect.OpenAppSettings -> settingsLauncher.launch(appSettingsIntent(context))
                WalkEffect.RequestNotificationPermission -> {
                    if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.onAction(WalkAction.StartConfirmed)
                    }
                }
                is WalkEffect.ChangeOrientation -> onRequestOrientation(effect.orientation)
                is WalkEffect.CaptureTerritory -> captureTarget = effect.target
            }
        }
    }
    LaunchedEffect(walkController) {
        walkController.events.collect { event ->
            if (event is com.daengs.app.walk.WalkEvent.MomentRecorded) {
                val result = snackbar.showSnackbar("${event.type.label} 기록을 남겼어요", actionLabel = "취소",
                    duration = androidx.compose.material3.SnackbarDuration.Short)
                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                    try {
                        app.walkEntries.deleteAndEnqueue(event.momentId.removePrefix("moment-"),
                            app.walkRuntime.delivery::enqueue)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        snackbar.showSnackbar(e.message ?: "기록 취소를 완료하지 못했어요.")
                    }
                }
            }
        }
    }
    DisposableEffect(viewModel) {
        onDispose(viewModel::deactivate)
    }

    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(viewModel, lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, _ ->
            viewModel.updateSharedReadsForeground(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
        }
        lifecycle.addObserver(observer)
        viewModel.updateSharedReadsForeground(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
        onDispose {
            lifecycle.removeObserver(observer)
            viewModel.updateSharedReadsForeground(false)
        }
    }

    BackHandler { viewModel.onAction(WalkAction.Back) }

    Column(modifier) {
      TerritoryPhotoStatus(photos, viewModel::retryTerritoryPhoto, Modifier.statusBarsPadding())
      androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
        WalkScreen(
        state = renderedState,
        outside = outside,
        avatarBreed = avatarBreed,
        avatarPhoto = avatarPhoto,
        photoOf = photoOf,
        onAction = { action ->
            if (action == WalkAction.PhotographWalk) {
                diaryCaptureSession = state.tracking.activeSessionId
            } else if (action == WalkAction.OpenEntries) {
                initialEntry = null; entryError = null; editorOpen = true
            } else if (action is WalkAction.AddMoment && action.type == com.daengs.app.walk.WalkMomentType.NOTE) {
                entrySessionId?.let { sessionId ->
                    val sample = state.tracking.latestMomentFix?.takeIf {
                        it.isFreshEnoughForMoment(android.os.SystemClock.elapsedRealtimeNanos())
                    }
                    initialEntry = com.daengs.app.walk.WalkEntry(sessionId = sessionId,
                        type = com.daengs.app.walk.WalkMomentType.NOTE,
                        recordedAtMillis = System.currentTimeMillis(), point = sample?.point,
                        locationCapturedAtMillis = sample?.capturedAtMillis, accuracyMeters = sample?.accuracyMeters)
                    entryError = null; editorOpen = true
                }
            } else if (action is WalkAction.SelectMoment) {
                val photo = diaryPhotos.firstOrNull { "photo-${it.id}" == action.id }
                if (photo != null) selectedPhotoId = photo.id else {
                    initialEntry = entries.firstOrNull { "moment-${it.id}" == action.id }
                    entryError = null; editorOpen = true
                }
            } else viewModel.onAction(action)
        },
        modifier = Modifier.fillMaxSize(),
    )
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(bottom = 170.dp),
        contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
        androidx.compose.material3.SnackbarHost(snackbar)
    }
    if (editorOpen) WalkEntryEditor(entries, initialEntry,
        pets.filter { it.id in state.tracking.activeDogIds || it.id in state.completedSummary?.dogIds.orEmpty() },
        entryError, entryBusy, { saveEntry(it) }, { saveEntry(it, true) }, { editorOpen = false },
        diaryPhotos = diaryPhotos, onOpenPhoto = { editorOpen = false; selectedPhotoId = it.id })
      }
    }
    diaryPhotos.firstOrNull { it.id == selectedPhotoId }?.let { photo ->
        WalkPhotoDialog(photo, app.walkPhotos::delete, { selectedPhotoId = null })
    }
    diaryCaptureSession?.let { sessionId ->
        WalkPhotoCaptureDialog(
            beginCapture = {
                val current = viewModel.state.value
                if (current.tracking.activeSessionId != sessionId || !hasPreciseLocation(context)) null else
                    com.daengs.app.walk.beginWalkPhotoCapture(current.tracking,
                        app.tokenStore.load()?.appUserId.orEmpty(), System.currentTimeMillis(),
                        android.os.SystemClock.elapsedRealtimeNanos())
            },
            onSave = app::saveWalkPhoto,
            onDismiss = { diaryCaptureSession = null },
        )
    }
    captureTarget?.let { target ->
        TerritoryCaptureDialog(
            siteId = target.siteId,
            beginCapture = { viewModel.startTerritoryCapture(target) },
            onSaved = viewModel::saveTerritoryCapture,
            onCaptureFailed = viewModel::cancelTerritoryCapture,
            online = viewModel.onlineTerritoryPhotos,
            onDismiss = { captureTarget = null },
        )
    }
}

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

private fun appSettingsIntent(context: Context): Intent = Intent(
    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    Uri.fromParts("package", context.packageName, null),
)

private fun hasPreciseLocation(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun hasLocationPermission(context: Context): Boolean =
    hasPreciseLocation(context) ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

