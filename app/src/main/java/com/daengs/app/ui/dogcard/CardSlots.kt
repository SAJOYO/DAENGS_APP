package com.daengs.app.ui.dogcard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
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
     * 이름바·번호판 자리. **열두 장 다 있다.**
     *
     * 한동안 열 장이 `null` 이었다. 저쪽이 이름을 모자이크로 뭉개 보내서 자리를
     * 못 믿었고, 번호는 아예 손을 못 댔다. 이제 `tools/card_text_slots.py` 가 인쇄된
     * 글자를 직접 지우고 그 자리를 재서 준다.
     *
     * **12장 공통이 아니다.** `punch_card_slots.py` 는 "프레임 배치가 공통" 이라고
     * 적어 뒀는데 아니다 — 피망·당근·가지·단호박은 검은 판에 제목이 가운데 오고
     * 번호판이 카드 **아래 왼쪽**에 따로 있다. 공통 상수 하나로 두면 그 넷에서
     * 엉뚱한 자리에 글자가 찍힌다. 한 장씩 재서 넣는다 (`docs/card-holes.md`).
     */
    val name: Slot?,
    val code: Slot?,
    /**
     * 번호판 자리에 **우리가 어두운 판을 깔지** 여부.
     *
     * 원화의 `NEO-0824` 를 지울 수 있느냐로 갈린다. 피망·당근·단호박·가지는 번호가
     * **이미 검은 상자 안**에 인쇄돼 있어서(바탕 밝기 0~5) 글자만 지우면 되고, 그
     * 상자가 곧 칩이다 — 위에 또 깔면 상자 안에 상자가 된다.
     *
     * 나머지 여덟 장은 번호가 홀로그램 무지개 위에 얹혀 있다. 30px 안에서 색이
     * 노랑 → 흰색으로 튀어서 **어떻게 메워도 얼룩이 남는다** (가로로 이으면 노란 띠,
     * 세로로 이으면 줄무늬). 복원을 포기하고 덮는다.
     */
    val codeChip: Boolean = false,
)

val CABBAGE_CARD = CardTemplate(
    id = "cabbage",
    label = "배추",
    art = "neo-hologram/art/cabbage-card-slots.webp",
    ratio = SLOTS_RATIO,
    face = Hole(51.48f, 45.14f, 20.23f, 15.17f),
    avatar = Hole(14.35f, 9.72f, 7.98f, 5.98f),
    name = Slot(19.63f, 4.03f, 70.46f, 9.58f),
    code = Slot(72.04f, 4.58f, 94.44f, 9.03f),
    codeChip = true,
)

val SWEET_POTATO_CARD = CardTemplate(
    id = "sweet-potato",
    label = "고구마",
    art = "neo-hologram/art/sweet-potato-card-slots.webp",
    ratio = SLOTS_RATIO,
    face = Hole(48.98f, 41.11f, 12.51f, 9.39f),
    avatar = Hole(14.72f, 9.58f, 7.94f, 5.96f),
    name = Slot(20.09f, 4.1f, 70.19f, 9.65f),
    code = Slot(71.76f, 4.65f, 94.44f, 9.1f),
    codeChip = true,
)

/**
 * **열두 장이 1080×1440 한 판이다.**
 *
 * 한동안 배추·고구마만 저쪽에서 따로 온 원화라 캔버스가 달랐다 (810×1125 · 816×1125).
 * 그 둘도 같은 판으로 다시 받으면서 통일됐다. 캔버스가 바뀌었으므로 **구멍을 다시
 * 쟀다** — `tools/card_text_slots.py` 가 표시 원(검은 얼굴창·흰 아바타)에서 최대
 * 내접원을 찾은 값이고, 저쪽 `measure_holes.py` 가 낸 값과 0.6%p 안에서 맞는다.
 */
private const val SLOTS_RATIO = 1080f / 1440f

val PEPPER_CARD = CardTemplate(
    id = "pepper",
    label = "피망",
    art = "neo-hologram/art/pepper-card-slots.webp",
    ratio = SLOTS_RATIO,
    face = Hole(51.76f, 51.67f, 18.44f, 13.83f),
    avatar = Hole(13.80f, 13.54f, 8.78f, 6.59f),
    name = Slot(21.39f, 6.53f, 85.56f, 11.25f),
    code = Slot(4.35f, 78.75f, 25.19f, 83.26f),
    codeChip = false,
)

val EGGPLANT_CARD = CardTemplate(
    id = "eggplant",
    label = "가지",
    art = "neo-hologram/art/eggplant-card-slots.webp",
    ratio = SLOTS_RATIO,
    face = Hole(48.24f, 56.32f, 18.64f, 13.98f),
    avatar = Hole(14.26f, 13.82f, 9.08f, 6.81f),
    name = Slot(16.94f, 6.6f, 88.15f, 12.43f),
    code = Slot(4.72f, 79.86f, 25.56f, 84.44f),
    codeChip = false,
)

val CARROT_CARD = CardTemplate(
    id = "carrot",
    label = "당근",
    art = "neo-hologram/art/carrot-card-slots.webp",
    ratio = SLOTS_RATIO,
    face = Hole(50.83f, 50.76f, 13.89f, 10.42f),
    avatar = Hole(14.07f, 13.68f, 8.73f, 6.55f),
    name = Slot(16.11f, 6.6f, 86.85f, 11.11f),
    code = Slot(4.35f, 78.75f, 25.19f, 83.26f),
    codeChip = false,
)

