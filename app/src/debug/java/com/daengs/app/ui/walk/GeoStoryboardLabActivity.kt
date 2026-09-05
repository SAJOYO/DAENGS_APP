package com.daengs.app.ui.walk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.diary.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Debug-only, login-free review of geo exports. All data stays separate from real walks. */
class GeoStoryboardLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = GeoStoryboardLabStore(File(noBackupFilesDir, "geo-storyboard-lab"))
        setContent {
            var bundle by remember { mutableStateOf<GeoStoryboardBundle?>(null) }
            var draft by remember { mutableStateOf(StoryboardDraft()) }
            var error by remember { mutableStateOf<String?>(null) }
            var busy by remember { mutableStateOf(false) }
            var editing by remember { mutableStateOf<StoryboardScene?>(null) }
            val scope = rememberCoroutineScope()
            fun loadBundle(read: () -> String) {
                busy = true
                scope.launch {
                    try {
                        val saved = withContext(Dispatchers.IO) {
                            val next = GeoStoryboardBundle.parse(read())
                            require(next.synthetic) { "이 화면은 합성 산책 검토용이에요." }
                            val edits = store.load(next.sessionId)?.draft ?: StoryboardDraft()
                            store.save(next, edits)
                            GeoStoryboardLabStore.Saved(next, edits)
                        }
                        bundle = saved.bundle; draft = saved.draft; error = null; editing = null
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        error = "불러오지 못했어요. 기존 구성은 유지돼요. ${e.message.orEmpty()}"
                    } finally { busy = false }
                }
            }
            fun save(next: StoryboardDraft) {
                val current = bundle ?: return
                busy = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { store.save(current, next) }
                        draft = next; editing = null; error = null
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        error = "저장하지 못했어요. 다시 시도해 주세요."
                    } finally { busy = false }
                }
            }
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) loadBundle {
                    val bytes = contentResolver.openInputStream(uri)?.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (output.size() <= 1_000_000) {
                            val count = input.read(buffer, 0, minOf(buffer.size, 1_000_001 - output.size()))
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray() }
                        ?: error("파일을 열 수 없어요.")
                    require(bytes.size <= 1_000_000) { "최대 1MB 파일을 선택해 주세요." }
                    bytes.toString(Charsets.UTF_8)
                }
            }
            LaunchedEffect(Unit) {
                busy = true
                try {
                    val saved = withContext(Dispatchers.IO) { store.latest() }
                    if (saved != null) { bundle = saved.bundle; draft = saved.draft }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    error = "이전 검토본을 읽지 못했어요. 예시나 파일을 선택해 주세요."
                } finally { busy = false }
            }
            DaengsTheme {
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    Text("geo 장면 검토 · 합성 산책", Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("pinless" to "핀 없음", "clustered" to "핀 쏠림", "gap" to "GPS 공백",
                            "movement" to "속도 변화", "updated" to "정정·삭제 예시").forEach { (file, label) ->
                            TextButton(enabled = !busy, onClick = {
                                loadBundle { assets.open("storyboard/$file.json").bufferedReader().use { it.readText() } }
                            }) { Text(label) }
                        }
                        TextButton(enabled = !busy, onClick = { picker.launch(arrayOf("application/json", "text/plain")) }) {
                            Text("JSON 불러오기")
                        }
                    }
                    Text("실제 산책과 분리된 예시예요. 같은 세션의 새 JSON은 편집·숨김을 유지하며 원본을 갱신해요.",
                        Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
                    val current = bundle
                    if (current == null) {
                        Text(error ?: if (busy) "불러오는 중" else "위에서 예시를 선택해 주세요.", Modifier.padding(16.dp))
                        TextButton(onClick = { finish() }) { Text("닫기") }
                    } else {
                        val scenes = applyStoryboardEdits(current.scenes, draft)
                        val snapshot = storyboardSnapshot(current.sessionId, scenes)
                        Box(Modifier.weight(1f)) {
                            StoryboardContent(scenes, busy, error,
                                reviewed = draft.reviewed == snapshot,
                                changed = draft.reviewed != null && draft.reviewed != snapshot,
                                canReview = scenes.any { it.available && !it.hidden } &&
                                    scenes.none { it.available && !it.hidden && it.needsReview },
                                onBack = { finish() }, onEdit = { editing = it },
                                onToggle = { save(draft.edit(it, hidden = !it.hidden)) },
                                onAcknowledge = { save(draft.edit(it, acknowledge = true)) },
                                onOriginal = {}, onReview = { save(draft.copy(reviewed = snapshot)) },
                                connectionNotice = "geo가 계산한 장면·환경 근거를 불러왔어요. 자동 지점은 행동 기록을 늘리지 않아요. AI 일기는 미연결 상태예요.")
                        }
                    }
                    editing?.let { scene ->
                        var title by remember(scene.id) { mutableStateOf(scene.title) }
                        var body by remember(scene.id) { mutableStateOf(scene.body) }
                        AlertDialog(onDismissRequest = { if (!busy) editing = null },
                            title = { Text("장면 문구 편집") }, text = {
                                Column {
                                    OutlinedTextField(title, { if (it.length <= 80) title = it }, label = { Text("제목") }, enabled = !busy)
                                    OutlinedTextField(body, { if (it.length <= 2000) body = it }, label = { Text("내용") }, minLines = 3, enabled = !busy)
                                    error?.let { Text(it) }
                                }
                            }, confirmButton = {
                                TextButton(enabled = !busy && title.isNotBlank(),
                                    onClick = { save(draft.edit(scene, title.trim(), body.trim())) }) { Text("저장") }
                            }, dismissButton = { TextButton(enabled = !busy, onClick = { editing = null }) { Text("취소") } })
                    }
                }
            }
        }
    }
}
