package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.theme.*

/** Scene actions scroll with the prose, including at the middle drawer height. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DiarySceneFooter(index: Int, count: Int, onEdit: () -> Unit, onDelete: (() -> Unit)?,
    onPrevious: () -> Unit, onNext: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        HorizontalDivider(color = PinkFaint)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onEdit, modifier = Modifier.semantics { contentDescription = "장면 수정" },
                    contentPadding = PaddingValues(horizontal = 4.dp)) { Text("내용 수정", fontSize = 12.sp, color = TextMuted) }
                onDelete?.let { remove -> TextButton(onClick = remove,
                    modifier = Modifier.semantics { contentDescription = "장면 삭제" },
                    contentPadding = PaddingValues(horizontal = 4.dp)) { Text("삭제", fontSize = 12.sp, color = TextMuted) } }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = index > 0, onClick = onPrevious, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text("이전", fontSize = 12.sp)
                }
                Text("${index + 1} / $count", fontSize = 11.sp, color = TextMuted)
                TextButton(enabled = index in 0 until count - 1, onClick = onNext,
                    contentPadding = PaddingValues(horizontal = 4.dp)) { Text("다음", fontSize = 12.sp) }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Preview(showBackground = true, widthDp = 320, fontScale = 1.3f)
@Composable
private fun SceneFooterPreview() { DiaryReviewTheme { DiarySceneFooter(1, 5, {}, {}, {}, {}) } }
