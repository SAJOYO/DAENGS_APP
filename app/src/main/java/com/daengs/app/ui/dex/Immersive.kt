package com.daengs.app.ui.dex

import androidx.compose.ui.graphics.ImageBitmap
import com.daengs.app.ui.dogcard.CardFace
import com.daengs.app.ui.dogcard.CardTemplate
import com.daengs.app.ui.dogcard.Hole
import com.daengs.app.ui.dogcard.Slot
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.sin

// ---------------------------------------------------------------------------
// ☆☆☆ 이머시브 — 꾹 누르면 카드 "안으로" 들어간다
//
// 웹판 `immersive.css` + `immersive.mjs` 를 옮긴 것이다. 저쪽이 맨 위에 적어 둔
// 전제를 그대로 이어받는다.
//
// > 정직하게 적어 둘 것 — 진짜 이머시브 카드는 원화를 레이어로 나눠 그린다.
// > 여기 art/*.webp 는 프레임까지 인쇄된 완성 카드 한 장이라 배추만 오려낼 수가 없다.
//
// 그래서 배추 카드만 **레이어 원화 세 장**을 따로 갖는다.
//
//   cabbage-back.webp     프레임 없는 배경
//   cabbage-subject.webp  알파가 있는 주인공(누끼)
//   cabbage-card.webp     진입 때만 쓰는 카드 (모서리 바깥이 알파)
//
// ## 평면 일곱 장
//
// 뒤에서 앞으로 갈수록 많이 움직인다. 이 차이가 깊이를 만든다 — 숫자는 저쪽
// `--par` 값 그대로다.
//
//   하늘 5 · 환경 12 · 빛줄기 18 · 먼지 30 · 주인공 52 · 자막 30 · 앞잎 100
//
// 이슬만 0 이다. 카메라 유리에 맺힌 것이라 화면과 같이 움직이면 안 된다.
// ---------------------------------------------------------------------------

/** 꾹 누르는 시간(ms). 저쪽 `HOLD_MS`. */
const val IMMERSIVE_HOLD_MS = 520

/** 이만큼(px) 움직이면 꾹이 아니라 스크롤로 본다. 저쪽 `SLOP`. */
const val IMMERSIVE_SLOP = 10f

/**
 * 배추 카드의 무대 값. 저쪽 `cards.mjs` 의 `scene` 을 옮겼다.
 *
 * [fit] 이 특히 중요하다 — **원본 카드 그림 안에서 누끼가 차지하는 자리**(카드 크기
 * 대비 %)다. 들어갈 때 카드와 누끼를 겹쳐 놓고 카드만 지우는데, 이 값이 맞아야 틀이
 * 녹는 동안 캐릭터가 한 픽셀도 안 움직인다.
 */