val DANHOBAK_CARD = CardTemplate(
    id = "danhobak",
    label = "단호박",
    art = "neo-hologram/art/danhobak-card-slots.webp",
    ratio = SLOTS_RATIO,
    face = Hole(51.57f, 50.56f, 15.35f, 11.51f),
    avatar = Hole(13.80f, 13.68f, 8.73f, 6.55f),
    name = Slot(16.67f, 6.88f, 84.81f, 11.11f),
    code = Slot(4.35f, 79.31f, 24.72f, 83.89f),
    codeChip = false,
)

val MUSHROOM_CARD = CardTemplate(
    id = "mushroom",
    label = "버섯",
    art = "neo-hologram/art/mushroom-card-slots.webp",
    ratio = SLOTS_RATIO,
    face = Hole(51.57f, 44.93f, 14.22f, 10.67f),
    avatar = Hole(14.44f, 9.58f, 7.73f, 5.80f),
    name = Slot(19.81f, 4.03f, 70.37f, 9.58f),
    code = Slot(71.94f, 4.58f, 94.44f, 9.03f),
    codeChip = true,
)

val BROCCOLI_CARD = CardTemplate(
    id = "broccoli",
    label = "브로콜리",
    art = "neo-hologram/art/broccoli-card-slots.webp",
    ratio = SLOTS_RATIO,
    face = Hole(51.48f, 51.53f, 10.02f, 7.52f),
    avatar = Hole(14.54f, 9.86f, 7.71f, 5.78f),
    name = Slot(19.81f, 4.38f, 71.02f, 9.93f),
    code = Slot(72.59f, 4.93f, 94.44f, 9.38f),
    codeChip = true,
)

val CUCUMBER_CARD = CardTemplate(
    id = "cucumber",
    label = "오이",
    art = "neo-hologram/art/cucumber-card-slots.webp",
    ratio = SLOTS_RATIO,
    face = Hole(50.56f, 47.43f, 10.29f, 7.71f),
    avatar = Hole(14.91f, 9.86f, 7.72f, 5.79f),
    name = Slot(20.28f, 4.44f, 70.19f, 10.0f),
    code = Slot(71.76f, 5.0f, 94.44f, 9.44f),
    codeChip = true,
)

val SPINACH_CARD = CardTemplate(
    id = "spinach",
    label = "시금치",
    art = "neo-hologram/art/spinach-card-slots.webp",
    ratio = SLOTS_RATIO,
    face = Hole(47.22f, 36.60f, 13.20f, 9.90f),
    avatar = Hole(14.72f, 9.79f, 7.73f, 5.80f),
    name = Slot(20.09f, 4.38f, 70.19f, 9.93f),
    code = Slot(71.76f, 4.93f, 94.44f, 9.38f),
    codeChip = true,
)

val TOMATO_CARD = CardTemplate(
    id = "tomato",
    label = "토마토",
    art = "neo-hologram/art/tomato-card-slots.webp",
    ratio = SLOTS_RATIO,
    face = Hole(51.94f, 46.18f, 15.74f, 11.80f),
    avatar = Hole(14.63f, 9.44f, 7.73f, 5.80f),
    name = Slot(19.81f, 4.17f, 70.56f, 9.58f),
    code = Slot(72.13f, 4.65f, 94.44f, 9.03f),
    codeChip = true,
)

val LETTUCE_CARD = CardTemplate(
    id = "lettuce",
    label = "상추",
    art = "neo-hologram/art/lettuce-card-slots.webp",
    ratio = SLOTS_RATIO,
    face = Hole(50.00f, 38.82f, 9.97f, 7.48f),
    avatar = Hole(13.06f, 12.85f, 7.71f, 5.78f),
    name = Slot(18.52f, 7.29f, 72.31f, 12.99f),
    code = Slot(73.89f, 7.85f, 94.44f, 12.36f),
    codeChip = true,
)

// -- 과일 --------------------------------------------------------------------
//
// **비율이 카드마다 다르다.** 야채 열두 장은 1080x1440(=`SLOTS_RATIO`) 한 판인데
// 과일은 0.699~0.804 로 제각각 들어왔다. `CardTemplate.ratio` 가 원래 카드별
// 필드라 값만 다르게 넣으면 되지만, **폭에서만 반지름을 재던 자리**는 같이 고쳐야
// 한다 (`ui/dex/FoilQuiet.kt`).
//
// 좌표는 `tools/punch_fruit_holes.py`(구멍)와 `tools/card_text_slots.py`(글자칸)가
// 재서 준 값이다. `docs/card-holes.md` 참고.

val APPLE_CARD = CardTemplate(
    id = "apple",
    label = "사과",
    art = "neo-hologram/art/apple-card-slots.webp",
    ratio = 0.7064f,
    face = Hole(51.19f, 43.00f, 14.75f, 10.42f),
    avatar = Hole(13.05f, 9.45f, 8.21f, 5.76f),
    name = Slot(17.93f, 4.02f, 72.11f, 9.25f),
    code = Slot(73.62f, 4.49f, 94.50f, 8.71f),
    codeChip = true,
)

val BANANA_CARD = CardTemplate(
    id = "banana",
    label = "바나나",
    art = "neo-hologram/art/banana-card-slots.webp",
    ratio = 0.7009f,
    face = Hole(54.05f, 32.94f, 7.57f, 5.31f),
    avatar = Hole(14.14f, 10.05f, 8.62f, 6.04f),
    name = Slot(19.05f, 4.54f, 75.14f, 10.08f),
    code = Slot(76.67f, 5.07f, 94.48f, 9.48f),
    codeChip = true,
)

val BLUEBERRY_CARD = CardTemplate(
    id = "blueberry",
    label = "블루베리",
    art = "neo-hologram/art/blueberry-card-slots.webp",
    ratio = 0.8003f,
    face = Hole(49.91f, 45.51f, 11.85f, 9.49f),
    avatar = Hole(13.06f, 10.73f, 8.51f, 6.81f),
    name = Slot(19.07f, 4.56f, 72.99f, 10.63f),
    code = Slot(74.51f, 5.14f, 94.47f, 9.99f),
    codeChip = true,
)

