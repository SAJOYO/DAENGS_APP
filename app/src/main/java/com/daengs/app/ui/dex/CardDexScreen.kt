package com.daengs.app.ui.dex

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.miniroom.art.rememberAssetImage
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.geometry.Rect

// ---------------------------------------------------------------------------
// 네오 채소 도감
//
// 카드 12장을 모아 두고, 눌러서 크게 보고, 문지르거나 폰을 기울여 홀로그램 포일을
// 구경한다. 이머시브인 카드는 꾹 누르면 카드 "안으로" 들어간다 ([ImmersiveScreen]).
// 지금은 No.01 배추와 No.10 고구마 둘이다 ([IMMERSIVE_SCENES]).
//
// ## 어디서 왔나
//
// 원본은 팀 저장소 `SAJOYO/DAENGS_dev` 의 웹 데모(`frontend/public/neo-hologram/`)다.
// 한동안 그 폴더를 통째로 앱에 싣고 WebView 로 띄웠는데, **앱으로 통일하기로 하고
// 네이티브로 다시 만들었다.** 웹판은 커밋 3e1bf6c 까지 있으니 필요하면 꺼내 볼 수 있다.
//
// 카드 그림(`assets/neo-hologram/art/`)만 저쪽에서 그대로 가져다 쓴다. 프레임·제목·
// 기술명·수치가 전부 구워져 있어서, 우리가 만드는 건 **그 위에 얹는 효과뿐**이다.
// 포일을 어떻게 옮겼는지는 [Foil] 에 적어 뒀다.
//
// 화면 톤은 앱을 따른다 — 웹판은 어두운 배경(#0E0B05)이라 방에서 넘어올 때 분위기가
// 확 바뀌었는데, 네이티브로 온 이유가 그걸 없애는 것이기도 하다.
// ---------------------------------------------------------------------------

/** 도감 배경. 앱 크림색보다 살짝 가라앉혀 카드가 떠 보이게 한다. */
private val DexBg = Color(0xFFF6E9E3)

/** 카드 칸의 세로 비율. 웹판 `.slot .frame { aspect-ratio: 4/5 }` 와 같다. */
private const val SLOT_RATIO = 1.25f



@Composable
fun CardDexScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    var opened by remember { mutableStateOf<Int?>(null) }
    // **어느 장면인지가 곧 이머시브인지 여부다.** 예전에는 켜짐/꺼짐 불리언 하나였는데,
    // 카드가 둘이 되면서 "켜졌다"만으로는 무엇을 그릴지 모른다.
    var scene by remember { mutableStateOf<ImmersiveScene?>(null) }
    // 이머시브가 출발할 자리. 그리드가 사라진 뒤에도 써야 하므로 여기 둔다.
    var from by remember { mutableStateOf(Rect.Zero) }

    BackHandler {
        when {
            scene != null -> scene = null
            opened != null -> opened = null
            else -> onClose()
        }
    }

    scene?.let { showing ->
        ImmersiveScreen(scene = showing, from = from, onClose = { scene = null })
        return
    }

    Box(modifier.fillMaxSize().background(DexBg)) {
        DexGrid(
            onOpen = { opened = it },
            onClose = onClose,
            onImmersive = { at, picked ->
                from = at
                scene = picked
            },
        )

        AnimatedVisibility(
            visible = opened != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            val start = opened ?: 0
            CardViewer(startIndex = start, onClose = { opened = null })
        }
    }
}

// -- 그리드 -----------------------------------------------------------------

@Composable
private fun DexGrid(
    onOpen: (Int) -> Unit,
    onClose: () -> Unit,
    onImmersive: (Rect, ImmersiveScene) -> Unit,
) {
    LazyVerticalGrid(
        // 두 칸. 웹판에서 한 칸이면 카드가 화면을 꽉 채워 무거웠다.
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().systemBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
            DexHeader(onClose = onClose)
        }
        items(DEX_CARDS) { card ->
            GridCard(
                card = card,
                onOpen = { onOpen(DEX_CARDS.indexOf(card)) },
                // 이머시브인 카드는 [IMMERSIVE_SCENES] 가 정한다. 없으면 null 이 가고,
                // 그러면 꾹 누르기도 캡션 아래 배지도 안 붙는다.
                onImmersive = IMMERSIVE_SCENES[card.no]?.let { picked ->
                    { at: Rect -> onImmersive(at, picked) }
                },
            )
        }
    }
}