@Immutable
data class ImmersiveScene(
    /**
     * 무대 이름. **화면에 박아 두면 안 된다** — 배추 하나뿐일 때는 상수였는데,
     * 고구마를 넣으니 보랏빛 결계 위에 배추 이름이 떴다.
     *
     * 내 카드로 들어갔으면 화면이 **우리 아이 이름으로 덮어쓴다**
     * (`ImmersiveScreen(titleOverride = ...)`). 여기 값은 아직 안 뽑은 카드로
     * 들어갔을 때(프리뷰·표본)만 쓰인다.
     */
    val title: String,
    val place: String,
    val back: String,
    val subject: String,
    val card: String,
    /**
     * 그림 영역만 투명하게 지운 카드. [card] 아래에 같은 자리로 깔려 있다가, 카드가
     * 녹으면 드러나서 **창틀**이 된다. 없으면(`null`) 예전처럼 카드가 녹기만 한다.
     */
    val frame: String?,
    /**
     * 창틀에 우리 글자를 찍을 자리. **창틀 크기 대비 %** 다.
     *
     * 카드의 자리를 그대로 못 쓴다 — 창틀은 카드와 크기도 비율도 다른 별도 렌더다
     * (고구마 창틀 1067x1474 vs 카드 816x1125). `tools/punch_card_frame.py` 가
     * 저쪽 글자를 지우면서 같은 값을 쓴다.
     */
    val frameName: Slot? = null,
    val frameCode: Slot? = null,
    /**
     * 창틀의 번호 자리에 어두운 칩을 깔지. [com.daengs.app.ui.dogcard.CardTemplate.codeChip]
     * 과 같은 뜻이고, 창틀이 있는 셋(배추·고구마·상추)은 전부 홀로그램 위라 켠다.
     */
    val frameChip: Boolean = false,
    /**
     * 배경음. `assets/` 아래 경로이고, null 이면 무음이다.
     *
     * **[DexCard] 가 아니라 여기 있다.** 음악이 필요한 곳이 이머시브뿐이라서다 —
     * 그리드와 확대 뷰 12장까지 번지면 그때 카드로 올린다.
     *
     * OGG 인 이유는 `tools/convert_audio.py` 에 적어 뒀다. 요약하면 MP3 는 이어
     * 붙이면 틈이 생겨서, 29초마다 한 번씩 "툭" 이 들린다.
     */
    val bgm: String?,
    /**
     * 틀에서 **뒤가 비치는 자리 전부**를 감싸는 상자 (카드 크기 대비 %).
     *
     * [fit] 과 다르다. [fit] 은 누끼가 놓이는 자리이고, 이쪽은 **뒤를 받쳐 줄 범위**다.
     * 틀에는 그림창 말고도 뚫린 데가 있다 — 원래 카드에서 그림 위에 얹혀 있던 반투명
     * 판들이라, 그림을 지울 때 뒤가 같이 비었다. 그림창만 재서 넣으면 그 자리가
     * 검게 남는다.
     *
     * 창이 뚫린 데보다 커도 상관없다. 넘치는 만큼은 틀의 불투명한 부분이 가린다 —
     * 모양은 틀의 알파가 잡고, 이 상자는 범위만 정한다. 다만 **카드 바깥 둥근
     * 모서리까지 삼키면 안 된다.** 받쳐 주면 둥근 귀퉁이로 텃밭이 새어 나와 카드가
     * 직사각형으로 보인다.
     */
    val window: Win,
    /**
     * 겉잎 겹. **같은 누끼**를 고리로 오려 앞에 세운다 (저쪽 `.dio-rind`).
     *
     * 기울이면 각 겹이 속과 다른 속도로 어긋나고, 그 어긋남이 배추의 두께가 된다.
     * 방울이나 빛과 달리 캐릭터 자신이 갈라져 움직이는 거라 제일 직관적이다.
     *
     * **아직 진짜 레이어가 아니다.** 겹이 속과 같은 픽셀이라 비켜도 새로 드러나는 게
     * 없다. 겹마다 그린 원화가 오면 고리 마스크를 빼고 그림만 갈아 끼우면 되고,
     * 나머지 구조(깊이 · 시차 · 그림자)는 그대로 쓴다. 저쪽도 같은 상태다.
     *
     * 바깥일수록 높이 띄운다 — 배추는 잎이 여러 겹이라 계단이 하나면 "두 장"으로
     * 보이고, 두세 단이면 두께로 읽힌다.
     */
    val shells: List<Shell> = listOf(
        Shell(z = 16f, r0 = 14f, r1 = 44f, r2 = 54f, r3 = 86f, shadow = 0.18f, opacity = 1f),
        Shell(z = 32f, r0 = 44f, r1 = 62f, r2 = 62f, r3 = 72f, shadow = 0.32f, opacity = 1f),
    ),
    val fit: Fit,
    val motes: Int = 52,
    val dew: Int = 15,
    /**
     * 앞에서 날리는 잎. 0이면 안 날린다.
     *
     * **주인공보다 앞에 그려진다.** 그래서 자리는 가운데를 피해야 하고(안 그러면
     * 얼굴을 덮는다), 크기와 번짐은 px 이 아니라 **누끼 높이 대비 %** 여야 한다 —
     * px 로 두면 화면이 작아질 때 잎만 안 줄어 캐릭터를 덮는다.
     */
    val leaves: Int = 0,
    val accent: Color,
    val accent2: Color,
) {
    @Immutable
    data class Fit(val x: Float, val y: Float, val w: Float, val h: Float)

    /** [window] 의 상자. 값은 전부 카드 크기 대비 %. */
    @Immutable
    data class Win(val x: Float, val y: Float, val w: Float, val h: Float)

    /**
     * 겉잎 한 겹.
     *
     * 고리는 [r0]~[r1] 에서 서서히 나타나고 [r2]~[r3] 에서 사라진다. 단위는 누끼
     * 중심에서 가장 먼 귀퉁이까지 거리의 %다 (저쪽 `radial-gradient` 와 같다).
     *
     * **경계를 넓게 흐리는 게 중요하다.** 딱 자르면 어긋나는 순간 "두께"가 아니라
     * "인쇄가 밀린 두 장"으로 보인다. 원 마스크가 잎 한복판을 가로지르기 때문이다.
     * 맨 바깥 겹은 실루엣에 닿기 전에 흐려져야 한다 — 안 그러면 배경과 맞닿는
     * 가장자리에서 속과 겹쳐 보이고, 같은 픽셀이라 그 자리가 제일 티가 난다.
     *
     * @param z 앞으로 띄운 높이. 클수록 기울일 때 많이 어긋나고 그림자도 길어진다.
     *   **잔상은 이 값으로 잡는다.** 저쪽 값(34 · 70)을 그대로 썼더니 잎 윤곽이 두 번
     *   그려져 잔상으로 보인다는 지적을 받았다. 절반으로 줄이니 사라졌고, 겹은 여전히
     *   속보다 5px · 10px 더 움직여 두께는 남는다. 겹마다 그린 원화가 오면 그때는
     *   겹이 속과 다른 그림이라 다시 올려도 된다.
     * @param shadow 뒤에 드리우는 그림자의 진하기. 뒤쪽 겹까지 또렷하면 고리 경계가
     *   테두리 선처럼 드러나서, 두께가 아니라 오려 붙인 원으로 보인다.
     * @param opacity 겹의 불투명도. **1 로 둔다.**
     *   잔상을 없애려고 0.55 로 비쳐 보게 해 봤는데 **더 나빠졌다** — 밀린 복사본이
     *   속에 녹는 게 아니라 이중노출처럼 번져서, 두께가 아니라 초점이 나간 것으로
     *   보였다. 이 값이 아니라 [z] 로 잡아야 한다.
     */
    @Immutable
    data class Shell(
        val z: Float,
        val r0: Float,
        val r1: Float,
        val r2: Float,
        val r3: Float,
        val shadow: Float,
        val opacity: Float,
    )
}