val CHERRY_CARD = CardTemplate(
    id = "cherry",
    label = "체리",
    art = "neo-hologram/art/cherry-card-slots.webp",
    ratio = 0.8003f,
    face = Hole(40.95f, 53.92f, 17.42f, 13.98f),
    avatar = Hole(13.50f, 11.09f, 7.89f, 6.31f),
    name = Slot(19.07f, 5.49f, 72.91f, 10.77f),
    code = Slot(74.42f, 5.99f, 94.47f, 10.20f),
    codeChip = true,
)

val KIWI_CARD = CardTemplate(
    id = "kiwi",
    label = "키위",
    art = "neo-hologram/art/kiwi-card-slots.webp",
    ratio = 0.6987f,
    face = Hole(34.59f, 45.47f, 11.69f, 8.20f),
    avatar = Hole(14.17f, 10.20f, 8.92f, 6.27f),
    name = Slot(19.47f, 4.67f, 74.90f, 10.33f),
    code = Slot(76.43f, 5.20f, 94.47f, 9.73f),
    codeChip = true,
)

val KIWI_SEED_CARD = CardTemplate(
    id = "kiwi-seed",
    label = "키위 씨앗",
    art = "neo-hologram/art/kiwi-seed-card-slots.webp",
    ratio = 0.7255f,
    face = Hole(49.95f, 46.43f, 17.84f, 12.94f),
    avatar = Hole(13.67f, 10.09f, 7.96f, 5.81f),
    name = Slot(18.91f, 4.42f, 73.13f, 9.92f),
    code = Slot(74.72f, 4.96f, 94.48f, 9.31f),
    codeChip = true,
)

val KIWI_GENTLE_CARD = CardTemplate(
    id = "kiwi-gentle",
    label = "젠틀 키위",
    art = "neo-hologram/art/kiwi-gentle-card-slots.webp",
    ratio = 0.7222f,
    face = Hole(49.53f, 40.28f, 15.10f, 9.86f),
    avatar = Hole(11.68f, 8.40f, 3.33f, 2.44f),
    name = Slot(23.92f, 4.27f, 64.92f, 8.94f),
    // **번호판만 손으로 쟀다.** 도구가 낸 값(`4.67..8.47`)은 위로 은테를 물고 아래로
    // 인쇄된 `KR-0524` 를 반쯤 잘라서, 덮으라고 있는 칩이 글자 아래를 못 가렸다 —
    // 실기기에서 번호판 밑으로 `KR-0524` 가 그대로 비쳤다. 이 카드는 틀이 달라
    // (초록 테두리 · 밝은 번호판) 도구가 어두운 바를 기준으로 잡는 앵커가 안 먹는다.
    // 판에서 글자 화소를 직접 재서 넣는다. 얼굴 구멍을 손으로 잰 것과 같은 이유다.
    //
    // 가로도 틀려 있었다. `94.47` 은 판 안쪽(92%)을 넘어 **은테와 바깥 초록 테까지**
    // 칩이 걸쳤다 — 칩은 슬롯을 6% 넓혀 그리므로 오른쪽 끝이 95.31% 였다.
    // 인쇄된 글자는 89.77% 에서 끝나므로 `90.5` 면 글자를 덮으면서 판 안에 머문다.
    // 글자 크기는 슬롯 **높이**가 정하고 가로는 넘칠 때만 줄이므로, 좁혀도 안 작아진다.
    code = Slot(66.51f, 6.64f, 90.50f, 9.49f),
    codeChip = true,
)

val MANGO_CARD = CardTemplate(
    id = "mango",
    label = "망고",
    art = "neo-hologram/art/mango-card-slots.webp",
    ratio = 0.6987f,
    face = Hole(55.92f, 43.37f, 11.93f, 8.30f),
    avatar = Hole(13.65f, 9.97f, 8.40f, 5.90f),
    name = Slot(18.80f, 4.40f, 73.95f, 10.00f),
    code = Slot(75.48f, 4.93f, 94.47f, 9.40f),
    codeChip = true,
)

val MELON_CARD = CardTemplate(
    id = "melon",
    label = "멜론",
    art = "neo-hologram/art/melon-card-slots.webp",
    ratio = 0.6996f,
    face = Hole(49.95f, 42.19f, 11.78f, 8.21f),
    avatar = Hole(13.88f, 9.91f, 8.25f, 5.77f),
    name = Slot(19.18f, 4.41f, 74.81f, 10.55f),
    code = Slot(76.34f, 5.01f, 94.47f, 9.88f),
    codeChip = true,
)

val PEACH_CARD = CardTemplate(
    id = "peach",
    label = "복숭아",
    art = "neo-hologram/art/peach-card-slots.webp",
    ratio = 0.6996f,
    face = Hole(51.81f, 50.40f, 12.40f, 8.68f),
    avatar = Hole(14.12f, 10.15f, 8.30f, 5.81f),
    name = Slot(18.89f, 3.94f, 75.10f, 10.48f),
    code = Slot(76.62f, 4.54f, 94.47f, 9.81f),
    codeChip = true,
)

val PEAR_CARD = CardTemplate(
    id = "pear",
    label = "배",
    art = "neo-hologram/art/pear-card-slots.webp",
    ratio = 0.8040f,
    face = Hole(53.96f, 47.89f, 11.52f, 9.26f),
    avatar = Hole(13.48f, 11.12f, 7.87f, 6.33f),
    name = Slot(18.24f, 4.86f, 72.60f, 10.94f),
    code = Slot(74.11f, 5.44f, 94.48f, 10.30f),
    codeChip = true,
)

