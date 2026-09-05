package com.daengs.app.ui.walk

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.pet.Pet
import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.WalkMoment
import com.daengs.app.walk.WalkMomentAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 지도/완료/지난 기록에서 같은 원본을 편집한다. 입력 중 위치는 entry에 고정된다. */
@Composable
fun WalkEntryEditor(
    entries: List<WalkEntry>,
    initial: WalkEntry?,
    pets: List<Pet>,
    error: String?,
    busy: Boolean,
    onSave: (WalkEntry) -> Unit,
    onDelete: (WalkEntry) -> Unit,
    onDismiss: () -> Unit,
    diaryPhotos: List<com.daengs.app.walk.WalkPhoto> = emptyList(),
    onOpenPhoto: (com.daengs.app.walk.WalkPhoto) -> Unit = {},
) {
    var selected by remember(initial?.id) { mutableStateOf(initial) }
    var text by remember(selected?.id) { mutableStateOf(selected?.note.orEmpty()) }
    val current = selected
    val latest = entries.firstOrNull { it.id == current?.id }
    val changed = current?.baseVersion != null && current.baseVersion != latest?.baseVersion
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(current?.type?.label ?: "산책 기록") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (current == null) {
                    if (entries.isEmpty() && diaryPhotos.isEmpty()) Text("아직 남긴 기록이 없어요.")
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        val timeline = (entries.map { DiaryRow(it.recordedAtMillis, entry = it) } +
                            diaryPhotos.map { DiaryRow(it.capturedAtMillis, photo = it) }).sortedBy { it.at }
                        items(timeline, key = { it.key }) { row ->
                            val entry = row.entry
                            val photo = row.photo
                            Text(if (photo != null) "사진 · ${entryClock(photo.capturedAtMillis)}" else
                                "${entry!!.type.label} · ${entryClock(entry.recordedAtMillis)}" +
                                    (entry.note?.let { "\n$it" } ?: ""),
                                Modifier.fillMaxWidth().clickable {
                                    if (photo != null) onOpenPhoto(photo) else selected = entry
                                }.padding(vertical = 12.dp))
                        }
                    }
                } else {
                    Text(entryClock(current.recordedAtMillis))
                    if (changed) {
                        Text(if (latest == null) "이 기록은 삭제됐어요. 작성 중인 내용은 아래에 남겨 두었어요."
                            else "편집 중 기록이 변경됐어요. 최신 내용을 확인해 주세요.",
                            color = MaterialTheme.colorScheme.error)
                        if (latest != null) {
                            val petName = pets.firstOrNull { it.id == latest.petId }?.name
                                ?: latest.petId ?: "미지정"
                            Text("최신 기록: ${latest.note ?: latest.type.label}" +
                                if (latest.type == WalkMomentType.NOTE) "" else " · 대상: $petName")
                            TextButton(enabled = !busy, onClick = {
                                // 내용은 유지하고 사용자가 확인한 버전만 갱신한다. 이후 또 바뀌면 다시 막는다.
                                selected = current.copy(baseVersion = latest.baseVersion, syncError = latest.syncError)
                            }) { Text("확인했어요 · 작성 중인 내용으로 계속") }
                        }
                    }
                    current.syncError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text(current.point?.let { "위치와 함께 남긴 기록" } ?: "위치 없이 남긴 메모")
                    if (current.type == WalkMomentType.NOTE) {
                        OutlinedTextField(value = text, onValueChange = { if (it.length <= 2000) text = it },
                            label = { Text("기억하고 싶은 내용을 적어 주세요") }, modifier = Modifier.fillMaxWidth(),
                            enabled = !busy, minLines = 3)
                    } else {
                        Row {
                            WalkMomentType.entries.filter { it != WalkMomentType.NOTE }.forEach { type ->
                                TextButton(enabled = !busy, onClick = { selected = current.copy(type = type) }) {
                                    Text(if (current.type == type) "✓ ${type.label}" else type.label)
                                }
                            }
                        }
                        Text("누구의 행동인가요?")
                        TextButton(enabled = !busy, onClick = { selected = current.copy(petId = null) }) {
                            Text(if (current.petId == null) "✓ 미지정" else "미지정")
                        }
                        pets.forEach { pet ->
                            TextButton(enabled = !busy, onClick = { selected = current.copy(petId = pet.id) }) {
                                Text((if (current.petId == pet.id) "✓ " else "") + pet.name)
                            }
                        }
                    }
                    if (entries.any { it.id == current.id }) {
                        TextButton(enabled = !busy, onClick = { onDelete(current) }) { Text("기록 삭제") }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (current != null) TextButton(
                enabled = !busy && !changed && (current.type != WalkMomentType.NOTE || text.isNotBlank()),
                onClick = { onSave(if (current.type == WalkMomentType.NOTE) current.copy(note = text.trim()) else current) },
            ) { Text(if (busy) "저장 중" else "저장") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("닫기") } },
    )
}

private fun entryClock(at: Long) = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(at))

private data class DiaryRow(val at: Long, val entry: WalkEntry? = null,
    val photo: com.daengs.app.walk.WalkPhoto? = null) {
    val key: String get() = photo?.let { "photo-${it.id}" } ?: "entry-${entry!!.id}"
}

/** 같은 위치여도 ID가 다른 기록을 원본 단계에서 합치지 않는다. */
internal fun List<WalkEntry>.entryMoments(): List<WalkMoment> = mapNotNull { entry ->
    entry.point?.let { point -> WalkMoment(
        id = "moment-${entry.id}", point = point,
        actions = mapOf(entry.type to WalkMomentAction(entry.type, entry.recordedAtMillis,
            entry.locationCapturedAtMillis ?: entry.recordedAtMillis)),
    ) }
}

@Preview
@Composable
private fun WalkEntryEditorPreview() {
    MaterialTheme {
        WalkEntryEditor(emptyList(), WalkEntry(sessionId = "preview", type = WalkMomentType.NOTE,
            recordedAtMillis = 0, note = "오늘 처음 걸어본 길"), emptyList(), null, false, {}, {}, {})
    }
}
