package com.daengs.app.ui.walk.records

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextMuted
import com.daengs.app.ui.walk.WalkPhotoDialog
import com.daengs.app.ui.walk.reading.DiaryOriginalActionReading
import com.daengs.app.ui.walk.reading.DiarySceneReading
import com.daengs.app.walk.WalkPhoto
import com.daengs.app.walk.diary.*
import com.daengs.app.walk.records.WalkBehaviorRecord
import com.daengs.app.walk.records.WalkRecordsSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

private sealed interface RecordReadingState {
    data object Loading : RecordReadingState
    data class Ready(val diary: DiaryWalk?) : RecordReadingState
    data object Failed : RecordReadingState
}

/** Mounted only for the selected action. Changing selection cancels its saved-diary observation. */
@Composable
internal fun BehaviorRecordReading(record: WalkBehaviorRecord, source: WalkRecordsSource?, modifier: Modifier = Modifier) {
    var attempt by remember(source, record) { mutableIntStateOf(0) }
    var state by remember(source, record, attempt) { mutableStateOf<RecordReadingState>(RecordReadingState.Loading) }
    LaunchedEffect(source, record, attempt) {
        if (source == null) {
            state = RecordReadingState.Ready(DiaryWalk(record.walk.summary, emptyList(), "", sourceEntries = listOf(record.entry)))
            return@LaunchedEffect
        }
        try { source.observeDiary(record.walk).collect { state = RecordReadingState.Ready(it) } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { state = RecordReadingState.Failed }
    }
    val diary = (state as? RecordReadingState.Ready)?.diary
    val reading = diary?.let { DiaryActionTarget(record.entry.sessionId, record.entry.id).resolve(it) }
    val scene = reading?.scene
    var photo by remember(source, record, scene) { mutableStateOf<WalkPhoto?>(null) }
    Column(modifier.testTag("records-behavior-reading-${record.key}"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state) {
            RecordReadingState.Loading -> {
                DiaryOriginalActionReading(record.entry)
                Text("일기를 불러오는 중…", color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            RecordReadingState.Failed -> {
                DiaryOriginalActionReading(record.entry)
                Text("일기를 불러오지 못했어요. 원본 행동을 보고 있어요.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { attempt++ }) { Text("다시 불러오기") }
            }
            is RecordReadingState.Ready -> when {
                reading == null -> Text("삭제되었거나 현재 읽을 수 없는 행동이에요.", color = TextMuted)
                scene != null -> DiarySceneReading(scene, diarySceneKind(scene, requireNotNull(diary).sourceEntries,
                    record.entry.sessionId), onPhoto = { photo = it })
                else -> DiaryOriginalActionReading(reading.entry)
            }
        }
    }
    photo?.let { WalkPhotoDialog(it, null) { photo = null } }
}

@Preview(showBackground = true, widthDp = 340)
@Composable
private fun BehaviorRecordReadingPreview() { DaengsTheme {
    previewRecordBehaviors().records.firstOrNull()?.let { BehaviorRecordReading(it, null, Modifier.padding(16.dp)) }
} }