val STRAWBERRY_CARD = CardTemplate(
    id = "strawberry",
    label = "딸기",
    art = "neo-hologram/art/strawberry-card-slots.webp",
    ratio = 0.7064f,
    face = Hole(47.06f, 53.49f, 12.05f, 8.51f),
    avatar = Hole(13.99f, 10.09f, 8.21f, 5.80f),
    name = Slot(19.35f, 4.36f, 74.67f, 10.05f),
    code = Slot(76.19f, 4.89f, 94.50f, 9.45f),
    codeChip = true,
)

val WATERMELON_CARD = CardTemplate(
    id = "watermelon",
    label = "수박",
    art = "neo-hologram/art/watermelon-card-slots.webp",
    ratio = 0.8003f,
    face = Hole(60.38f, 44.04f, 14.39f, 11.52f),
    avatar = Hole(12.97f, 10.56f, 8.16f, 6.56f),
    name = Slot(18.54f, 4.64f, 73.08f, 10.91f),
    code = Slot(74.60f, 5.21f, 94.47f, 10.27f),
    codeChip = true,
)


/**
 * 도감 순서와 같다 — 야채 열두 장 다음에 과일 열세 장. 뽑기가 이 목록을 **통째로
 * 균등**으로 뽑으므로, 갈래를 안 가리고 스물다섯 종이 같은 확률이다.
 *
 * id 는 `ui/dex/DexCards.kt` 의 `DEX_CARDS` 와 한 글자도 안 다르다. 그래서 뽑은
 * 카드를 도감 칸에 얹을 때 변환표가 필요 없다 — **우연히 맞은 것이라** 어긋나면
 * 조용히 카드가 사라진다. `CardTemplateTest` 가 그것을 잠근다.
 */
val CARD_TEMPLATES = listOf(
    // 야채
    CABBAGE_CARD, PEPPER_CARD, EGGPLANT_CARD, CARROT_CARD,
    DANHOBAK_CARD, MUSHROOM_CARD, BROCCOLI_CARD, CUCUMBER_CARD,
    SPINACH_CARD, SWEET_POTATO_CARD, TOMATO_CARD, LETTUCE_CARD,
    // 과일
    APPLE_CARD, BANANA_CARD, BLUEBERRY_CARD, CHERRY_CARD,
    KIWI_CARD, KIWI_SEED_CARD, KIWI_GENTLE_CARD, MANGO_CARD,
    MELON_CARD, PEACH_CARD, PEAR_CARD, STRAWBERRY_CARD,
    WATERMELON_CARD,
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
data class CardFace(
    val image: ImageBitmap,
    val core: IntRect,
    /**
     * 사용자가 원형 틀에 직접 맞춘 얼굴인가.
     *
     * 맞춘 얼굴에는 **[CORE_OVERFILL] 도 턱걸이도 안 건다.** 둘 다 사용자가 못 보고
     * 맡겼을 때 필요한 보정이라, 직접 맞춘 것에 또 걸면 본 것과 다르게 나온다.
     */
    val framed: Boolean = false,
)

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
            drawNameAndCode(measurer, name, code, template.name, template.code, template.codeChip)
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
 * 잘리는 가장자리를 흐리는 폭. **구멍 반지름 대비**다 — 고정 픽셀이면 안 된다.
 * 피망 `rx` 18.44% 와 상추 9.97% 로 구멍이 3.4배 차이 나서, 한 카드에서 맞춘 띠가
 * 다른 카드에서는 두 배로 보이거나 사라진다.
 *
 * **띠는 [CLIP_BLEED] 의 바깥쪽 끝에만 둔다** (구멍 반지름의 1.07~1.12배).
 * 카드 스물다섯 장의 판 알파를 3도 간격으로 재 보니 판이 불투명해지는 자리가
 * 구멍 반지름의 **1.000~1.050배**였다 (제일 바깥이 브로콜리 1.050). 즉 이 띠는
 * 카드에서는 전부 판 밑에 깔린다 — 안쪽으로 더 들이면 판이 안 덮는 자리가
 * 반투명해져서 [CardFace] 가 경고하는 **찢어진 자국**이 그대로 난다.
 *
 * 그래서 카드에서는 한 픽셀도 안 바뀌고, **판을 안 덮는 세 자리**에서만 듣는다 —
 * 이머시브의 튀어나온 누끼(`drawPopOut`)와 창틀 아바타는 얼굴 위에 아무것도 안
 * 얹어서 [CLIP_BLEED] 타원의 톱니가 그대로 드러나 있었다.
 */
private const val CLIP_FEATHER = 0.05f

/**
 * 얼굴을 한 번 더, 이만큼 크게 **밑에 깔고** 그 위에 제 크기로 덮는다.
 *
 * 구멍 테까지 실루엣이 못 닿는 자리가 남아 있었다. 내보낸 카드 세 장에서 창 안의
 * **완전히 투명한 화소**를 세어 보니 키위 650 · 피망 248 · 토마토 76 개였고,
 * 자리는 두 창 모두 **아래쪽 턱선**이었다 — 거기는 카드에 구멍이 뚫린 것이라
 * 받는 사람 배경색이 그대로 비친다.
 *
 * [CORE_OVERFILL] 이 같은 문제를 얼굴 전체를 키워서 막던 것인데, 키우면 얼굴이
 * 더 잘린다. 밑판은 **보이는 얼굴을 안 건드린다** — 제 크기 얼굴이 투명한
 * 자리에만 드러나므로, 맞춘 얼굴(`framed`)이 본 것과 달라지지도 않는다.
 * 흰 띠가 아니라 **얼굴 제 색**이라 [CardFace] 가 경고하는 밝은 테도 안 생긴다.
 */
private const val GAP_PLUG = 1.05f

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
    drawNameAndCode(measurer, name, code, template.name, template.code, template.codeChip, at, box)
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
    chip: Boolean,
    at: Offset,
    box: Size,
) {
    drawNameAndCode(measurer, name, code, nameSlot, codeSlot, chip, at, box)
}

