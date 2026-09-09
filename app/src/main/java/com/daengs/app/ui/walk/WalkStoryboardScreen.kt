package com.daengs.app.ui.walk

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.DaengsApp
import com.daengs.app.pet.Pet
import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.WalkHistory
import com.daengs.app.walk.WalkSummary
import com.daengs.app.walk.diary.*
import com.daengs.app.walk.store.WalkStoryboardRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun WalkStoryboardScreen(sessionId: String, history: WalkHistory, pets: List<Pet>, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as DaengsApp
    val scope = rememberCoroutineScope()
    val entryFlow = remember(sessionId) { app.walkEntries.observe(sessionId) }
    val entries by entryFlow.collectAsState(initial = null)
    val analysis by remember(sessionId) { app.walkEntryDao.observeSceneAnalysis(sessionId) }.collectAsState(initial = null)
    val rawEntries by remember(sessionId) { app.walkEntryDao.observeEntries(sessionId) }.collectAsState(initial = emptyList())
    val photoState by remember(sessionId) { app.walkEntryDao.observePhotoSync(sessionId) }.collectAsState(initial = null)
    val photoRows by remember(sessionId) { app.walkEntryDao.observePhotos(sessionId) }.collectAsState(initial = emptyList())
    var analyzing by remember { mutableStateOf(false) }
    var walk by remember(sessionId) { mutableStateOf<WalkSummary?>(null) }
    var draft by remember(sessionId) { mutableStateOf<StoryboardDraft?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<StoryboardScene?>(null) }
    var original by remember { mutableStateOf<WalkEntry?>(null) }
    val owner = remember(sessionId) { app.tokenStore.load()?.appUserId.orEmpty() }
    suspend fun requireOwner() {
        check(app.tokenStore.load()?.appUserId.orEmpty() == owner &&
            app.walkEntryDao.session(sessionId)?.ownerId == owner) { "현재 계정의 산책이 아닙니다." }
    }
    LaunchedEffect(sessionId) {
        try {
            requireOwner()
            draft = StoryboardDraft.parse(app.walkEntryDao.storyboard(sessionId)?.payload)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = "검토 내용을 불러오지 못했어요. ${e.message.orEmpty()}"
        }
    }
    LaunchedEffect(sessionId, entries) {
        try {
            requireOwner()
            walk = history.sessionDetail(sessionId)?.summary
            if (walk == null) error = "산책 기록을 찾을 수 없어요."
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = e.message
        }
    }
    fun analyze(refresh: Boolean = false) {
        analyzing = true
        scope.launch {
            try {
                requireOwner()
                val auth = app.sessionProvider.freshSession() ?: error("로그인 후 분석할 수 있어요.")
                app.walkRuntime.sync.syncPendingSession(auth.accessToken, sessionId, includeStoryboard = false)
                app.walkEntryDao.session(sessionId)?.serverWalkId?.let {
                    app.walkStoryboardSync.sync(auth.accessToken, sessionId, it, refresh = refresh)
                }
                error = null
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error = "분석을 완료하지 못했어요. 기록 동기화 상태를 확인하고 다시 시도해 주세요."
            } finally { analyzing = false }
        }
    }
    LaunchedEffect(sessionId) {
        if (owner.isNotEmpty()) app.walkRuntime.delivery.enqueue(sessionId)
    }
    fun save(next: StoryboardDraft) {
        busy = true
        scope.launch {
            try {
                requireOwner()
                app.walkEntryDao.saveStoryboard(WalkStoryboardRow(sessionId, next.toJson()))
                draft = next; editing = null; error = null
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error = "저장하지 못했어요. 다시 시도해 주세요."
            } finally { busy = false }
        }
    }
    BackHandler { if (!busy) onBack() }
    val current = draft
    val summary = walk
    if (current == null || summary == null || entries == null) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
            TextButton(onClick = onBack) { Text("← 산책 상세") }
            Text(error ?: "스토리보드를 준비하고 있어요.")
        }
        return
    }
    val analysisView = storyboardAnalysisView(analysis, rawEntries, photoState, photoRows)
    val bundle = analysisView.bundle
    val sources = bundle?.scenes?.map { scene ->
        val id = scene.id.removePrefix("geo:")
        if (id == "start" || id == "end" || id.startsWith("entry:")) scene.copy(id = id) else scene
    }
    val scenes = if (sources != null) applyStoryboardEdits(sources, current)
        else storyboardScenes(summary, entries.orEmpty(), current).map { scene ->
            if (scene.id.startsWith("geo:")) scene.copy(evidence = "최신 분석을 기다리는 장면이에요. 작성한 문구는 보관돼요.")
            else scene
        }
    val notice = analysisView.notice
    val snapshot = storyboardSnapshot(sessionId, scenes, bundle?.title)
    val unresolved = scenes.any { it.available && !it.hidden && it.needsReview }
    StoryboardContent(scenes, busy, error,
        reviewed = current.reviewed == snapshot && (owner.isEmpty() || analysisView.canReview),
        changed = current.reviewed != null && current.reviewed != snapshot,
        canReview = !analyzing && (owner.isEmpty() || analysisView.canReview) && !unresolved && scenes.any { it.available && !it.hidden },
        onBack = onBack, onEdit = { editing = it },
        onToggle = { save(current.edit(it, hidden = !it.hidden)) },
        onAcknowledge = { save(current.edit(it, acknowledge = true)) },
        onOriginal = { scene -> original = entries.orEmpty().firstOrNull { "entry:${it.id}" == scene.id } },
        onReview = { save(current.copy(reviewed = snapshot)) }, connectionNotice = notice,
        onAnalyze = { analyze(refresh = analysisView.canReview) }, analyzing = analyzing,
        selectionNotice = bundle?.selection?.description(), petNames = pets.associate { it.id to it.name },
        diaryTitle = walkDiaryTitle(summary, bundle?.title))
    editing?.let { scene ->
        var title by remember(scene.id) { mutableStateOf(scene.title) }
        var body by remember(scene.id) { mutableStateOf(scene.sceneBody()) }
        AlertDialog(onDismissRequest = { if (!busy) editing = null },
            title = { Text("장면 문구 편집") }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("원본 행동·메모는 바뀌지 않아요.")
                    OutlinedTextField(title, { if (it.length <= 80) title = it },
                        label = { Text("제목") }, enabled = !busy)
                    OutlinedTextField(body, { if (it.length <= MAX_DIARY_SCENE_BODY_LENGTH) body = it },
                        label = { Text("설명·남길 이야기") }, minLines = 3, enabled = !busy)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }, confirmButton = {
                TextButton(enabled = !busy && title.isNotBlank(),
                    onClick = { save(current.edit(scene, title.trim(), body, bodyScope = SceneBodyScope.SCENE)) }) { Text("저장") }
            }, dismissButton = { TextButton(enabled = !busy, onClick = { editing = null }) { Text("취소") } })
    }
    original?.let { entry ->
        WalkEntryEditor(entries.orEmpty(), entry, pets.filter { it.id in summary.dogIds }, error, busy,
            onSave = { updated ->
                busy = true
                scope.launch {
                    try {
                        requireOwner(); app.walkEntries.save(updated)
                        app.walkRuntime.delivery.enqueue(sessionId); original = null; error = null
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        error = e.message
                    } finally { busy = false }
                }
            }, onDelete = { removed ->
                busy = true
                scope.launch {
                    try {
                        requireOwner(); app.walkEntries.delete(removed.id)
                        app.walkRuntime.delivery.enqueue(sessionId); original = null; error = null
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        error = e.message
                    } finally { busy = false }
                }
            }, onDismiss = { if (!busy) original = null })
    }
}

