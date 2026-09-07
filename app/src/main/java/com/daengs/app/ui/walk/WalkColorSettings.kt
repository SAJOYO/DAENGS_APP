package com.daengs.app.ui.walk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.DaengsApp
import com.daengs.app.map.style.WalkStyleStore
import com.daengs.app.map.style.speedScaleStops
import com.daengs.app.map.style.rememberWalkStyle
import com.daengs.app.walk.sync.WalkApi

@Composable
internal fun WalkColorSettingsButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember(context) { WalkStyleStore(context) }
    val selection by rememberWalkStyle()
    var open by rememberSaveable { mutableStateOf(false) }
    val inspection = LocalInspectionMode.current
    LaunchedEffect(open) {
        if (!open || inspection || !WalkApi.configured) return@LaunchedEffect
        val app = context.applicationContext as? DaengsApp ?: return@LaunchedEffect
        val session = app.sessionProvider.freshSession() ?: return@LaunchedEffect
        WalkApi.stylePolicy(session.accessToken).getOrNull()?.let(store::cachePolicy)
    }
    TextButton(onClick = { open = true }, modifier = modifier) { Text("색상") }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("산책 지도 설정") },
        text = {
            Column(Modifier.selectableGroup()) {
                Text("동선 색상", style = MaterialTheme.typography.titleSmall)
                Text("느릴수록 어둡게, 빠를수록 선명하게", style = MaterialTheme.typography.bodySmall)
                selection.policy.themes.forEach { theme ->
                    Row(Modifier.fillMaxWidth().selectable(
                        selected = selection.themeId == theme.id, role = Role.RadioButton,
                        onClick = { store.select(theme.id) },
                    ).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selection.themeId == theme.id, onClick = null)
                        Column(Modifier.weight(1f)) {
                            Text(theme.label)
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.fillMaxWidth().height(7.dp).background(Brush.horizontalGradient(
                                *selection.policy.speedScaleStops(theme.id).map { (offset, color) -> offset to Color(color) }.toTypedArray(),
                            )))
                        }
                    }
                }
                Text("속도 5단계 · 경계는 부드럽게 연결돼요", style = MaterialTheme.typography.bodySmall)
                Text("속도를 알 수 없는 구간은 회색으로 표시해요.", style = MaterialTheme.typography.bodySmall)
                Text("이 기기의 산책 중·지난 기록에 함께 적용돼요.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text("완료") } },
    )
}

@Preview
@Composable
private fun WalkColorSettingsPreview() {
    com.daengs.app.ui.theme.DaengsTheme { WalkColorSettingsButton() }
}
