package com.daengs.app.ui.dogcard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.miniroom.art.rememberAssetImage
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// 카드 = **저쪽 완성 카드에서 네 자리를 비운 판** + 우리 것
//
// 저쪽(`SAJOYO/DAENGS_CARDS`)이 주는 `*-card.webp` 는 프레임·이름·번호·얼굴이 전부
// 인쇄된 완성 카드다. `tools/punch_card_slots.py` 가 거기서 네 자리를 비워
// `*-card-slots.webp` 를 만든다.
//
//     아바타 원 · 큰 얼굴   → 알파를 깎아 뚫는다
//     이름 · 번호           → 바 색으로 메운다 (뚫으면 홀로그램 무늬까지 사라진다)
//
// 앱은 이렇게 그린다.
//
//     우리 개 얼굴 (두 자리)  →  비운 카드를 그 위에  →  글자를 빈 바에
//
// **얼굴을 카드 위가 아니라 아래에 둔다.** 그래야 구멍 테두리의 잎이 얼굴 가장자리를
// 물어서 끼어 든 것으로 보이고, **누끼에서 제일 어려운 털 가장자리가 그 밑으로
// 숨는다.** 위에 얹으면 오려 붙인 스티커가 된다.
//
// ## 코틀린으로 채소를 그리려던 것은 버렸다
//
// `DrawScope` 에서 sin/cos 로 잎을 그려 봤고, 베지에로 그린 잎을 회전시켜 배치하는
// VectorDrawable 도 만들어 봤다. 둘 다 종이를 오려 붙인 것처럼 나왔다. 저쪽 원화는
// 사진에 가깝고 우리가 그린 것은 아무리 만져도 그 옆에 못 선다. **그림은 저쪽이
// 그리고 우리는 자리를 비운다** 가 맞다.
// ---------------------------------------------------------------------------

/** 카드 크기 대비 % 로 잡은 타원. `tools/punch_card_slots.py` 가 재서 준다. */
@Immutable
data class Hole(val cx: Float, val cy: Float, val rx: Float, val ry: Float)

/** 카드 크기 대비 % 로 잡은 사각형. */
@Immutable
data class Slot(val x0: Float, val y0: Float, val x1: Float, val y1: Float)

@Immutable
data class CardTemplate(
    val id: String,
    val label: String,
    /** `assets/` 아래 경로. 네 자리를 비운 카드. */
    val art: String,
    val ratio: Float,
    val face: Hole,
    val avatar: Hole,
    val name: Slot,
    val code: Slot,
)

val CABBAGE_CARD = CardTemplate(
    id = "cabbage",
    label = "배추",
    art = "neo-hologram/art/cabbage-card-slots.webp",
    ratio = 810f / 1125f,
    face = Hole(52.84f, 44.71f, 19.81f, 14.27f),
    avatar = Hole(13.09f, 9.20f, 8.06f, 5.80f),
    name = Slot(24.0f, 4.0f, 70.0f, 9.8f),
    code = Slot(75.0f, 4.8f, 94.0f, 9.4f),
)

val SWEET_POTATO_CARD = CardTemplate(
    id = "sweet-potato",
    label = "고구마",
    art = "neo-hologram/art/sweet-potato-card-slots.webp",
    ratio = 816f / 1125f,
    face = Hole(48.84f, 40.76f, 12.13f, 8.80f),
    avatar = Hole(13.36f, 9.16f, 8.13f, 5.90f),
    name = Slot(24.0f, 4.0f, 70.0f, 9.8f),
    code = Slot(75.0f, 4.8f, 94.0f, 9.4f),
)

val CARD_TEMPLATES = listOf(CABBAGE_CARD, SWEET_POTATO_CARD)

/**
 * 카드 한 장. [face] 가 null 이면 구멍이 빈 채로 그려진다 — 판만 볼 때 쓴다.
 *
 * @param name 이름바에 쓸 글자. 저쪽 카드에서는 "CABBAGE NEO" 자리다
 * @param code 번호판. 저쪽은 "NEO-0824"
 */
@Composable
fun PersonalCard(
    template: CardTemplate,
    face: ImageBitmap?,
    name: String,
    code: String,
    modifier: Modifier = Modifier,
) {
    val art = rememberAssetImage(template.art)
    val measurer = rememberTextMeasurer()
    Box(modifier.aspectRatio(template.ratio)) {
        Canvas(Modifier.fillMaxSize()) {
            if (face != null) {
                drawInHole(face, template.face)
                drawInHole(face, template.avatar)
            }
            art?.let {
                drawImage(
                    image = it,
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = FilterQuality.High,
                )
            }
            // 글자는 카드 **위에** 그린다. 비운 바가 이미 카드 안에 있으므로,
            // 아래에 두면 카드가 덮어 버린다.
            drawSlotText(measurer, name, template.name, TITLE)
            drawSlotText(measurer, code, template.code, CODE)
        }
    }
}

