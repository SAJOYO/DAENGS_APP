package com.daengs.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import com.daengs.app.miniroom.art.drawPawStamp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 아이콘을 전부 Canvas 로 그린다.
 *
 * material-icons-extended 를 넣지 않는 이유: 이 화면에 필요한 발바닥·발자국·
 * 강아지 관련 아이콘이 어차피 없어서 직접 그려야 하고, 라이브러리는 수 MB 를
 * 더한다. 시안 아이콘은 전부 단순 도형이라 손으로 그리는 편이 싸다.
 *
 * 모든 아이콘은 24x24 좌표계로 그리고 실제 크기에 맞춰 스케일된다.
 */
enum class DaengsIcon {
    Paw, Home, Book, Bell, Person, Chat, Camera, Clock, Pin, Paws, Send, ChevronRight, CaretDown, Sun, Heart,
    Mic, Gallery, Sound, SoundOff
}

@Composable
fun DaengsIconView(
    icon: DaengsIcon,
    modifier: Modifier = Modifier,
    tint: Color = Color.Black,
    filled: Boolean = false,
) {
    Canvas(modifier) {
        val s = size.minDimension / 24f
        scale(s, s, pivot = Offset.Zero) {
            when (icon) {
                DaengsIcon.Paw -> drawPawStamp(Offset(12f, 14f), 6.4f, tint)
                DaengsIcon.Home -> iconHome(tint, filled)
                DaengsIcon.Book -> iconBook(tint)
                DaengsIcon.Bell -> iconBell(tint)
                DaengsIcon.Person -> iconPerson(tint)
                DaengsIcon.Chat -> iconChat(tint)
                DaengsIcon.Camera -> iconCamera(tint)
                DaengsIcon.Clock -> iconClock(tint)
                DaengsIcon.Pin -> iconPin(tint)
                DaengsIcon.Paws -> iconPaws(tint)
                DaengsIcon.Send -> iconSend(tint)
                DaengsIcon.ChevronRight -> iconChevronRight(tint)
                DaengsIcon.CaretDown -> iconCaretDown(tint)
                DaengsIcon.Sun -> iconSun(tint)
                DaengsIcon.Heart -> iconHeart(tint)
                DaengsIcon.Mic -> iconMic(tint)
                DaengsIcon.Gallery -> iconGallery(tint)
                DaengsIcon.Sound -> iconSound(tint, on = true)
                DaengsIcon.SoundOff -> iconSound(tint, on = false)
            }
        }
    }
}

private fun DrawScope.iconHome(tint: Color, filled: Boolean) {
    val roof = Path().apply {
        moveTo(12f, 3f); lineTo(21.5f, 11f); lineTo(2.5f, 11f); close()
    }
    drawPath(roof, tint)
    drawRoundRect(tint, Offset(4.5f, 10f), Size(15f, 11f), CornerRadius(2.4f, 2.4f))
    // 문 — 채워진 상태면 흰색으로 파낸다
    val door = if (filled) Color.White else Color.White
    drawRoundRect(door, Offset(9.5f, 13.5f), Size(5f, 7.5f), CornerRadius(2.5f, 2.5f))
    drawCircle(door, 0.9f, Offset(12f, 11.6f))
}

