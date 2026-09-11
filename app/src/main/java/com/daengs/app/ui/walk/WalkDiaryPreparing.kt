package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** The map and editable cards are composed only after a single result is published. */
@Composable
internal fun WalkDiaryPreparing(onBack: () -> Unit, onRefresh: () -> Unit, error: String? = null) {
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack) { Text("‹ 산책 기록") }
        Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            if (error == null) CircularProgressIndicator(Modifier.size(32.dp))
            Spacer(Modifier.height(24.dp))
            Text(error ?: "동선과 장면을 정리하고 있어요.", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onRefresh) { Text("새로고침") }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WalkDiaryPreparingPreview() {
    MaterialTheme { WalkDiaryPreparing({}, {}) }
}