/** No.01 배추. 이슬 맺힌 텃밭. */
val CABBAGE_SCENE = ImmersiveScene(
    title = "배추",
    place = "이슬 맺힌 텃밭 · 해 뜨기 직전",
    back = "neo-hologram/art/cabbage-back.webp",
    subject = "neo-hologram/art/cabbage-subject.webp",
    card = "neo-hologram/art/cabbage-card.webp",
    frame = "neo-hologram/art/cabbage-card-frame.webp",
    frameChip = true,
    frameName = Slot(18.17f, 3.95f, 71.89f, 9.62f),
    frameCode = Slot(73.49f, 4.51f, 94.5f, 9.05f),
    bgm = bgmFor("cabbage"),
    window = ImmersiveScene.Win(4.91f, 10.28f, 90.51f, 81.58f),
    fit = ImmersiveScene.Fit(6.06f, 14.15f, 87.43f, 62.70f),
    leaves = 7,
    accent = Color(0xFF8FD94A),
    accent2 = Color(0xFFD8F07A),
)

/**
 * No.10 고구마. 보랏빛 결계.
 *
 * 값은 저쪽 `cards.mjs` 의 `scene` 을 옮긴 것이고, **눈으로 맞춘 게 아니라 잰 것**이다.
 * [window] 는 틀의 알파를 훑어 잰 경계상자이고 — 배추와 달리 뚫린 데가 그림창
 * 하나뿐이라 덩어리 하나가 그대로 창이 된다 — [fit] 은 누끼를 0.74~0.86 배율로 훑어
 * 카드 그림과 맞춰 본 템플릿 매칭 결과다(배율 0.794 · 자리 94,168).
 *
 * [shells] 의 `z` 는 **저쪽 값(34 · 70)의 절반**이다. 배추에서 그대로 썼다가 잎 윤곽이
 * 두 번 그려져 잔상으로 보인다는 지적을 받고 절반으로 줄인 그 값이다 ([Shell.z] 참고).
 * 그림자만 저쪽 값(0.22)을 따른다.
 */
