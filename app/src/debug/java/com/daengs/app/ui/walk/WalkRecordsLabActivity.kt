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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.walk.records.WalkRecordsScreen
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
    var openedId by rememberSaveable { mutableStateOf<String?>(null) }
    WalkRecordsScreen(source = WalkRecordsLabFixture, pets = WalkRecordsLabFixture.pets,
        onBack = onBack, onOpen = { openedId = it },
        sampleLabel = "가상 산책 12회 · 화면 시연", today = WalkRecordsLabFixture.today)
    WalkRecordsLabFixture.records.firstOrNull { it.summary.sessionId == openedId }?.let { record ->
        SampleWalkRecordDialog(record, onDismiss = { openedId = null })
    }
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
