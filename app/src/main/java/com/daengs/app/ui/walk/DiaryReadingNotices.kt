package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.ui.theme.*

/** Explorer hosts these in its reading region so map notices cannot consume the controls' viewport. */
@Composable
internal fun DiaryReadingNotices(generation: String?, direction: Boolean, error: String?,
    onZoom: () -> Unit, onRetry: () -> Unit, compact: Boolean = false) {
    generation?.takeIf { it.isNotBlank() }?.let {
        Text(it, Modifier.padding(horizontal = if (compact) 0.dp else 20.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyMedium)
    }
    if (direction) Row(Modifier.padding(horizontal = if (compact) 0.dp else 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("현재 화면에서는 방향을 표시하기 어려워요.", Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall, color = TextMuted)
        TextButton(onClick = onZoom) { Text("동선 확대") }
    }
    if (error != null) Row(Modifier.padding(horizontal = if (compact) 0.dp else 20.dp)) {
        Text(error, Modifier.weight(1f))
        TextButton(onClick = onRetry) { Text("다시 시도") }
    }
}

@Preview(showBackground = true, widthDp = 320, fontScale = 1.3f)
@Composable
private fun DiaryReadingNoticesPreview() { DiaryReviewTheme { Column {
    DiaryReadingNotices("저장한 장면을 보여드려요.", true, "장면을 불러오지 못했어요.", {}, {})
} } }