val SWEET_POTATO_SCENE = ImmersiveScene(
    title = "고구마",
    place = "보랏빛 결계 · 의식이 시작되기 직전",
    back = "neo-hologram/art/sweet-potato-back.webp",
    subject = "neo-hologram/art/sweet-potato-subject.webp",
    card = "neo-hologram/art/sweet-potato-card.webp",
    frame = "neo-hologram/art/sweet-potato-card-frame.webp",
    frameChip = true,
    frameName = Slot(19.03f, 4.0f, 72.26f, 10.18f),
    frameCode = Slot(73.86f, 4.62f, 94.5f, 9.56f),
    bgm = bgmFor("sweet-potato"),
    window = ImmersiveScene.Win(6.28f, 10.85f, 88.57f, 81.61f),
    fit = ImmersiveScene.Fit(11.52f, 14.93f, 80.02f, 62.84f),
    shells = listOf(
        ImmersiveScene.Shell(z = 16f, r0 = 14f, r1 = 44f, r2 = 54f, r3 = 86f, shadow = 0.22f, opacity = 1f),
        ImmersiveScene.Shell(z = 32f, r0 = 44f, r1 = 62f, r2 = 62f, r3 = 72f, shadow = 0.32f, opacity = 1f),
    ),
    accent = Color(0xFFA0656F),
    accent2 = Color(0xFFD8A89E),
)

/**
 * No.12 상추. 황금 무대.
 *
 * **포일을 가진 채 이머시브인 첫 카드다.** 저쪽도 이 카드에서 `isImmersive` 를
 * `rarity` 가 아니라 `scene` 의 유무로 바꿨다 — 포일은 카드 위에 얹히는 겹이고
 * 이머시브는 별개의 화면이라 서로 포기할 이유가 없다. 우리는 [DexCard.foil] 과
 * [IMMERSIVE_SCENES] 가 처음부터 따로라 그대로 맞는다. No.12 는 [Foil.Metal] 을 쓴다.
 *
 * [window] 가 배추(81.58)·고구마(81.61)보다 낮은 81 미만인 것은 **이 틀의 그림창이
 * 짧아서**다. 틀마다 다르므로 카드가 늘 때마다 저쪽이 재서 준다.
 */
val LETTUCE_SCENE = ImmersiveScene(
    title = "상추",
    place = "황금 무대 · 잎이 날리는 밤",
    back = "neo-hologram/art/lettuce-back.webp",
    subject = "neo-hologram/art/lettuce-subject.webp",
    card = "neo-hologram/art/lettuce-card.webp",
    frame = "neo-hologram/art/lettuce-card-frame.webp",
    frameChip = true,
    frameName = Slot(18.54f, 4.28f, 73.62f, 10.63f),
    frameCode = Slot(75.22f, 4.91f, 94.5f, 9.99f),
    bgm = bgmFor("lettuce"),
    window = ImmersiveScene.Win(5.17f, 11.55f, 90.46f, 76.39f),
    fit = ImmersiveScene.Fit(11.22f, 15.47f, 80.33f, 62.13f),
    shells = listOf(
        ImmersiveScene.Shell(z = 16f, r0 = 14f, r1 = 44f, r2 = 54f, r3 = 86f, shadow = 0.22f, opacity = 1f),
        ImmersiveScene.Shell(z = 32f, r0 = 44f, r1 = 62f, r2 = 62f, r3 = 72f, shadow = 0.32f, opacity = 1f),
    ),
    motes = 46,
    dew = 9,
    leaves = 14,
    accent = Color(0xFFB2D121),
    accent2 = Color(0xFFE3F493),
)

/**
 * 이머시브를 빌드에 넣는가.
 *
 * **한 번 껐다가 다시 켰다.** 무대는 그림 넉 장(배경·주인공 누끼·진입 카드·창틀)으로
 * 이뤄지는데, 카드가 "우리 아이의 카드" 가 된 뒤로 **무대에 선 주인공만 저쪽 강아지로
 * 남았다.** 그 반쪽 상태로는 "내 카드로 들어갔는데 남의 개가 서 있는" 화면이 나서,
 * 야채 몸통에 얼굴이 합쳐진 전신 그림이 올 때까지 꺼 뒀었다.
 *
 * 전신 그림은 필요 없었다. **카드에서 한 것과 똑같이 누끼의 얼굴 자리만 뚫으면 된다**
 * (`tools/punch_subject_face.py`). 자리는 재지도 않는다 — 카드 안 얼굴 구멍과 카드 안
 * 누끼 자리를 이미 갖고 있어서 [ImmersiveScene.faceInSubject] 가 나눠서 구한다.
 *
 * 값을 남겨 두는 이유는 **끄고 내보낼 수 있어야 하기 때문**이다. 이머시브가 걸린
 * 카드는 열두 장 중 셋뿐이라, 원화가 모자란 채로 심사를 받아야 하는 날이 오면 여기
 * 하나로 뺀다.
 *
 * ⚠️ **곡은 여기 안 매여 있다.** `CARD_BGM` 이 곡의 원본이라 이머시브를 꺼도
 * 턴테이블은 그대로 돈다. 그러려고 갈라 뒀다.
 */
