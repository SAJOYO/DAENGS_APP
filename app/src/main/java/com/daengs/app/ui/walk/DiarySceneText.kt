package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import com.daengs.app.R
import com.daengs.app.walk.diary.DiarySceneContent

@Composable
internal fun DiarySceneText(background: String, content: DiarySceneContent, onEditRecord: (() -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        content.address?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (background.isNotBlank()) Text(background, fontStyle = FontStyle.Italic,
            fontSize = 20.sp, lineHeight = 30.sp)
        if (background.isNotBlank()) HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (content.recordKind.startsWith("observed_")) "이동 기록" else "직접 남긴 기록",
                modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            onEditRecord?.let { edit ->
                IconButton(onClick = edit) {
                    Icon(painterResource(R.drawable.ic_diary_edit), contentDescription = "원본 기록 수정",
                        modifier = Modifier.size(20.dp))
                }
            }
        }
        Text(content.recordText, fontSize = 20.sp, lineHeight = 30.sp)
        Text(content.locationLabel, style = MaterialTheme.typography.bodyMedium)
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