/** 이름·번호판. 카드 **위에** 그린다 — 아래에 두면 카드가 덮는다. */
fun DrawScope.drawCardText(
    measurer: TextMeasurer,
    template: CardTemplate,
    name: String,
    code: String,
) {
    drawNameAndCode(measurer, name, code, template.name, template.code, template.codeChip)
}

/** 칩 속. 당근형 카드에 인쇄된 번호 상자 안쪽을 재서 넣었다 (밝기 0~13). */
private val ChipInk = Color(0xFF0C0F12)

/** 칩 테두리. 같은 상자의 은색 레일에서 뽑았다 (밝은 쪽 평균 230). */
private val ChipRail = Color(0xFFD3D8DC)

private const val CHIP_RADIUS = 0.28f
private const val CHIP_STROKE = 0.06f

/**
 * 번호판 자리를 덮는 어두운 칩. [CardTemplate.codeChip] 이 켜진 카드만 그린다.
 *
 * **지우는 대신 덮는다.** 이유는 [CardTemplate.codeChip] 에 적었다.
 *
 * 생김새는 **피망·당근·단호박·가지에 인쇄된 검은 상자를 흉내 낸 것**이다. 그 넷은
 * 상자가 이미 있어서 이걸 안 그리는데, 열두 장이 나란히 놓이는 곳이 도감이라
 * 두 벌이 서로 달라 보이면 안 된다.
 *
 * **칸보다 조금 넓게 그린다.** 딱 맞추면 인쇄된 `NEO-0824` 의 획 끝이 테두리 밖으로
 * 삐져나온다.
 */
private const val CHIP_BLEED = 1.06f

private fun DrawScope.drawCodeChip(slot: Slot, at: Offset, box: Size) {
    val left = at.x + box.width * slot.x0 / 100f
    val top = at.y + box.height * slot.y0 / 100f
    val right = at.x + box.width * slot.x1 / 100f
    val bottom = at.y + box.height * slot.y1 / 100f
    val cx = (left + right) / 2f
    val cy = (top + bottom) / 2f
    val w = (right - left) * CHIP_BLEED
    val h = (bottom - top) * CHIP_BLEED
    if (w <= 0f || h <= 0f) return

    val corner = CornerRadius(h * CHIP_RADIUS, h * CHIP_RADIUS)
    drawRoundRect(
        color = ChipInk,
        topLeft = Offset(cx - w / 2f, cy - h / 2f),
        size = Size(w, h),
        cornerRadius = corner,
    )
    drawRoundRect(
        color = ChipRail,
        topLeft = Offset(cx - w / 2f, cy - h / 2f),
        size = Size(w, h),
        cornerRadius = corner,
        style = Stroke(width = h * CHIP_STROKE),
    )
}

/**
 * 칩 → 이름 → 번호를 한 번에. **화면·내보내기·이머시브가 모두 이 함수를 부른다.**
 *
 * 예전에는 세 곳이 각각 `drawSlotText` 를 두 번씩 불렀다. 칩이 생기면서 "칩을 먼저
 * 깐다" 는 순서가 늘었는데, 한 곳만 빠뜨리면 그 화면에서만 저쪽 번호가 비친다.
 */
