package com.daengs.app.ui

import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
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
    Mic, Gallery, Sound, SoundOff,
    TerritoryPole,

    // 날씨. 해만 있으면 비 오는 날에도 해가 뜬다.
    Moon, Cloud, CloudRain, CloudSnow,

    // 보행 영상.
    Video, VideoLibrary, Play, Chart, Compare, Check, Close, Trash, Bulb, Joint,

    /** 배웅한 아이 표시. **이 아이콘만 tint 를 안 쓴다** — 무지개는 색이 곧 뜻이다. */
    Rainbow,

    /**
     * 화면 눕히기·세우기.
     *
     * 산책 화면이 같은 뜻으로 제 그림을 따로 갖고 있었다(`WalkTools` 의 `ROTATE`).
     * 이머시브에도 같은 버튼이 생기면서 두 벌이 되므로 여기로 올린다.
     */
    Rotate,
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
                DaengsIcon.TerritoryPole -> iconTerritoryPole(tint)
                DaengsIcon.Rotate -> {
                    fun line(x: Float, y: Float, a: Float, b: Float) =
                        drawLine(tint, Offset(x, y), Offset(a, b), 1.7f)
                    rotate(-30f, pivot = Offset(12f, 12f)) {
                        drawRoundRect(
                            tint,
                            Offset(8.2f, 4.8f),
                            androidx.compose.ui.geometry.Size(7.6f, 14.4f),
                            androidx.compose.ui.geometry.CornerRadius(2f),
                            style = Stroke(1.5f),
                        )
                    }
                    drawArc(tint, 205f, 110f, false, Offset(1f, 1f),
                        androidx.compose.ui.geometry.Size(22f, 22f), style = Stroke(1.5f))
                    line(18f, 3f, 20f, 6f); line(20f, 6f, 16f, 6f)
                    drawArc(tint, 25f, 110f, false, Offset(1f, 1f),
                        androidx.compose.ui.geometry.Size(22f, 22f), style = Stroke(1.5f))
                    line(6f, 21f, 4f, 18f); line(4f, 18f, 8f, 18f)
                }
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
                DaengsIcon.Rainbow -> iconRainbow()
                DaengsIcon.Moon -> iconMoon(tint)
                DaengsIcon.Cloud -> iconCloud(tint)
                DaengsIcon.CloudRain -> iconCloudRain(tint)
                DaengsIcon.CloudSnow -> iconCloudSnow(tint)
                DaengsIcon.Heart -> iconHeart(tint)
                DaengsIcon.Mic -> iconMic(tint)
                DaengsIcon.Gallery -> iconGallery(tint)
                DaengsIcon.Sound -> iconSound(tint, on = true)
                DaengsIcon.SoundOff -> iconSound(tint, on = false)
                DaengsIcon.Video -> iconVideo(tint)
                DaengsIcon.VideoLibrary -> iconVideoLibrary(tint)
                DaengsIcon.Play -> iconPlay(tint)
                DaengsIcon.Chart -> iconChart(tint)
                DaengsIcon.Compare -> iconCompare(tint)
                DaengsIcon.Check -> iconCheck(tint)
                DaengsIcon.Close -> iconClose(tint)
                DaengsIcon.Trash -> iconTrash(tint)
                DaengsIcon.Bulb -> iconBulb(tint)
                DaengsIcon.Joint -> iconJoint(tint)
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

/** 영상 촬영. 몸통에 재생 삼각형 하나. */
private fun DrawScope.iconVideo(tint: Color) {
    drawRoundRect(tint, Offset(2.6f, 5.4f), Size(18.8f, 13.2f), CornerRadius(3.4f, 3.4f), style = Stroke(1.9f))
    val play = Path().apply {
        moveTo(9.8f, 8.6f); lineTo(15.6f, 12f); lineTo(9.8f, 15.4f); close()
    }
    drawPath(play, tint)
}

/**
 * 영상 불러오기. 뒤에 한 장이 더 겹쳐 있어 **여럿 중 하나 고르기**로 읽힌다.
 *
 * 촬영 아이콘과 몸통이 같아서, 겹친 장이 없으면 21dp 에서 둘이 구분되지 않는다.
 * 앞 장은 흰색으로 한 번 채워 뒤 장을 가린다 ([iconHome] 의 문과 같은 방법).
 */
private fun DrawScope.iconVideoLibrary(tint: Color) {
    drawRoundRect(tint, Offset(6.4f, 2.8f), Size(15f, 10.8f), CornerRadius(3f, 3f), style = Stroke(1.9f))
    drawRoundRect(Color.White, Offset(2.6f, 7.6f), Size(15f, 13f), CornerRadius(3f, 3f))
    drawRoundRect(tint, Offset(2.6f, 7.6f), Size(15f, 13f), CornerRadius(3f, 3f), style = Stroke(1.9f))
    val play = Path().apply {
        moveTo(8.2f, 10.6f); lineTo(13.2f, 14.1f); lineTo(8.2f, 17.6f); close()
    }
    drawPath(play, tint)
}

