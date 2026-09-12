package com.daengs.app.ui.walk

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import com.daengs.app.BuildConfig
import com.daengs.app.ui.theme.*
import com.daengs.app.walk.diary.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import java.io.IOException
import kotlin.coroutines.coroutineContext

@Composable
internal fun DiarySlotPreviewScreen(sessionId: String, onBack: () -> Unit) {
    if (!BuildConfig.DEBUG) return
    val app = LocalContext.current.applicationContext as DaengsApp
    val api = remember { DiarySlotPreviewApi() }
    key(sessionId) {
        DiarySlotPreviewBrowser(onBack, load = {
            loadDiarySlotPreview(sessionId, { app.tokenStore.load()?.appUserId },
                app.sessionProvider::freshSession, app.walkEntryDao::session,
                sync = { token, id -> app.walkRuntime.sync.syncPendingSession(token, id, includeStoryboard = false) },
                fetch = api::generate)
        })
    }
}

/** Opening/recomposing never calls the model. One button press makes one request. */
@Composable
internal fun DiarySlotPreviewBrowser(onBack: () -> Unit, load: suspend () -> DiarySlotPreview) {
    var request by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<DiarySlotPreview?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val latestLoad by rememberUpdatedState(load)
    LaunchedEffect(request) {
        if (request == 0) return@LaunchedEffect
        try {
            val loaded = latestLoad()
            coroutineContext.ensureActive()
            result = loaded
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            coroutineContext.ensureActive()
            error = when (failure) {
                is DiarySlotPreviewException -> failure.message
                is IOException -> "서버에 연결하지 못했어요. 연결을 확인하고 다시 시도해 주세요."
                is IllegalStateException -> failure.message
                else -> "미리보기 결과를 읽지 못했어요. 잠시 뒤 다시 시도해 주세요."
            }
        } finally { loading = false }
    }
    BackHandler(onBack = onBack)
    DiarySlotPreviewContent(result, loading, error, onBack, onGenerate = {
        if (!loading) { loading = true; result = null; error = null; request++ }
    })
}

@Composable
internal fun DiarySlotPreviewContent(
    result: DiarySlotPreview?, loading: Boolean, error: String?, onBack: () -> Unit, onGenerate: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(CreamBg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row {
            TextButton(onClick = onBack) { Text("‹ 산책으로") }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text("개발용 일기 미리보기", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text("생성 문장과 선정 자료를 확인하는 개발용 화면이에요. 결과는 원래 일기에 저장되지 않아요.", color = TextMuted)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onGenerate, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                    Text(when { loading -> "미리보기 만드는 중"; error != null -> "다시 시도";
                        result != null -> "다시 만들기"; else -> "미리보기 만들기" })
                }
            }
            if (loading) item {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("산책 기록을 서버와 맞추고 문장을 만들고 있어요.", Modifier.padding(top = 8.dp))
            }
            if (error != null) item { Text(error, color = MaterialTheme.colorScheme.error) }
            if (result != null) {
                item {
                    Text(result.title, style = MaterialTheme.typography.titleLarge)
                    if (result.policyVersion.isNotBlank()) Text("슬롯 정책: ${result.policyVersion}",
                        style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    Text(when (result.modelStatus) {
                        "accepted" -> "배경 문장을 더한 미리보기예요."
                        "not_requested" -> "배경 문장을 생성하지 않은 기본 장면이에요."
                        else -> if (result.failureCode == "budget_exceeded")
                            "자료가 많아 이번에는 배경 문장을 만들지 못했어요. 기본 장면을 보여드려요."
                            else "배경 문장을 만들지 못해 기본 장면을 보여드려요. 다시 시도할 수 있어요."
                    }, color = TextMuted)
                    if (result.contextPending) Text("주변 자료를 준비 중이라 일부 배경이 비어 있을 수 있어요. 잠시 뒤 다시 만들어 보세요.",
                        Modifier.padding(top = 8.dp), color = TextMuted)
                    if (result.excludedBackgroundCount > 0) Text("현재 장면에 사용할 수 없는 주변 자료 ${result.excludedBackgroundCount}건은 제외했어요.",
                        Modifier.padding(top = 8.dp), color = TextMuted)
                }
                items(result.scenes, key = { it.id }) { scene -> DiarySlotSceneCard(scene) }
            }
        }
    }
}

@Composable
private fun DiarySlotSceneCard(scene: DiarySlotScene) {
    var expanded by remember(scene) { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(scene.title, style = MaterialTheme.typography.titleMedium)
            Text(scene.body, style = MaterialTheme.typography.bodyLarge)
            Text(listOf("space" to "공간", "environment" to "환경", "motion" to "동선").joinToString(" · ") { (part, label) ->
                "$label ${scene.evidence.count { it.part == part }}"
            }, style = MaterialTheme.typography.labelMedium, color = TextMuted)
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "자료 접기" else "장면 자료 보기") }
            if (expanded) {
                if (scene.body != scene.baseBody) {
                    Text("배경을 더하기 전", style = MaterialTheme.typography.labelLarge)
                    Text(scene.baseBody)
                    HorizontalDivider()
                }
                if (scene.evidence.isEmpty() && scene.locationReference == null) Text("이 장면에 연결된 배경 자료가 없어요.", color = TextMuted)
                (scene.evidence + listOfNotNull(scene.locationReference)).forEach { evidence ->
                    var rawExpanded by remember(evidence) { mutableStateOf(false) }
                    Text(slotEvidenceTitle(evidence.role) + if (evidence.id in scene.citations) " · 문장에 인용" else " · 선택된 자료",
                        style = MaterialTheme.typography.labelLarge)
                    Text(slotEvidenceDescription(evidence), style = MaterialTheme.typography.bodyMedium)
                    evidence.raw?.let { raw ->
                        TextButton(onClick = { rawExpanded = !rawExpanded }) {
                            Text(if (rawExpanded) "근거 원문 접기" else "근거 원문 보기")
                        }
                        if (rawExpanded) Text(raw, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun DiarySlotResultPreview() {
    DaengsTheme {
        DiarySlotPreviewContent(DiarySlotPreview("sample", "저녁 산책", "preview", "accepted", null, false, 0,
            listOf(DiarySlotScene("note", "벤치 옆에서", "가까운 공원 곁에서 산책 기록을 남겼다.\n\n물을 마셨다.", "물을 마셨다.",
                listOf(DiarySlotEvidence("park", "space", "scene_registered_point_distance", "{\"name\":\"동네공원\",\"distance_m\":40}")),
                citations = setOf("park")))), false, null, {}, {})
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 640)
@Composable
private fun DiarySlotLoadingPreview() {
    DaengsTheme { DiarySlotPreviewContent(null, true, null, {}, {}) }
}
