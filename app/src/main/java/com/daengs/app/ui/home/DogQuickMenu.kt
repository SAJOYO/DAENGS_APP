package com.daengs.app.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextDark
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 강아지 머리 위에 뜨는 퀵 메뉴 한 칸.
 *
 * @param label 버튼 밑에 붙는 말. **두 글자에서 네 글자.** 길면 옆 칸과 붙는다
 * @param icon 동그라미 안에 들어가는 그림
 */
data class DogQuickAction(
    val label: String,
    val icon: DaengsIcon,
    val onClick: () -> Unit,
)

/**
 * 방에서 강아지를 누르면 **그 아이 머리 위에** 뜨는 부채꼴 메뉴.
 *
 * 방에서 제일 눌러 보고 싶게 생긴 것이 강아지인데, 여태 눌러도 아무 일이 없었다
 * (끄는 것만 됐다). 그 자리를 채우는 것이 하나이고, 다른 하나는 **누른 아이가 누구인지**
 * 를 기능에 실어 보내는 것이다 — 방에 여러 마리가 있어도 산책이든 카드든 대표 아이로만
 * 흘러가고 있었다.
 *
 * ## 이 컴포저블이 지키는 것
 *
 * **자리는 받는다, 계산하지 않는다.** [head] 는 캔버스가 히트 판정에 쓴 그 셈에서 나온
 * 값이다 (`DogTapTarget`). 여기서 격자나 그림 크기를 다시 만지면 방 그림·배치가 바뀔 때
 * 가리키는 곳과 눌리는 곳이 조용히 갈라진다.
 *
 * **화면 밖으로 안 나간다.** 강아지가 벽 쪽에 붙어 있으면 부채꼴 한쪽이 잘린다. 그래서
 * 중심 x 를 [EDGE_MARGIN] 안쪽으로 당긴다 — 부채꼴이 아이에게서 조금 비껴 있는 편이
 * 버튼이 반쯤 잘려 못 눌리는 것보다 낫다.
 *
 * **바깥을 누르면 닫힌다.** 뒤에 투명한 판을 깔아 둔다. 그 판이 없으면 메뉴를 닫으려고
 * 방을 눌렀을 때 **다른 강아지가 잡혀서** 메뉴가 옮겨 다닌다.
 *
 * @param head 머리 꼭대기. 캔버스 왼쪽 위 기준 **픽셀**
 * @param actions 왼쪽부터 차례로 놓인다. 셋을 기준으로 각도를 잡았다
 * @param onDismiss 바깥을 눌렀을 때
 */