const val IMMERSIVE_IN_BUILD = true

/**
 * 무대 주인공의 얼굴에 끼울 것. 얼굴 그림과 누끼 안에서의 자리다.
 *
 * 자리는 [ImmersiveScene.faceInSubject] 가 카드의 구멍에서 계산한다 — 따로 재지 않는다.
 */
@Immutable
data class SubjectFace(
    val face: CardFace,
    val hole: Hole,
    /**
     * 진입 연출에 쓸 **우리 카드**. 자리를 비운 판과 글자까지 한 벌이다.
     *
     * null 이면 저쪽 완성 카드가 그대로 녹는다 — 무대에는 우리 아이가 서 있는데
     * 들어가는 카드만 저쪽 것인 어정쩡한 상태가 되므로, 있으면 늘 넘긴다.
     */
    val entryArt: ImageBitmap? = null,
    val template: CardTemplate? = null,
    val name: String = "",
    val code: String = "",
)


/**
 * 누끼 안에서의 **얼굴 자리**. 무대 주인공에도 우리 아이 얼굴을 끼우려고 쓴다.
 *
 * **따로 재지 않는다.** 두 값을 이미 갖고 있어서 나누면 나온다 —
 * 카드 안 얼굴 구멍([CardTemplate.face])과 카드 안 누끼 자리([ImmersiveScene.fit])가
 * 둘 다 카드 크기 대비 % 라, 누끼를 기준으로 다시 재면 그만이다.
 *
 * 그래서 무대에 쓸 값이 카드의 값을 따라간다 — 카드 구멍을 고치면 무대도 같이 맞는다.
 */
fun ImmersiveScene.faceInSubject(template: CardTemplate): Hole = Hole(
    cx = (template.face.cx - fit.x) / fit.w * 100f,
    cy = (template.face.cy - fit.y) / fit.h * 100f,
    rx = template.face.rx / fit.w * 100f,
    ry = template.face.ry / fit.h * 100f,
)


/**
 * 카드 번호 → 이머시브 장면. **여기 없으면 이머시브가 아니다.**
 *
 * 카드마다 필요한 것이 레이어 원화 넉 장 · 곡 하나 · 실측값 둘(창 · fit)이라,
 * 저쪽에서 그 한 벌이 오기 전에는 늘릴 수가 없다. 오면 여기 줄 하나를 더한다.
 */
val IMMERSIVE_SCENES: Map<Int, ImmersiveScene> = mapOf(
    1 to CABBAGE_SCENE,
    10 to SWEET_POTATO_SCENE,
    12 to LETTUCE_SCENE,
)

/**
 * 평면 하나가 시선에 따라 얼마나 밀리는가.
 *
 * 저쪽은 `translate3d(px * par, py * par * .68, 0)` 이다 — 세로는 가로의 68% 만
 * 움직인다. 사람이 폰을 기울일 때 좌우가 더 크게 느껴지기 때문이다.
 */
fun parallax(aim: Offset, par: Float): Offset =
    Offset((aim.x - 0.5f) * 2f * par, (aim.y - 0.5f) * 2f * par * 0.68f)

/** 저쪽 `--par` 값. 뒤에서 앞으로. */
object Par {
    const val SKY = 5f
    const val AMBIENT = 12f
    const val RAYS = 18f
    const val MOTES = 30f
    const val SUBJECT = 52f
    const val HUD = 30f

    /** 앞잎. 제일 앞이라 제일 많이 움직인다. */
    const val FORE = 100f

    /** 이슬만 0 이다 — 카메라 유리에 맺힌 것이라 화면을 따라 움직이면 안 된다. */
    const val DEW = 0f
}

/**
 * 씨를 고정한 난수.
 *
 * **장면은 매번 같은 모양이어야 한다.** 열 때마다 먼지가 다른 자리에 있으면 카드가
 * 아니라 스크린세이버가 된다 — 저쪽 주석 그대로다. 그래서 카드 id 로 씨를 만든다.
 */
class SceneRng(seed: Int) {
    private var s: Int = if (seed == 0) 1 else seed

