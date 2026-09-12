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
import com.daengs.app.walk.toEntryMoments
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.WalkMoment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 지도/완료/지난 기록에서 같은 원본을 편집한다. 입력 중 위치는 entry에 고정된다.
 *
 * 껍데기만 여기 있고 알맹이는 [WalkEntryEditorContent] 다. 나눈 이유는 테스트다 —
 * `AlertDialog` 안에 텍스트필드가 있으면 Robolectric 에서 영영 idle 이 안 돼
 * `setContent` 가 `AppNotIdleException` 으로 죽는다. 다이얼로그만·텍스트필드만이면
 * 멀쩡하고 둘이 만나야 터진다. 화면 버그가 아니라 테스트 환경 문제라, 알맹이를
 * 다이얼로그 없이도 그릴 수 있게 두고 테스트는 그쪽을 본다 (`WalkEntryEditorTest`).
 */
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
    WalkEntryEditorContent(
        entries,
        initial,
        pets,
        error,
        busy,
        onSave,
        onDelete,
        onDismiss,
        diaryPhotos,
        onOpenPhoto,
    ) { title, body, confirm, dismiss ->
        AlertDialog(
            onDismissRequest = { if (!busy) onDismiss() },
            title = title, text = body, confirmButton = confirm, dismissButton = dismiss,
        )
    }
}

/**
 * 편집기의 알맹이. 상태를 여기서 들고, 조각 넷을 [container] 에 넘긴다.
 *
 * [container] 를 밖에서 받는 이유는 **같은 상태를 다이얼로그의 슬롯 넷에 나눠 넣어야
 * 하기 때문**이다. 조각별로 함수를 쪼개면 `selected`·`text` 를 셋 이상으로 끌어올려야
 * 하고, 그러면 저장 버튼의 활성 조건이 상태와 떨어진다. 테스트는 다이얼로그 대신
 * 평범한 `Column` 을 넘겨 같은 알맹이를 창 없이 그린다.
 */
@Composable
internal fun WalkEntryEditorContent(
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
    container: @Composable (
        title: @Composable () -> Unit,
        body: @Composable () -> Unit,
        confirm: @Composable () -> Unit,
        dismiss: @Composable () -> Unit,
    ) -> Unit,
) {
    var selected by remember(initial?.id) { mutableStateOf(initial) }
    var text by remember(selected?.id) { mutableStateOf(selected?.note.orEmpty()) }
    val current = selected
    val latest = entries.firstOrNull { it.id == current?.id }
    val changed = current?.baseVersion != null && current.baseVersion != latest?.baseVersion
    container(
        // **목록(`산책 기록`)과 이름이 갈려야 한다.** 이건 산책 한 건 안에 남긴
        // 것이고 저건 산책들의 목록이다. "지금" 을 넣지 않는 이유는 이 편집기가
        // 지난 산책 상세(`WalkDiaryMapScreen`)에서도 열리기 때문이다.
        { Text(current?.type?.label ?: "이 산책에 남긴 것") },
        {
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
                                    (entry.note?.let { "\n$it" } ?: "\n${entry.locationLabel}"),
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
                    Text(current.locationLabel)
                    if (current.syncPending) Text("기기에 저장했어요 · 동기화 대기 중")
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
        {
            if (current != null) TextButton(
                enabled = !busy && !changed && (current.type != WalkMomentType.NOTE || text.isNotBlank()),
                onClick = { onSave(if (current.type == WalkMomentType.NOTE) current.copy(note = text.trim()) else current) },
            ) { Text(if (busy) "저장 중" else "저장") }
        },
        { TextButton(enabled = !busy, onClick = onDismiss) { Text("닫기") } },
    )
}

internal val WalkEntry.locationLabel: String get() = pin?.label
    ?: if (point != null) "위치와 함께 남긴 기록" else if (type == WalkMomentType.NOTE) "위치 없이 남긴 메모" else "위치 없이 남긴 행동"

private fun entryClock(at: Long) = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(at))

private data class DiaryRow(val at: Long, val entry: WalkEntry? = null,
    val photo: com.daengs.app.walk.WalkPhoto? = null) {
    val key: String get() = photo?.let { "photo-${it.id}" } ?: "entry-${entry!!.id}"
}

/** 같은 위치여도 ID가 다른 기록을 원본 단계에서 합치지 않는다. */
internal fun List<WalkEntry>.entryMoments(): List<WalkMoment> = toEntryMoments()

@Preview
@Composable
private fun WalkEntryEditorPreview() {
    MaterialTheme {
        WalkEntryEditor(emptyList(), WalkEntry(sessionId = "preview", type = WalkMomentType.NOTE,
            recordedAtMillis = 0, note = "오늘 처음 걸어본 길"), emptyList(), null, false, {}, {}, {})
    }
}