@Composable
fun DogQuickMenu(
    head: Offset,
    actions: List<DogQuickAction>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) return

    val density = LocalDensity.current
    // 튀어나오듯 뜬다.
    //
    // ⚠️ **첫 합성에서 목표값을 주면 애니메이션이 아예 안 돈다.** `animateFloatAsState`
    //    는 처음 값을 목표와 같게 잡으므로, `targetValue = 1f` 만 적으면 이미 1f 에서
    //    시작해 움직일 것이 없다. 그래서 한 프레임 뒤에 목표를 바꿔 준다.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val pop by animateFloatAsState(
        targetValue = if (shown) 1f else 0.7f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 520f),
        label = "quick-menu-pop",
    )

    BoxWithConstraints(modifier.fillMaxSize().testTag(QUICK_MENU_TAG)) {
        // 부채꼴을 화면 안으로 당기려면 **여기서 잰 가로**가 필요하다. 밖에서 받아오면
        // 방 캔버스와 이 판의 크기가 어긋날 때 조용히 빗나간다.
        val widthPx = constraints.maxWidth.toFloat()
        // 바깥 판. **물결 효과를 끈다** — 방 위에서 동그라미가 번지면 그림이 지저분해진다.
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .testTag(QUICK_MENU_SCRIM_TAG),
        )

        actions.forEachIndexed { i, action ->
            val angle = fanAngleOf(i, actions.size)
            val dx = with(density) { (FAN_RADIUS.toPx() * cos(angle)) }
            val dy = with(density) { (FAN_RADIUS.toPx() * sin(angle)) }
            val half = with(density) { CELL_WIDTH.toPx() / 2f }
            val margin = with(density) { EDGE_MARGIN.toPx() }
            // 중심을 화면 안쪽으로 당긴 뒤에 부채꼴을 편다. 당기기 전에 펴면 한쪽만 잘린다.
            val cx = head.x.coerceIn(margin + half, (widthPx - margin - half).coerceAtLeast(margin + half))
            Column(
                Modifier
                    .offset {
                        // ⚠️ **칸이 아니라 동그라미의 중심**을 반지름 자리에 놓는다.
                        //    칸 바닥을 맞췄더니 글자 높이(68dp)만큼 통째로 더 올라가서,
                        //    메뉴가 방 위쪽 벽까지 떠올랐다 (실기기에서 바로 보였다).
                        //    동그라미는 칸의 맨 위에 있으므로 그 절반만 빼면 된다.
                        IntOffset(
                            (cx + dx - half).roundToInt(),
                            (head.y + dy - with(density) { BUTTON_SIZE.toPx() / 2f }).roundToInt(),
                        )
                    }
                    .scale(pop)
                    .size(width = CELL_WIDTH, height = CELL_HEIGHT),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier
                        .size(BUTTON_SIZE)
                        .shadow(4.dp, RoundedCornerShape(50), clip = false)
                        .background(CardWhite, RoundedCornerShape(50))
                        .clickable(onClick = action.onClick)
                        .testTag(quickActionTag(action.label)),
                    contentAlignment = Alignment.Center,
                ) {
                    DaengsIconView(action.icon, Modifier.size(18.dp), tint = DaengPink)
                }
                Text(
                    action.label,
                    color = TextDark,
                    fontSize = 10.sp,
                    // ⚠️ **줄 높이를 글자에 맞춰 못 박는다.** 기본값은 한글 글꼴이
                    //    위아래로 남겨 둔 여백까지 담아서, 상자 안에서 글자가 아래로
                    //    가라앉은 것처럼 보인다 (실기기에서 바로 보였다).
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(CardWhite.copy(alpha = 0.92f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 5.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/**
 * 강아지 퀵 메뉴에 올라가는 셋. **없는 길은 빼고 만든다.**
 *
 * 셋 다 *"이 아이와"* 가 말이 되는 것들이다. 음악(턴테이블)과 방 꾸미기(`+`)를 뺀 것은
 * 자리가 모자라서가 아니라 **방 이야기지 강아지 이야기가 아니어서**다. 여기에 방
 * 기능을 끼우기 시작하면 이 메뉴가 무엇의 메뉴인지가 흐려진다.
 *
 * null 인 길은 칸 자체가 안 생긴다 — 로그인 전이나 강아지가 없을 때 눌리지 않는 버튼을
 * 보여 주는 것보다 없는 편이 낫다. 그래서 [DogQuickMenu] 는 칸 수에 맞춰 부채꼴을
 * 다시 편다 ([fanAngleOf]).
 */
internal fun dogQuickActions(
    onWalk: (() -> Unit)?,
    onDraw: (() -> Unit)?,
    onAsk: (() -> Unit)?,
): List<DogQuickAction> = buildList {
    onWalk?.let { add(DogQuickAction("산책", DaengsIcon.Paws, it)) }
    onDraw?.let { add(DogQuickAction("카드", DaengsIcon.Book, it)) }
    onAsk?.let { add(DogQuickAction("질문", DaengsIcon.Chat, it)) }
}

/**
 * 칸 하나가 앉는 각도(라디안). **화면 좌표라 y 가 아래로 간다** — 위로 펴려면 음수다.
 *
 * 하나면 곧게 위로, 여럿이면 [FAN_SPREAD] 를 고르게 나눈다. 셋일 때 -150°·-90°·-30° 다.
 */
internal fun fanAngleOf(index: Int, count: Int): Float {
    if (count <= 1) return -HALF_PI
    val step = FAN_SPREAD / (count - 1)
    return -HALF_PI - FAN_SPREAD / 2f + step * index
}

/**
 * 머리에서 버튼 중심까지.
 *
 * 가까우면 아이를 가리고, 멀면 누구 메뉴인지가 흐려진다. **실기기에서 재서 정했다** —
 * 62dp 는 방 위쪽 벽까지 올라가서 문 옆 「산책 나가기」 알약과 겹쳤다.
 */
private val FAN_RADIUS = 44.dp

/** 부채꼴이 벌어지는 각도. 120°. 더 벌리면 양 끝이 아이 옆구리로 내려온다. */
private const val FAN_SPREAD = 2.0943952f

private const val HALF_PI = 1.5707964f

private val BUTTON_SIZE = 36.dp
private val CELL_WIDTH = 46.dp
private val CELL_HEIGHT = 56.dp

/** 화면 가장자리에서 이만큼은 떨어진다. 벽에 붙은 아이의 메뉴가 잘리지 않게. */
private val EDGE_MARGIN = 8.dp

internal const val QUICK_MENU_TAG = "dog-quick-menu"
internal const val QUICK_MENU_SCRIM_TAG = "dog-quick-menu-scrim"

internal fun quickActionTag(label: String) = "dog-quick-action-$label"

/**
 * 머리 위에 셋이 어떻게 앉는지. **모션은 프리뷰에서 안 돈다** — 여기서 보는 것은 각도와
 * 간격, 그리고 글자가 옆 칸과 안 붙는지다.
 *
 * 가장자리에 붙은 경우는 프리뷰로 못 본다. 실기기에서 아이를 벽 쪽으로 끌어다 놓고 본다.
 */
@Preview(showBackground = true, backgroundColor = 0xFFFDF4F0, widthDp = 320, heightDp = 260)
@Composable
private fun DogQuickMenuPreview() {
    DaengsTheme {
        Box(Modifier.fillMaxSize()) {
            DogQuickMenu(
                head = Offset(480f, 560f),
                actions = listOf(
                    DogQuickAction("산책", DaengsIcon.Paws) {},
                    DogQuickAction("카드", DaengsIcon.Book) {},
                    DogQuickAction("질문", DaengsIcon.Chat) {},
                ),
                onDismiss = {},
            )
        }
    }
}