private fun DrawScope.drawNameAndCode(
    measurer: TextMeasurer,
    name: String,
    code: String,
    nameSlot: Slot?,
    codeSlot: Slot?,
    chip: Boolean,
    at: Offset = Offset.Zero,
    box: Size = size,
) {
    if (chip && codeSlot != null) drawCodeChip(codeSlot, at, box)
    drawSlotText(measurer, name, nameSlot, TITLE, at, box)
    drawSlotText(measurer, code, codeSlot, CODE, at, box)
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
    if (rx <= 0f || ry <= 0f) return

    // 사용자가 맞춘 얼굴은 **키우지 않는다.** 원 안에서 보고 정한 크기라 여기서 또
    // 1.15배 하면 본 것보다 크게 나온다.
    val overfill = if (face.framed) 1f else CORE_OVERFILL
    // 또렷한 얼굴이 구멍을 덮을 만큼 키운다. 짧은 쪽이 아니라 **모자란 쪽**에
    // 맞춰야 구멍이 찬다.
    val scale = maxOf(rx * 2f / core.width, ry * 2f / core.height) * overfill

    // 가로는 비트맵 한가운데가 아니라 **또렷한 얼굴의 한가운데**에 맞춘다.
    val left = cx - (core.left + core.width / 2f) * scale
    val top = if (face.framed) {
        // **맞춘 얼굴은 가운데다.** 원 안에서 가운데에 놓고 본 것이므로
        // 카드에서도 가운데여야 같은 그림이 된다.
        cy - (core.top + core.height / 2f) * scale
    } else {
        // 세로는 한가운데가 아니라 **턱을 구멍 아래에 건다.**
        //
        // 구멍보다 크게 그리니 어딘가는 잘려야 하는데, 이마와 귀가 잘리는 것은
        // 괜찮고 **코가 잘리면 개로 안 보인다.** 한가운데에 맞췄더니 코가 먼저
        // 잘리고 이마만 남았다 — 실기기에서 봤다.
        cy + box.height * hole.ry / 100f - core.bottom * scale
    }
    val faceW = face.image.width * scale
    val faceH = face.image.height * scale

    // 구멍 한가운데에서 밖으로 [spread] 배 벌려 그린다. 1f 이면 제자리다.
    fun DrawScope.paintFace(spread: Float) {
        val l = cx + (left - cx) * spread
        val t = cy + (top - cy) * spread
        drawImage(
            image = face.image,
            dstOffset = IntOffset(l.roundToInt(), t.roundToInt()),
            dstSize = IntSize((faceW * spread).roundToInt(), (faceH * spread).roundToInt()),
            filterQuality = FilterQuality.High,
        )
    }

    val bleed = Rect(cx - rx, cy - ry, cx + rx, cy + ry)
    val clip = Path().apply { addOval(bleed) }

    // **띠가 한 화소도 안 되면 레이어를 안 뜬다.** 도감 그리드의 작은 카드가 그렇다 —
    // 거기서는 흐려 봐야 안 보이는데 칸마다 오프스크린 레이어만 두 장씩 늘어난다.
    val feather = minOf(rx, ry) * CLIP_FEATHER / CLIP_BLEED
    val soft = feather >= 0.75f
    val canvas = drawContext.canvas
    if (soft) canvas.saveLayer(bleed, Paint())

    clipPath(clip) {
        paintFace(GAP_PLUG)
        paintFace(1f)
    }

    if (soft) {
        // 블러가 아니라 **알파를 깎는다.** `RenderEffect` 는 API 31 부터인데
        // minSdk 26 이라 `Cutout` 도 같은 벽에서 같은 수를 쓴다.
        val inner = (CLIP_BLEED - CLIP_FEATHER) / CLIP_BLEED
        // 원 하나를 세로로 눌러 타원으로 쓴다. 구멍은 카드마다 rx != ry 다.
        scale(scaleX = 1f, scaleY = ry / rx, pivot = Offset(cx, cy)) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.Black,
                    inner to Color.Black,
                    1f to Color.Transparent,
                    center = Offset(cx, cy),
                    radius = rx,
                ),
                radius = rx,
                center = Offset(cx, cy),
                blendMode = BlendMode.DstIn,
            )
        }
        canvas.restore()
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
 * 카드 이름에 쓰는 글씨체 — 하이커(HiKR) ExtraBold.
 *
 * **한글에 `FontFamily.Serif` 는 사실상 안 먹는다.** 안드로이드 기본 serif 에는 한글이
 * 없어서 시스템 한글 폰트로 떨어지고, 결국 기기 기본 고딕이 나온다. 저쪽 원화의
 * `CABBAGE NEO` 는 각진 영문 서체인데 우리 한글만 딴 세상이었다.
 *
 * **케리스 케듀체에서 갈아탔다.** 케듀는 획 끝이 둥글어서, 각지고 두꺼운 원화 글자
 * 옆에 놓으면 우리 이름만 물러 보였다. 하이커는 획이 각지고 더 굵어서 카드의 결에
 * 붙는다 — 이름을 바 전체로 키우기로 하면서 차이가 더 벌어졌다.
 *
 * **OTF 로 넣는다.** 같은 글꼴인데 TTF 는 2.30MB, OTF(CFF)는 391KB 다. 케듀(438KB)
 * 보다 오히려 작아서 APK 가 줄었다. 완성형 한글 11,172자와 라틴·숫자가 다 들어 있다 —
 * 이름에 영문을 쓰는 사람이 있다.
 *
 * **굵기 하나만 넣는다.** 한글 폰트는 글자 수가 많아 굵기마다 0.4~1MB 씩 는다.
 *
 * **라이선스 (눈누, 2026-09-03).** 한국관광공사 배포. 인쇄·웹·포장지·영상·BI/CI·
 * **임베딩**이 모두 허용이고, APK 에 파일이 들어가 배포되는 것이 그 "임베딩" 이다.
 * 다만 케듀와 달리 **폰트 파일 자체의 수정·복제·재배포는 허용이 아니다** — 우리는
 * 파일을 그대로 넣고 앱으로만 배포하므로 해당이 없지만, 폰트를 따로 빼서 나눠 주거나
 * 서브셋으로 잘라 넣는 것은 하지 말 것.
 */
private val HikrBold = FontFamily(Font(R.font.hikr_extrabold, FontWeight.ExtraBold))

/**
 * 이름은 **바를 세로로 거의 채운다** (`fill`).
 *
 * 0.60 이던 것을 키웠다. 뒷말만 갈아 끼우던 때는 옆의 `MUSHROOM` 과 키를 맞춰야 해서
 * 작았는데, 바를 통째로 쓰기로 하면서 맞출 상대가 없어졌다. 작게 두면 넓은 바 한가운데
 * 글자만 동동 뜬다.
 */
private val TITLE = SlotFace(HikrBold, FontWeight.ExtraBold, 0.02f, 0.90f)

/**
 * 번호판. 원화의 `NEO-0824` 는 굵은 산세리프였는데 **이름과 같은 글꼴로 통일한다.**
 * 칩은 우리가 그리는 판이고, 시스템 산세리프는 기기마다 달라서 어떤 폰에서는 칩 안에서
 * 혼자 다른 글씨가 됐다. 하이커는 획이 각져서 번호에도 맞는다.
 */
private val CODE = SlotFace(HikrBold, FontWeight.ExtraBold, 0.04f, 0.62f)

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

