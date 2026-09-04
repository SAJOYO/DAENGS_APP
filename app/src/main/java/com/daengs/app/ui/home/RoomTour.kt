package com.daengs.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// 방 둘러보기
//
// **방에 처음 들어온 사람은 무엇을 누를 수 있는지 모른다.** 문·턴테이블·액자는 라벨
// 붙은 버튼이 아니라 그림의 일부라, 누를 수 있다는 것 자체가 안 보인다. 이 저장소는
// 같은 교훈을 한 번 적었다 — 이머시브에 "꾹 눌러서 들어가기" 배지를 단 이유가
// "꾹 누르기는 발견해야 아는 손짓이라 유일한 길이면 안 된다" 였다.
//
// **글로만 말하지 않는다.** "문을 눌러 산책해요" 라고만 쓰면 어느 그림이 문인지
// 모른다. 화면을 어둡게 하고 그 자리만 밝혀 한 줄씩 말한다.
// ---------------------------------------------------------------------------

/** 둘러보기가 가리키는 자리. */
enum class TourStop { Door, Turntable, Frame, Storage, Chat }

/**
 * 한 단계.
 *
 * @param stop 밝힐 자리
 * @param text 그 자리에서 할 말. **한 줄이다** — 여러 줄이면 그림이 아니라 글을 읽게 된다
 */
data class TourStep(val stop: TourStop, val text: String)

/**
 * 다섯 단계. **방 안 셋을 먼저, 방 밖 둘을 뒤에** 둔다 — 눈이 방에서 화면 아래로
 * 한 번만 옮겨 가면 된다.
 */
val TOUR_STEPS = listOf(
    TourStep(TourStop.Door, "문을 눌러서 산책해요"),
    TourStep(TourStop.Turntable, "턴테이블을 눌러 노래를 들어요"),
    TourStep(TourStop.Frame, "도감과 액자에서 카드를 뽑아요"),
    TourStep(TourStop.Storage, "저장소에서 아이와 나의 기록을 봐요"),
    TourStep(TourStop.Chat, "궁금한 게 있을 땐 채팅을 눌러보세요"),
)

/**
 * 각 자리가 화면 어디에 있는지.
 *
 * **자리를 여기에 박지 않는다.** 그리는 쪽이 자기 자리를 등록한다 ([tourSpot]) —
 * 방 그림이 바뀌거나 하단바가 움직여도 같이 따라간다. 좌표를 복사해 두면 반드시
 * 어긋난다.
 */
@Stable
class TourSpots {
    private val rects = mutableStateMapOf<TourStop, Rect>()

    fun put(stop: TourStop, rect: Rect) {
        if (rects[stop] != rect) rects[stop] = rect
    }

    operator fun get(stop: TourStop): Rect? = rects[stop]
}

/** 이 자리를 둘러보기가 가리킬 수 있게 등록한다. 창 기준 좌표다. */
fun Modifier.tourSpot(spots: TourSpots?, stop: TourStop): Modifier =
    if (spots == null) this else onGloballyPositioned { spots.put(stop, it.boundsInWindow()) }

/**
 * 밝힌 자리를 얼마나 넉넉하게 뚫을지 (px).
 *
 * 딱 맞게 뚫으면 물건이 구멍에 꽉 차서 **무엇을 가리키는지가 아니라 잘린 것으로**
 * 읽힌다. 조금 남겨야 "이것" 이 된다.
 */
private const val HOLE_PAD = 14f

/**
 * 말풍선을 구멍 위에 둘지 아래에 둘지.
 *
 * **구멍을 가리면 안 된다.** 아래에 자리가 모자라면 위로 올린다 — 하단바를 가리킬
 * 때가 그렇다.
 *
 * @return 말풍선 위 끝의 y
 */
fun tourCaptionTop(hole: Rect, screenHeight: Float, captionHeight: Float, gap: Float): Float {
    val below = hole.bottom + gap
    if (below + captionHeight <= screenHeight) return below
    val above = hole.top - gap - captionHeight
    return above.coerceAtLeast(gap)
}

