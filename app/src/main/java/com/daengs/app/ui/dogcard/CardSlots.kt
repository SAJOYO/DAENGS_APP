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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.R
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
    /**
     * 이름바·번호판 자리. **null 이면 안 그린다.**
     *
     * 12장 공통이 아니다 — `tools/punch_card_slots.py` 는 공통이라고 적어 뒀는데,
     * 피망·당근·가지·단호박은 검은 프레임에 제목이 가운데 오고 번호판이 아래에
     * 따로 있다. 공통 상수 하나로 두면 그 넷에서 **엉뚱한 자리에 글자가 찍힌다.**
     * 받은 판은 이름·번호 자리가 이미 비워져 있어서 안 그려도 카드가 성립한다.
     * 실기기에서 재는 대로 한 장씩 채운다 (`docs/card-holes.md`).
     */
    val name: Slot?,
    val code: Slot?,
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

/**
 * 새로 들어온 열 장은 1080×1440 한 판이다. 배추·고구마만 저쪽에서 따로 온 원화라
 * 캔버스가 다르다 (810×1125 · 816×1125). WebP 헤더로 확인했다.
 */
private const val SLOTS_RATIO = 1080f / 1440f

val PEPPER_CARD = CardTemplate(
    id = "pepper",
    label = "피망",
    art = "neo-hologram/art/pepper-card-slots.webp",
    ratio = SLOTS_RATIO,
    face = Hole(51.76f, 51.67f, 18.44f, 13.83f),
    avatar = Hole(13.80f, 13.54f, 8.78f, 6.59f),
    name = Slot(59.44f, 6.25f, 76.11f, 12.08f),
    code = null,
)

val EGGPLANT_CARD = CardTemplate(
    id = "eggplant",
    label = "가지",
    art = "neo-hologram/art/eggplant-card-slots.webp",
    ratio = SLOTS_RATIO,
    face = Hole(48.24f, 56.32f, 18.64f, 13.98f),
    avatar = Hole(14.26f, 13.82f, 9.08f, 6.81f),
    name = Slot(70.56f, 7.99f, 76.85f, 13.61f),
    code = null,
)

val CARROT_CARD = CardTemplate(
    id = "carrot",
    label = "당근",
    art = "neo-hologram/art/carrot-card-slots.webp",
    ratio = SLOTS_RATIO,
    face = Hole(50.83f, 50.76f, 13.89f, 10.42f),
    avatar = Hole(14.07f, 13.68f, 8.73f, 6.55f),
    name = Slot(63.89f, 6.25f, 70.93f, 13.19f),
    code = null,
)

val DANHOBAK_CARD = CardTemplate(
    id = "danhobak",
    label = "단호박",
    art = "neo-hologram/art/danhobak-card-slots.webp",
    ratio = SLOTS_RATIO,
    face = Hole(51.57f, 50.56f, 15.35f, 11.51f),
    avatar = Hole(13.80f, 13.68f, 8.73f, 6.55f),
    name = Slot(73.52f, 6.25f, 80.56f, 12.64f),
    code = null,
)

val MUSHROOM_CARD = CardTemplate(
    id = "mushroom",
    label = "버섯",
    art = "neo-hologram/art/mushroom-card-slots.webp",
    ratio = SLOTS_RATIO,
    face = Hole(51.57f, 44.93f, 14.22f, 10.67f),
    avatar = Hole(14.44f, 9.58f, 7.73f, 5.80f),
    name = Slot(56.48f, 4.03f, 67.96f, 9.86f),
    code = null,
)

val BROCCOLI_CARD = CardTemplate(
    id = "broccoli",
    label = "브로콜리",
    art = "neo-hologram/art/broccoli-card-slots.webp",
    ratio = SLOTS_RATIO,
    face = Hole(51.48f, 51.53f, 10.02f, 7.52f),
    avatar = Hole(14.54f, 9.86f, 7.71f, 5.78f),
    name = Slot(53.52f, 4.03f, 67.22f, 9.86f),
    code = null,
)

val CUCUMBER_CARD = CardTemplate(
    id = "cucumber",
    label = "오이",
    art = "neo-hologram/art/cucumber-card-slots.webp",
    ratio = SLOTS_RATIO,
    face = Hole(50.56f, 47.43f, 10.29f, 7.71f),
    avatar = Hole(14.91f, 9.86f, 7.72f, 5.79f),
    name = Slot(57.22f, 4.1f, 69.44f, 9.72f),
    code = null,
)

