package com.daengs.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.ui.DaengsLogo
import com.daengs.app.ui.DogAvatar
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextMuted

enum class TopTab(val label: String, val icon: DaengsIcon) {
    Home("홈", DaengsIcon.Home),
    Record("기록", DaengsIcon.Book),
}

@Composable
fun DaengsTopBar(
    selected: TopTab,
    onSelect: (TopTab) -> Unit,
    onBell: () -> Unit,
    onProfile: () -> Unit,
    avatar: DogBreed = HomeDemoData.DOG_BREED,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(CreamBg)
            // 방에 세로를 양보하려고 바짝 붙였다. 방이 세로로 긴 그림이라 위에서 몇 dp 를
            // 아끼면 방 전체 크기가 그만큼 커진다.
            .padding(start = 18.dp, end = 12.dp, top = 2.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DaengsLogo()
        Spacer(Modifier.weight(1f))

        TopTab.entries.forEach { tab ->
            TopTabItem(tab, tab == selected) { onSelect(tab) }
            Spacer(Modifier.width(14.dp))
        }

        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onBell)
                .padding(6.dp),
        ) {
            DaengsIconView(DaengsIcon.Bell, Modifier.size(22.dp), tint = TextMuted)
        }
        Spacer(Modifier.width(6.dp))

        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onProfile)
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DogAvatar(avatar, Modifier.size(32.dp))
            DaengsIconView(DaengsIcon.CaretDown, Modifier.size(15.dp), tint = TextMuted)
        }
    }
}

@Composable
private fun TopTabItem(tab: TopTab, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) DaengPinkDeep else TextMuted
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DaengsIconView(tab.icon, Modifier.size(23.dp), tint = tint, filled = selected)
        Spacer(Modifier.height(3.dp))
        Text(
            tab.label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Preview(widthDp = 411, showBackground = true, backgroundColor = 0xFFFDF1EC)
@Composable
private fun DaengsTopBarPreview() {
    DaengsTheme {
        DaengsTopBar(TopTab.Home, {}, {}, {})
    }
}
