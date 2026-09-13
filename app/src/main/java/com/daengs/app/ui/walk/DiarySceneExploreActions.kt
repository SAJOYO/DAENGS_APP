package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.ui.theme.*
import androidx.compose.ui.unit.sp

/** Explicit actions live inside the independently scrollable scene, preserving its reading height. */
@Composable
internal fun DiarySceneExploreActions(onReturn: (() -> Unit)?, onNeighborhood: (() -> Unit)?) {
    if (onReturn == null && onNeighborhood == null) return
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        onReturn?.let { TextButton(onClick = it) { Text("선택 구간으로 돌아가기") } }
        onNeighborhood?.let { OutlinedButton(onClick = it,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, DaengsColors.BorderNeutral),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextDark)) {
            Text("이 장면 앞뒤 30초 보기", fontSize = 13.sp)
        } }
    }
}

@Preview(showBackground = true, widthDp = 320, fontScale = 1.3f)
@Composable
private fun SceneExploreActionsPreview() { DiaryReviewTheme { DiarySceneExploreActions({}, {}) } }
