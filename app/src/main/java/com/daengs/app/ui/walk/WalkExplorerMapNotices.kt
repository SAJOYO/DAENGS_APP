package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextMuted
import com.daengs.app.walk.diary.DiaryScene

/** Auxiliary map hints belong in the explorer's scrolling region, below its time controls. */
@Composable
internal fun WalkExplorerMapNotices(scenes: List<DiaryScene>, offscreen: List<DiaryScene>, needsDirectionZoom: Boolean,
    onScene: (DiaryScene) -> Unit, onZoom: () -> Unit) {
    if (offscreen.isNotEmpty()) DiaryOffscreenMenu(scenes, offscreen, onScene)
    if (needsDirectionZoom) Row(verticalAlignment = Alignment.CenterVertically) {
        Text("현재 화면에서는 방향을 표시하기 어려워요.", Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall, color = TextMuted)
        TextButton(onClick = onZoom) { Text("동선 확대") }
    }
}

@Preview(showBackground = true, widthDp = 320, fontScale = 1.3f)
@Composable
private fun ExplorerMapNoticesPreview() { DaengsTheme { WalkExplorerMapNotices(emptyList(), emptyList(), true, {}, {}) } }