val SPINACH_CARD = CardTemplate(
    id = "spinach",
    label = "시금치",
    art = "neo-hologram/art/spinach-card-slots.webp",
    ratio = SLOTS_RATIO,
    face = Hole(47.22f, 36.60f, 13.20f, 9.90f),
    avatar = Hole(14.72f, 9.79f, 7.73f, 5.80f),
    name = Slot(50.56f, 4.03f, 67.22f, 9.86f),
    code = null,
)

val TOMATO_CARD = CardTemplate(
    id = "tomato",
    label = "토마토",
    art = "neo-hologram/art/tomato-card-slots.webp",
    ratio = SLOTS_RATIO,
    face = Hole(51.94f, 46.18f, 15.74f, 11.80f),
    avatar = Hole(14.63f, 9.44f, 7.73f, 5.80f),
    name = Slot(51.3f, 3.82f, 67.22f, 9.44f),
    code = null,
)

val LETTUCE_CARD = CardTemplate(
    id = "lettuce",
    label = "상추",
    art = "neo-hologram/art/lettuce-card-slots.webp",
    ratio = SLOTS_RATIO,
    face = Hole(50.00f, 38.82f, 9.97f, 7.48f),
    avatar = Hole(13.06f, 12.85f, 7.71f, 5.78f),
    name = Slot(53.52f, 6.32f, 71.67f, 11.94f),
    code = null,
)

/**
 * 도감 순서(No.01~12)와 같다. 뽑기가 이 목록을 **균등**으로 뽑는다.
 *
 * id 는 `ui/dex/DexCards.kt` 의 `DEX_CARDS` 와 한 글자도 안 다르다. 그래서 뽑은
 * 카드를 도감 칸에 얹을 때 변환표가 필요 없다 — **우연히 맞은 것이라** 어긋나면
 * 조용히 카드가 사라진다. `CardTemplateTest` 가 그것을 잠근다.
 */
val CARD_TEMPLATES = listOf(
    CABBAGE_CARD, PEPPER_CARD, EGGPLANT_CARD, CARROT_CARD,
    DANHOBAK_CARD, MUSHROOM_CARD, BROCCOLI_CARD, CUCUMBER_CARD,
    SPINACH_CARD, SWEET_POTATO_CARD, TOMATO_CARD, LETTUCE_CARD,
)

/**
 * 구멍에 끼울 얼굴.
 *
 * **비트맵만으로는 부족하다.** 누끼는 목 아래가 서서히 흐려지며 끝나는데, 그
 * 꼬리까지 포함한 사각형을 구멍에 맞추면 얼굴이 꼬리가 넓은 쪽으로 밀린다.
 * [core] 는 또렷한 얼굴만의 자리다 — `Cutout.faceFor` 가 재어 준다.
 *
 * 구멍에는 **테두리를 안 두른 판**을 넣어야 한다. 흰 띠가 구멍 안으로 들어오면
 * 카드가 찢어진 자국처럼 보인다.
 */
@Immutable
data class CardFace(val image: ImageBitmap, val core: IntRect)

/**
 * 카드 한 장. [face] 가 null 이면 구멍이 빈 채로 그려진다 — 판만 볼 때 쓴다.
 *
 * @param name 이름바에 쓸 글자. 저쪽 카드에서는 "CABBAGE NEO" 자리다
 * @param code 번호판. 저쪽은 "NEO-0824"
 */