/**
 * 재생. 썸네일 한가운데 얹는 삼각형이라 **테두리 원을 안 그린다** — 원은 화면이
 * 흰 동그라미로 따로 깔고, 여기서 또 그리면 두 겹이 된다.
 */
private fun DrawScope.iconPlay(tint: Color) {
    val p = Path().apply {
        moveTo(8.4f, 5.4f); lineTo(18.6f, 12f); lineTo(8.4f, 18.6f); close()
    }
    drawPath(p, tint)
}

/** 결과 보기. 막대 셋. 높이가 다 달라야 "재 놓은 것" 으로 읽힌다. */
private fun DrawScope.iconChart(tint: Color) {
    listOf(4.4f to 14.4f, 10.2f to 7.4f, 16f to 11.2f).forEach { (x, top) ->
        drawRoundRect(tint, Offset(x, top), Size(3.6f, 19.6f - top), CornerRadius(1.5f, 1.5f))
    }
}

/** 비교. 위아래로 엇갈린 화살표 둘 — 두 기록을 맞바꿔 본다는 뜻이다. */
private fun DrawScope.iconCompare(tint: Color) {
    drawLine(tint, Offset(3.6f, 8.6f), Offset(19f, 8.6f), 1.9f, StrokeCap.Round)
    drawPath(
        Path().apply { moveTo(15.6f, 5.2f); lineTo(19.8f, 8.6f); lineTo(15.6f, 12f) },
        tint,
        style = Stroke(1.9f, cap = StrokeCap.Round),
    )
    drawLine(tint, Offset(20.4f, 15.4f), Offset(5f, 15.4f), 1.9f, StrokeCap.Round)
    drawPath(
        Path().apply { moveTo(8.4f, 12f); lineTo(4.2f, 15.4f); lineTo(8.4f, 18.8f) },
        tint,
        style = Stroke(1.9f, cap = StrokeCap.Round),
    )
}

/** 완료 표시. 진행 카드의 끝난 줄에 쓴다. */
private fun DrawScope.iconCheck(tint: Color) {
    drawPath(
        Path().apply { moveTo(5.4f, 12.6f); lineTo(10f, 17f); lineTo(18.6f, 7.4f) },
        tint,
        style = Stroke(2.4f, cap = StrokeCap.Round),
    )
}

private fun DrawScope.iconClose(tint: Color) {
    drawLine(tint, Offset(6.4f, 6.4f), Offset(17.6f, 17.6f), 2.1f, StrokeCap.Round)
    drawLine(tint, Offset(17.6f, 6.4f), Offset(6.4f, 17.6f), 2.1f, StrokeCap.Round)
}

/** 삭제. 뚜껑·손잡이·통. */
private fun DrawScope.iconTrash(tint: Color) {
    drawLine(tint, Offset(3.8f, 6.4f), Offset(20.2f, 6.4f), 1.9f, StrokeCap.Round)
    drawPath(
        Path().apply { moveTo(9.2f, 6.2f); lineTo(9.2f, 3.8f); lineTo(14.8f, 3.8f); lineTo(14.8f, 6.2f) },
        tint,
        style = Stroke(1.9f, cap = StrokeCap.Round),
    )
    drawPath(
        Path().apply {
            moveTo(5.8f, 6.8f); lineTo(6.8f, 20.2f); lineTo(17.2f, 20.2f); lineTo(18.2f, 6.8f)
        },
        tint,
        style = Stroke(1.9f, cap = StrokeCap.Round),
    )
}

/** 안내 전구. 시안의 💡 자리 — 이모지는 기기마다 그림이 달라서 직접 그린다. */
private fun DrawScope.iconBulb(tint: Color) {
    drawCircle(tint, 5.6f, Offset(12f, 9.6f), style = Stroke(1.9f))
    drawLine(tint, Offset(9.4f, 16.6f), Offset(14.6f, 16.6f), 1.9f, StrokeCap.Round)
    drawLine(tint, Offset(10.2f, 19.6f), Offset(13.8f, 19.6f), 1.9f, StrokeCap.Round)
}

/**
 * 관절 지표 한 줄 앞에 붙는 표시. 뼈 두 마디가 한 점에서 꺾인 모양이다.
 *
 * 발바닥([iconPaws])을 쓰지 않는 이유: 발바닥은 이 앱에서 **산책**을 뜻하는 자리라
 * 비교표에 두면 걸은 기록과 잰 지표가 섞여 보인다.
 */
