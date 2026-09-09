package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.walk.diary.DiarySceneContent

@Composable
internal fun DiarySceneText(background: String, content: DiarySceneContent) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        content.address?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
        if (background.isNotBlank()) Text(background, fontStyle = FontStyle.Italic,
            style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider()
        Text(if (content.recordKind.startsWith("observed_")) "이동 기록" else "직접 남긴 기록",
            style = MaterialTheme.typography.labelSmall)
        Text(content.recordText, style = MaterialTheme.typography.bodyMedium)
        Text(content.locationLabel, style = MaterialTheme.typography.bodySmall)
    }
}

@Preview(showBackground = true)
@Composable
private fun DiarySceneTextPreview() {
    MaterialTheme { Box(Modifier.padding(16.dp)) {
        DiarySceneText("작은 가게들이 모인 길 옆이었다.", DiarySceneContent("두부랑 사진 한 장!", "note",
            locationLabel = "기록할 때 확인된 위치"))
    } }
}
