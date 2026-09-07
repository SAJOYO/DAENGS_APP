package com.daengs.app.ui.places.lab

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme

@Composable
internal fun PlaceFloatingNotices(messages: List<String>, modifier: Modifier = Modifier) {
    var dismissed by remember(messages) { mutableStateOf(false) }
    if (messages.isEmpty() || dismissed) return
    Surface(modifier = modifier, color = DaengsColors.Surface.copy(alpha = .96f),
        shape = RoundedCornerShape(12.dp), shadowElevation = 3.dp) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                messages.forEach { Text(it, color = DaengsColors.TextSecondary, fontSize = 11.sp) }
            }
            IconButton(onClick = { dismissed = true }, modifier = Modifier.semantics { contentDescription = "안내 닫기" }) {
                Text("×", fontSize = 18.sp, color = DaengsColors.TextSecondary)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun FloatingNoticesPreview() {
    DaengsTheme { PlaceFloatingNotices(listOf("가상 위치로는 주변 장소를 검색할 수 없어요.", "로그인 후 반려견을 선택할 수 있어요.")) }
}