/**
 * 하이커 글자의 **눈에 보이는 상자**(잉크)가 줄 상자 어디에 오는지. 폰트를 직접 재서
 * 넣었다 — upem 1000, hhea `asc 830 / desc -170 / gap 0` 이라 줄 높이가 정확히 1em 이고,
 * 한글 잉크는 베이스라인 기준 `-0.024 ~ +0.740em` 이다.
 *
 *     잉크 높이   = 0.740 + 0.024        = 0.764
 *     베이스라인  = 줄 위에서 0.830
 *     잉크 가운데 = 0.830 - (0.740-0.024)/2 = 0.472  (줄 위에서)
 *
 * **줄 상자 한가운데(0.5)에 맞추면 안 된다.** 한글은 내려긋는 획이 거의 없어 줄 상자
 * 아래가 비고, 그만큼 글자가 처져 보인다. 글꼴을 바꾸면 이 둘을 다시 재야 한다.
 */
private const val INK_HEIGHT = 0.764f
private const val INK_CENTER = 0.472f

/**
 * 잉크 한가운데를 칸 한가운데보다 이만큼 더 올린다 (칸 높이 대비).
 *
 * 기하학적으로 가운데에 놓으면 처져 보인다 — 제목 바는 아래쪽에 은색 레일이 붙어 있어
 * 아랫동이 시각적으로 무겁다. 실기기에서 올려 가며 정했다.
 */
