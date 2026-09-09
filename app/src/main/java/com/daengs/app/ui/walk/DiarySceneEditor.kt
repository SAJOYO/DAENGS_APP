package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.walk.diary.*

@Composable
internal fun DiarySceneEditor(scene: DiaryScene, busy: Boolean, error: String?,
    onSave: (String, String) -> Unit, onDismiss: () -> Unit,
    dialog: @Composable (@Composable () -> Unit, @Composable () -> Unit,
        @Composable () -> Unit, @Composable () -> Unit) -> Unit = { title, body, confirm, dismiss ->
        AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
            title = title, text = body, confirmButton = confirm, dismissButton = dismiss)
    }) {
    var title by rememberSaveable(scene.id) { mutableStateOf(scene.title) }
    var body by rememberSaveable(scene.id) { mutableStateOf(scene.body) }
    dialog({ Text("장면 수정") },
        { Column(Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("장면 제목") },
                enabled = !busy, isError = title.length > 80, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(body, { body = it },
                label = { Text("장면 내용") },
                enabled = !busy, minLines = 3, isError = body.length > MAX_DIARY_SCENE_BODY_LENGTH,
                modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        { TextButton(onClick = { onSave(title.trim(), body) },
            enabled = !busy && title.isNotBlank() && title.length <= 80 && body.length <= MAX_DIARY_SCENE_BODY_LENGTH) {
            Text(if (busy) "저장 중" else "저장")
        } },
        { TextButton(onClick = onDismiss, enabled = !busy) { Text("취소") } })
}

@Preview(showBackground = true)
@Composable
private fun DiarySceneEditorPreview() {
    MaterialTheme {
        DiarySceneEditor(DiaryScene("s/n", "s", 0, "두부와 잠깐 쉬어 간 길", "공원 옆이었다. 두부랑 사진 한 장!",
            null, "", content = DiarySceneContent("두부랑 사진 한 장!", "note", locationLabel = "")),
            false, null, { _, _ -> }, {})
    }
}
