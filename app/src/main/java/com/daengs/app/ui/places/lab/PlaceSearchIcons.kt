package com.daengs.app.ui.places.lab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme

/** 카테고리와 같은 24 단위 선 아이콘. 전환 의미와 상태는 바깥 버튼이 읽어 준다. */
@Composable
internal fun RobotSearchIcon(modifier: Modifier, active: Boolean) {
    val tint = DaengsColors.TextPrimary
    val eyes = if (active) DaengsColors.BrandPrimary else tint
    Canvas(modifier) {
        scale(size.minDimension / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(1.6f, cap = StrokeCap.Round)
            drawRoundRect(tint, Offset(4f, 7f), Size(16f, 13f), CornerRadius(4f), style = stroke)
            drawLine(tint, Offset(12f, 7f), Offset(12f, 4f), 1.6f, StrokeCap.Round)
            drawCircle(tint, 1.2f, Offset(12f, 3f))
            drawLine(tint, Offset(1.5f, 12f), Offset(1.5f, 15f), 1.6f, StrokeCap.Round)
            drawLine(tint, Offset(22.5f, 12f), Offset(22.5f, 15f), 1.6f, StrokeCap.Round)
            drawCircle(eyes, 1.2f, Offset(8.5f, 12.5f))
            drawCircle(eyes, 1.2f, Offset(15.5f, 12.5f))
            drawLine(tint, Offset(10f, 16.5f), Offset(14f, 16.5f), 1.6f, StrokeCap.Round)
        }
    }
}

@Composable
internal fun SearchActionIcon(modifier: Modifier) {
    val tint = DaengsColors.TextPrimary
    Canvas(modifier) {
        scale(size.minDimension / 24f, pivot = Offset.Zero) {
            drawCircle(tint, 7f, Offset(10f, 10f), style = Stroke(1.8f))
            drawLine(tint, Offset(15.2f, 15.2f), Offset(21f, 21f), 1.8f, StrokeCap.Round)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchIconsPreview() {
    DaengsTheme { Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        SearchActionIcon(Modifier.size(24.dp))
        RobotSearchIcon(Modifier.size(24.dp), false)
        RobotSearchIcon(Modifier.size(24.dp), true)
    } }
}