@Composable
internal fun StoryboardContent(
    scenes: List<StoryboardScene>, busy: Boolean, error: String?, reviewed: Boolean, changed: Boolean,
    canReview: Boolean, onBack: () -> Unit, onEdit: (StoryboardScene) -> Unit,
    onToggle: (StoryboardScene) -> Unit, onAcknowledge: (StoryboardScene) -> Unit,
    onOriginal: (StoryboardScene) -> Unit, onReview: () -> Unit,
    connectionNotice: String = "이 기기에 저장돼요. 환경·이동 분석 장면과 AI 일기 생성은 아직 연결되지 않았어요.",
    onAnalyze: (() -> Unit)? = null,
    analyzing: Boolean = false,
    selectionNotice: String? = null,
    petNames: Map<String, String> = emptyMap(),
    diaryTitle: String? = null,
) {
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
        item {
            TextButton(enabled = !busy, onClick = onBack) { Text("← 산책 상세") }
            Text("스토리보드 검토", style = MaterialTheme.typography.headlineSmall)
            diaryTitle?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            Text("시간순 장면을 확인하고 일기에 남길 내용을 골라보세요.")
            Text(connectionNotice,
                style = MaterialTheme.typography.bodySmall)
            selectionNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            onAnalyze?.let { action ->
                TextButton(enabled = !busy && !analyzing, onClick = action) { Text(if (analyzing) "일기 준비 중" else "일기 만들기 · 다시 시도") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        items(scenes, key = { it.id }) { scene ->
            var evidenceOpen by remember(scene.id) { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(formatWalkClock(scene.atMillis), style = MaterialTheme.typography.labelMedium)
                    Text(scene.title, style = MaterialTheme.typography.titleMedium)
                    scene.entryReference?.let { entry ->
                        Text(if (entry.isNote) "산책 전체 메모" else "대상 강아지: " +
                            if (entry.petId == null) "미지정" else petNames[entry.petId] ?: "이름 확인 필요",
                            style = MaterialTheme.typography.labelMedium)
                    }
                    if (scene.diary != null) DiarySceneText(scene.sceneBody())
                    else if (scene.body.isNotBlank()) Text(scene.body)
                    if (!scene.available) Text(scene.evidence, color = MaterialTheme.colorScheme.error)
                    else if (scene.needsReview) Text("원본이 바뀌었어요. 문구와 근거를 확인해 주세요.",
                        color = MaterialTheme.colorScheme.error)
                    if (scene.hidden) Text("이번 구성에서 숨긴 장면")
                    Row {
                        TextButton(enabled = !busy && scene.available, onClick = { onEdit(scene) }) { Text("문구 편집") }
                        TextButton(enabled = !busy && scene.available, onClick = { onToggle(scene) }) {
                            Text(if (scene.hidden) "복원" else "숨기기")
                        }
                        TextButton(onClick = { evidenceOpen = !evidenceOpen }) { Text("근거") }
                    }
                    if (evidenceOpen) {
                        Text(scene.evidence, style = MaterialTheme.typography.bodySmall)
                        if (scene.available && scene.id.startsWith("entry:"))
                            TextButton(enabled = !busy, onClick = { onOriginal(scene) }) { Text("원본 기록 수정") }
                    }
                    if (scene.available && scene.needsReview)
                        TextButton(enabled = !busy, onClick = { onAcknowledge(scene) }) { Text("변경 내용 확인했어요") }
                }
            }
        }
        item {
            if (changed) Text("검토 완료 후 구성이 바뀌었어요. 다시 확인해 주세요.")
            Button(enabled = !busy && canReview && !reviewed, onClick = onReview,
                modifier = Modifier.fillMaxWidth()) { Text(if (reviewed) "현재 구성 검토 완료" else "이 구성 검토 완료") }
            Text("검토 완료 시 현재 장면의 사본을 보관해요. 이후 편집해도 이전 검토본은 다음 완료 전까지 유지돼요.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StoryboardPreview() {
    MaterialTheme { StoryboardContent(listOf(
        StoryboardScene("start", 0, "산책 시작", "", "시작 시각", "1"),
        StoryboardScene("entry:1", 60000, "킁킁", "잠깐 쉬어간 순간", "직접 남긴 킁킁 기록", "2"),
        StoryboardScene("end", 120000, "산책 마무리", "이동거리 120m", "산책 요약", "3")),
        false, null, false, false, true, {}, {}, {}, {}, {}, {}) }
}
