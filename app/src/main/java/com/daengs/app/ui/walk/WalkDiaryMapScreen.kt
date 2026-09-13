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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.daengs.app.BuildConfig
import com.daengs.app.auth.AccountScope
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.completedroute.CompletedRouteLayerState
import com.daengs.app.map.layers.moments.MomentMarkerState
import com.daengs.app.map.shell.*
import com.daengs.app.pet.Pet
import com.daengs.app.ui.theme.*
import com.daengs.app.walk.*
import com.daengs.app.walk.detail.WalkDetailActions
import com.daengs.app.walk.detail.WalkDetailSource
import com.daengs.app.walk.diary.*
import com.daengs.app.walk.routeexplorer.SceneRouteRelation
import kotlinx.coroutines.CancellationException

/** The route keys this composition by session and login generation. */
@Composable
internal fun WalkDiaryMapForAccount(sessionId: String, source: WalkDetailSource, actions: WalkDetailActions,
    onBack: () -> Unit, modifier: Modifier, pets: List<Pet>, origin: WalkSessionOrigin, backupAccount: AccountScope,
    backupAction: @Composable () -> Unit,
    readComparison: suspend (DiaryComparisonSnapshot) -> DiaryPlaceComparison?,
) {
    val explorer = rememberWalkRouteExplorer(sessionId, null)
    val state = rememberWalkDetailState(source, actions, explorer)
    val readView = state.readView
    val readingMemory = rememberDiaryReadingMemory()
    RememberWalkExplorationPersistence(source, backupAccount.ownerId.orEmpty(), readView, explorer, readingMemory)
    val detail = readView?.route?.detail
    val route = detail?.route
    val diary = readView?.diary
    val loaded = state.loaded
    val error = state.error
    val selectedId = explorer.selectedSceneId
    val navigation = rememberSaveable(sessionId, saver = DiaryMapNavigation.Saver) { DiaryMapNavigation() }
    val camera = navigation.camera
    var visibility by remember { mutableStateOf<MapVisibilityResult?>(null) }
    var directionCount by remember(sessionId) { mutableStateOf<Int?>(null) }
    val editors = rememberWalkDiaryEditorState(sessionId)
    val chosenPoint = editors.chosenPoint
    var slotPreviewOpen by remember(sessionId) { mutableStateOf(false) }
    var comparisonOpen by remember(sessionId, backupAccount) { mutableStateOf(false) }
    var placeComparison by remember(sessionId, backupAccount) { mutableStateOf<DiaryPlaceComparison?>(null) }
    var usePlaceExplanation by remember(sessionId, backupAccount) { mutableStateOf(false) }
    var comparisonEvidenceOpen by remember(sessionId, backupAccount) { mutableStateOf(false) }
    fun openSlotPreview() { if (BuildConfig.DEBUG) { explorer.pause(); slotPreviewOpen = true } }
    LaunchedEffect(loaded, detail == null) {
        if (loaded && detail == null) {
            explorer.overview(); editors.clear()
        }
    }
    BackHandler { when {
        loaded && detail == null -> onBack()
        editors.adding -> editors.cancelAdding()
        explorer.panelOpen -> explorer.choosePanel(false)
        selectedId != null -> explorer.closeScene()
        readingMemory.groupIds.isNotEmpty() -> readingMemory.inspect(emptyList())
        else -> onBack()
    } }
    val originalScenes = diary?.scenes.orEmpty()
    LaunchedEffect(originalScenes, readView?.scenesLoading, readingMemory.groupIds) {
        if (loaded && readView?.scenesLoading == false && diary != null) {
            val valid = originalScenes.mapTo(hashSetOf()) { it.id }
            readingMemory.inspect(readingMemory.groupIds.filter { it in valid })
        }
    }
    val sceneKinds = remember(diary) { diary?.sceneKinds().orEmpty() }
    val comparisonSnapshot = remember(originalScenes, backupAccount, diary?.preparing) {
        if (BuildConfig.DEBUG && diary?.preparing != true && originalScenes.size in 1..12 && !backupAccount.ownerId.isNullOrBlank())
            DiaryComparisonSnapshot.create(requireNotNull(backupAccount.ownerId), sessionId, originalScenes)
        else null
    }
    val activeComparison = placeComparison?.takeIf { it.snapshotDigest == comparisonSnapshot?.digest }
    LaunchedEffect(comparisonSnapshot?.digest) {
        val snapshot = comparisonSnapshot ?: return@LaunchedEffect
        // Re-entry may restore a matching local result. It never calls a provider or writes the diary.
        try { placeComparison = readComparison(snapshot) }
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
        val current = state.readView ?: return
        val original = originalScenes.singleOrNull { it.id == scene.id } ?: return
        val insideGroup = scene.id in readingMemory.groupIds
        if (!navigation.selectScene(current, original, fromMap || insideGroup)) return
        if (!insideGroup) readingMemory.inspect(emptyList())
        explorer.selectScene(scene.id, fromMap || insideGroup)
    }
    val completed = remember(route, chosenPoint) { route?.toCompletedRouteLayerState(chosenPoint) ?: CompletedRouteLayerState() }
    val markers = remember(originalScenes, selectedId, readingMemory.groupIds) {
        diarySceneMarkers(originalScenes, selectedId, readingMemory.groupIds)
    }
    val currentReview = readView?.route?.review
    val sceneFocus = selectedOriginal?.let { readView?.focusFor(it) }
    fun selectContext(context: com.daengs.app.walk.trajectory.RecordContext, fromMap: Boolean = false) {
        readingMemory.inspect(emptyList())
        explorer.selectContext(context.id, openExplorer = context.kind != com.daengs.app.walk.trajectory.RecordContextKind.GAP,
            fromMap = fromMap)
        if (!fromMap) navigation.fit(context.locations,
            expandedContext = context.kind != com.daengs.app.walk.trajectory.RecordContextKind.GAP)
    }
    val presentation = recordPresentationLayer(explorer, detail, sceneFocus)
    val highlightPaths = presentation.emphasisPaths
    val overviewDirections = explorer.mode == RouteExplorerMode.OVERVIEW || explorer.mode == RouteExplorerMode.REPLAY && explorer.timeRange == null
    val mapScene = remember(completed, markers, detail?.stayStamps, presentation) {
        diaryDisplayScene(composeMapScene(MapPurpose.WALK, MapSceneSources(completedRoute = completed, moments = markers,
            stayStamps = detail?.stayStamps.orEmpty()))).copy(
                sessionExplorer = presentation)
    }
    val walkingBounds = remember(detail, currentReview, originalScenes) { detail?.let { diaryWalkingBounds(it, currentReview, originalScenes) }.orEmpty() }
    val wholeBounds = remember(detail, currentReview, originalScenes) { detail?.let { diaryWholeRecordBounds(it, currentReview, originalScenes) }.orEmpty() }
    val visibilityTargets = remember(originalScenes) { diaryVisibilityTargets(originalScenes) }
    val offscreen = diaryOffscreenScenes(originalScenes, visibility?.takeIf {
        it.query.revisionKey == readView?.revisionKey && it.query.targets == visibilityTargets })
    LaunchedEffect(walkingBounds) { navigation.initialize(walkingBounds) }
    fun wholeRecord() { readingMemory.inspect(emptyList()); explorer.overview(); navigation.fit(wholeBounds, DiaryMapView.WHOLE) }
    Column(modifier.fillMaxSize().background(CreamBg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (loaded && detail == null && error == null) {
            TextButton(onClick = onBack) { Text("‹ ${origin.backLabel}") }
            Text("삭제되었거나 현재 계정에서 볼 수 없는 산책이에요.", Modifier.padding(24.dp))
        } else {
            WalkDiaryMapContent(scenes, selected, !loaded || readView?.scenesLoading == true, error,
                readingMemory = readingMemory,
                onReturnToRange = if (explorer.returnRange != null) ({
                    if (explorer.returnToRange()) readingMemory.inspect(emptyList())
                }) else null,
                onSceneNeighborhood = if (selectedOriginal != null && readView != null &&
                    explorer.sceneNeighborhood(readView, selectedOriginal) != null) ({
                    val current = state.readView
                    if (explorer.selectedSceneId == selectedOriginal.id && current === readView &&
                        explorer.selectSceneNeighborhood(readView, selectedOriginal)) readingMemory.inspect(emptyList())
                }) else null,
                sceneGroup = readingMemory.groupIds.takeIf { it.isNotEmpty() }?.let { ids -> scenes.filter { it.id in ids } },
                onClearGroup = { explorer.closeScene(); readingMemory.inspect(emptyList()) },
                sceneKinds = sceneKinds,
                walkDogIds = detail?.summary?.dogIds.orEmpty(), walkPets = pets,
                onSelect = { selectScene(it) }, onClose = explorer::closeScene,
                selectionFromMap = explorer.selectionFromMap,
                selectionPending = selectedId != null && readView?.scenesLoading == true,
                mapView = navigation.view, offscreenScenes = offscreen,
                onWalkingOverview = { readingMemory.inspect(emptyList()); explorer.overview(); navigation.fit(walkingBounds, DiaryMapView.WALKING) },
                selectedRouteNotice = sceneFocus?.let(::sceneRouteNotice),
                explorerFocusId = explorer.selectedContext?.id,
                onContextDismiss = { if (explorer.selectedContext != null) explorer.overview() },
                gapContexts = currentReview?.context?.contexts.orEmpty(),
                selectedGap = explorer.selectedContext?.takeIf { it.kind == com.daengs.app.walk.trajectory.RecordContextKind.GAP },
                onSelectGap = { selectContext(it) },
                onEdit = { scene ->
                    explorer.pause()
                    editors.editScene(originalScenes.firstOrNull { it.id == scene.id })
                    state.clearSceneError()
                },
                onPhoto = { explorer.pause(); editors.showPhoto(it) },
                onDelete = { scene ->
                    explorer.pause()
                    editors.removeScene(originalScenes.firstOrNull { it.id == scene.id })
                    state.clearSceneError()
                },
                onRetry = state::retry,
                onAdd = {
                    readingMemory.inspect(emptyList())
                    explorer.choosePanel(false)
                    val hasRoute = !route?.points.isNullOrEmpty()
                    editors.beginAdding(requireNotNull(detail).summary, hasRoute)
                    if (!hasRoute) state.clearEntryError()
                },
                adding = editors.adding,
                generationNotice = state.generationError ?: diary?.notice,
                generating = state.generating, onGenerate = state::generateDiary,
                generationActionLabel = if (state.canGenerateDiary) "일기 생성·갱신" else "새로고침",
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
                    readingMemory.inspect(emptyList())
                    editors.cancelAdding(); explorer.choosePanel(open)
                },
                explorerPanel = { notices -> WalkRouteExplorerPanel(explorer, onOverview = ::wholeRecord,
                    readingNotices = notices,
                    reading = readingMemory,
                    onSection = { section -> navigation.fit(section.path) },
                    onAuxiliary = { section -> navigation.fit(section.path) },
                    onContext = { selectContext(it) }) },
                directionNotice = directionCount == 0 &&
                    (presentation.highlightPaths.any { it.size >= 2 } || presentation.observedDirectionEdges.isNotEmpty() ||
                        overviewDirections && route?.segments?.any { it.points.size >= 2 } == true),
                onZoomRoute = {
                    (highlightPaths.flatten().takeIf { it.isNotEmpty() } ?: route?.bounds)?.let { points ->
                        points.getOrNull(points.size / 2)
                    }?.let {
                        navigation.locate(it, zoom = 18.0)
                    }
                },
                summaryContent = { detail?.summary?.let { summary ->
                    WalkSessionSummary(summary, compact = true)
                } },
                mapLegend = { ObservedRouteLegend(presentation.observedParts.map { it.role }) },
                backupAction = backupAction,
                onOverview = ::wholeRecord,
                modifier = Modifier.weight(1f), map = { viewport ->
                    val query = MapVisibilityQuery(readView?.revisionKey.orEmpty(), visibilityTargets, viewport.bottomOcclusionPx,
                        viewport.controlsWidthPx, viewport.controlsHeightPx, viewport.settingsCoverPx, viewport.settingsTopPx)
                    val latestQuery by rememberUpdatedState(query)
                    if (wholeBounds.isEmpty() || LocalInspectionMode.current) Box(Modifier.fillMaxSize().background(PinkFaint), contentAlignment = Alignment.Center) {
                        Text(if (!loaded) "경로를 불러오고 있어요." else "표시할 위치 기록이 없어요.", color = TextMuted)
                    } else MapHost(scene = mapScene, searchOrigin = null, followDevice = false,
                        fitBounds = camera.bounds, centerOn = camera.center,
                        centerZoom = camera.zoom, onRouteDirectionCount = { directionCount = it },
                        centerMinZoom = camera.minZoom,
                        cameraRequestKey = camera.revision, centerYFraction = viewport.selectionYFraction,
                        topPaddingPx = viewport.controlsHeightPx,
                        bottomPaddingPx = if (camera.expandedContext)
                            viewport.contextBottomPaddingPx else viewport.bottomPaddingPx,
                        keepSelectionVisible = true,
                        onCameraIdle = {}, onCameraGesture = navigation::gesture, onSelectPlace = {},
                        visibilityQuery = query, onVisibility = { if (it.query == latestQuery) visibility = it },
                        onSelectMoment = { id -> scenes.firstOrNull { it.id == id }?.let { selectScene(it, fromMap = true) } },
                        onSelectMomentGroup = { ids ->
                            val members = originalScenes.filter { it.id in ids }
                            if (members.size == 1) selectScene(members.single(), fromMap = true)
                            else if (members.isNotEmpty()) {
                                editors.cancelAdding(); explorer.choosePanel(false)
                                readingMemory.inspect(members.map { it.id })
                                readingMemory.groupList.requestScrollToItem(0)
                            }
                        },
                        onSelectRecordContext = { id -> currentReview?.context?.context(id)?.let { selectContext(it, fromMap = true) } },
                        onSelectRouteEndpoint = { id -> currentReview?.context?.context(id)?.let { selectContext(it, fromMap = true) } },
                        onMapTap = { point ->
                            if (editors.adding) editors.choosePoint(route?.nearestPointTo(point, 30.0))
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
    WalkDiaryEditorDialogs(editors, state, route, detail?.summary?.dogIds.orEmpty(), pets, actions::deletePhoto)
}

/** A completed board must not reframe a route the user is already browsing. */
internal fun diaryOverviewBounds(route: List<GeoPoint>, anchor: GeoPoint?, scenes: List<DiaryScene>): List<GeoPoint> =
    route.ifEmpty { listOfNotNull(anchor) }.ifEmpty { scenes.mapNotNull { it.point } }

internal const val SCENE_ROUTE_MIN_ZOOM = 18.0

/** Preserve one input per scene; the provider groups projected positions using the shared map policy. */
internal fun diarySceneMarkers(scenes: List<DiaryScene>, selectedId: String?, inspected: List<String> = emptyList()): List<MomentMarkerState> =
    scenes.mapIndexedNotNull { index, scene -> scene.point?.takeIf { it.isDiaryLocation() }?.let { point ->
        MomentMarkerState(scene.id, point, "${index+1}", selected = scene.id == selectedId,
            aboveRouteEndpoints = true,
            diaryPin = com.daengs.app.map.layers.moments.DiaryPinAppearance(index+1,
                inspected = scene.id in inspected, dimmed = inspected.isNotEmpty() && scene.id !in inspected))
    } }

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

/** Display-only suppression at the same observed position; route and stay data stay intact. */
internal fun diaryDisplayScene(scene: MapScene): MapScene = scene.copy(
    allowRegionalOverview = true,
    completedRoute = scene.completedRoute.copy(
        start = scene.completedRoute.start?.copy(compact = true),
        end = scene.completedRoute.end?.copy(compact = true)),
    stayStamps = scene.stayStamps.filterNot { stay ->
        scene.moments.any { it.point.distanceTo(stay.point) <= 1.0 }
    },
)
