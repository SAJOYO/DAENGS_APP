package com.daengs.app.ui.walk

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.daengs.app.DaengsApp
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.completedroute.CompletedRouteLayerState
import com.daengs.app.map.layers.moments.MomentMarkerState
import com.daengs.app.map.shell.*
import com.daengs.app.pet.Pet
import com.daengs.app.ui.theme.*
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** One selected session; entries, map styling and raw-history reconstruction are shared with walking. */
@Composable
internal fun WalkSessionDetailRoute(
    sessionId: String, history: WalkHistory, onBack: () -> Unit,
    modifier: Modifier = Modifier, pets: List<Pet> = emptyList(),
    origin: WalkSessionOrigin = WalkSessionOrigin.RECORDS,
) {
    WalkDiaryMapScreen(sessionId, history, onBack, modifier, pets, origin)
}

@Composable
internal fun WalkDiaryMapScreen(
    sessionId: String, history: WalkHistory, onBack: () -> Unit,
    modifier: Modifier = Modifier, pets: List<Pet> = emptyList(),
    origin: WalkSessionOrigin = WalkSessionOrigin.RECORDS,
) {
    val app = LocalContext.current.applicationContext as DaengsApp
    val backupAccount by app.sessionProvider.accountScope.collectAsState()
    val backupSource = remember(app, backupAccount) { app.routeBackupSource(backupAccount) }
    val reader = remember(app) { WalkDiaryReader(app.walkEntryDao, app.walkPhotos) {
        app.tokenStore.load()?.appUserId.orEmpty()
    } }
    var detail by remember(sessionId) { mutableStateOf<WalkSessionDetail?>(null) }
    val route = detail?.route
    val explorer = rememberWalkRouteExplorer(route, detail?.summary?.activeDurationMillis ?: 0)
    var diary by remember(sessionId) { mutableStateOf<DiaryWalk?>(null) }
    LaunchedEffect(diary?.preparing, diary == null, explorer) {
        if (diary == null || diary?.preparing == true) explorer.pause()
    }
    var loaded by remember(sessionId) { mutableStateOf(false) }
    var error by remember(sessionId) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var selectedId by rememberSaveable(sessionId) { mutableStateOf<String?>(null) }
    // Camera intent is independent of sheet/card selection. Clearing a card must not reframe the map.
    var cameraLatitude by rememberSaveable(sessionId) { mutableStateOf<Double?>(null) }
    var cameraLongitude by rememberSaveable(sessionId) { mutableStateOf<Double?>(null) }
    var cameraRequest by rememberSaveable(sessionId) { mutableIntStateOf(0) }
    var cameraZoom by rememberSaveable(sessionId) { mutableStateOf<Double?>(null) }
    var directionCount by remember(sessionId) { mutableStateOf<Int?>(null) }
    val cameraTarget = cameraLatitude?.let { lat -> cameraLongitude?.let { lng -> GeoPoint(lat, lng) } }
    fun requestCamera(point: GeoPoint?) {
        cameraLatitude = point?.latitude; cameraLongitude = point?.longitude; cameraZoom = null; cameraRequest++
    }
    var adding by rememberSaveable(sessionId) { mutableStateOf(false) }
    var chosenPoint by remember(sessionId) { mutableStateOf<WalkRoutePoint?>(null) }
    var entry by remember(sessionId) { mutableStateOf<WalkEntry?>(null) }
    var editorOpen by remember(sessionId) { mutableStateOf(false) }
    var entryError by remember(sessionId) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var generating by remember(sessionId) { mutableStateOf(false) }
    var generationError by remember(sessionId) { mutableStateOf<String?>(null) }
    var photo by remember(sessionId) { mutableStateOf<WalkPhoto?>(null) }
    var editingScene by remember(sessionId) { mutableStateOf<DiaryScene?>(null) }
    var sceneError by remember(sessionId) { mutableStateOf<String?>(null) }
    var savingScene by remember(sessionId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val entries by remember(sessionId) { app.walkEntries.observe(sessionId) }.collectAsState(initial = emptyList())
    LaunchedEffect(sessionId) {
        app.walkDiaryPublication.start(sessionId)
        app.walkRuntime.delivery.enqueue(sessionId)
    }
    fun generate() {
        if (diary?.published == true) {
            app.walkDiaryPublication.start(sessionId)
            retry++
            return
        }
        if (generating) return
        generating = true; generationError = null
        scope.launch {
            try {
                val auth = app.sessionProvider.freshSession() ?: error("로그인 후 일기를 만들 수 있어요.")
                app.walkRuntime.sync.syncPendingSession(auth.accessToken, sessionId, includeStoryboard = false)
                val remoteId = app.walkEntryDao.session(sessionId)?.serverWalkId ?: error("산책 동기화를 먼저 완료해 주세요.")
                app.walkStoryboardSync.sync(auth.accessToken, sessionId, remoteId, refresh = true)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                generationError = "일기를 확인하지 못했어요. 잠시 뒤 다시 시도해 주세요."
            } finally { generating = false }
        }
    }
    LaunchedEffect(sessionId, history, retry) {
        error = null; loaded = false
        try { history.changes.collect { detail = history.sessionDetail(sessionId); loaded = true } }
        catch (e: Exception) { if (e is CancellationException) throw e; error = "산책 경로를 불러오지 못했어요." }
    }
    LaunchedEffect(detail, retry) {
        diary = null
        val summary = detail?.summary ?: return@LaunchedEffect
        try { reader.observe(listOf(summary), mapOf(sessionId to detail!!.observations)).collect { diary = it.singleOrNull() } }
        catch (e: Exception) { if (e is CancellationException) throw e; error = "장면을 불러오지 못했어요." }
    }
    fun change(value: WalkEntry, delete: Boolean) {
        busy = true; entryError = null
        scope.launch {
            try {
                if (delete) app.walkEntries.deleteAndEnqueue(value.id, app.walkRuntime.delivery::enqueue)
                else { app.walkEntries.save(value); app.walkRuntime.delivery.enqueue(sessionId) }
                editorOpen = false; adding = false; chosenPoint = null
            } catch (e: Exception) { if (e is CancellationException) throw e; entryError = e.message ?: "저장하지 못했어요." }
            finally { busy = false }
        }
    }
    LaunchedEffect(loaded, detail == null) {
        if (loaded && detail == null) {
            selectedId = null; adding = false; chosenPoint = null
            entry = null; editorOpen = false; editingScene = null; photo = null
        }
    }
    BackHandler { when {
        loaded && detail == null -> onBack()
        adding -> { adding = false; chosenPoint = null }
        explorer.panelOpen -> explorer.choosePanel(false)
        selectedId != null -> selectedId = null
        else -> onBack()
    } }
    val scenes = diary?.scenes.orEmpty()
    val selected = scenes.firstOrNull { it.id == selectedId }
    fun selectScene(scene: DiaryScene) {
        explorer.choosePanel(false)
        selectedId = scene.id
        scene.point?.let(::requestCamera)
    }
    val completed = remember(route, chosenPoint) { route?.toCompletedRouteLayerState(chosenPoint) ?: CompletedRouteLayerState() }
    val markers = remember(scenes, selectedId) { diarySceneMarkers(scenes, selectedId) }
    val highlightPaths = remember(explorer.selectedPass) { listOfNotNull(explorer.selectedPass?.path) }
    val replayPoint = explorer.replayFrame?.point
    val mapScene = remember(completed, markers, detail?.stayStamps, highlightPaths, replayPoint) {
        diaryDisplayScene(composeMapScene(MapPurpose.WALK, MapSceneSources(completedRoute = completed, moments = markers,
            stayStamps = detail?.stayStamps.orEmpty()))).copy(
                sessionExplorer = com.daengs.app.map.layers.completedroute.SessionRouteExplorerLayerState(highlightPaths, replayPoint))
    }
    val bounds = remember(route, detail?.summary?.anchor, scenes) {
        route?.bounds.orEmpty().ifEmpty { listOfNotNull(detail?.summary?.anchor) } + scenes.mapNotNull { it.point }
    }
    Column(modifier.fillMaxSize().background(CreamBg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (loaded && detail == null && error == null) {
            TextButton(onClick = onBack) { Text("‹ ${origin.backLabel}") }
            Text("삭제되었거나 현재 계정에서 볼 수 없는 산책이에요.", Modifier.padding(24.dp))
        } else if (!loaded || diary == null || diary?.preparing == true) {
            WalkDiaryPreparing(onBack = onBack, onRefresh = {
                app.walkDiaryPublication.start(sessionId)
                retry++
            }, error = error, backLabel = origin.backLabel)
        } else {
            WalkDiaryMapContent(scenes, selected, !loaded || (detail != null && diary == null), error,
                onSelect = ::selectScene, onClose = { selectedId = null },
                onEdit = { scene -> explorer.pause(); editingScene = scene; sceneError = null },
                onPhoto = { explorer.pause(); photo = it }, onRetry = { retry++ },
                onAdd = {
                    explorer.choosePanel(false)
                    selectedId = null; chosenPoint = null
                    if (route?.points.isNullOrEmpty()) {
                        entry = WalkEntry(sessionId = sessionId, type = WalkMomentType.NOTE,
                            recordedAtMillis = requireNotNull(detail).summary.startedAtMillis)
                        entryError = null; editorOpen = true
                    } else adding = !adding
                },
                adding = adding,
                generationNotice = generationError ?: diary?.notice,
                generating = generating, onGenerate = ::generate,
                generationActionLabel = if (diary?.published == true) "새로고침" else "일기 생성·갱신",
                title = detail?.summary?.let { walkDiaryTitle(it, diary?.title) } ?: "산책 일기",
                subtitle = detail?.summary?.let { formatWalkDay(it.startedAtMillis) }.orEmpty(),
                onBack = onBack, mapSettings = { WalkMapSettingsButton() },
                backLabel = origin.backLabel,
                explorerSelected = explorer.panelOpen,
                onChooseExplorer = { open ->
                    adding = false; chosenPoint = null; selectedId = null; explorer.choosePanel(open)
                },
                explorerPanel = { WalkRouteExplorerPanel(explorer) { requestCamera(null) } },
                directionNotice = directionCount == 0 && route?.segments?.any { it.points.size >= 2 } == true,
                onZoomRoute = {
                    route?.points?.let { points -> points.getOrNull(points.size / 2)?.point }?.let {
                        requestCamera(it); cameraZoom = 18.0
                    }
                },
                summaryContent = { detail?.summary?.let { summary ->
                    WalkSessionSummary(summary, pets.filter { it.id in summary.dogIds }.map { it.name })
                } },
                backupAction = {
                    key(sessionId, backupAccount) {
                        backupSource?.let { WalkRouteBackupStatus(sessionId, it) }
                    }
                },
                onOverview = { explorer.overview(); requestCamera(null) },
                modifier = Modifier.weight(1f), map = { viewport ->
                    if (bounds.isEmpty() || LocalInspectionMode.current) Box(Modifier.fillMaxSize().background(PinkFaint), contentAlignment = Alignment.Center) {
                        Text(if (!loaded) "경로를 불러오고 있어요." else "표시할 위치 기록이 없어요.", color = TextMuted)
                    } else MapHost(scene = mapScene, searchOrigin = null, followDevice = false,
                        fitBounds = bounds, centerOn = cameraTarget,
                        centerZoom = cameraZoom, onRouteDirectionCount = { directionCount = it },
                        cameraRequestKey = cameraRequest, centerYFraction = viewport.selectionYFraction,
                        bottomPaddingPx = viewport.bottomPaddingPx, keepSelectionVisible = true,
                        onCameraIdle = {}, onCameraGesture = {}, onSelectPlace = {},
                        onSelectMoment = { id -> scenes.firstOrNull { it.id == id }?.let(::selectScene) },
                        onMapTap = { point ->
                            if (adding) chosenPoint = route?.nearestPointTo(point, 30.0)
                            else if (explorer.panelOpen) explorer.inspect(point)
                        },
                        modifier = Modifier.fillMaxSize())
                })
        }
    }
    // A removed walk must not keep an already-open editor or photo above the unavailable state.
    if (loaded && detail == null) return
    if (adding && chosenPoint != null && !editorOpen) {
        val point = requireNotNull(chosenPoint)
        AlertDialog(onDismissRequest = { chosenPoint = null }, title = { Text("이 지점에 기록 남기기") },
            text = { Column {
                Text("${formatWalkClock(point.capturedAtMillis)}에 저장된 위치예요.")
                // A loop may visit one spot repeatedly. Let the user choose the observed time explicitly.
                val nearby = route?.points.orEmpty().filter { it.point.distanceTo(point.point) <= 6.0 }
                val alternatives = nearby.filterIndexed { i, p -> i == 0 || p.capturedAtMillis - nearby[i-1].capturedAtMillis > 30_000 }
                if (alternatives.size > 1) Row {
                    alternatives.take(6).forEach { p -> TextButton(onClick = { chosenPoint = p }) { Text(formatWalkClock(p.capturedAtMillis)) } }
                }
                WalkMomentType.entries.forEach { type -> TextButton(onClick = {
                    entry = point.toDiaryEntry(sessionId, type, detail?.summary?.dogIds?.singleOrNull())
                    entryError = null; editorOpen = true
                }) { Text(type.label) } }
            } }, confirmButton = {}, dismissButton = { TextButton(onClick = { chosenPoint = null }) { Text("다른 위치") } })
    }
    if (editorOpen) WalkEntryEditor(entries, entry, pets.filter { it.id in detail?.summary?.dogIds.orEmpty() },
        entryError, busy, { change(it, false) }, { change(it, true) }, { editorOpen = false; chosenPoint = null })
    editingScene?.let { scene ->
        DiarySceneEditor(scene, savingScene, sceneError, onSave = { title, body ->
            val source = scene.source
            if (source == null) sceneError = "장면을 다시 열어 주세요."
            else {
                savingScene = true; sceneError = null
                scope.launch {
                    try {
                        val owner = app.tokenStore.load()?.appUserId.orEmpty()
                        app.walkEntryDao.saveDiarySceneEdit(sessionId, owner, source, title, body)
                        editingScene = null
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        sceneError = e.message ?: "저장하지 못했어요."
                    } finally { savingScene = false }
                }
            }
        }, onDismiss = { editingScene = null })
    }
    photo?.let { WalkPhotoDialog(it, app.walkPhotos::delete, { photo = null }) }
}

internal fun WalkRoutePoint.toDiaryEntry(sessionId: String, type: WalkMomentType, petId: String?): WalkEntry =
    WalkEntry(sessionId = sessionId, type = type, recordedAtMillis = capturedAtMillis, point = point,
        locationCapturedAtMillis = capturedAtMillis, accuracyMeters = accuracyMeters, petId = petId)

/** Same-location labels list sequence numbers, never a count of nearby observations. */
internal fun diarySceneMarkers(scenes: List<DiaryScene>, selectedId: String?): List<MomentMarkerState> {
    val order = scenes.withIndex().associate { it.value.id to it.index + 1 }
    return diaryLocationGroups(scenes).map { group ->
        val chosen = group.firstOrNull { it.id == selectedId } ?: group.first()
        MomentMarkerState(chosen.id, requireNotNull(chosen.point), group.joinToString(" · ") { order[it.id].toString() },
            selected = group.any { it.id == selectedId }, aboveRouteEndpoints = true,
            sequenceLabel = if (group.size <= 3) group.joinToString(" · ") { order[it.id].toString() }
                else (group.take(2) + listOfNotNull(group.firstOrNull { it.id == selectedId }))
                    .distinctBy { it.id }.sortedBy { order[it.id] }.joinToString(" · ") { order[it.id].toString() } + " …")
    }
}


/** Display-only suppression at the same observed position; route and stay data stay intact. */
internal fun diaryDisplayScene(scene: MapScene): MapScene = scene.copy(
    completedRoute = scene.completedRoute.copy(
        start = scene.completedRoute.start?.copy(compact = true),
        end = scene.completedRoute.end?.copy(compact = true)),
    stayStamps = scene.stayStamps.filterNot { stay ->
        scene.moments.any { it.point.distanceTo(stay.point) <= 1.0 }
    },
)
