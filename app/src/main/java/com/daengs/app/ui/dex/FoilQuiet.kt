package com.daengs.app.ui.dex

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.daengs.app.ui.dogcard.CARD_TEMPLATES
import com.daengs.app.ui.dogcard.CardTemplate
import com.daengs.app.ui.dogcard.Hole
import com.daengs.app.ui.dogcard.Slot
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// 포일이 닿지 않을 자리
//
// 포일은 카드 **전체**를 덮었다. 그런데 아트창 배경은 이미 인쇄된 무지개 폭발이라
// 그 위에 움직이는 포일이 또 얹히면 그림을 읽을 수가 없다 — "홀로그램 때문에
// 정신없는 카드가 많다".
//
// `FoilTune.shineOpacity` 주석이 같은 병을 이미 한 번 적어 뒀다. 포일 대부분이
// `color-dodge` 라 밝은 원화에 얹으면 흰색으로 클리핑되고, **제일 먼저 사라지는 것이
// 얼굴**이다. 그때는 세기를 통째로 0.30 까지 낮춰 막았는데, 그러면 반짝여야 할 금속
// 프레임까지 같이 죽는다. **자리를 가르는 편이 맞다.**
//
// ## 포일을 건드리지 않는다. 카드를 다시 그린다
//
// 마스크를 포일 쪽에 넣으려면 [foilLayer] 안으로 들어가야 한다. 그 함수가
// `saveLayer(bounds, paint)` + `blendMode` 로 합성하기 때문이다 — **바깥에서 한 겹 더
// 감싸면 안쪽 `ColorDodge` 가 카드 그림이 아니라 투명과 섞여서 포일이 통째로 망가진다.**
// 그런데 `foilLayer` 호출 지점이 서른 곳 가까이 된다.
//
// 그래서 반대로 한다. **포일을 다 그린 뒤에, 조용할 자리에만 카드를 다시 그린다.**
// 한 함수로 끝나고 `FoilPainters.kt` 는 한 글자도 안 바뀐다.
//
// ⚠️ **얼굴([HoloCard] 의 `beneath`)도 같이 다시 그려야 한다.** 카드 알파가 얼굴
//    자리에서 0 이라, `art` 만 다시 그리면 그 구멍으로 **얼굴 위 포일이 그대로 남는다.**
//    실제로 그려 보면 비글이 누렇게 뜨고 눈이 한쪽은 보라, 한쪽은 초록이 된다.
//
// ## 야채 실루엣이 아니라 아트창인 이유
//
// 처음 요청은 "야채만 빼 달라" 였다. 열두 장을 실제로 그려 비교하고 아트창으로 바꿨다 —
// **야채만 빼면 바로 옆 인쇄된 무지개 위에서 포일이 계속 날뛴다.** 야채 경계를 아무리
// 정확히 따도 그 알갱이는 안 없어진다. 반대로 아트창을 통째로 재우면 은색 프레임·
// 이름바·별·수치칩에서만 반짝이는데, 그게 실물 홀로 카드가 실제로 반짝이는 자리다.
// 비교 그림은 `HISTORY.md` 참고.
// ---------------------------------------------------------------------------

/**
 * 포일이 닿지 않을 자리와 얼마나 걷어낼지.
 *
 * 좌표 단위는 [Hole] · [Slot] 과 같은 **카드 크기 대비 %** 다 (0~100).
 */
@Immutable
data class FoilQuiet(
    /** 그림창. 이 안에서는 포일이 잦아든다. */
    val window: Slot,
    /**
     * 왼쪽 위 작은 얼굴.
     *
     * **창 밖에 있는 카드가 있다.** 아바타는 카드마다 위아래로 3%p 넘게 움직여서
     * (`cy - ry` 가 3.7% ~ 7.0%), 창 하나로는 어떤 카드는 덮고 어떤 카드는 못 덮는다.
     * 그러면 같은 강아지 얼굴이 큰 창에서는 멀쩡하고 작은 원에서만 누렇게 뜬다.
     * 그래서 창과 **따로** 더한다.
     */
    val avatar: Hole,
    /**
     * 얼마나 걷어낼지. 1 이면 그 자리에서 포일이 완전히 사라진다.
     *
     * **1 로 두지 않는다.** 포일이 딱 0 이 되면 그 자리만 죽은 판으로 보여서, 반짝이는
     * 프레임과 나란히 놓였을 때 인쇄 사고처럼 읽힌다. 조금 남겨 두면 "여기는 약하다"
     * 로 읽힌다.
     */
    val strength: Float = 0.9f,
)

