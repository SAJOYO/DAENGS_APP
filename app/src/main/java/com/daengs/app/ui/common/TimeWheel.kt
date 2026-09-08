package com.daengs.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengsTheme
import java.time.LocalTime

/**
 * 돌려서 고르는 시각 다이얼. [DateWheel] 과 같은 줄([Wheel])을 시·분으로 쓴다.
 *
 * **왜 글자로 안 받나.** `08:00` 을 적게 하면 콜론 자리와 자판 전환이 번거롭고, 서버는
 * `HH:MM` 만 받아서(저쪽 `_FEEDING_TIME`) `8시` 같은 값은 422 다. 고르게 하면 틀릴 수 없다.
 *
 * **분은 5분 단위다.** 급식 시각을 분 단위로 정하는 집은 없고, 60칸을 굴리면 원하는 분을
 * 지나치기 쉽다. 서버에서 온 값이 5분 단위가 아니면 그 값을 목록에 끼워 넣어 잃지 않는다.
 */
@Composable
fun TimeWheel(
    value: LocalTime,
    onChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hours = remember { (0..23).toList() }
    val minutes = remember(value.minute) {
        ((0 until 60 step MINUTE_STEP) + value.minute).distinct().sorted()
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardWhite),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Wheel(
            items = hours,
            selected = value.hour,
            suffix = "시",
            width = 84.dp,
            label = { "%02d시".format(it) },
        ) { onChange(LocalTime.of(it, value.minute)) }
        Wheel(
            items = minutes,
            selected = value.minute,
            suffix = "분",
            width = 84.dp,
            label = { "%02d분".format(it) },
        ) { onChange(LocalTime.of(value.hour, it)) }
    }
}

private const val MINUTE_STEP = 5

@Preview(widthDp = 411, heightDp = 260)
@Composable
private fun TimeWheelPreview() {
    val time = remember { androidx.compose.runtime.mutableStateOf(LocalTime.of(8, 0)) }
    DaengsTheme {
        Box(Modifier.background(CreamBg).padding(20.dp)) {
            TimeWheel(value = time.value, onChange = { time.value = it })
        }
    }
}
