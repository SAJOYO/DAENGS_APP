package com.daengs.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextMuted

enum class BottomTab(val label: String, val icon: DaengsIcon) {
    Home("홈", DaengsIcon.Home),
    Walks("산책기록", DaengsIcon.Paws),
    Dex("도감", DaengsIcon.Book),
    My("마이", DaengsIcon.Person),
}

private val BarHeight = 64.dp
private val FabSize = 58.dp
private val FabLift = 22.dp

/**
 * 하단 네비게이션.
 *
 * Material3 `NavigationBar` 를 쓰지 않는다 — 시안의 가운데 버튼이 바 위로
 * 튀어나오는데 NavigationBar 는 자식을 바 안에 가두므로 맞지 않는다.
 *
 * 시스템 네비게이션 바 인셋은 아이콘 줄에 [navigationBarsPadding] 으로 직접 먹인다.
 * 인셋 높이를 밖에서 계산해 넘기면 기기마다(3버튼/제스처) 어긋나서 라벨이 잘린다.
 * 이렇게 하면 흰 바탕은 제스처 핸들 아래까지 늘어나고 아이콘 줄은 그 위에 남는다.
 */
@Composable
fun DaengsBottomBar(
    selected: BottomTab,
    onSelect: (BottomTab) -> Unit,
    onCenter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth()) {

        Surface(
            color = CardWhite,
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(top = FabLift)
                .shadow(10.dp, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp), clip = false),
        ) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().height(BarHeight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                BottomItem(BottomTab.Home, selected, onSelect, Modifier.weight(1f))
                BottomItem(BottomTab.Walks, selected, onSelect, Modifier.weight(1f))
                // 가운데 버튼 자리
                Spacer(Modifier.weight(1f))
                BottomItem(BottomTab.Dex, selected, onSelect, Modifier.weight(1f))
                BottomItem(BottomTab.My, selected, onSelect, Modifier.weight(1f))
            }
        }

        Box(
            Modifier
                .align(Alignment.TopCenter)
                .size(FabSize)
                .shadow(8.dp, RoundedCornerShape(50), clip = false)
                .clip(RoundedCornerShape(50))
                .background(DaengPink)
                .clickable(onClick = onCenter),
            contentAlignment = Alignment.Center,
        ) {
            DaengsIconView(DaengsIcon.Paw, Modifier.size(29.dp), tint = CardWhite)
        }
    }
}

@Composable
private fun BottomItem(
    tab: BottomTab,
    selected: BottomTab,
    onSelect: (BottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = tab == selected
    val tint = if (active) DaengPinkDeep else TextMuted
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = { onSelect(tab) },
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DaengsIconView(tab.icon, Modifier.size(23.dp), tint = tint, filled = active)
        Spacer(Modifier.height(3.dp))
        Text(
            tab.label,
            color = tint,
            fontSize = 10.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Preview(widthDp = 411, showBackground = true, backgroundColor = 0xFFFDF1EC)
@Composable
private fun DaengsBottomBarPreview() {
    DaengsTheme {
        DaengsBottomBar(BottomTab.Home, {}, {})
    }
}
