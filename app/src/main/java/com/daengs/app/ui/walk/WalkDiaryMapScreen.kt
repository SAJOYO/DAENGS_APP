package com.daengs.app.ui.walk

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
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
internal fun WalkDiaryMapScreen(
    sessionId: String, history: WalkHistory, onBack: () -> Unit,
    modifier: Modifier = Modifier, pets: List<Pet> = emptyList(),
) {
    val app = LocalContext.current.applicationContext as DaengsApp
    val reader = remember(app) { WalkDiaryReader(app.walkEntryDao, app.walkPhotos) {
        app.tokenStore.load()?.appUserId.orEmpty()
    } }
    var detail by remember(sessionId) { mutableStateOf<WalkSessionDetail?>(null) }
    var diary by remember(sessionId) { mutableStateOf<DiaryWalk?>(null) }
    var loaded by remember(sessionId) { mutableStateOf(false) }
    var error by remember(sessionId) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var selectedId by rememberSaveable(sessionId) { mutableStateOf<String?>(null) }
    var adding by rememberSaveable(sessionId) { mutableStateOf(false) }
    var chosenPoint by remember(sessionId) { mutableStateOf<WalkRoutePoint?>(null) }
    var entry by remember(sessionId) { mutableStateOf<WalkEntry?>(null) }
    var editorOpen by remember(sessionId) { mutableStateOf(false) }
    var entryError by remember(sessionId) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var photo by remember(sessionId) { mutableStateOf<WalkPhoto?>(null) }
    var storyboardOpen by rememberSaveable(sessionId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val entries by remember(sessionId) { app.walkEntries.observe(sessionId) }.collectAsState(initial = emptyList())
    LaunchedEffect(sessionId, history, retry) {
        error = null; loaded = false
        try { history.changes.collect { detail = history.sessionDetail(sessionId); loaded = true } }
        catch (e: Exception) { if (e is CancellationException) throw e; error = "산책 경로를 불러오지 못했어요." }
    }
    LaunchedEffect(detail?.summary, retry) {
        diary = null
        val summary = detail?.summary ?: return@LaunchedEffect
        try { reader.observe(listOf(summary)).collect { diary = it.singleOrNull() } }
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
    if (storyboardOpen) { WalkStoryboardScreen(sessionId, history, pets) { storyboardOpen = false }; return }
    BackHandler { when { adding -> { adding = false; chosenPoint = null }; selectedId != null -> selectedId = null; else -> onBack() } }
    val scenes = diary?.scenes.orEmpty()
    val selected = scenes.firstOrNull { it.id == selectedId }
    val route = detail?.route
    val completed = remember(route, chosenPoint) { route?.toCompletedRouteLayerState(chosenPoint, ::formatWalkClock) ?: CompletedRouteLayerState() }
    val markers = remember(scenes, selectedId) { diarySceneMarkers(scenes, selectedId) }
    val mapScene = remember(completed, markers, detail?.stayStamps) {
        composeMapScene(MapPurpose.WALK, MapSceneSources(completedRoute = completed, moments = markers,
            stayStamps = detail?.stayStamps.orEmpty()))
    }
    val bounds = remember(route, detail?.summary?.anchor, scenes) {
        route?.bounds.orEmpty().ifEmpty { listOfNotNull(detail?.summary?.anchor) } + scenes.mapNotNull { it.point }
    }
    Column(modifier.fillMaxSize().background(CreamBg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ 목록") }
            Text(detail?.summary?.let { walkDiaryTitle(it, diary?.title) } ?: "산책 일기",
                Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            WalkColorSettingsButton()
        }
        if (loaded && detail == null) {
            Text("삭제되었거나 현재 계정에서 볼 수 없는 산책이에요.", Modifier.padding(24.dp))
        } else {
            WalkDiaryMapContent(scenes, selected, !loaded || (detail != null && diary == null), error,
                onSelect = { selectedId = it.id }, onClose = { selectedId = null },
                onEdit = { scene -> entry = entries.firstOrNull { it.id == scene.entryId }; entryError = null; editorOpen = entry != null },
                onPhoto = { photo = it }, onRetry = { retry++ },
                onAdd = {
                    selectedId = null; chosenPoint = null
                    if (route?.points.isNullOrEmpty()) {
                        entry = WalkEntry(sessionId = sessionId, type = WalkMomentType.NOTE,
                            recordedAtMillis = requireNotNull(detail).summary.startedAtMillis)
                        entryError = null; editorOpen = true
                    } else adding = !adding
                },
                onReview = { storyboardOpen = true }, adding = adding,
                modifier = Modifier.weight(1f), map = {
                    if (bounds.isEmpty() || LocalInspectionMode.current) Box(Modifier.fillMaxSize().background(PinkFaint), contentAlignment = Alignment.Center) {
                        Text(if (!loaded) "경로를 불러오고 있어요." else "표시할 위치 기록이 없어요.", color = TextMuted)
                    } else MapHost(scene = mapScene, searchOrigin = null, followDevice = false,
                        fitBounds = bounds, centerOn = chosenPoint?.point ?: selected?.point,
                        onCameraIdle = {}, onCameraGesture = {}, onSelectPlace = {},
                        onSelectMoment = { selectedId = it },
                        onMapTap = { point -> if (adding) chosenPoint = route?.nearestPointTo(point, 30.0) },
                        modifier = Modifier.fillMaxSize())
                })
        }
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
            selected = group.any { it.id == selectedId })
    }
}

@Composable
internal fun WalkDiaryMapContent(
    scenes: List<DiaryScene>, selected: DiaryScene?, loading: Boolean, error: String?,
    onSelect: (DiaryScene) -> Unit, onClose: () -> Unit, onEdit: (DiaryScene) -> Unit,
    onPhoto: (WalkPhoto) -> Unit, onRetry: () -> Unit, onAdd: () -> Unit, onReview: () -> Unit,
    adding: Boolean = false, modifier: Modifier = Modifier, map: @Composable () -> Unit,
) {
    val scroll = rememberLazyListState()
    Column(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1.25f)) { map() }
        Surface(Modifier.fillMaxWidth().weight(1f), color = CardWhite) {
            Column(Modifier.padding(horizontal = 14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onAdd, enabled = !loading && error == null) { Text(if (adding) "위치 선택 취소" else "＋ 기록") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onReview, enabled = !loading && error == null) { Text("스토리보드 검토") }
                }
                if (adding) Text("동선에서 기록을 남길 위치를 눌러 주세요.", style = MaterialTheme.typography.bodySmall)
                if (error != null) Row { Text(error, Modifier.weight(1f)); TextButton(onClick = onRetry) { Text("다시 시도") } }
                if (loading) Text("장면을 불러오고 있어요.")
                else if (selected == null) {
                    Text("${scenes.size}개 장면 · 시간순", color = TextMuted, style = MaterialTheme.typography.labelMedium)
                    LazyColumn(state = scroll, modifier = Modifier.weight(1f)) {
                        itemsIndexed(scenes, key = { _, scene -> scene.id }) { index, scene ->
                            TextButton(onClick = { onSelect(scene) }, modifier = Modifier.fillMaxWidth()) {
                                Text("${index + 1}", Modifier.width(28.dp), fontWeight = FontWeight.Bold)
                                Column(Modifier.weight(1f)) {
                                    Text(scene.title, color = TextDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${formatWalkClock(scene.atMillis)}${if (scene.point == null) " · 위치 없는 장면" else ""}",
                                        style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                }
                            }
                        }
                    }
                } else {
                    val index = scenes.indexOfFirst { it.id == selected.id }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onClose) { Text("‹ 장면 목록") }
                        Text("장면 ${index + 1}", style = MaterialTheme.typography.labelMedium)
                    }
                    LazyColumn(Modifier.weight(1f)) {
                        item {
                            Text(selected.title, fontWeight = FontWeight.SemiBold)
                            Text(selected.body, style = MaterialTheme.typography.bodyMedium)
                            if (selected.point == null) Text("확인된 위치가 없어 지도에 점을 찍지 않았어요.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            if (selected.needsReview) Text("원본이 바뀌어 다시 검토가 필요한 장면이에요.", style = MaterialTheme.typography.bodySmall)
                            selected.photo?.let { p -> TextButton(onClick = { onPhoto(p) }) { Text("사진 보기") } }
                            if (selected.entryId != null) TextButton(onClick = { onEdit(selected) }) { Text("기록 편집") }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(enabled = index > 0, onClick = { scenes.getOrNull(index - 1)?.let(onSelect) }) { Text("이전") }
                        Text("${index + 1} / ${scenes.size}", Modifier.align(Alignment.CenterVertically), color = TextMuted)
                        TextButton(enabled = index in 0 until scenes.lastIndex, onClick = { scenes.getOrNull(index + 1)?.let(onSelect) }) { Text("다음") }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 780)
@Composable
private fun DiaryMapPreview() {
    val scene = DiaryScene("s/n", "s", 0, "벤치 옆에서 남긴 메모", "함께 걸었던 하루", null, "사용자 기록", entryId = "n")
    DaengsTheme { WalkDiaryMapContent(listOf(scene), scene, false, null, {}, {}, {}, {}, {}, {}, {},
        map = { Box(Modifier.fillMaxSize().background(PinkFaint), contentAlignment = Alignment.Center) { Text("산책 지도") } }) }
}