@Composable
fun PersonalCard(
    template: CardTemplate,
    face: CardFace?,
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

/** 구멍보다 이만큼 넓게 그린다. 넘치는 만큼은 카드가 덮어서 테두리 틈을 막는다. */
private const val CLIP_BLEED = 1.12f

/**
 * 또렷한 얼굴을 구멍보다 이만큼 크게 잡는다.
 *
 * **머리가 둥글지 않아서 필요하다.** 얼굴 자리를 구멍에 딱 맞추면 실루엣이 타원
 * 안으로 파고드는 자리(귀 옆 · 이마 위)마다 구멍 속이 비친다. 실기기에서 배추 ·
 * 고구마 양쪽에 같은 자리로 났다. 키우면 얼굴이 조금 더 잘리는 대신 구멍이 찬다.
 */
private const val CORE_OVERFILL = 1.15f

/**
 * 구멍에 얼굴을 끼운다.
 *
 * **비트맵 사각형이 아니라 [CardFace.core] 를 구멍에 맞춘다.** 사각형으로 맞추면
 * 목 아래로 흐려지는 꼬리까지 셈에 들어가서, 꼬리가 한쪽으로 퍼진 사진에서는
 * 머리가 반대쪽으로 밀리고 구멍 한쪽이 통째로 빈다.
 */
/**
 * 얼굴을 이 카드의 두 구멍에 끼운다. **`PersonalCard` 밖에서도 쓴다** — 도감이
 * 같은 카드를 그려야 하는데, 거기는 포일과 꾹 게이지가 함께 도는 `HoloCard` 안이다.
 * 합치는 규칙이 두 벌이 되면 뽑을 때와 도감에서 얼굴 자리가 달라진다.
 */
fun DrawScope.drawCardFace(face: CardFace, template: CardTemplate) {
    drawInHole(face, template.face)
    drawInHole(face, template.avatar)
}

/**
 * 합쳐 놓은 카드 한 장을 **임의의 사각형에** 그린다.
 *
 * 이머시브 진입 연출이 쓴다 — 거기서는 카드가 화면 한가운데에서 자라며 녹는데,
 * 그동안 보이는 것이 저쪽 완성 카드가 아니라 **우리 카드**여야 한다.
 *
 * 순서는 화면에서 그리는 것과 같다: 얼굴 → 자리를 비운 판 → 글자.
 */
fun DrawScope.drawPersonalCardAt(
    art: ImageBitmap,
    template: CardTemplate,
    face: CardFace?,
    measurer: TextMeasurer,
    name: String,
    code: String,
    at: Offset,
    box: Size,
    alpha: Float = 1f,
) {
    if (face != null) {
        drawInHoleOf(face, template.face, at, box)
        drawInHoleOf(face, template.avatar, at, box)
    }
    drawImage(
        image = art,
        dstOffset = IntOffset(at.x.roundToInt(), at.y.roundToInt()),
        dstSize = IntSize(box.width.roundToInt(), box.height.roundToInt()),
        alpha = alpha,
        filterQuality = FilterQuality.High,
    )
    drawSlotText(measurer, name, template.name, TITLE, at, box)
    drawSlotText(measurer, code, template.code, CODE, at, box)
}

/**
 * 이름·번호판만 **임의의 사각형에** 그린다. 이머시브 창틀이 쓴다 — 창틀은 그림이
 * 이미 비워져 있고 글자만 얹으면 된다.
 */
fun DrawScope.drawSlotTextAt(
    measurer: TextMeasurer,
    name: String,
    code: String,
    nameSlot: Slot?,
    codeSlot: Slot?,
    at: Offset,
    box: Size,
) {
    drawSlotText(measurer, name, nameSlot, TITLE, at, box)
    drawSlotText(measurer, code, codeSlot, CODE, at, box)
}

/** 이름·번호판. 카드 **위에** 그린다 — 아래에 두면 카드가 덮는다. */
fun DrawScope.drawCardText(
    measurer: TextMeasurer,
    template: CardTemplate,
    name: String,
    code: String,
) {
    drawSlotText(measurer, name, template.name, TITLE)
    drawSlotText(measurer, code, template.code, CODE)
}

private fun DrawScope.drawInHole(face: CardFace, hole: Hole) =
    drawInHoleOf(face, hole, Offset.Zero, size)

/**
 * 얼굴을 **임의의 사각형 안**의 구멍에 끼운다.
 *
 * 카드는 화면을 통째로 쓰지만 이머시브의 주인공 누끼는 무대 한가운데의 한 조각이라,
 * 자리와 크기를 받아야 한다. 규칙은 [drawInHole] 과 한 벌이다 — 두 벌이 되면 카드와
 * 무대에서 얼굴 자리가 달라진다.
 *
 * @param at 사각형의 왼쪽 위
 * @param box 사각형 크기. [hole] 은 이 크기 대비 % 다
 */
fun DrawScope.drawInHoleOf(face: CardFace, hole: Hole, at: Offset, box: Size) {
    val core = face.core
    if (core.width <= 0 || core.height <= 0) return

    val cx = at.x + box.width * hole.cx / 100f
    val cy = at.y + box.height * hole.cy / 100f
    val rx = box.width * hole.rx / 100f * CLIP_BLEED
    val ry = box.height * hole.ry / 100f * CLIP_BLEED

    // 또렷한 얼굴이 구멍을 덮을 만큼 키운다. 짧은 쪽이 아니라 **모자란 쪽**에
    // 맞춰야 구멍이 찬다.
    val scale = maxOf(rx * 2f / core.width, ry * 2f / core.height) * CORE_OVERFILL

    val clip = Path().apply { addOval(Rect(cx - rx, cy - ry, cx + rx, cy + ry)) }
    clipPath(clip) {
        // 가로는 비트맵 한가운데가 아니라 **또렷한 얼굴의 한가운데**에 맞춘다.
        val left = cx - (core.left + core.width / 2f) * scale
        // 세로는 한가운데가 아니라 **턱을 구멍 아래에 건다.**
        //
        // 구멍보다 크게 그리니 어딘가는 잘려야 하는데, 이마와 귀가 잘리는 것은
        // 괜찮고 **코가 잘리면 개로 안 보인다.** 한가운데에 맞췄더니 코가 먼저
        // 잘리고 이마만 남았다 — 실기기에서 봤다.
        val top = cy + box.height * hole.ry / 100f - core.bottom * scale
        drawImage(
            image = face.image,
            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
            dstSize = IntSize(
                (face.image.width * scale).roundToInt(),
                (face.image.height * scale).roundToInt(),
            ),
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

/**
 * 카드 이름에 쓰는 글씨체 — 케리스 케듀체 Bold.
 *
 * **한글에 `FontFamily.Serif` 는 사실상 안 먹는다.** 안드로이드 기본 serif 에는 한글이
 * 없어서 시스템 한글 폰트로 떨어지고, 결국 기기 기본 고딕이 나온다. 저쪽 원화의
 * `CABBAGE NEO` 는 각진 영문 서체인데 우리 한글만 딴 세상이었다.
 *
 * **굵기 하나만 넣는다.** 한글 폰트는 글자 수가 많아 굵기마다 0.4~1MB 씩 는다.
 * 그리고 윤곽선(Line) 판은 안 쓴다 — 이름이 얹히는 자리가 홀로그램 무지개라
 * 획 속으로 배경이 비쳐서 뭉개지고, 그리드에서 40dp 로 줄면 아예 사라진다.
 *
 * **라이선스는 확인했다** (눈누, 2026-09-02). 임베딩·상업적 이용·재배포 모두 허용이고
 * 출처 표기 의무도 없다. 금지된 것은 폰트 파일 자체를 유료로 파는 것뿐이라 해당 없다.
 * APK 에 파일이 들어가 배포되는 것이 "임베딩" 이다.
 */
private val KeduBold = FontFamily(Font(R.font.keris_kedu_bold, FontWeight.Bold))

private val TITLE = SlotFace(KeduBold, FontWeight.Bold, 0.06f, 0.60f)
private val CODE = SlotFace(FontFamily.SansSerif, FontWeight.Black, 0.01f, 0.58f)

/**
 * 생일을 번호판 글자로.
 *
 * **접두사가 `NEO` 였다.** 저쪽 카드에 인쇄된 `NEO-0824` 를 그대로 흉내 낸 것인데,
 * 네오는 이 저장소를 만든 사람의 강아지 이름이다. 다른 사람이 쓰면 남의 개 이름이
 * 자기 카드 번호판에 찍힌다. 앱 이름을 딴 `DG` 로 바꾼다.
 *
 * 자릿수는 그대로 둔다 — 번호판 칸이 좁아서 긴 접두사는 글자가 줄어든다.
 */
fun birthCode(month: Int, day: Int): String = "DG-%02d%02d".format(month, day)

private fun DrawScope.drawSlotText(
    measurer: TextMeasurer,
    text: String,
    slot: Slot?,
    face: SlotFace,
    at: Offset = Offset.Zero,
    box: Size = size,
) {
    if (slot == null || text.isBlank()) return
    val left = at.x + box.width * slot.x0 / 100f
    val top = at.y + box.height * slot.y0 / 100f
    val right = at.x + box.width * slot.x1 / 100f
    val bottom = at.y + box.height * slot.y1 / 100f
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

@Preview(name = "피망 판 · 구멍이 제일 큰 카드", widthDp = 220, heightDp = 310)
@Composable
private fun PepperTemplatePreview() {
    PersonalCard(PEPPER_CARD, face = null, name = "몽이", code = birthCode(4, 12), modifier = Modifier.size(200.dp))
}

// 피망 rx 18.44% · 상추 9.97% 로 **구멍이 3.4배 차이 난다**. 같은 얼굴을 끼워도
// 한쪽은 카드를 채우고 한쪽은 잎 사이의 점이 된다. 둘을 나란히 놓고 본다.

@Preview(name = "상추 판 · 구멍이 제일 작은 카드", widthDp = 220, heightDp = 310)
@Composable
private fun LettuceTemplatePreview() {
    PersonalCard(LETTUCE_CARD, face = null, name = "몽이", code = birthCode(4, 12), modifier = Modifier.size(200.dp))
}

@Preview(name = "고구마 판 · 작게", widthDp = 130, heightDp = 190)
@Composable
private fun SweetPotatoSmallPreview() {
    PersonalCard(SWEET_POTATO_CARD, face = null, name = "몽이", code = birthCode(4, 12), modifier = Modifier.size(110.dp))
}
