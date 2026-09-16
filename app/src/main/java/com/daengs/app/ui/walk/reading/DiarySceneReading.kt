package com.daengs.app.ui.walk.reading

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.ui.theme.TextMuted
import com.daengs.app.ui.walk.DiaryReadingPhoto
import com.daengs.app.ui.walk.DiarySceneHeading
import com.daengs.app.walk.WalkPhoto
import com.daengs.app.walk.diary.DiaryScene
import com.daengs.app.walk.diary.DiarySceneKind

/** Shared saved-scene reading. Navigation, editing and drawer state belong to the caller. */
@Composable
internal fun DiarySceneReading(scene: DiaryScene, kind: DiarySceneKind,
    modifier: Modifier = Modifier, onPhoto: (WalkPhoto) -> Unit,
    routeNotice: String? = null, afterOriginals: @Composable () -> Unit = {}) {
    Column(modifier) {
        DiarySceneHeading(scene, kind, Modifier.fillMaxWidth().padding(top = 6.dp), detail = true)
        Spacer(Modifier.height(18.dp))
        DiarySceneText(scene.body)
        RelationalSceneOriginals(scene, onPhoto)
        afterOriginals()
        routeNotice?.let { Text(it, Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodySmall, color = TextMuted) }
        if (scene.needsReview) Text("원본 기록이 바뀌었어요. 수정한 문장은 유지했어요.",
            Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
        scene.photo?.let { photo -> DiaryReadingPhoto(photo) { onPhoto(photo) } }
        if (scene.content?.photoId != null && scene.photo == null)
            Text("사진 파일은 촬영한 기기에서 볼 수 있어요.", style = MaterialTheme.typography.bodySmall)
    }
}

@Preview(showBackground = true, widthDp = 340)
@Composable
private fun DiarySceneReadingPreview() { DiaryReviewTheme {
    DiarySceneReading(DiaryScene("s/a", "s", 0, "나무 아래서 킁킁", "잠깐 멈춰 냄새를 맡았다.", null, ""),
        DiarySceneKind.SNIFFING, Modifier.padding(16.dp), {})
} }