/**
 * 구멍에 얼굴을 끼운다. **구멍보다 조금 크게** 그린다 — 딱 맞추면 가장자리에 틈이
 * 보이는데, 넘치는 만큼은 어차피 카드가 덮는다.
 */
private fun DrawScope.drawInHole(face: ImageBitmap, hole: Hole) {
    val cx = size.width * hole.cx / 100f
    val cy = size.height * hole.cy / 100f
    val rx = size.width * hole.rx / 100f * 1.12f
    val ry = size.height * hole.ry / 100f * 1.12f

    val clip = Path().apply { addOval(Rect(cx - rx, cy - ry, cx + rx, cy + ry)) }
    clipPath(clip) {
        // 짧은 쪽을 구멍에 맞춘다. 긴 쪽이 넘치는 것은 카드가 가린다.
        val ratio = face.width.toFloat() / face.height
        val h = maxOf(ry * 2f, rx * 2f / ratio)
        val w = h * ratio
        drawImage(
            image = face,
            dstOffset = IntOffset((cx - w / 2f).roundToInt(), (cy - h / 2f).roundToInt()),
            dstSize = IntSize(w.roundToInt(), h.roundToInt()),
            filterQuality = FilterQuality.High,
        )
    }
}

/**
 * 비운 바에 글자를 앉힌다.
 *
 * 글자 크기는 **바 높이에서 구한다.** dp 로 박아 두면 카드가 그리드에서 작아질 때
 * 바 밖으로 넘친다 — 카드는 확대 뷰와 그리드에서 크기가 다르다.
 */
/**
 * 글자 결을 원화에 맞춘다.
 *
 * 저쪽 카드는 제목이 **세리프 스몰캡스**("Cabbage Neo")고 번호는 **굵은 산세리프**
 * ("NEO-0824")다. 둘을 세리프 하나로 뭉뚱그렸더니 번호판이 카드와 따로 놀았다.
 *
 * 자간도 다르다 — 제목은 넓게 벌어져 있고 번호는 붙어 있다.
 */
private data class SlotFace(
    val family: FontFamily,
    val weight: FontWeight,
    val letterSpacing: Float,
    val fill: Float,
)

private val TITLE = SlotFace(FontFamily.Serif, FontWeight.Bold, 0.06f, 0.60f)
private val CODE = SlotFace(FontFamily.SansSerif, FontWeight.Black, 0.01f, 0.58f)

/** 생일을 번호판 글자로. 저쪽 `NEO-0824` 가 월일이라 그 자리에 그대로 들어간다. */
fun birthCode(month: Int, day: Int): String = "NEO-%02d%02d".format(month, day)

private fun DrawScope.drawSlotText(
    measurer: TextMeasurer,
    text: String,
    slot: Slot,
    face: SlotFace,
) {
    if (text.isBlank()) return
    val left = size.width * slot.x0 / 100f
    val top = size.height * slot.y0 / 100f
    val right = size.width * slot.x1 / 100f
    val bottom = size.height * slot.y1 / 100f
    val boxW = right - left
    val boxH = bottom - top
    if (boxW <= 0f || boxH <= 0f) return

    val base = boxH * face.fill
    val style = TextStyle(
        color = Color.White,
        fontSize = base.toSp(),
        fontWeight = face.weight,
        fontFamily = face.family,
        letterSpacing = (base * face.letterSpacing).toSp(),
        textAlign = TextAlign.Center,
    )
    val laid = measurer.measure(text, style, maxLines = 1)
    // 넘치면 줄인다. 이름이 긴 개도 있다.
    val scale = minOf(1f, boxW / laid.size.width.toFloat())
    val fitted = if (scale < 1f) {
        measurer.measure(
            text,
            style.copy(
                fontSize = (base * scale).toSp(),
                letterSpacing = (base * scale * face.letterSpacing).toSp(),
            ),
            maxLines = 1,
        )
    } else {
        laid
    }
    drawText(
        textLayoutResult = fitted,
        topLeft = Offset(
            left + (boxW - fitted.size.width) / 2f,
            top + (boxH - fitted.size.height) / 2f,
        ),
    )
}

private fun Float.toSp() = (this / 2.6f).sp

// -- 프리뷰 ------------------------------------------------------------------
//
// 얼굴 없이 판만 본다. 실제 얼굴은 누끼가 있어야 해서 실기기에서 본다.

@Preview(name = "배추 판", widthDp = 220, heightDp = 310)
@Composable
private fun CabbageTemplatePreview() {
    PersonalCard(CABBAGE_CARD, face = null, name = "몽이", code = birthCode(4, 12), modifier = Modifier.size(200.dp))
}

@Preview(name = "고구마 판 · 작게", widthDp = 130, heightDp = 190)
@Composable
private fun SweetPotatoSmallPreview() {
    PersonalCard(SWEET_POTATO_CARD, face = null, name = "몽이", code = birthCode(4, 12), modifier = Modifier.size(110.dp))
}
