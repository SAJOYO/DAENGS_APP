package com.daengs.app.ui.dex

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.daengs.app.ui.dogcard.CARD_TEMPLATES
import com.daengs.app.ui.dogcard.CardTemplate
import com.daengs.app.ui.dogcard.Hole
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// 포일이 닿지 않을 자리 — **강아지 얼굴 둘**
//
// 포일은 카드 전체를 덮었다. 그러면 제일 먼저 망가지는 것이 우리 아이 얼굴이다.
// `FoilTune.shineOpacity` 주석이 같은 병을 이미 적어 뒀다 — 포일 대부분이
// `color-dodge` 라 밝은 원화에 얹으면 흰색으로 클리핑되고, **제일 먼저 사라지는 게
// 흰 강아지 얼굴**이다. 실기기에서 보면 비글이 누렇게 뜨고, 가지 카드에서는 눈이
// 한쪽은 보라 한쪽은 초록이 된다.
//
// 그때는 포일 세기를 통째로 0.30 까지 낮춰 막았는데, 그러면 반짝여야 할 금속 프레임까지
// 같이 죽는다. **세기가 아니라 자리를 가른다.**
//
// ## 포일을 건드리지 않는다. 카드를 다시 그린다
//
// 마스크를 포일 쪽에 넣으려면 [foilLayer] 안으로 들어가야 한다. 그 함수가
// `saveLayer(bounds, paint)` + `blendMode` 로 합성하기 때문이다 — **바깥에서 한 겹 더
// 감싸면 안쪽 `ColorDodge` 가 카드 그림이 아니라 투명과 섞여서 포일이 통째로 망가진다.**
// 그런데 `foilLayer` 호출 지점이 서른 곳 가까이 된다.
//
// 그래서 반대로 한다. **포일을 다 그린 뒤에, 얼굴 자리에만 카드를 다시 그린다.**
// 한 함수로 끝나고 `FoilPainters.kt` 는 한 글자도 안 바뀐다.
//
// ⚠️ **[HoloCard] 가 그리는 세 겹을 그대로 다시 그린다** — 얼굴(`beneath`) · 카드(`art`) ·
//    글자(`above`). 하나라도 빠지면 그 자리가 덮인다.
//    - `beneath` 를 빼면: 카드 알파가 얼굴 자리에서 0 이라 그 구멍으로 **얼굴 위 포일이
//      그대로 남는다.** 걷어내려던 바로 그것이 안 걷힌다
//    - `above` 를 빼면: 아바타 원이 이름칸과 겹치는 카드에서 **이름 글자가 잘린다.**
//      아트창 전체를 재우던 판에서 "우리 아이" 가 위 절반만 남는 것으로 실제로 걸렸다
//      (2026-09-04, 시금치)
//
// ## 왜 야채도 아니고 아트창도 아닌가
//
// 처음 요청은 "야채만 빼 달라" 였고, 열두 장을 실제로 그려 비교하면서 두 번 좁혔다.
//
//   야채 실루엣  마스크 12장을 손으로 튜닝해야 하고, 그러고도 **바로 옆 인쇄된 무지개
//                위에서 포일이 계속 날뛴다.** 야채 경계를 정확히 따도 그 알갱이는 안 없어진다
//   아트창 전체  그림은 깨끗해지는데 **덮는 면적이 너무 넓다.** 이름칸이 창 위 끝에
//                걸치는 카드가 있어 글자가 잘렸고, 카드가 통째로 얌전해져 홀로그램
//                카드 같지 않아졌다
//
// 남은 것이 **얼굴 둘**이다. 지켜야 할 것은 우리 아이 얼굴이고, 야채와 프레임의 반짝임은
// 원래 이 카드의 재미다. 자리가 작아서 겹칠 것도 없다.
// ---------------------------------------------------------------------------

/**
 * 포일이 닿지 않을 자리와 얼마나 걷어낼지.
 *
 * 좌표 단위는 [Hole] 과 같은 **카드 크기 대비 %** 다 (0~100).
 */
@Immutable
data class FoilQuiet(
    /** 그림 한가운데 큰 얼굴창. */
    val face: Hole,
    /** 왼쪽 위 작은 아바타 원. **같은 아이 얼굴이라 같이 지킨다** — 한쪽만 지키면 큰 얼굴은 멀쩡한데 작은 원만 누렇게 뜬다. */
    val avatar: Hole,
    /**
     * 구멍 반지름의 몇 배까지 잦아들지.
     *
     * **구멍보다 넓어야 한다.** 얼굴은 구멍보다 조금 크게 그려서 구멍 테두리가 얼굴
     * 가장자리를 물게 돼 있다(`CardSlots.kt`). 딱 구멍만큼만 재우면 그 물린 테두리에
     * 포일이 남아 얼굴에 링이 생긴다.
     */
    val spread: Float = 1.45f,
    /** 이 비율 안쪽은 [strength] 를 그대로 다 쓰고, 그 바깥은 잦아든다. */
    val core: Float = 0.72f,
    /**
     * 얼마나 걷어낼지. 1 이면 그 자리에서 포일이 완전히 사라진다.
     *
     * **1 로 두지 않는다.** 포일이 딱 0 이 되면 얼굴만 죽은 판으로 보여서, 반짝이는
     * 카드에 얼굴을 오려 붙인 것처럼 읽힌다. 조금 남겨 두면 "여기는 약하다" 가 된다.
     */
    val strength: Float = 0.92f,
)

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
    ?.let { FoilQuiet(face = it.face, avatar = it.avatar) }

