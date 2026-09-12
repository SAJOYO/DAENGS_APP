package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** Scene-only preparation; the session map remains available above this panel. */
@Composable
internal fun WalkDiaryPreparing(onRefresh: () -> Unit, error: String? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)
        .testTag("diary-scenes-preparing"), verticalAlignment = Alignment.CenterVertically) {
        if (error == null) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(16.dp))
        }
        Text(error ?: "장면을 정리하고 있어요.", Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onRefresh) { Text("새로고침") }
    }
}

@Preview(showBackground = true)
@Composable
private fun WalkDiaryPreparingPreview() {
    MaterialTheme { WalkDiaryPreparing({}) }
}