    fun next(): Float {
        s = s * 1664525 + 1013904223
        return ((s.toLong() and 0xFFFFFFFFL).toFloat() / 4294967296f)
    }

    fun range(from: Float, to: Float) = from + next() * (to - from)
}

fun seedOf(text: String): Int = text.fold(7) { h, c -> h * 31 + c.code }

/** 떠다니는 초록빛 한 알. */
@Immutable
data class Mote(val at: Offset, val r: Float, val alpha: Float, val phase: Float, val speed: Float)

/** 카메라 유리에 맺힌 이슬. */
@Immutable
data class Dew(val at: Offset, val r: Float, val alpha: Float, val runs: Boolean)

/**
 * 앞에서 날리는 잎 한 장.
 *
 * [size] 와 [blur] 는 **누끼 높이 대비 %** 다. 저쪽 `.leaf` 가 `--hh` 를 곱하는 것과
 * 같다 — px 로 두면 폰에서 화면만 작아지고 잎은 그대로라 캐릭터를 덮는다.
 */
@Immutable
data class Leaf(
    val at: Offset,
    val size: Float,
    val alpha: Float,
    val blur: Float,
    /** 기울기(도). 흔들리면 여기서 13도가 더해진다. */
    val rot: Float,
    /** 흔들리며 밀리는 거리. 이것도 누끼 높이 대비 %. */
    val sway: Offset,
    val period: Float,
    val delay: Float,
)

/**
 * 장면을 만든다. 같은 [seed] 면 언제나 같은 배치가 나온다.
 *
 * 이슬은 **화면 한가운데를 피한다** — 주인공 얼굴에 앉으면 캐릭터가 안 읽힌다
 * (저쪽 `offCenter`).
 */
fun buildScene(scene: ImmersiveScene, seed: Int): SceneParts {
    val rng = SceneRng(seed)
    val motes = List(scene.motes) {
        Mote(
            at = Offset(rng.next(), rng.next()),
            r = rng.range(0.8f, 2.6f),
            alpha = rng.range(0.15f, 0.55f),
            phase = rng.range(0f, (2 * PI).toFloat()),
            speed = rng.range(0.15f, 0.5f),
        )
    }
    val dew = List(scene.dew) {
        // 가운데를 피해 자리를 잡는다
        var p = Offset(rng.next(), rng.next())
        var guard = 0
        while (kotlin.math.hypot(p.x - 0.5f, p.y - 0.45f) < 0.26f && guard++ < 8) {
            p = Offset(rng.next(), rng.next())
        }
        Dew(at = p, r = rng.range(2.5f, 7f), alpha = rng.range(0.18f, 0.5f), runs = it < 3)
    }
    // 잎도 가운데를 피한다. **이슬보다 더 중요하다** — 잎은 주인공보다 앞에
    // 그려지므로 얼굴에 앉으면 캐릭터가 아예 안 읽힌다. 저쪽도 같은 `offCenter` 를
    // 쓰는데, 예전에 우리가 화면 전체에 균등하게 뿌렸다가 밭을 가렸다.
    val leaves = List(scene.leaves) {
        var p = Offset(rng.next(), rng.next())
        var guard = 0
        while (kotlin.math.hypot(p.x - 0.5f, p.y - 0.45f) < 0.26f && guard++ < 8) {
            p = Offset(rng.next(), rng.next())
        }
        Leaf(
            at = p,
            size = rng.range(11f, 33f),
            alpha = rng.range(0.16f, 0.42f),
            blur = rng.range(1.25f, 3.45f),
            rot = rng.range(0f, 360f),
            sway = Offset(rng.range(-16f, 16f), rng.range(-10f, 16f)),
            period = rng.range(9f, 17f),
            delay = rng.range(0f, 16f),
        )
    }

    return SceneParts(motes, dew, leaves)
}

@Immutable
data class SceneParts(
    val motes: List<Mote>,
    val dew: List<Dew>,
    val leaves: List<Leaf> = emptyList(),
)

/** 먼지가 떠다니는 위치. 시간에 따라 아주 느리게 흔들린다. */
fun Mote.drift(timeMs: Long, size: Size): Offset {
    val t = timeMs / 1000f * speed
    return Offset(
        (at.x + sin(t + phase) * 0.01f) * size.width,
        (at.y + sin(t * 0.7f + phase) * 0.014f) * size.height,
    )
}