/**
 * [from] 부터 세어 **자리가 있는 첫 단계.** 없으면 null.
 *
 * 자리가 없는 단계는 건너뛴다. 두 경우가 있다.
 *
 * - **아직 안 올라왔다.** 방이 자리를 알려 주는 것은 첫 배치 뒤라, 겹이 먼저 뜨는
 *   프레임이 실제로 있다 (기기에서 매번 그렇다)
 * - **그 물건이 방에 없다.** 턴테이블은 치울 수 있다
 *
 * 둘 다 **그 단계만 빼고 넘어가야** 한다. 예전에는 여기서 겹을 통째로 안 그렸는데,
 * 그러면 화면에 아무것도 없으면서 둘러보기는 켜진 상태라 **넘길 방법도 건너뛸 방법도
 * 없어진다.**
 */
fun showableStep(from: Int, hasSpot: (TourStop) -> Boolean): Int? =
    (from..TOUR_STEPS.lastIndex).firstOrNull { hasSpot(TOUR_STEPS[it].stop) }

/**
 * 어둡게 깔고 한 곳만 밝히는 겹.
 *
 * @param spots 자리 등록부. 아직 안 올라온 자리면 [showableStep] 이 그 단계를 건너뛴다 —
 *   빈 화면에 대고 "여기를 누르세요" 라고 하면 안 된다
 * @param stepIndex 지금 몇 번째. 여기서부터 **자리가 있는** 단계를 찾아 그린다
 * @param onNext 넘길 때. **실제로 보여 준 단계 번호**를 준다 — 건너뛴 단계가 있으면
 *   부르는 쪽이 센 번호와 다르다
 */
@Composable
fun RoomTourOverlay(
    spots: TourSpots,
    stepIndex: Int,
    onNext: (shown: Int) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 하나도 못 찾으면 이번 프레임은 넘긴다. 방이 자리를 알려 주면 다음 프레임에 뜬다.
    val at = showableStep(stepIndex) { spots[it] != null } ?: return
    val step = TOUR_STEPS[at]
    val hole = spots[step.stop] ?: return
    val density = LocalDensity.current
    val gap = with(density) { 16.dp.toPx() }
    val captionHeight = with(density) { 132.dp.toPx() }
    var screenHeightPx by remember { mutableFloatStateOf(0f) }

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { screenHeightPx = it.size.height.toFloat() }
            // **겹 아래로 손이 안 내려간다.** 밝힌 자리를 눌러 보라고 안내하는 것이
            // 아니라 읽고 넘기는 것이라, 눌러서 뭔가 되면 단계가 꼬인다.
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { onNext(at) },
            ),
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Color(0xCC1A1210))
            // 구멍. `Clear` 는 오프스크린 층에서만 먹는다 — 안 그러면 화면 전체가 뚫린다.
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(hole.left - HOLE_PAD, hole.top - HOLE_PAD),
                size = Size(hole.width + HOLE_PAD * 2, hole.height + HOLE_PAD * 2),
                cornerRadius = CornerRadius(18f, 18f),
                blendMode = BlendMode.Clear,
            )
        }

        val top = tourCaptionTop(hole, screenHeightPx, captionHeight, gap)
        TourCaption(
            step = step,
            index = at,
            onNext = { onNext(at) },
            onSkip = onSkip,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .offset { IntOffset(0, top.roundToInt()) },
        )
    }
}

@Composable
private fun TourCaption(
    step: TourStep,
    index: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(CardWhite)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(
            "${index + 1} / ${TOUR_STEPS.size}",
            color = TextMuted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(step.text, color = TextDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // **건너뛰기는 첫 단계부터 보인다.** 마지막에야 나오면 그때까지는 갇힌 셈이다.
            Text(
                "건너뛰기",
                color = TextMuted,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onSkip)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Spacer(Modifier.width(1.dp).weight(1f))
            Text(
                if (index == TOUR_STEPS.lastIndex) "다 봤어요" else "다음",
                color = CardWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(DaengPink)
                    .clickable(onClick = onNext)
                    .padding(horizontal = 20.dp, vertical = 9.dp),
            )
        }
    }
}

// -- 프리뷰 ------------------------------------------------------------------
//
// 진짜 자리는 방이 그려져야 나오므로, 여기서는 **말풍선이 앉는 모양**만 본다.

@Preview(widthDp = 411, heightDp = 300, showBackground = true)
@Composable
private fun TourCaptionPreview() {
    DaengsTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TourCaption(TOUR_STEPS.first(), 0, {}, {})
            TourCaption(TOUR_STEPS.last(), TOUR_STEPS.lastIndex, {}, {})
        }
    }
}
