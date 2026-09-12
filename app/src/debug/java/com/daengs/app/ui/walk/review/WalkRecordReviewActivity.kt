package com.daengs.app.ui.walk.review

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.BuildConfig
import com.daengs.app.auth.AuthApi
import com.daengs.app.auth.loginWithKakao
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.walk.WalkRouteReviewContent
import com.daengs.app.walk.sync.RemoteWalk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class WalkRecordReviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as? WalkRecordReviewApplication ?: run { finish(); return }
        val source = WalkRecordReviewSource(app.sessions, File(noBackupFilesDir, "real-record-review"))
        setContent { DaengsTheme {
            val scope = rememberCoroutineScope()
            var busy by remember { mutableStateOf(false) }
            var notice by remember { mutableStateOf("원래 앱과 같은 계정으로 로그인한 뒤 기록을 선택해 주세요.") }
            var walks by remember { mutableStateOf(emptyList<RemoteWalk>()) }
            var selected by remember { mutableStateOf<ReviewRecord?>(null) }
            fun run(operation: suspend () -> Unit) {
                if (busy) return
                busy = true
                scope.launch {
                    try { operation() }
                    catch (e: Exception) {
                        if (e is CancellationException) throw e
                        notice = "확인하지 못했어요 (${e.javaClass.simpleName}). 로그인 또는 서버 응답을 확인해 주세요."
                        // No exception messages or authentication payloads in the exported report.
                        File(noBackupFilesDir, "review-error.txt").writeText(e.javaClass.name)
                    } finally { busy = false }
                }
            }
            BackHandler(selected != null) { selected = null }
            val record = selected
            if (record != null) key(record.detail.summary.sessionId) {
                WalkRouteReviewContent(record.detail, record.scenes,
                    if (record.folder.isEmpty()) "가상 기록 · 왕복 / 공백 / 제외 이동 / 작은 흔들림"
                        else "실제 서버 기록 · 기존 앱 정책 비교값 · ${record.detail.observations.size}개 관측",
                    onBack = { selected = null })
            } else ReviewControls(busy, notice, walks, onLogin = { run {
                val login = loginWithKakao(this@WalkRecordReviewActivity).getOrThrow()
                app.sessions.save(AuthApi.loginWithKakao(login.idToken, login.nonce).getOrThrow())
                walks = source.list()
                notice = "${walks.size}건을 찾았어요. 산책을 선택하면 저장된 원본과 장면만 읽어요."
            } }, onList = { run {
                walks = source.list()
                notice = "${walks.size}건을 찾았어요."
            } }, onFixture = { selected = recordContextMapFixture() }, onWalk = { walk -> run {
                selected = source.read(walk)
                notice = "원본과 비교 보고서를 검토 앱 안에 보관했어요."
            } })
        } }
    }
}

@Composable
private fun ReviewControls(busy: Boolean, notice: String, walks: List<RemoteWalk>,
    onLogin: () -> Unit, onList: () -> Unit, onWalk: (RemoteWalk) -> Unit,
    onFixture: () -> Unit = {},
) {
    val time = remember { DateTimeFormatter.ofPattern("M/d HH:mm:ss").withZone(ZoneId.of("Asia/Seoul")) }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
        .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("실제 산책 대조", style = MaterialTheme.typography.headlineSmall)
        Text(BuildConfig.API_BASE_URL, style = MaterialTheme.typography.labelSmall)
        Text("저장된 기록을 읽고, 같은 좌표로 동선과 장면을 비교해요.")
        Button(onClick = onLogin, enabled = !busy) { Text("카카오 계정으로 기록 확인") }
        OutlinedButton(onClick = onList, enabled = !busy) { Text("로그인한 계정의 목록 읽기") }
        Text(notice)
        OutlinedButton(onClick = onFixture, enabled = !busy) { Text("가상 반례 지도 확인") }
        if (busy) CircularProgressIndicator()
        walks.forEach { walk ->
            OutlinedButton(onClick = { onWalk(walk) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("${time.format(Instant.ofEpochMilli(walk.startedAtMillis))} · ${(walk.endedAtMillis - walk.startedAtMillis) / 1000}초")
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ReviewControlsPreview() { DaengsTheme {
    ReviewControls(false, "원래 앱과 같은 계정으로 기록을 확인해 주세요.", emptyList(), {}, {}, {})
} }
