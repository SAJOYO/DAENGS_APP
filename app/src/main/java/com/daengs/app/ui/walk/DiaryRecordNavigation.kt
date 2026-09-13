package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.ui.theme.*
import com.daengs.app.walk.diary.DiaryScene

@Composable
internal fun DiaryRecordMapButtons(view: DiaryMapView, onWalking: () -> Unit, onWhole: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = CardWhite, shadowElevation = 2.dp) {
        Row(Modifier.padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(DiaryMapView.WALKING to "보행 중심", DiaryMapView.WHOLE to "전체 기록").forEach { (mode, label) ->
                TextButton(onClick = if (mode == DiaryMapView.WALKING) onWalking else onWhole,
                    modifier = Modifier.heightIn(min = 42.dp), contentPadding = PaddingValues(horizontal = 9.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = if (view == mode) TextDark else TextMuted,
                        containerColor = if (view == mode) PinkFaint else CardWhite)) {
                    Text(label, maxLines = 1, fontSize = 12.sp,
                        fontWeight = if (view == mode) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
internal fun DiaryOffscreenMenu(scenes: List<DiaryScene>, offscreen: List<DiaryScene>, onSelect: (DiaryScene) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.padding(horizontal = 12.dp)) {
        TextButton(onClick = { open = true }) { Text("화면 밖 장면 ${offscreen.size}개", color = TextMuted) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            offscreen.forEach { scene -> DropdownMenuItem(onClick = { open = false; onSelect(scene) },
                text = { Text("${scenes.indexOfFirst { it.id == scene.id } + 1} · ${scene.title}",
                    maxLines = 2, overflow = TextOverflow.Ellipsis) }) }
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun DiaryRecordNavigationPreview() { DaengsTheme { Column {
    DiaryRecordMapButtons(DiaryMapView.WALKING, {}, {})
    val scene = DiaryScene("s/7", "s", 0, "공원에 도착해서", "", null, "")
    DiaryOffscreenMenu(listOf(scene), listOf(scene), {})
} } }
