package com.daengs.app.ui.walk

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.daengs.app.map.shell.MapHost
import com.daengs.app.map.shell.MapScene
import com.daengs.app.ui.theme.*
import com.daengs.app.walk.WalkHistory
import com.daengs.app.walk.WalkPhoto
import com.daengs.app.walk.WalkSummary
import com.daengs.app.walk.diary.*
import kotlinx.coroutines.CancellationException

@Composable
internal fun WalkDiaryMapScreen(
    walks: List<WalkSummary>, history: WalkHistory, sessionScope: String?,
    onScope: (String?) -> Unit, onOpen: (String) -> Unit, modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as DaengsApp
    val reader = remember(app) { WalkDiaryReader(app.walkEntryDao, app.walkPhotos) {
        app.tokenStore.load()?.appUserId.orEmpty()
    } }
    var data by remember(walks) { mutableStateOf<List<DiaryWalk>?>(null) }
    var error by remember(walks) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val cohortKey = walks.map { it.sessionId }.sorted().joinToString("|")
    var selectedId by rememberSaveable(cohortKey) { mutableStateOf<String?>(null) }
    var locationIds by rememberSaveable(cohortKey) { mutableStateOf<List<String>?>(null) }
    var unlocated by rememberSaveable(cohortKey) { mutableStateOf(false) }
    var photo by remember { mutableStateOf<WalkPhoto?>(null) }
    LaunchedEffect(walks, retry) {
        data = null; error = null
        try { reader.observe(walks).collect { data = it } }
        catch (e: Exception) {
            if (e is CancellationException) throw e
            data = emptyList()
            error = "장면을 불러오지 못했어요. 다시 시도해 주세요."
        }
    }
    val allScenes = data.orEmpty().flatMap { it.scenes }
        .sortedWith(compareBy<DiaryScene> { it.atMillis }.thenBy { it.id })
    val scenes = allScenes.filter {
        (sessionScope == null || it.sessionId == sessionScope) &&
            (locationIds == null || it.id in locationIds.orEmpty()) && (!unlocated || it.point == null)
    }
    val selected = scenes.firstOrNull { it.id == selectedId }
    val selectedSession = selected?.sessionId ?: sessionScope
    var completed by remember(selectedSession) { mutableStateOf(CompletedRouteLayerState()) }
    var routeBounds by remember(selectedSession) { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var routeError by remember(selectedSession) { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedSession, walks, retry) {
        completed = CompletedRouteLayerState(); routeBounds = emptyList(); routeError = null
        if (selectedSession != null && walks.any { it.sessionId == selectedSession }) {
            try {
                val detail = history.sessionDetail(selectedSession)
                completed = detail?.route?.toCompletedRouteLayerState(formatTime = ::formatWalkClock)
                    ?: CompletedRouteLayerState()
                routeBounds = detail?.route?.bounds.orEmpty().ifEmpty { listOfNotNull(detail?.summary?.anchor) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                routeError = "이 산책의 경로를 불러오지 못했어요."
            }
        }
    }
    LaunchedEffect(walks) {
        if (sessionScope != null && walks.none { it.sessionId == sessionScope }) onScope(null)
    }
    BackHandler(selected != null) { selectedId = null }
    fun all() { onScope(null); selectedId = null; locationIds = null; unlocated = false }
    val groups = remember(allScenes) { diaryLocationGroups(allScenes) }
    val mapScenes = groups.map { group ->
        MomentMarkerState(group.first().id, requireNotNull(group.first().point),
            if (group.size > 1) "${group.size}개 장면" else group.first().title,
            selected = group.any { it.id == selectedId })
    }
    val bounds = routeBounds.ifEmpty {
        walks.flatMap { it.segments.flatMap { path -> path.map { sample -> sample.point } } } +
            allScenes.mapNotNull { it.point } + walks.mapNotNull { it.anchor }
    }
    WalkDiaryMapContent(
        scenes, selected, sessionScope != null || locationIds != null, unlocated,
        loading = data == null && error == null,
        error = error ?: routeError,
        notice = data.orEmpty().firstOrNull { it.summary.sessionId == selectedSession }?.notice,
        onAll = { all() }, onUnlocated = { all(); unlocated = true },
        onSelect = { selectedId = it.id }, onClose = { selectedId = null }, onOpen = onOpen,
        onPhoto = { photo = it }, onRetry = { retry++ }, modifier = modifier,
        map = {
            if (bounds.isEmpty()) Box(Modifier.fillMaxSize().background(CreamBg).padding(24.dp)) {
                Text(if (data == null) "산책 위치를 불러오고 있어요." else "표시할 위치가 없어요. 아래에서 장면을 볼 수 있어요.")
            } else if (!LocalInspectionMode.current) MapHost(
                scene = MapScene(moments = mapScenes, completedRoute = completed),
                searchOrigin = null, followDevice = false, fitBounds = bounds,
                centerOn = selected?.point, onCameraIdle = {}, onCameraGesture = {}, onSelectPlace = {},
                onSelectMoment = { id -> groups.firstOrNull { it.first().id == id }?.let { group ->
                    onScope(null); unlocated = false; locationIds = group.map { it.id }; selectedId = group.first().id
                } }, modifier = Modifier.fillMaxSize(),
            )
        },
    )
    photo?.let { current -> WalkPhotoDialog(current, app.walkPhotos::delete, { photo = null }) }
}

@Composable
internal fun WalkDiaryMapContent(
    scenes: List<DiaryScene>, selected: DiaryScene?, scoped: Boolean, unlocated: Boolean,
    loading: Boolean, error: String?, notice: String?,
    onAll: () -> Unit, onUnlocated: () -> Unit, onSelect: (DiaryScene) -> Unit,
    onClose: () -> Unit, onOpen: (String) -> Unit, onPhoto: (WalkPhoto) -> Unit,
    onRetry: () -> Unit, modifier: Modifier = Modifier, map: @Composable () -> Unit,
) {
    val listState = rememberLazyListState()
    Column(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1f)) { map() }
        Surface(color = CardWhite, modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!scoped && !unlocated, onAll, label = { Text("모든 장면") })
                    FilterChip(unlocated, onUnlocated, label = { Text("위치 없는 장면") })
                }
                if (error != null) Row {
                    Text(error, Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text("다시 시도") }
                }
                if (loading) {
                    Text("장면을 불러오고 있어요.")
                } else if (selected == null) {
                    Text("${if (scoped) "선택한 범위 · " else ""}${scenes.size}개 장면 · 오래된 순",
                        style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    if (scenes.isEmpty() && error == null) Text("이 조건에 남아 있는 장면이 없어요.", Modifier.padding(vertical = 12.dp))
                    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(scenes, key = { it.id }) { scene ->
                            OutlinedCard(onClick = { onSelect(scene) }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("${formatWalkDay(scene.atMillis)} · ${formatWalkClock(scene.atMillis)}",
                                        style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                    Text(scene.title, fontWeight = FontWeight.SemiBold)
                                    if (scene.body.isNotBlank()) Text(scene.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (scene.point == null) Text("위치 없는 장면", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                } else {
                    DiarySelectedCard(selected, scenes, notice, onSelect, onClose, onOpen, onPhoto)
                }
            }
        }
    }
}

@Composable
private fun DiarySelectedCard(
    selected: DiaryScene, scenes: List<DiaryScene>, notice: String?,
    onSelect: (DiaryScene) -> Unit, onClose: () -> Unit, onOpen: (String) -> Unit,
    onPhoto: (WalkPhoto) -> Unit,
) {
    var evidence by remember(selected.id) { mutableStateOf(false) }
    val index = scenes.indexOfFirst { it.id == selected.id }
    Column {
        Row {
            Text(selected.title, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 2)
            TextButton(onClick = onClose) { Text("장면 목록") }
        }
        LazyColumn(Modifier.weight(1f)) {
            item {
                Text("${formatWalkDay(selected.atMillis)} · ${formatWalkClock(selected.atMillis)}",
                    color = TextMuted, style = MaterialTheme.typography.labelMedium)
                if (selected.body.isNotBlank()) Text(selected.body)
                if (selected.point == null) Text("확인된 위치가 없어 지도에 점을 찍지 않았어요.")
                if (selected.needsReview) Text("원본이 바뀌었어요. 작성한 문구를 확인해 주세요.", color = MaterialTheme.colorScheme.error)
                selected.photo?.let { image -> TextButton(onClick = { onPhoto(image) }) { Text("사진 보기") } }
                TextButton(onClick = { evidence = !evidence }) { Text(if (evidence) "근거 닫기" else "근거 보기") }
                if (evidence) { Text(selected.evidence); notice?.let { Text(it) } }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(enabled = index > 0, onClick = { scenes.getOrNull(index - 1)?.let(onSelect) }) { Text("이전") }
            TextButton(onClick = { onOpen(selected.sessionId) }) { Text("원본 산책") }
            TextButton(enabled = index in 0 until scenes.lastIndex,
                onClick = { scenes.getOrNull(index + 1)?.let(onSelect) }) { Text("다음") }
        }
    }
}

@Preview(device = "spec:width=411dp,height=891dp", showBackground = true)
@Composable
private fun WalkDiaryMapPreview() {
    val scene = DiaryScene("s/entry:a", "s", 1_788_324_720_000L, "잠깐 쉬어간 곳",
        "벤치 옆에서 잠시 쉬었다.", GeoPoint(37.5, 127.0), "직접 남긴 기록")
    DaengsTheme {
        WalkDiaryMapContent(listOf(scene), scene, false, false, false, null, null,
            {}, {}, {}, {}, {}, {}, {}, map = {
                Box(Modifier.fillMaxSize().background(PinkFaint).padding(24.dp)) { Text("산책 지도") }
            })
    }
}