@Composable
private fun DexHeader(onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "← 방으로",
                color = TextMuted,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClose)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text("채소가 된 네오", color = TextDark, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "${DEX_CARDS.size} / ${DEX_CARDS.size} 수집 · 카드를 눌러 크게 보세요",
            color = TextMuted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GridCard(card: DexCard, onOpen: () -> Unit, onImmersive: ((Rect) -> Unit)?) {
    // 그리드에서는 작게 그리므로 절반 크기로 읽는다. 12장을 원본으로 들면 55MB 다.
    val art = rememberAssetImage(card.art, sample = 2)

    // 이머시브가 **이 카드 자리에서** 출발하도록 화면 위 사각형을 들고 있는다.
    // 창 위 좌표라 이머시브가 자기 자리를 빼서 쓴다 — 둘 다 같은 창이라 그걸로 맞는다.
    var at by remember { mutableStateOf(Rect.Zero) }
    val enter = onImmersive?.let { go -> { go(at) } }
    val rub = rememberRubState(onTap = onOpen, onHold = enter)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // **카드 높이를 칸마다 똑같이 맞춘다.** 그림 비율이 두 종류라(0.72 와 0.80)
        // 폭을 맞추면 높이가 제각각이 되어 줄이 어긋난다. 웹판이 "실물 카드 바인더와
        // 같은 정렬" 이라고 부르는 배치를 그대로 쓴다 — 높이는 같고 폭만 비율만큼
        // 달라지며, 그림은 한 픽셀도 안 잘린다.
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            HoloCard(
                art = art,
                foil = card.foil,
                input = rub.input,
                // 그리드에서는 기울이지 않는다. 열두 장이 한꺼번에 도는 건 산만하다.
                tilt = false,
                // 꾹 누르는 동안 차오르는 테두리. **없으면 카드가 멈춘 줄 안다** —
                // 꾹 누르기는 눌러보기 전엔 알 수가 없다.
                hold = rub.hold,
                holdColor = card.accent,
                // 칸 폭의 4:5. 웹판 `.slot .frame` 과 같은 비율이다.
                modifier = Modifier
                    .height(maxWidth * SLOT_RATIO)
                    .onGloballyPositioned { at = it.boundsInWindow() }
                    // **드래그를 안 먹는다.** 먹으면 카드를 짚고 쓸어내릴 때 목록이
                    // 안 움직인다.
                    .rubbable(rub, consume = false),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("No. %02d".format(card.no), color = TextMuted, fontSize = 10.sp)
        Text(card.name, color = TextDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(card.statLine, color = TextMuted, fontSize = 11.sp, textAlign = TextAlign.Center)
        // **캡션 아래에 둔다.** 카드와 캡션 사이에 끼우면 이 칸만 캡션이 밀려 내려가
        // 옆 칸과 줄이 어긋난다. 웹판도 캡션 다음이다.
        if (onImmersive != null) {
            Spacer(Modifier.height(6.dp))
            // 꾹 누르기는 발견해야 아는 손짓이라 유일한 길이면 안 된다 (저쪽 주석).
            Text(
                "★★★ 꾹 눌러서 들어가기",
                color = TextDark,
                fontSize = 10.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(card.accent.copy(alpha = 0.25f))
                    .clickable { onImmersive(at) }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

// -- 확대 뷰 ----------------------------------------------------------------

/**
 * 카드를 화면 가득 보는 뷰.
 *
 * 저쪽이 정한 손짓을 그대로 따른다 — **문지르면 포일 구경**, 좌우 버튼으로 넘기기.
 * 쓸어서 넘기기는 저쪽이 해보고 버렸다 (포일을 구경하다 보면 손이 저절로 빨라져서
 * 속도로는 못 가른다).
 */
@Composable
private fun CardViewer(startIndex: Int, onClose: () -> Unit) {
    var index by remember { mutableIntStateOf(startIndex) }
    val card = DEX_CARDS[index]
    // 확대 뷰는 한 장뿐이라 원본 해상도로 읽는다.
    val art = rememberAssetImage(card.art)
    val rub = rememberRubState()

    // 폰을 기울이면 카드가 따라 기운다. **확대 뷰에서만** 켠다 — 그리드에서 열두 장이
    // 한꺼번에 도는 건 산만하고 비싸다.
    val tiltTracker = remember { TiltTracker() }
    DeviceTilt { beta, gamma -> tiltTracker.feed(beta, gamma) }
    // 카드를 넘기면 지금 자세가 다시 정면이 된다
    LaunchedEffect(index) { tiltTracker.reset() }

    // **손가락이 이긴다.** 두 입력이 같은 카드를 두고 매 프레임 싸우면 화면이 떤다.
    val input = if (rub.input.intensity > 0f) rub.input else (tiltTracker.input ?: rub.input)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE8241C1A))
            // 카드 밖을 누르면 닫힌다
            .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onClose() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.systemBarsPadding().padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HoloCard(
                art = art,
                foil = card.foil,
                input = input,
                tilt = true,
                modifier = Modifier.fillMaxWidth().rubbable(rub),
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NavButton("‹") { index = (index - 1 + DEX_CARDS.size) % DEX_CARDS.size }
                Spacer(Modifier.size(18.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(card.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(card.statLine, color = Color(0xFFD9C9C3), fontSize = 12.sp)
                }
                Spacer(Modifier.size(18.dp))
                NavButton("›") { index = (index + 1) % DEX_CARDS.size }
            }
        }

        Text(
            "✕",
            color = Color.White,
            fontSize = 18.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .systemBarsPadding()
                .padding(16.dp)
                .clip(CircleShape)
                .background(Color(0x33FFFFFF))
                .clickable(onClick = onClose)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun NavButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = TextDark,
        fontSize = 20.sp,
        modifier = Modifier
            .clip(CircleShape)
            .background(PinkFaint)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp),
    )
}

@Suppress("unused")
private val unusedPalette = listOf(CreamBg, DaengPink)
