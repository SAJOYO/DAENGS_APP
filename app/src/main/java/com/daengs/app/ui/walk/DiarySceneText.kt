package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Receives exactly the same complete prose as the scene editor. */
@Composable
internal fun DiarySceneText(body: String) {
    if (body.isNotBlank()) Text(body, fontSize = 16.sp, lineHeight = 27.sp)
}

@Preview(showBackground = true)
@Composable
private fun DiarySceneTextPreview() {
    MaterialTheme { Box(Modifier.padding(16.dp)) {
        DiarySceneText("작은 가게들이 모인 길 옆이었다. 두부랑 사진 한 장!\n잠깐 쉬었다가 다시 걸었다.")
    } }
}
