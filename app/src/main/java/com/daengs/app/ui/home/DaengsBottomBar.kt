package com.daengs.app.ui.home

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
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

/**
 * 하단 탭.
 *
 * **[Nearby] 는 예전에 "산책기록" 이었다.** 그런데 여는 화면은 처음부터 장소 지도라
 * 이름과 내용이 달랐다 — 탭을 누르면 산책 기록이 나올 줄 알고 누른다. 산책 기록은
 * 홈의 "오늘의 산책 요약" 카드에서 펼치는 것으로 자리가 정해져서, 이 탭은 장소
 * 찾기 전용이 됐다.
 */
enum class BottomTab(val label: String, val icon: DaengsIcon) {
    Home("홈", DaengsIcon.Home),
    Nearby("내 주변", DaengsIcon.Pin),
    Dex("도감", DaengsIcon.Book),

    /**
     * 찍은 사진과 영상을 모아 볼 자리. **아직 준비 중이다.**
     *
     * 예전에는 이 자리가 "마이"(강아지 목록·설정)였고 이름표만 먼저 "저장소" 로
     * 바꿔 뒀다. 그때 주석에 "화면 내용이 실제로 저장소가 될 때 상수도 같이 바꾼다"
     * 고 적었고, 지금 그렇게 했다 — **이름표와 하는 일이 어긋난 채로 두지 않는다.**
     *
     * 마이는 상단바의 프로필 사진 버튼으로 간다. 탭이 아니다.
     */
    Storage("저장소", DaengsIcon.Camera),
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
    /** 방 둘러보기가 "저장소" 를 밝힐 수 있게 자리를 등록한다. null 이면 안 한다. */
    tourSpots: TourSpots? = null,
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
                BottomItem(BottomTab.Nearby, selected, onSelect, Modifier.weight(1f))
                // 가운데 버튼 자리
                Spacer(Modifier.weight(1f))
                BottomItem(BottomTab.Dex, selected, onSelect, Modifier.weight(1f))
                BottomItem(
                    BottomTab.Storage,
                    selected,
                    onSelect,
                    Modifier.weight(1f).tourSpot(tourSpots, TourStop.Storage),
                )
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

/**
 * 가로에서 하단바 대신 서는 **왼쪽 세로 레일**.
 *
 * ## 왜 필요한가
 *
 * 가로에서는 세로 공간이 411dp 뿐이라 하단바가 설 자리가 없다. 실제로 가로로 눕히면
 * **바가 통째로 사라져서 도감에 들어가면 다른 탭으로 갈 방법이 없었다** — 뒤로가기
 * 말고는 갇힌다. 세로 공간을 안 먹는 쪽으로 옮긴다.
 *
 * 항목·아이콘·고른 표시는 [DaengsBottomBar] 와 같은 [BottomItem] 을 쓴다. 두 벌로
 * 만들면 한쪽만 고쳐지는 날이 온다.
 */
@Composable
fun DaengsNavRail(
    selected: BottomTab,
    onSelect: (BottomTab) -> Unit,
    onCenter: () -> Unit,
    modifier: Modifier = Modifier,
    tourSpots: TourSpots? = null,
    /**
     * 레일 맨 위에 얹을 것 (프로필·알림).
     *
     * **가로에서는 상단바를 없앤다.** 로고가 두 줄이라 상단바만으로 화면의 3분의 1을
     * 먹었다. 프로필은 어디선가는 눌러야 하므로 레일이 받는다.
     */
    header: (@Composable () -> Unit)? = null,
) {
    Surface(
        color = CardWhite,
        shape = RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp),
        modifier = modifier
            .fillMaxHeight()
            .shadow(10.dp, RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp), clip = false),
    ) {
        // **스크롤을 둔다.** 가로에서는 세로가 411dp 뿐이라 머리(프로필) + 항목 넷 +
        // 가운데 버튼이 다 안 들어가서 **저장소가 화면 밖으로 밀렸다** — 눌릴 수가
        // 없으니 그 탭에 영영 못 간다. 넘치면 스크롤되고, 안 넘치면 가운데에 선다.
        Column(
            Modifier
                .width(RailWidth)
                .fillMaxHeight()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            header?.let {
                it()
                Spacer(Modifier.height(14.dp))
            }
            BottomItem(BottomTab.Home, selected, onSelect)
            BottomItem(BottomTab.Nearby, selected, onSelect)
            // 가운데 버튼은 세로에서도 가운데다. 하단바에서 튀어나온 그 자리와 같은 뜻이다.
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .size(FabSize)
                    .shadow(8.dp, RoundedCornerShape(50), clip = false)
                    .clip(RoundedCornerShape(50))
                    .background(DaengPink)
                    .clickable(onClick = onCenter),
                contentAlignment = Alignment.Center,
            ) {
                DaengsIconView(DaengsIcon.Paw, Modifier.size(29.dp), tint = CardWhite)
            }
            Spacer(Modifier.height(10.dp))
            BottomItem(BottomTab.Dex, selected, onSelect)
            BottomItem(
                BottomTab.Storage,
                selected,
                onSelect,
                Modifier.tourSpot(tourSpots, TourStop.Storage),
            )
        }
    }
}

/** 레일의 폭. 아이콘 23dp + 글자 10sp 가 들어가고 손가락이 닿는 최소치를 넘는다. */
private val RailWidth = 76.dp

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
