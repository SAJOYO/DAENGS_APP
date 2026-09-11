package com.daengs.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.TextMuted
import kotlin.math.abs

/**
 * 휠을 담는 카드. **잠들어 있다가 톡 누르면 깨어난다.**
 *
 * 휠 안쪽은 `LazyColumn` 이라 그 위의 세로 제스처를 남김없이 먹는다. 그런데 휠을 쓰는
 * 화면은 전부 `verticalScroll` 폼이어서, 폼을 내리려고 휠 위에 손을 얹으면 폼 대신 날짜가
 * 돌아갔다. **스크롤이 안 된 것은 눈에 보이지만 생일이 하루 옮겨간 것은 안 보인다** —
 * 그게 이 장치가 막으려는 것이다.
 *
 * 세로 드래그 하나로 "폼을 내리려는 손"과 "휠을 돌리려는 손"을 구별할 방법은 없다. 축이
 * 같아서 속도로도 거리로도 갈리지 않는다. 그래서 **가르지 않고 물어본다** — 잠든 동안은
 * 휠이 드래그를 아예 안 받아 폼이 움직이고, 한 번 톡 누른 뒤에만 휠이 돌아간다.
 *
 * 깨어난 휠은 폼이 한 칸이라도 움직이면 다시 잠든다([onGloballyPositioned] 로 제 자리가
 * 바뀌는 것을 본다). 다 고르고 폼을 내리면 손을 안 대도 알아서 닫힌다.
 *
 * **전부 잠든 채로 시작한다.** 등록 폼처럼 눌러서 펼친 휠을 깨운 채로 내놓는 편이 탭이
 * 하나 주는데, 그러면 다 고른 뒤 폼을 내릴 때 휠이 깨어 있어 값이 또 조용히 바뀐다.
 * 폴더블처럼 휠이 화면을 거의 다 차지하는 기하에서는 휠 바깥을 잡을 자리도 없어서
 * 빠져나갈 길이 없다. 탭 하나가 그것보다 싸다.
 *
 * @param awake 지금 휠이 깨어 있나. 깨어 있는 동안만 드래그가 휠로 간다
 * @param onWake 잠든 카드를 톡 눌렀다
 * @param onSleep 폼이 움직였으니 도로 잠들 때다
 * @param hint 잠든 동안 카드 밑에 적을 안내. 무엇을 돌리는 휠인지는 화면마다 다르다
 */
@Composable
internal fun WheelCard(
    awake: Boolean,
    onWake: () -> Unit,
    onSleep: () -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    columns: @Composable RowScope.() -> Unit,
) {
    // 폼이 움직이면 카드도 따라 움직인다. 그 움직임이 "지금 폼을 보고 있다" 는 신호다.
    var seenY by remember { mutableFloatStateOf(Float.NaN) }

    Column(
        modifier.onGloballyPositioned { coords ->
            val y = coords.positionInWindow().y
            // 처음 자리를 잡을 때는 비교할 것이 없다. 한 픽셀 어림은 반올림이라 안 센다.
            if (!seenY.isNaN() && awake && abs(y - seenY) > 1f) onSleep()
            seenY = y
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(SHAPE)
                .background(CardWhite)
                // 테두리는 잠든 쪽도 같은 두께로 둔다. 굵기가 바뀌면 카드가 들썩인다.
                .border(BORDER, if (awake) DaengPink else Color.Transparent, SHAPE)
                .then(
                    if (awake) {
                        Modifier
                    } else {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            // 물결은 안 준다. 테두리가 켜지는 것이 눌렸다는 대답이다.
                            indication = null,
                            onClickLabel = hint,
                            onClick = onWake,
                        )
                    },
                )
                .testTag(if (awake) TAG_AWAKE else TAG_ASLEEP),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = columns,
        )
        Spacer(Modifier.height(4.dp))
        // **깨어 있을 때도 자리를 비워 둔다.** 글자가 사라지며 카드가 올라오면
        // 고르는 중에 화면이 흔들린다.
        Text(
            if (awake) "" else hint,
            color = TextMuted,
            fontSize = 11.sp,
            modifier = Modifier
                .height(HINT_HEIGHT)
                .semantics { if (awake) contentDescription = "" },
        )
    }
}

private val SHAPE = RoundedCornerShape(14.dp)

/** 깨어 있다는 표시. 이보다 가늘면 화면에서 안 보이고, 굵으면 칸 글자를 밀어낸다. */
private val BORDER = 1.5.dp

/** 안내 한 줄 자리. 11sp 한 줄이 들어간다. */
private val HINT_HEIGHT = 14.dp

internal const val TAG_AWAKE = "wheel-awake"
internal const val TAG_ASLEEP = "wheel-asleep"
