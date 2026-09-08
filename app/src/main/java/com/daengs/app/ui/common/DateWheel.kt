package com.daengs.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import java.time.LocalDate
import java.time.YearMonth

/**
 * 돌려서 고르는 날짜 다이얼.
 *
 * **왜 글자로 안 받나.** 앞서 생일을 `2023-05-14` 로 적게 했는데, 하이픈 자리를 틀리거나
 * 자판을 숫자로 바꾸는 것부터가 번거롭다. 날짜는 **고르는 것**이지 쓰는 것이 아니다.
 *
 * **왜 Material 달력이 아닌가.** 그쪽은 제 색과 제 모서리를 들고 와서 이 앱 안에서만
 * 다른 화면처럼 보인다. 여기는 세 줄을 굴리는 것뿐이라 앱 색으로 그리는 편이 낫다.
 *
 * 가운데 한 칸만 진하게 두고 위아래는 흐리게 둔다 — 지금 걸린 값이 어느 줄인지
 * 눈으로 바로 잡히게 하려는 것이다.
 *
 * @param value 지금 고른 날. 없는 날(2월 30일 등)은 그 달의 마지막 날로 당겨진다
 * @param years 고를 수 있는 해의 범위
 */
@Composable
fun DateWheel(
    value: LocalDate,
    onChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    years: IntRange = (LocalDate.now().year - 30)..LocalDate.now().year,
) {
    val yearList = remember(years) { years.toList() }
    val monthList = remember { (1..12).toList() }
    // 달이 바뀌면 날 수가 바뀐다. 31일에 걸어 두고 2월로 옮기면 28일로 당겨야 한다.
    val dayList = remember(value.year, value.monthValue) {
        (1..YearMonth.of(value.year, value.monthValue).lengthOfMonth()).toList()
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
            items = yearList,
            selected = value.year,
            suffix = "년",
            width = 96.dp,
        ) { onChange(safeDate(it, value.monthValue, value.dayOfMonth)) }
        Wheel(
            items = monthList,
            selected = value.monthValue,
            suffix = "월",
            width = 76.dp,
        ) { onChange(safeDate(value.year, it, value.dayOfMonth)) }
        Wheel(
            items = dayList,
            selected = value.dayOfMonth,
            suffix = "일",
            width = 76.dp,
        ) { onChange(safeDate(value.year, value.monthValue, it)) }
    }
}

/** 없는 날은 그 달의 마지막 날로 당긴다. 1월 31일에서 2월로 옮기면 2월 28일이다. */
private fun safeDate(year: Int, month: Int, day: Int): LocalDate =
    LocalDate.of(year, month, day.coerceAtMost(YearMonth.of(year, month).lengthOfMonth()))

/**
 * 한 줄. 가운데 칸이 고른 값이다.
 *
 * `internal` 인 이유는 [TimeWheel] 이 같은 줄로 시·분을 굴리기 때문이다 — 날짜와 시각이
 * 다르게 생기면 같은 폼 안에서 두 가지 다이얼이 된다.
 *
 * @param label 칸에 찍을 글자. 분은 `5` 가 아니라 `05` 여야 시계처럼 읽힌다
 */
@Composable
internal fun Wheel(
    items: List<Int>,
    selected: Int,
    suffix: String,
    width: androidx.compose.ui.unit.Dp,
    label: (Int) -> String = { "$it$suffix" },
    onSelect: (Int) -> Unit,
) {
    val index = items.indexOf(selected).coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = index)
    // 위아래 두 칸을 비워야 첫 값과 마지막 값도 가운데에 설 수 있다.
    val row = ROW_HEIGHT

    // **멈춘 뒤에만 알린다.** 굴리는 동안 알리면 스쳐 지나간 값까지 다 골라져서,
    // 날 수가 바뀌는 달 줄이 굴러가는 도중에 튄다.
    val settled by remember { derivedStateOf { !state.isScrollInProgress } }
    LaunchedEffect(settled) {
        if (!settled) return@LaunchedEffect
        items.getOrNull(centerIndex(state))?.let { if (it != selected) onSelect(it) }
    }
    // 밖에서 값이 바뀌면(달이 바뀌어 날이 당겨진 경우) 줄도 따라간다.
    LaunchedEffect(selected) {
        val want = items.indexOf(selected)
        if (want >= 0 && want != centerIndex(state) && !state.isScrollInProgress) {
            state.scrollToItem(want)
        }
    }

    Box(Modifier.width(width).height(row * VISIBLE), contentAlignment = Alignment.Center) {
        // 고른 칸을 받쳐 주는 띠. 선을 두 줄 긋는 것보다 이쪽이 앱 결에 맞는다.
        Box(
            Modifier
                .fillMaxWidth()
                .height(row)
                .padding(horizontal = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PinkFaint),
        )
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            contentPadding = PaddingValues(vertical = row * ((VISIBLE - 1) / 2)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(items) { _, item ->
                val on = item == selected
                Box(Modifier.height(row), contentAlignment = Alignment.Center) {
                    Text(
                        label(item),
                        color = if (on) TextDark else TextMuted,
                        fontSize = if (on) 17.sp else 15.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

/** 지금 가운데에 선 칸. 반 칸 넘게 밀렸으면 다음 칸으로 본다. */
private fun centerIndex(state: LazyListState): Int {
    val first = state.firstVisibleItemIndex
    val item = state.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: return first
    return if (item > 0 && state.firstVisibleItemScrollOffset > item / 2) first + 1 else first
}

private val ROW_HEIGHT = 40.dp

/** 보이는 칸 수. **홀수여야 한다** — 가운데가 하나여야 고른 값이 하나다. */
private const val VISIBLE = 5

@androidx.compose.ui.tooling.preview.Preview(widthDp = 411, heightDp = 260)
@Composable
private fun DateWheelPreview() {
    val day = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(LocalDate.of(2023, 5, 14))
    }
    com.daengs.app.ui.theme.DaengsTheme {
        Box(Modifier.background(com.daengs.app.ui.theme.CreamBg).padding(20.dp)) {
            DateWheel(value = day.value, onChange = { day.value = it })
        }
    }
}