/**
 * 얼굴 자리에 카드를 다시 그려 포일을 걷어낸다.
 *
 * [drawFoil] **뒤에** 부른다. 앞에서 부르면 그 위에 포일이 다시 덮여 아무 일도 안 한다.
 */
internal fun DrawScope.drawFoilQuiet(
    quiet: FoilQuiet,
    art: ImageBitmap?,
    beneath: (DrawScope.() -> Unit)?,
    above: (DrawScope.() -> Unit)? = null,
) {
    if (quiet.strength <= 0.001f) return
    val canvas = drawContext.canvas
    val bounds = Rect(Offset.Zero, size)

    // 레이어 투명도가 곧 "얼마나 걷어내나" 다. 다시 그린 카드가 반쯤 비치면 포일도
    // 반쯤 남는다.
    canvas.saveLayer(bounds, Paint().apply { alpha = quiet.strength.coerceIn(0f, 1f) })

    // [HoloCard] 와 **같은 순서**여야 한다 — 얼굴 · 카드 · 글자.
    beneath?.invoke(this)
    if (art != null) {
        drawImage(
            image = art,
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.High,
        )
    }
    above?.invoke(this)

    // 방금 다시 그린 카드를 **얼굴 둘만 남기고** 지운다.
    //
    // ⚠️ 마스크를 **자기 레이어 안에서** 만들어야 한다. 두 원을 `DstIn` 으로 바로
    //    그리면 뒤에 그린 원이 앞의 것을 지워서 **한 자리만 남는다.** 레이어에 둘을
    //    평범하게 겹쳐 그린 뒤, 그 레이어 전체를 `DstIn` 으로 내린다.
    canvas.saveLayer(bounds, Paint().apply { blendMode = BlendMode.DstIn })
    softHole(quiet.face, quiet)
    softHole(quiet.avatar, quiet)
    canvas.restore()

    canvas.restore()
}

/**
 * 가운데가 불투명하고 가장자리로 갈수록 투명해지는 **타원** 하나.
 *
 * ⚠️ **화면 전체에 사각형으로 그린다.** `drawCircle` 로 그리면 그 원 바깥이 마스크에서
 *    빠져서, 두 번째 원을 그릴 때 첫 원까지 같이 살아남지 못한다. 그라디언트 끝 색이
 *    투명이고 `Clamp` 라 사각형으로 덮어도 바깥은 투명하다.
 *
 * ⚠️ **원이 아니라 타원이다.** 처음에는 반지름을 폭에서만 쟀다 — 야채 열두 장이
 *    1080×1440 한 판이라 `1080×rx% == 1440×ry%` 가 성립했기 때문이다(배추 218.5 vs
 *    218.4). **과일이 들어오면서 그 전제가 깨졌다.** 비율이 0.699~0.804 로 제각각이고,
 *    젠틀 키위는 얼굴창 자체가 정원이 아니다(322×291px). 폭으로만 재면 세로가 모자라
 *    **얼굴 위아래 가장자리에 포일 링이 남는다.**
 *
 *    타원 그라디언트는 없으므로 **좌표를 늘려 원으로 만든 뒤 그린다** — `scale` 로
 *    세로를 `rx/ry` 배 눌러 두면 그 안에서는 정원이고, 화면에 놓일 때 타원이 된다.
 */
private fun DrawScope.softHole(hole: Hole, quiet: FoilQuiet) {
    val cx = size.width * hole.cx / 100f
    val cy = size.height * hole.cy / 100f
    val rx = size.width * hole.rx / 100f * quiet.spread
    val ry = size.height * hole.ry / 100f * quiet.spread
    if (rx <= 0f || ry <= 0f) return
    withTransform({ scale(scaleX = 1f, scaleY = ry / rx, pivot = Offset(cx, cy)) }) {
        drawRect(
            brush = Brush.radialGradient(
                0f to Color.White,
                quiet.core.coerceIn(0f, 0.99f) to Color.White,
                1f to Color.Transparent,
                center = Offset(cx, cy),
                radius = rx,
            ),
            // `scale` 이 그리는 자리를 눌러 놓으므로, 눌린 만큼 넓게 덮어야 화면
            // 전체가 마스크에 든다. 안 그러면 위아래가 마스크 밖으로 새어 나간다.
            topLeft = Offset(0f, cy - (cy + size.height) * rx / ry),
            size = androidx.compose.ui.geometry.Size(size.width, size.height * rx / ry * 2f),
        )
    }
}