private const val OPTICAL_LIFT = 0.05f

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

    // **`fill` 은 잉크가 칸 높이를 차지하는 비율이다.** 글자 크기에 바로 곱하면
    // 글꼴마다 줄 높이가 달라서 같은 값이 어디선 넘치고 어디선 뜬다.
    fun styleAt(px: Float) = TextStyle(
        color = Color.White,
        fontSize = px.toSp(),
        fontWeight = face.weight,
        fontFamily = face.family,
        letterSpacing = (px * face.letterSpacing).toSp(),
        textAlign = TextAlign.Center,
    )

    var px = boxH * face.fill / INK_HEIGHT
    var laid = measurer.measure(text, styleAt(px), maxLines = 1)

    // **한 번 재서 되맞춘다.** [toSp] 가 밀도 2.6 을 상수로 가정한 환산이라, 실제
    // 픽셀은 `(px / 2.6) x measurer 의 밀도` 다 — 우리가 예측할 수 없다. 밀도 3.0
    // 폰에서는 `fill = 0.90` 이 칸의 1.04배가 되어 **바를 넘쳤다.**
    //
    // `2.6f` 를 걷어내는 대신 잰 값으로 고친다. 내보내기와 화면이 같은 measurer 를
    // 쓰기로 한 설계가 그 상수에 걸려 있다 (`CardRender.kt`).
    val ink = laid.size.height * INK_HEIGHT
    if (ink > 0f) {
        px *= boxH * face.fill / ink
        laid = measurer.measure(text, styleAt(px), maxLines = 1)
    }

    // 넘치면 줄인다. 이름이 긴 개도 있다.
    if (laid.size.width > boxW) {
        px *= boxW / laid.size.width.toFloat()
        laid = measurer.measure(text, styleAt(px), maxLines = 1)
    }

    // 세로는 줄 상자가 아니라 **잉크 한가운데**를 칸에 맞춘다. 줄 상자가 칸보다
    // 커져도 상관없다 — 넘치는 부분은 빈 여백이라 아무것도 안 그려진다.
    drawText(
        textLayoutResult = laid,
        topLeft = Offset(
            left + (boxW - laid.size.width) / 2f,
            top + boxH / 2f - boxH * OPTICAL_LIFT - laid.size.height * INK_CENTER,
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

// **두 디자인을 다 본다.** 프리뷰가 전부 버섯형이라 당근형(검은 판에 제목이 가운데,
// 번호판은 아래 왼쪽)이 한 번도 안 보였다. 이름칸도 번호 자리도 저쪽과 다르다.

@Preview(name = "당근 판 · 번호가 아래에 있는 쪽", widthDp = 220, heightDp = 310)
@Composable
private fun CarrotTemplatePreview() {
    PersonalCard(CARROT_CARD, face = null, name = "몽이", code = birthCode(4, 12), modifier = Modifier.size(200.dp))
}

// 칩을 깐 쪽과 안 깐 쪽이 나란히 보여야 한다 — 둘이 달라 보이면 도감에서 티가 난다.

@Preview(name = "버섯 판 · 칩을 깐 쪽", widthDp = 220, heightDp = 310)
@Composable
private fun MushroomTemplatePreview() {
    PersonalCard(MUSHROOM_CARD, face = null, name = "몽이", code = birthCode(4, 12), modifier = Modifier.size(200.dp))
}

// 이름이 길면 줄어든다. 칸을 넘지 않는지 본다.

@Preview(name = "이름이 긴 개", widthDp = 220, heightDp = 310)
@Composable
private fun LongNamePreview() {
    PersonalCard(TOMATO_CARD, face = null, name = "구름이형아", code = birthCode(12, 25), modifier = Modifier.size(200.dp))
}

@Preview(name = "영문 이름", widthDp = 220, heightDp = 310)
@Composable
private fun LatinNamePreview() {
    PersonalCard(SPINACH_CARD, face = null, name = "Bella", code = birthCode(7, 3), modifier = Modifier.size(200.dp))
}

// -- 프리뷰 · 구멍 가장자리 ----------------------------------------------------
//
// 위의 판 프리뷰는 전부 `face = null` 이다. 가장자리는 **얼굴이 있어야** 보이는데
// 진짜 누끼는 기기에서 사진을 찍어야 나온다. 그래서 여기서는 **가짜 누끼를 그려
// 넣는다** — 사진일 필요가 없다. 필요한 것은 "투명한 바탕에 앉은, 창보다 작을 수도
// 있고 아래가 턱에서 끊기는 실루엣" 하나뿐이고, 그게 [GAP_PLUG] 와 [CLIP_FEATHER]
// 가 다루는 전부다.

/**
 * 프리뷰용 가짜 누끼. 귀 둘과 턱에서 끊긴 머리 — 실루엣이 창 안으로 파고드는 자리와
 * 창 아래 테에 못 닿는 자리를 **일부러** 만든다. 실기기에서 그 두 자리가 비쳤다.
 */
private fun fakeFace(side: Int = 512): CardFace {
    val image = ImageBitmap(side, side)
    val s = side.toFloat()
    CanvasDrawScope().draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = androidx.compose.ui.graphics.Canvas(image),
        size = Size(s, s),
    ) {
        // `bakeFramed` 가 굽는 대로, **또렷한 얼굴이 정사각형을 꽉 채운다.** 아래만
        // 턱에서 끊긴다 — 실기기에서 비친 자리가 거기다.
        drawOval(Color(0xFFB08E62), Offset(0f, 0f), Size(s * 0.30f, s * 0.40f))
        drawOval(Color(0xFFB08E62), Offset(s * 0.70f, 0f), Size(s * 0.30f, s * 0.40f))
        drawOval(Color(0xFFCBA97E), Offset(0f, s * 0.04f), Size(s, s * 0.84f))
        drawOval(Color(0xFF2B2118), Offset(s * 0.40f, s * 0.50f), Size(s * 0.20f, s * 0.14f))
    }
    return CardFace(image, IntRect(0, 0, side, side), framed = true)
}

/**
 * **판을 안 덮는 자리.** 이머시브의 튀어나온 누끼와 창틀 아바타가 이렇게 그린다 —
 * 얼굴 위에 아무것도 안 얹으므로 [CLIP_BLEED] 타원이 맨살로 드러난다. 카드 프리뷰로는
 * [CLIP_FEATHER] 가 한 화소도 안 보인다 (띠가 판 밑에 깔리게 잡아 뒀다).
 */
@Preview(name = "구멍 가장자리 · 판 없이 (흐린 띠가 보이는 유일한 자리)", widthDp = 260, heightDp = 260)
@Composable
private fun BareHoleEdgePreview() {
    val face = remember { fakeFace() }
    Canvas(Modifier.size(240.dp)) {
        drawRect(Color(0xFF2E4A1E))
        drawInHoleOf(face, PEPPER_CARD.face, Offset.Zero, size)
    }
}

/**
 * 구멍이 제일 큰 카드와 제일 작은 카드에 **같은 얼굴**을 끼운다. 띠도 밑판도
 * 구멍 반지름 대비라서, 3.4배 차이 나는 두 창에서 같은 두께로 읽혀야 한다.
 */
@Preview(name = "피망 · 얼굴을 끼운 판 (구멍 제일 큼)", widthDp = 220, heightDp = 310)
@Composable
private fun PepperFacePreview() {
    val face = remember { fakeFace() }
    PersonalCard(PEPPER_CARD, face = face, name = "몽이", code = birthCode(4, 12), modifier = Modifier.size(200.dp))
}

@Preview(name = "상추 · 얼굴을 끼운 판 (구멍 제일 작음)", widthDp = 220, heightDp = 310)
@Composable
private fun LettuceFacePreview() {
    val face = remember { fakeFace() }
    PersonalCard(LETTUCE_CARD, face = face, name = "몽이", code = birthCode(4, 12), modifier = Modifier.size(200.dp))
}

/** 화면 크기로도 본다. 도감 그리드에서는 띠가 한 화소가 안 돼서 굳은 테로 물러선다. */
@Preview(name = "도감 칸 크기 · 얼굴을 끼운 판", widthDp = 130, heightDp = 190)
@Composable
private fun GridSizeFacePreview() {
    val face = remember { fakeFace() }
    PersonalCard(TOMATO_CARD, face = face, name = "몽이", code = birthCode(4, 12), modifier = Modifier.size(110.dp))
}

// 칩이 **은테를 밟는지**는 220dp 프리뷰에서 안 보인다. 칩 오른쪽 끝과 판 안쪽 선의
// 차이가 카드 폭의 1~2%p 라 220dp 에서 두세 화소다 — 그래서 `94.44` 가 스물네 장에
// 그대로 남아 있었다. **크게 띄워 놓고 오른쪽 위를 본다.**

@Preview(name = "번호칩이 은테를 밟는지 · 토마토 크게", widthDp = 430, heightDp = 580)
@Composable
private fun ChipAgainstFramePreview() {
    PersonalCard(TOMATO_CARD, face = null, name = "몽이", code = birthCode(8, 24), modifier = Modifier.size(400.dp))
}

// 번호칸이 제일 오른쪽까지 가는 카드. 여기서 안 넘으면 어디서도 안 넘는다.

@Preview(name = "번호칩 · 칸이 제일 오른쪽인 키위", widthDp = 430, heightDp = 580)
@Composable
private fun ChipRightMostPreview() {
    PersonalCard(KIWI_CARD, face = null, name = "몽이", code = birthCode(5, 24), modifier = Modifier.size(400.dp))
}

// 번호가 길면 줄어든다. 칩의 둥근 끝에 닿지 않는지 본다 — 여백이 0 이던 때는 닿았다.

@Preview(name = "번호가 긴 경우", widthDp = 430, heightDp = 580)
@Composable
private fun LongCodePreview() {
    PersonalCard(TOMATO_CARD, face = null, name = "몽이", code = "DG-12251225", modifier = Modifier.size(400.dp))
}

// 칩을 안 까는 넷(번호가 카드 아래 왼쪽)은 이 변경에 안 흔들려야 한다.

@Preview(name = "가지 판 · 칩을 안 까는 쪽", widthDp = 430, heightDp = 580)
@Composable
private fun EggplantNoChipPreview() {
    PersonalCard(EGGPLANT_CARD, face = null, name = "몽이", code = birthCode(8, 24), modifier = Modifier.size(400.dp))
}