private fun DrawScope.iconJoint(tint: Color) {
    drawLine(tint, Offset(5.4f, 5.4f), Offset(12f, 12f), 2.1f, StrokeCap.Round)
    drawLine(tint, Offset(12f, 12f), Offset(7.6f, 19f), 2.1f, StrokeCap.Round)
    drawCircle(Color.White, 3.4f, Offset(12f, 12f))
    drawCircle(tint, 3.4f, Offset(12f, 12f), style = Stroke(1.9f))
    drawLine(tint, Offset(15f, 12f), Offset(19.4f, 12f), 2.1f, StrokeCap.Round)
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

/**
 * 무지개. 배웅한 아이 옆에 붙는다.
 *
 * **[tint] 를 안 받는다.** 다른 아이콘은 색이 장식이지만 무지개는 색이 곧 뜻이라,
 * 한 색으로 칠하면 아무것도 아닌 반원이 된다.
 *
 * 일곱 겹은 24px 안에서 뭉친다. 다섯 겹으로 줄이고 두께를 키웠다.
 */
private fun DrawScope.iconRainbow() {
    val bands = listOf(
        Color(0xFFE86A6A),
        Color(0xFFF0A23C),
        Color(0xFFF2D24B),
        Color(0xFF6FBF73),
        Color(0xFF5B8FD9),
    )
    val stroke = 1.7f
    bands.forEachIndexed { i, color ->
        val r = 9.5f - i * stroke
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(12f - r, 16f - r),
            size = Size(r * 2f, r * 2f),
            style = Stroke(width = stroke, cap = StrokeCap.Butt),
        )
    }
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

/**
 * 초승달. **밤·맑음의 아이콘이다.**
 *
 * 원에서 원을 뺀다 — 손으로 맞춘 큐빅보다 정확하고 짧다. 파낼 원을 오른쪽 위로
 * 밀면 왼쪽 아래가 부푼 초승달이 남는다.
 *
 * 별은 안 찍는다. 이 아이콘이 서는 자리가 17dp 라 점 하나는 먼지로 보인다.
 */
private fun DrawScope.iconMoon(tint: Color) {
    val outer = Path().apply { addOval(Rect(3.6f, 3.6f, 20.4f, 20.4f)) }
    val inner = Path().apply { addOval(Rect(8.2f, -1.0f, 25.0f, 15.8f)) }
    // op 는 **받는 쪽**을 결과로 채운다. 빈 Path 에 넣어야 outer 가 안 망가진다.
    drawPath(Path().apply { op(outer, inner, PathOperation.Difference) }, tint)
}

/**
 * 비·눈이 함께 쓰는 구름.
 *
 * 아래 끝이 y=14.8 이라 강수 자리로 16~21 이 남는다. 좌우는 5.6~20.8 로 24 안에 든다.
 */
private fun DrawScope.cloudBody(tint: Color) {
    drawCircle(tint, 3.6f, Offset(9.2f, 10.4f))
    drawCircle(tint, 4.6f, Offset(14.2f, 9.6f))
    drawCircle(tint, 3.2f, Offset(17.6f, 12.2f))
    drawRoundRect(tint, Offset(5.6f, 10.2f), Size(15.2f, 4.6f), CornerRadius(2.3f, 2.3f))
}

/**
 * 흐림. 구름만 둔다.
 *
 * 비·눈과 달리 아래가 비어서 그림이 위로 쏠려 보이므로 **조금 내려 그린다.**
 * 강수 자리(16~21)의 절반쯤을 먹는 위치다.
 */
private fun DrawScope.iconCloud(tint: Color) {
    translate(top = 3f) { cloudBody(tint) }
}

/** 비. 빗줄기 셋을 사선으로, 가운데를 길게 — 그래야 흩뿌리는 느낌이 난다. */
private fun DrawScope.iconCloudRain(tint: Color) {
    cloudBody(tint)
    drawLine(tint, Offset(8.8f, 16.6f), Offset(7.4f, 20.2f), 1.9f, StrokeCap.Round)
    drawLine(tint, Offset(12.6f, 16.6f), Offset(11.2f, 21.4f), 1.9f, StrokeCap.Round)
    drawLine(tint, Offset(16.4f, 16.6f), Offset(15.0f, 20.2f), 1.9f, StrokeCap.Round)
}

/**
 * 눈. 점 셋을 지그재그로 둔다.
 *
 * 6갈래 눈꽃은 17dp 에서 얼룩이 된다. 점이면 빗줄기와 실루엣이 확실히 갈린다.
 */
private fun DrawScope.iconCloudSnow(tint: Color) {
    cloudBody(tint)
    drawCircle(tint, 1.15f, Offset(8.8f, 17.8f))
    drawCircle(tint, 1.15f, Offset(12.4f, 20.4f))
    drawCircle(tint, 1.15f, Offset(16.0f, 17.8f))
}
