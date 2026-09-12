package com.daengs.app.ui.walk

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.daengs.app.DaengsApp
import com.daengs.app.BuildConfig
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.completedroute.CompletedRouteLayerState
import com.daengs.app.map.layers.moments.MomentMarkerState
import com.daengs.app.map.shell.*
import com.daengs.app.pet.Pet
import com.daengs.app.ui.theme.*
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.*
import com.daengs.app.walk.routeexplorer.SceneRouteRelation
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
    val explorer = rememberWalkRouteExplorer(sessionId, detail)
    var diary by remember(sessionId) { mutableStateOf<DiaryWalk?>(null) }
    var loaded by remember(sessionId) { mutableStateOf(false) }
    var error by remember(sessionId) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val selectedId = explorer.selectedSceneId
    // Camera intent is independent of sheet/card selection. Clearing a card must not reframe the map.
    var cameraLatitude by rememberSaveable(sessionId) { mutableStateOf<Double?>(null) }
    var cameraLongitude by rememberSaveable(sessionId) { mutableStateOf<Double?>(null) }
    var cameraRequest by rememberSaveable(sessionId) { mutableIntStateOf(0) }
    var cameraZoom by rememberSaveable(sessionId) { mutableStateOf<Double?>(null) }
    var cameraSectionIndex by remember(route) { mutableStateOf<Int?>(null) }
    var cameraAuxiliaryId by remember(detail) { mutableStateOf<String?>(null) }
    var cameraContextId by remember(detail) { mutableStateOf<String?>(null) }
    var directionCount by remember(sessionId) { mutableStateOf<Int?>(null) }
    val cameraTarget = cameraLatitude?.let { lat -> cameraLongitude?.let { lng -> GeoPoint(lat, lng) } }
    fun requestCamera(point: GeoPoint?) {
        cameraSectionIndex = null
        cameraAuxiliaryId = null
        cameraContextId = null
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
    var slotPreviewOpen by remember(sessionId) { mutableStateOf(false) }
    var comparisonOpen by remember(sessionId, backupAccount) { mutableStateOf(false) }
    var placeComparison by remember(sessionId, backupAccount) { mutableStateOf<DiaryPlaceComparison?>(null) }
    var usePlaceExplanation by remember(sessionId, backupAccount) { mutableStateOf(false) }
    var comparisonEvidenceOpen by remember(sessionId, backupAccount) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun openSlotPreview() { if (BuildConfig.DEBUG) { explorer.pause(); slotPreviewOpen = true } }
    val entries by remember(sessionId) { app.walkEntries.observe(sessionId) }.collectAsState(initial = emptyList())
    LaunchedEffect(sessionId) {
        app.walkDiaryPublication.start(sessionId)
        app.walkRuntime.delivery.enqueue(sessionId)
    }
    fun generate() {
        if (!loaded || diary == null || diary?.preparing == true || diary?.published == true) {
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
            explorer.overview(); adding = false; chosenPoint = null
            entry = null; editorOpen = false; editingScene = null; photo = null
        }
    }
    BackHandler { when {
        loaded && detail == null -> onBack()
        adding -> { adding = false; chosenPoint = null }
        explorer.panelOpen -> explorer.choosePanel(false)
        selectedId != null -> explorer.closeScene()
        else -> onBack()
    } }
    val originalScenes = diary?.scenes.orEmpty()
    val comparisonSnapshot = remember(originalScenes, backupAccount, diary?.preparing) {
        if (BuildConfig.DEBUG && diary?.preparing != true && originalScenes.size in 1..12 && !backupAccount.ownerId.isNullOrBlank())
            DiaryComparisonSnapshot.create(requireNotNull(backupAccount.ownerId), sessionId, originalScenes)
        else null
    }
    val activeComparison = placeComparison?.takeIf { it.snapshotDigest == comparisonSnapshot?.digest }
    LaunchedEffect(comparisonSnapshot?.digest) {
        val snapshot = comparisonSnapshot ?: return@LaunchedEffect
        // Re-entry may restore a matching local result. It never calls a provider or writes the diary.
        try { placeComparison = DiaryComparisonFiles.read(app, snapshot) }
        catch (e: Exception) {
            if (e is CancellationException) throw e
            placeComparison = null
        }
    }
    val scenes = if (activeComparison != null && comparisonSnapshot != null)
        activeComparison.project(comparisonSnapshot, usePlaceExplanation) else originalScenes
    val selected = scenes.firstOrNull { it.id == selectedId }
    val selectedOriginal = originalScenes.firstOrNull { it.id == selectedId }
    fun selectScene(scene: DiaryScene, fromMap: Boolean = false) {
        explorer.selectScene(scene.id, fromMap)
        if (!fromMap) scene.point?.let(::requestCamera)
    }
    val completed = remember(route, chosenPoint) { route?.toCompletedRouteLayerState(chosenPoint) ?: CompletedRouteLayerState() }
    val markers = remember(originalScenes, selectedId) { diarySceneMarkers(originalScenes, selectedId) }
    val currentReview = explorer.review?.takeIf { it.detail == detail }
    val selectedEntry = entries.firstOrNull { it.id == selectedOriginal?.entryId }
    val sceneFocus = remember(selectedOriginal, currentReview, selectedEntry) {
        selectedOriginal?.let { currentReview?.recordSceneFocus(it, selectedEntry) }
    }
    fun selectContext(context: com.daengs.app.walk.trajectory.RecordContext, fromMap: Boolean = false) {
        explorer.selectContext(context.id, openExplorer = context.kind != com.daengs.app.walk.trajectory.RecordContextKind.GAP,
            fromMap = fromMap)
        if (!fromMap) { requestCamera(null); cameraContextId = context.id }
    }
    val presentation = recordPresentationLayer(explorer, detail, sceneFocus)
    val highlightPaths = presentation.emphasisPaths
    val overviewDirections = explorer.mode in setOf(RouteExplorerMode.OVERVIEW, RouteExplorerMode.REPLAY)
    val mapScene = remember(completed, markers, detail?.stayStamps, presentation) {
        diaryDisplayScene(composeMapScene(MapPurpose.WALK, MapSceneSources(completedRoute = completed, moments = markers,
            stayStamps = detail?.stayStamps.orEmpty()))).copy(
                sessionExplorer = presentation)
    }
    val overviewBounds = remember(route, detail?.summary?.anchor, originalScenes) {
        diaryOverviewBounds(route?.bounds.orEmpty(), detail?.summary?.anchor, originalScenes)
    }
    val bounds = cameraContextId?.let { currentReview?.context?.context(it)?.locations?.takeIf { points -> points.isNotEmpty() } }
        ?: cameraAuxiliaryId?.let { id -> currentReview?.observed?.sections?.firstOrNull { it.id == id }?.path }
        ?: cameraSectionIndex?.let { index ->
        explorer.review?.takeIf { it.detail.route == route }?.sections?.firstOrNull { it.index == index }?.path
    } ?: overviewBounds
    Column(modifier.fillMaxSize().background(CreamBg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (loaded && detail == null && error == null) {
            TextButton(onClick = onBack) { Text("‹ ${origin.backLabel}") }
            Text("삭제되었거나 현재 계정에서 볼 수 없는 산책이에요.", Modifier.padding(24.dp))
        } else {
            WalkDiaryMapContent(scenes, selected, !loaded || diary == null || diary?.preparing == true, error,
                onSelect = { selectScene(it) }, onClose = explorer::closeScene,
                selectionFromMap = explorer.selectionFromMap,
                selectedRouteNotice = sceneFocus?.let(::sceneRouteNotice),
                explorerFocusId = explorer.selectedContext?.id,
                onContextDismiss = { if (explorer.selectedContext != null) explorer.overview() },
                gapContexts = currentReview?.context?.contexts.orEmpty(),
                selectedGap = explorer.selectedContext?.takeIf { it.kind == com.daengs.app.walk.trajectory.RecordContextKind.GAP },
                onSelectGap = { selectContext(it) },
                onEdit = { scene ->
                    explorer.pause()
                    editingScene = originalScenes.firstOrNull { it.id == scene.id }
                    sceneError = null
                },
                onPhoto = { explorer.pause(); photo = it },
                onRetry = { app.walkDiaryPublication.start(sessionId); retry++ },
                onAdd = {
                    explorer.choosePanel(false)
                    chosenPoint = null
                    if (route?.points.isNullOrEmpty()) {
                        entry = WalkEntry(sessionId = sessionId, type = WalkMomentType.NOTE,
                            recordedAtMillis = requireNotNull(detail).summary.startedAtMillis)
                        entryError = null; editorOpen = true
                    } else adding = !adding
                },
                adding = adding,
                generationNotice = generationError ?: diary?.notice,
                generating = generating, onGenerate = ::generate,
                generationActionLabel = if (diary == null || diary?.preparing == true || diary?.published == true)
                    "새로고침" else "일기 생성·갱신",
                title = detail?.summary?.let { walkDiaryTitle(it, diary?.title) } ?: "산책 일기",
                subtitle = detail?.summary?.let { formatWalkDay(it.startedAtMillis) }.orEmpty(),
                onBack = onBack, mapSettings = { WalkMapSettingsButton() },
                backLabel = origin.backLabel,
                onSlotPreview = if (BuildConfig.DEBUG) ::openSlotPreview else null,
                onPlaceComparison = if (comparisonSnapshot != null) ({ explorer.pause(); comparisonOpen = true }) else null,
                comparisonContent = {
                    if (activeComparison != null) DiaryPlaceComparisonSwitch(usePlaceExplanation,
                        onChange = { usePlaceExplanation = it }, onEvidence = { comparisonEvidenceOpen = true })
                },
                explorerSelected = explorer.panelOpen,
                onChooseExplorer = { open ->
                    adding = false; chosenPoint = null; explorer.overview(); explorer.choosePanel(open)
                },
                explorerPanel = { WalkRouteExplorerPanel(explorer, onOverview = { requestCamera(null) },
                    onSection = { section -> requestCamera(null); cameraSectionIndex = section.index },
                    onAuxiliary = { section -> requestCamera(null); cameraAuxiliaryId = section.id },
                    onContext = { selectContext(it) }) },
                directionNotice = directionCount == 0 &&
                    (presentation.highlightPaths.any { it.size >= 2 } || presentation.observedDirectionEdges.isNotEmpty() ||
                        overviewDirections && route?.segments?.any { it.points.size >= 2 } == true),
                onZoomRoute = {
                    (highlightPaths.flatten().takeIf { it.isNotEmpty() } ?: route?.bounds)?.let { points ->
                        points.getOrNull(points.size / 2)
                    }?.let {
                        requestCamera(it); cameraZoom = 18.0
                    }
                },
                summaryContent = { detail?.summary?.let { summary ->
                    WalkSessionSummary(summary, pets.filter { it.id in summary.dogIds }.map { it.name })
                    ObservedRouteLegend(presentation.observedParts.map { it.role })
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
                        centerMinZoom = if (selectedId != null && highlightPaths.isNotEmpty()) SCENE_ROUTE_MIN_ZOOM else null,
                        cameraRequestKey = cameraRequest, centerYFraction = viewport.selectionYFraction,
                        // Padding belongs to the last camera request, not to marker selection.
                        bottomPaddingPx = if (cameraContextId?.let { currentReview?.context?.context(it)?.kind }
                            ?.let { it != com.daengs.app.walk.trajectory.RecordContextKind.GAP } == true)
                            viewport.contextBottomPaddingPx else viewport.bottomPaddingPx,
                        keepSelectionVisible = true,
                        onCameraIdle = {}, onCameraGesture = {}, onSelectPlace = {},
                        onSelectMoment = { id -> scenes.firstOrNull { it.id == id }?.let { selectScene(it, fromMap = true) } },
                        onSelectRecordContext = { id -> currentReview?.context?.context(id)?.let { selectContext(it, fromMap = true) } },
                        onSelectRouteEndpoint = { id -> currentReview?.context?.context(id)?.let { selectContext(it, fromMap = true) } },
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
    if (BuildConfig.DEBUG && comparisonOpen && comparisonSnapshot != null) androidx.compose.ui.window.Dialog(
        onDismissRequest = { comparisonOpen = false },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        key(comparisonSnapshot.digest) {
            DiaryPlaceComparisonScreen(comparisonSnapshot, onBack = { comparisonOpen = false }, onApply = {
                placeComparison = it; usePlaceExplanation = true; comparisonOpen = false
            })
        }
    }
    if (comparisonEvidenceOpen && activeComparison != null) AlertDialog(
        onDismissRequest = { comparisonEvidenceOpen = false }, title = { Text("장면 설명 근거") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${activeComparison.model} · 수집 근거와 원래 장면 기준\n${activeComparison.retrievedAt}")
            originalScenes.filter { selectedId == null || it.id == selectedId }.forEach { scene ->
                Text(scene.title, style = MaterialTheme.typography.titleSmall)
                val narration = activeComparison.narrations.getValue(scene.id)
                Text(narration.coverage, style = MaterialTheme.typography.bodySmall)
                val evidence = narration.evidence
                if (evidence.isEmpty()) Text("이 장면에는 배경 설명을 추가하지 않았어요.")
                else evidence.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        } }, confirmButton = { TextButton(onClick = { comparisonEvidenceOpen = false }) { Text("닫기") } },
    )
    if (BuildConfig.DEBUG && slotPreviewOpen) androidx.compose.ui.window.Dialog(
        onDismissRequest = { slotPreviewOpen = false },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        DiarySlotPreviewScreen(sessionId, onBack = { slotPreviewOpen = false })
    }
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

/** A completed board must not reframe a route the user is already browsing. */
internal fun diaryOverviewBounds(route: List<GeoPoint>, anchor: GeoPoint?, scenes: List<DiaryScene>): List<GeoPoint> =
    route.ifEmpty { listOfNotNull(anchor) }.ifEmpty { scenes.mapNotNull { it.point } }

internal const val SCENE_ROUTE_MIN_ZOOM = 18.0

internal fun sceneRouteNotice(relation: SceneRouteRelation): String = when (relation) {
    SceneRouteRelation.CONNECTED -> "이 장면 시각에 대응하는 동선을 강조했어요."
    SceneRouteRelation.NO_ROUTE -> "장면 위치는 있지만 이 시각과 연결되는 동선은 확인되지 않아요."
    SceneRouteRelation.UNLOCATED -> "이 장면에는 확인된 위치가 없어요."
    SceneRouteRelation.EARLIER_LOCATION -> "이전에 확인한 위치예요. 이 장면 시각의 동선은 확인되지 않아요."
    SceneRouteRelation.AMBIGUOUS -> "같은 시각의 위치 기록이 겹쳐 해당 동선을 구분하기 어려워요."
    SceneRouteRelation.OBSERVED_EXCLUDED -> "보행거리에서 제외된 관측 경로예요."
    SceneRouteRelation.OBSERVED_UNRESOLVED -> "보행 여부가 확정되지 않은 관측 경로예요."
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun WalkDiaryObservedSummaryPreview() { DaengsTheme {
    ObservedRouteLegend(com.daengs.app.map.layers.completedroute.RecordRouteRole.entries)
} }

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
