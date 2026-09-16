package com.daengs.app.ui.walk.reading

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.theme.TextMuted
import com.daengs.app.ui.walk.DiaryReadingPhoto
import com.daengs.app.walk.WalkPhoto
import com.daengs.app.walk.diary.DiaryScene

/** Original text/media remain separate from generated and user-edited diary prose. */
@Composable
internal fun RelationalSceneOriginals(scene: DiaryScene, onPhoto: (WalkPhoto) -> Unit) {
    scene.originalNotes.forEach { note ->
        Text("내 메모", Modifier.padding(top = 16.dp, bottom = 4.dp), color = TextMuted, fontSize = 12.sp)
        DiarySceneText(note)
    }
    scene.originalPhotos.forEach { original ->
        val photo = original.photo
        if (photo != null) DiaryReadingPhoto(photo) { onPhoto(photo) }
        else Text("사진 파일은 촬영한 기기에서 볼 수 있어요.", Modifier.padding(top = 12.dp), color = TextMuted, fontSize = 12.sp)
    }
    if (scene.notice.isNotEmpty()) Text(scene.notice, Modifier.padding(top = 12.dp), color = TextMuted, fontSize = 12.sp)
}

@Preview(showBackground = true, widthDp = 320, fontScale = 1.3f)
@Composable
private fun RelationalSceneOriginalsPreview() { DiaryReviewTheme {
    Column(Modifier.padding(20.dp)) {
        RelationalSceneOriginals(DiaryScene("s/1", "s", 0, "산책 장면 1", "", null, "",
            originalNotes = listOf("  오늘 남긴 메모\n다음 줄도 그대로."), notice = "공간 문장을 만들지 못했어요."), {})
    }
} }
