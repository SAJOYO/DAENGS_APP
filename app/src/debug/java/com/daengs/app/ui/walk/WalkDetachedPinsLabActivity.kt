package com.daengs.app.ui.walk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.BuildConfig
import com.daengs.app.DaengsApp
import com.daengs.app.ui.theme.DaengsTheme

/** Production diary screen, in-memory source only. Never opens the installed account's data. */
class WalkDetachedPinsLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val allowed = BuildConfig.APPLICATION_ID.endsWith(".routepreview") &&
            BuildConfig.API_BASE_URL.isBlank() && (application as DaengsApp).tokenStore.load() == null
        setContent { DaengsTheme {
            if (allowed) DetachedPinsLab { finish() }
            else Text("별도 동선 검토 앱에서만 여는 가상 기록이에요.", Modifier.padding(24.dp))
        } }
    }
}

@Composable
private fun DetachedPinsLab(onBack: () -> Unit = {}) {
    var dense by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = { dense = !dense }, modifier = Modifier.statusBarsPadding()) {
            Text(if (dense) "가상 산책 · 밀집 12건  →  일반 배치" else "가상 산책 · 일반 배치  →  밀집 12건")
        }
        key(dense) {
            val record = if (dense) WalkRecordsDenseLabFixture.records.first() else WalkRecordsLabFixture.records.first()
            WalkRecordsLabDetail(record, onBack)
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun DetachedPinsLabPreview() { DaengsTheme { DetachedPinsLab() } }