private fun DrawScope.iconBook(tint: Color) {
    drawRoundRect(tint, Offset(3f, 4f), Size(8f, 16f), CornerRadius(1.6f, 1.6f), style = Stroke(1.8f))
    drawRoundRect(tint, Offset(13f, 4f), Size(8f, 16f), CornerRadius(1.6f, 1.6f), style = Stroke(1.8f))
    drawLine(tint, Offset(12f, 4.5f), Offset(12f, 19.5f), strokeWidth = 1.8f, cap = StrokeCap.Round)
    listOf(8f, 11f, 14f).forEach {
        drawLine(tint, Offset(5.4f, it), Offset(8.6f, it), strokeWidth = 1.3f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.iconBell(tint: Color) {
    val p = Path().apply {
        moveTo(5.5f, 16.5f)
        lineTo(5.5f, 11f)
        cubicTo(5.5f, 7.4f, 8.4f, 4.8f, 12f, 4.8f)
        cubicTo(15.6f, 4.8f, 18.5f, 7.4f, 18.5f, 11f)
        lineTo(18.5f, 16.5f)
        close()
    }
    drawPath(p, tint, style = Stroke(1.9f))
    drawLine(tint, Offset(3.6f, 16.9f), Offset(20.4f, 16.9f), strokeWidth = 1.9f, cap = StrokeCap.Round)
    drawLine(tint, Offset(12f, 3f), Offset(12f, 4.9f), strokeWidth = 1.9f, cap = StrokeCap.Round)
    drawArc(tint, 0f, 180f, false, Offset(9.6f, 17.2f), Size(4.8f, 4.2f), style = Stroke(1.9f))
}

private fun DrawScope.iconPerson(tint: Color) {
    drawCircle(tint, 4.1f, Offset(12f, 8.2f), style = Stroke(1.9f))
    val p = Path().apply {
        moveTo(4.4f, 20.6f)
        cubicTo(4.4f, 16f, 7.8f, 13.6f, 12f, 13.6f)
        cubicTo(16.2f, 13.6f, 19.6f, 16f, 19.6f, 20.6f)
    }
    drawPath(p, tint, style = Stroke(1.9f))
}

private fun DrawScope.iconChat(tint: Color) {
    drawRoundRect(tint, Offset(2.6f, 4.4f), Size(18.8f, 13.4f), CornerRadius(5f, 5f), style = Stroke(1.9f))
    val tail = Path().apply {
        moveTo(8.4f, 17.4f); lineTo(8.4f, 21.4f); lineTo(12.8f, 17.6f); close()
    }
    drawPath(tail, tint)
    listOf(8f, 12f, 16f).forEach { drawCircle(tint, 1.25f, Offset(it, 11.1f)) }
}

private fun DrawScope.iconCamera(tint: Color) {
    drawRoundRect(tint, Offset(2.6f, 6.6f), Size(18.8f, 13f), CornerRadius(3.4f, 3.4f), style = Stroke(1.9f))
    val bump = Path().apply {
        moveTo(8.4f, 6.6f); lineTo(9.8f, 4f); lineTo(14.2f, 4f); lineTo(15.6f, 6.6f); close()
    }
    drawPath(bump, tint)
    drawCircle(tint, 3.9f, Offset(12f, 13.2f), style = Stroke(1.9f))
    drawCircle(tint, 1.1f, Offset(18f, 9.4f))
}

private fun DrawScope.iconMic(tint: Color) {
    // 마이크 통
    drawRoundRect(tint, Offset(8.6f, 2.6f), Size(6.8f, 11.4f), CornerRadius(3.4f, 3.4f))
    // 받침 — 통을 감싸는 반원과 대
    drawArc(
        tint, startAngle = 0f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(5.2f, 8.4f), size = Size(13.6f, 10.4f),
        style = Stroke(1.9f, cap = StrokeCap.Round),
    )
    drawLine(tint, Offset(12f, 18.8f), Offset(12f, 21.4f), strokeWidth = 1.9f, cap = StrokeCap.Round)
}

private fun DrawScope.iconGallery(tint: Color) {
    drawRoundRect(tint, Offset(3f, 4.4f), Size(18f, 15.2f), CornerRadius(3f, 3f), style = Stroke(1.9f))
    // 액자 속 산과 해. 사진첩임을 알리는 최소한의 표시다
    drawCircle(tint, 1.5f, Offset(8.4f, 9.4f))
    val hill = Path().apply {
        moveTo(4.4f, 17.4f); lineTo(10.2f, 11.4f); lineTo(14f, 15.2f)
        lineTo(16.4f, 12.8f); lineTo(19.6f, 16f); lineTo(19.6f, 17.4f); close()
    }
    drawPath(hill, tint)
}

/** 스피커. [on] 이면 음파 두 줄, 아니면 가위표. 몸통은 같아서 토글이 튀지 않는다. */
private fun DrawScope.iconSound(tint: Color, on: Boolean) {
    val body = Path().apply {
        moveTo(3.2f, 9.2f); lineTo(6.6f, 9.2f); lineTo(11.2f, 5f)
        lineTo(11.2f, 19f); lineTo(6.6f, 14.8f); lineTo(3.2f, 14.8f); close()
    }
    drawPath(body, tint)
    if (on) {
        drawArc(
            tint, startAngle = -52f, sweepAngle = 104f, useCenter = false,
            topLeft = Offset(9.6f, 7.4f), size = Size(9.2f, 9.2f),
            style = Stroke(1.9f, cap = StrokeCap.Round),
        )
        drawArc(
            tint, startAngle = -52f, sweepAngle = 104f, useCenter = false,
            topLeft = Offset(9.6f, 4.2f), size = Size(15.6f, 15.6f),
            style = Stroke(1.9f, cap = StrokeCap.Round),
        )
    } else {
        drawLine(tint, Offset(14.6f, 9.6f), Offset(20.4f, 14.4f), 1.9f, StrokeCap.Round)
        drawLine(tint, Offset(20.4f, 9.6f), Offset(14.6f, 14.4f), 1.9f, StrokeCap.Round)
    }
}

private fun DrawScope.iconClock(tint: Color) {
    drawCircle(tint, 8.6f, Offset(12f, 12f), style = Stroke(1.9f))
    drawLine(tint, Offset(12f, 12f), Offset(12f, 7.2f), strokeWidth = 1.9f, cap = StrokeCap.Round)
    drawLine(tint, Offset(12f, 12f), Offset(15.6f, 13.8f), strokeWidth = 1.9f, cap = StrokeCap.Round)
}

private fun DrawScope.iconPin(tint: Color) {
    val p = Path().apply {
        moveTo(12f, 21.5f)
        cubicTo(6.5f, 14.6f, 4.4f, 11.8f, 4.4f, 9.1f)
        cubicTo(4.4f, 5f, 7.8f, 2.4f, 12f, 2.4f)
        cubicTo(16.2f, 2.4f, 19.6f, 5f, 19.6f, 9.1f)
        cubicTo(19.6f, 11.8f, 17.5f, 14.6f, 12f, 21.5f)
        close()
    }
    drawPath(p, tint, style = Stroke(1.9f))
    drawCircle(tint, 2.9f, Offset(12f, 9f), style = Stroke(1.9f))
}

/** 산책 기록 — 발자국 두 개 */
private fun DrawScope.iconPaws(tint: Color) {
    drawPawStamp(Offset(8f, 9f), 4.4f, tint)
    drawPawStamp(Offset(15.5f, 16f), 4.4f, tint)
}

private fun DrawScope.iconSend(tint: Color) {
    val p = Path().apply {
        moveTo(9f, 5.5f); lineTo(16.5f, 12f); lineTo(9f, 18.5f)
    }
    drawPath(p, tint, style = Stroke(2.6f, cap = StrokeCap.Round))
}

private fun DrawScope.iconChevronRight(tint: Color) {
    val p = Path().apply {
        moveTo(10f, 6.5f); lineTo(15.5f, 12f); lineTo(10f, 17.5f)
    }
    drawPath(p, tint, style = Stroke(2f, cap = StrokeCap.Round))
}

private fun DrawScope.iconCaretDown(tint: Color) {
    val p = Path().apply {
        moveTo(7.5f, 10f); lineTo(12f, 14.5f); lineTo(16.5f, 10f); close()
    }
    drawPath(p, tint)
}

private fun DrawScope.iconSun(tint: Color) {
    drawCircle(tint, 5f, Offset(12f, 12f))
    for (i in 0 until 8) {
        val a = PI * i / 4.0
        val c = cos(a).toFloat()
        val s = sin(a).toFloat()
        drawLine(
            tint,
            Offset(12f + c * 7.4f, 12f + s * 7.4f),
            Offset(12f + c * 10f, 12f + s * 10f),
            strokeWidth = 1.9f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.iconHeart(tint: Color) {
    val p = Path().apply {
        moveTo(12f, 20.6f)
        cubicTo(3.2f, 14.8f, 2.2f, 10.4f, 4.6f, 7.4f)
        cubicTo(7f, 4.4f, 10.8f, 5.2f, 12f, 8.4f)
        cubicTo(13.2f, 5.2f, 17f, 4.4f, 19.4f, 7.4f)
        cubicTo(21.8f, 10.4f, 20.8f, 14.8f, 12f, 20.6f)
        close()
    }
    drawPath(p, tint)
}

/** 시안의 "오늘의 한 마디" 노트 점선 테두리에 쓰는 효과. */
fun dashEffect(): PathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 7f), 0f)
