package com.daengs.app.ui.walk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import com.daengs.app.map.provider.naver.LocalWalkMapDiagnostics
import com.daengs.app.map.provider.naver.WalkMapDiagnostics
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.auth.AccountScope
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.walk.records.RetainedWalkRecords
import com.daengs.app.ui.walk.records.WalkRecordsScreen
import com.daengs.app.ui.walk.records.rememberWalkRecordsRouteState
import com.daengs.app.walk.records.WalkRecord

/** Uses the real explorer and map renderer with an explicitly labelled synthetic source. */
class WalkRecordsLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DaengsTheme { WalkRecordsLab(onBack = { finish() }) } }
    }
}

@Composable
private fun WalkRecordsLab(onBack: () -> Unit = {}) {
    val diagnostics = remember { WalkMapDiagnostics() }
    var showDiagnostics by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalWalkMapDiagnostics provides diagnostics) {
        Box(Modifier.fillMaxSize()) {
            WalkRecordsLabContent(onBack)
            Surface(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
                shape = MaterialTheme.shapes.small) {
                TextButton(onClick = { showDiagnostics = true }, Modifier.testTag("records-layer-diagnostics")) {
                    Text("층 진단")
                }
            }
        }
    }
    if (showDiagnostics) WalkLayerDiagnosticsDialog(diagnostics) { showDiagnostics = false }
}

@Composable
private fun WalkRecordsLabContent(onBack: () -> Unit) {
    val state = rememberWalkRecordsRouteState(AccountScope("records-lab", 0))
    val opened = WalkRecordsLabFixture.records.firstOrNull { it.summary.sessionId == state.openedSessionId }
    if (opened != null) {
        // Unmount the actual MapView, matching production navigation while keeping synthetic data.
        SampleWalkRecordDialog(opened, onDismiss = state::closeDetail)
    } else RetainedWalkRecords(state) {
        WalkRecordsScreen(source = WalkRecordsLabFixture, pets = WalkRecordsLabFixture.pets,
            onBack = { state.captureRecords(); onBack() }, onOpen = state::open,
            sampleLabel = "가상 산책 12회 · 화면 시연", today = WalkRecordsLabFixture.today)
    }
}

@Composable
private fun WalkLayerDiagnosticsDialog(state: WalkMapDiagnostics, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("지도 층 진단 · 개발용") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("지도에서 읽어온 실제 적용값이에요. 좌표와 계정 정보는 수집하지 않아요.")
            listOf(
                Triple("배경 지도", state.showBase, { value: Boolean -> state.showBase = value }),
                Triple("셀로판", state.showTraces, { value: Boolean -> state.showTraces = value }),
                Triple("동선", state.showRoute, { value: Boolean -> state.showRoute = value }),
                Triple("마커", state.showMarkers, { value: Boolean -> state.showMarkers = value }),
            ).forEach { (label, enabled, change) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(enabled, change, Modifier.testTag("layer-toggle-$label"))
                    Text(label)
                }
            }
            state.details.toSortedMap().values.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            state.overlays.values.groupBy { it.role to it.globalZ }.toList().sortedBy { it.first.second }
                .forEach { (key, readings) ->
                    Text("${key.first} · z ${key.second} · ${readings.size}개", style = MaterialTheme.typography.titleSmall)
                    readings.map { it.description }.distinct().take(4).forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            if (state.overlays.isEmpty()) Text("현재 붙어 있는 지도 오버레이 없음")
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("지도 보기") } })
}

@Preview(showBackground = true)
@Composable
private fun WalkLayerDiagnosticsPreview() {
    DaengsTheme { WalkLayerDiagnosticsDialog(remember { WalkMapDiagnostics() }, {}) }
}

@Composable
private fun SampleWalkRecordDialog(record: WalkRecord, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(record.title.orEmpty()) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            WalkRouteThumbnail(record.summary, Modifier.fillMaxWidth().height(160.dp))
            Text(formatWalkDay(record.summary.startedAtMillis))
            Text("${formatWalkDuration(record.summary.activeDurationMillis)} · ${formatWalkDistance(record.summary.distanceMeters)}")
            record.notes.forEach { Text(it) }
            Text("가상의 산책 기록이에요.")
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("목록으로") } })
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WalkRecordsLabPreview() { DaengsTheme { WalkRecordsLab() } }

@Preview(showBackground = true)
@Composable
private fun SampleWalkRecordPreview() {
    DaengsTheme { SampleWalkRecordDialog(WalkRecordsLabFixture.records.first(), {}) }
}
