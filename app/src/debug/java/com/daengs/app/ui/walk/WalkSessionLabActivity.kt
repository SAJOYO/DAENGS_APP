package com.daengs.app.ui.walk

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.BuildConfig
import com.daengs.app.DaengsApp
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.*
import com.daengs.app.walk.store.WalkEntryRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.hypot

/** Emulator-only saved fixtures; opens the production detail, Reader, editor and SDK MapView. */
class WalkSessionLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DaengsApp
        // Never seed a signed-in account or an API-configured application, including on a phone.
        val allowed = Build.HARDWARE in setOf("ranchu", "goldfish") &&
            app.tokenStore.load() == null && BuildConfig.API_BASE_URL.isBlank()
        val scenario = intent.getStringExtra("case").takeIf { it in listOf("loop", "gap") } ?: "loop"
        setContent {
            DaengsTheme {
                if (allowed) SessionLab(app, scenario, intent.getBooleanExtra("completion", false))
                else Text("로그인과 API 설정이 없는 에뮬레이터에서만 가상 산책을 확인할 수 있어요.", Modifier.padding(24.dp))
            }
        }
    }
}

@Composable
private fun SessionLab(app: DaengsApp, scenario: String, completion: Boolean) {
    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var walks by remember { mutableStateOf<List<WalkSummary>>(emptyList()) }
    var opened by rememberSaveable { mutableStateOf<String?>("sdk-session-$scenario-v1") }
    var origin by rememberSaveable {
        mutableStateOf(if (completion) WalkSessionOrigin.COMPLETION else WalkSessionOrigin.RECORDS)
    }
    LaunchedEffect(Unit) {
        try {
            withContext(Dispatchers.IO) { seedSessionLab(app) }
            walks = listOf("loop", "gap").mapNotNull { app.walkRuntime.history.detail("sdk-session-$it-v1") }
            ready = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        SessionLabLabel()
        if (failed) Text("가상 산책 준비에 실패했어요.", Modifier.padding(20.dp))
        else if (!ready) Text("가상 산책을 준비하고 있어요.", Modifier.padding(20.dp))
        else opened?.let { id ->
            key(id, origin) {
                WalkSessionDetailRoute(id, app.walkRuntime.history, onBack = { opened = null },
                    origin = origin, modifier = Modifier.weight(1f))
            }
        } ?: Column(Modifier.weight(1f)) {
            TextButton(onClick = { origin = WalkSessionOrigin.COMPLETION; opened = walks.first().sessionId }) {
                Text("종료 직후 상세 열기")
            }
            WalkHistoryPageContent(walks, 1, false, false, {}, {}, {
                origin = WalkSessionOrigin.RECORDS; opened = it
            }, modifier = Modifier.weight(1f), titles = mapOf(
                "sdk-session-loop-v1" to "가상 산책 · 같은 길 3회 통과",
                "sdk-session-gap-v1" to "가상 산책 · 교차와 GPS 공백"))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SessionLabLabel() {
    Text("가상 동선 · SDK 확인용", Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp))
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SessionLabPreview() {
    DaengsTheme {
        Column {
            SessionLabLabel()
            WalkDiaryMapContent(emptyList(), null, false, null, {}, {}, {}, {}, {}, {},
                title = "가상 산책 · SDK 확인", explorerPanel = { Text("동선 탐색") },
                summaryContent = { WalkSessionSummary(WalkRecordsLabFixture.records.first().summary) },
                map = { Spacer(Modifier.fillMaxSize()) })
        }
    }
}

private suspend fun seedSessionLab(app: DaengsApp) {
    val log = app.walkRuntime.log
    val dao = app.walkEntryDao
    val scenarios = mapOf(
        "loop" to listOf(listOf(0 to 0, 140 to 0, 140 to 100, 0 to 100, 0 to 0,
            140 to 0, 0 to 0, -50 to -60)),
        "gap" to listOf(listOf(-80 to -60, 80 to 60, -80 to 60, 80 to -60),
            listOf(180 to -60, 220 to 40, 130 to 100)),
    )
    for ((name, chains) in scenarios) {
        val id = "sdk-session-$name-v1"
        if (dao.session(id)?.endedAtMillis != null) continue
        val start = java.time.Instant.parse("2026-09-11T08:00:00Z").toEpochMilli()
        log.openSession(RecordedSession(id, startedAtMillis = start))
        var seq = 0
        var elapsed = 0L
        for ((chain, corners) in chains.withIndex()) {
            if (chain > 0) elapsed += 90_000
            for ((leg, pair) in corners.zipWithNext().withIndex()) {
                val (a, b) = pair
                val count = ceil(hypot((b.first - a.first).toDouble(), (b.second - a.second).toDouble()) / 3).toInt()
                for (step in (if (leg == 0) 0 else 1)..count) {
                    val fraction = step.toDouble() / count
                    val x = a.first + (b.first - a.first) * fraction
                    val y = a.second + (b.second - a.second) * fraction
                    log.append(id, RecordedFix(seq++, chain, start + elapsed,
                        37.5445 + y / 111_195, 127.0377 + x / 88_170, 3f, false))
                    elapsed += listOf(4000L, 2000L, 1500L)[leg % 3]
                }
            }
        }
        val note = WalkEntry("$id-note", id, WalkMomentType.NOTE, start + 120_000,
            note = "가상 산책에서 남긴 메모예요. 수정 후 다시 열어 보세요.")
        dao.insertEntry(WalkEntryRow(note.id, id, note.toJson().toString(), 1, "sdk-lab", false))
        log.closeSession(id, start + elapsed)
        val board = requireNotNull(dao.prepareLocalDiary(id, ""))
        dao.publishDiaryBase(id, board.deadlineAtMillis)
    }
}
