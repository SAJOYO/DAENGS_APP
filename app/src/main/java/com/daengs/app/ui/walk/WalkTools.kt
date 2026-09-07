package com.daengs.app.ui.walk

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.theme.*

enum class WalkTool { POLE, ROTATE, CAMERA, RECORD, ENTRIES, LOCATE, PAUSE, PLAY, CLOSE }

@Composable
internal fun WalkToolButton(tool: WalkTool, label: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, active: Boolean = false, caption: String? = null) {
    val tint = if (active) DaengPinkDeep else TextDark
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier
        .widthIn(min = 48.dp).heightIn(min = 48.dp)
        .semantics { contentDescription = label; if (tool == WalkTool.POLE) selected = active },
        contentPadding = PaddingValues(4.dp), shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = tint,
            containerColor = if (active) DaengPinkDeep.copy(alpha = .12f) else Color.Transparent)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(22.dp)) {
                val c = if (enabled) tint else tint.copy(alpha = .4f)
                fun line(x: Float, y: Float, a: Float, b: Float) = drawLine(c, Offset(size.width*x/24, size.height*y/24), Offset(size.width*a/24, size.height*b/24), 1.7.dp.toPx())
                when (tool) {
                    WalkTool.POLE -> { line(12f,2f,12f,23f); line(4f,7f,20f,7f); line(7f,3f,7f,10f); line(17f,3f,17f,10f); line(8f,13f,16f,13f) }
                    WalkTool.ROTATE -> {
                        rotate(-30f) {
                            drawRoundRect(c, Offset(size.width*.34f, size.height*.2f),
                                Size(size.width*.32f, size.height*.6f), CornerRadius(2.dp.toPx()),
                                style = Stroke(1.5.dp.toPx()))
                            line(10f, 16f, 14f, 16f)
                        }
                        drawArc(c, 205f, 110f, false, Offset(size.width*.04f, size.height*.04f),
                            Size(size.width*.92f, size.height*.92f), style = Stroke(1.5.dp.toPx()))
                        line(18f, 3f, 20f, 6f); line(20f, 6f, 16f, 6f)
                        drawArc(c, 25f, 110f, false, Offset(size.width*.04f, size.height*.04f),
                            Size(size.width*.92f, size.height*.92f), style = Stroke(1.5.dp.toPx()))
                        line(6f, 21f, 4f, 18f); line(4f, 18f, 8f, 18f)
                    }
                    WalkTool.CAMERA -> { drawRect(c, Offset(size.width*.1f,size.height*.25f), Size(size.width*.8f,size.height*.6f), style=Stroke(1.5.dp.toPx())); drawCircle(c,size.width*.16f,Offset(size.width*.5f,size.height*.55f),style=Stroke(1.5.dp.toPx())); line(8f,6f,10f,3f); line(10f,3f,15f,3f) }
                    WalkTool.LOCATE -> { drawCircle(c,size.width*.36f,style=Stroke(1.5.dp.toPx())); drawCircle(c,size.width*.12f) }
                    WalkTool.RECORD -> { line(12f,3f,12f,21f); line(3f,12f,21f,12f) }
                    WalkTool.ENTRIES -> { for (y in listOf(5f,12f,19f)) line(4f,y,20f,y) }
                    WalkTool.PAUSE -> { line(8f,4f,8f,20f); line(16f,4f,16f,20f) }
                    WalkTool.PLAY -> { line(7f,3f,20f,12f); line(20f,12f,7f,21f); line(7f,21f,7f,3f) }
                    WalkTool.CLOSE -> { line(6f,6f,18f,18f); line(18f,6f,6f,18f) }
                }
            }
            caption?.let { Text(it, fontSize = 10.sp, maxLines = 1) }
        }
    }
}

/** Normal GPS is a dot; details and settings are shown only on request. */
@Composable
internal fun WalkGpsDot(good: Boolean, unavailable: Boolean, detail: String, onSettings: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = if (good) "GPS 양호" else if (unavailable) "GPS 확인 필요" else "GPS 불안정"
    IconButton(onClick = { expanded = true }, modifier = Modifier.semantics { contentDescription = label }) {
        Box(Modifier.size(if (good) 8.dp else 14.dp).background(
            if (good) Color(0xff4e9b70) else if (unavailable) Color(0xffbb5555) else Color(0xffb5802e), RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
            if (!good) Text("!", color = Color.White, fontSize = 10.sp)
        }
    }
    if (expanded) AlertDialog(onDismissRequest = { expanded = false }, title = { Text(label) }, text = { Text(detail) },
        confirmButton = { TextButton(onClick = { expanded = false }) { Text("닫기") } },
        dismissButton = { if (unavailable) TextButton(onClick = { expanded = false; onSettings() }) { Text("설정") } })
}

@Preview(showBackground = true)
@Composable
private fun WalkToolsPreview() { DaengsTheme { Row { WalkToolButton(WalkTool.POLE,"점령지 보기",{},active=true); WalkToolButton(WalkTool.ROTATE,"가로 보기",{}); WalkGpsDot(true,false,"현재 위치를 확인했어요",{}) } } }