/**
 * 열두 장이 함께 쓰는 그림창.
 *
 * **잰 값이 아니라 맞춘 값이다.** 프레임 두께가 카드마다 조금씩 달라서 자동으로 찾는
 * 것보다 미리보기를 보고 맞추는 편이 빠르다 — 저쪽 도구(`DAENGS_dev` 의
 * `tools/neo-hologram-layers.py`)도 아트 창 좌표를 같은 이유로 손으로 준다. 실제로
 * 변화량으로 자동 검출을 해 봤는데 피망이 `x0 = 72%` 로 나왔다. 야채 몸통이 매끈해서
 * "그림이 아닌 곳"으로 찍힌다.
 *
 * **열두 장을 다 겹쳐 보고 정했다.** 아래 76% 는 열두 장 모두 이름바 **바로 위**에
 * 떨어진다 (`FoilQuietTest` 가 얼굴 구멍이 이 창 안에 들어오는지로 지킨다).
 * 프레임 띠가 두꺼워서 1~2%p 어긋나도 눈에 안 보인다.
 *
 * 새로 들어온 카드가 이 창에서 벗어나면 [foilQuietFor] 에 그 카드만 예외를 둔다.
 */
val ART_WINDOW = Slot(x0 = 7f, y0 = 6f, x1 = 93f, y1 = 76f)

/**
 * 도감 카드 id 로 조용한 자리를 찾는다. 모르는 id 면 `null` — 그때는 예전처럼
 * 포일이 카드 전체를 덮는다.
 *
 * 도감(`DEX_CARDS`)과 카드 판(`CARD_TEMPLATES`)은 **같은 열두 장이고 id 가 같다.**
 * 그래서 도감 쪽에 좌표를 또 적지 않는다 — 두 군데가 되면 어긋난다.
 */
fun foilQuietFor(
    cardId: String,
    templates: List<CardTemplate> = CARD_TEMPLATES,
): FoilQuiet? = templates.firstOrNull { it.id == cardId }
    ?.let { FoilQuiet(window = ART_WINDOW, avatar = it.avatar) }

/**
 * 조용할 자리에 카드를 다시 그려 포일을 걷어낸다.
 *
 * [drawFoil] **뒤에** 부른다. 앞에서 부르면 그 위에 포일이 다시 덮여 아무 일도 안 한다.
 */
internal fun DrawScope.drawFoilQuiet(
    quiet: FoilQuiet,
    art: ImageBitmap?,
    beneath: (DrawScope.() -> Unit)?,
) {
    if (quiet.strength <= 0.001f) return
    val canvas = drawContext.canvas

    // 레이어 투명도가 곧 "얼마나 걷어내나" 다. 다시 그린 카드가 반쯤 비치면 포일도
    // 반쯤 남는다.
    canvas.saveLayer(
        Rect(Offset.Zero, size),
        Paint().apply { alpha = quiet.strength.coerceIn(0f, 1f) },
    )
    clipPath(quietPath(quiet)) {
        // **[HoloCard] 가 처음에 그리는 것과 같은 순서**여야 한다 — 얼굴이 먼저고
        // 카드가 그 위를 덮는다.
        beneath?.invoke(this)
        if (art != null) {
            drawImage(
                image = art,
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.High,
            )
        }
    }
    canvas.restore()
}

/**
 * 그림창과 아바타 원을 합친 자리.
 *
 * 두 조각을 한 [Path] 에 넣으면 합집합이 된다. 따로 두 번 자르면 두 번째가 첫 번째를
 * 대체해서 창이 사라진다.
 */
private fun DrawScope.quietPath(quiet: FoilQuiet): Path {
    val w = size.width
    val h = size.height
    return Path().apply {
        addRect(
            Rect(
                left = w * quiet.window.x0 / 100f,
                top = h * quiet.window.y0 / 100f,
                right = w * quiet.window.x1 / 100f,
                bottom = h * quiet.window.y1 / 100f,
            )
        )
        val cx = w * quiet.avatar.cx / 100f
        val cy = h * quiet.avatar.cy / 100f
        // 반지름은 폭 쪽만 쓴다 — 카드가 3:4 라 rx·ry 를 픽셀로 바꾸면 같은 값이다
        // (`FoilQuietTest` 가 열두 장에서 지킨다). 구멍보다 조금 크게 잡아 테두리를 문다.
        val r = w * quiet.avatar.rx / 100f * 1.08f
        addOval(Rect(cx - r, cy - r, cx + r, cy + r))
    }
}
