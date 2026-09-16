package com.daengs.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import com.daengs.app.miniroom.IntroTimeline
import com.daengs.app.miniroom.RoomIntro
import com.daengs.app.miniroom.sprite.rememberFrameClock
import com.daengs.app.ui.startup.SplashIcon
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengsTheme
import kotlinx.coroutines.delay

/**
 * 로딩 화면과 홈을 잇는 **크림 막.**
 *
 * 로딩 화면(크림 배경 + 아이콘)이 홈으로 컷 전환되던 자리다. 홈의 첫 프레임을 로딩의
 * 마지막 프레임과 같은 그림으로 시작해 [IntroTimeline.VEIL_END_MS] 동안 걷어 낸다.
 * 그 밑에서 방은 이미 문밖 풍경을 확대한 첫 컷을 그리고 있다 ([RoomIntro]).
 *
 * 알파는 `graphicsLayer` 안에서 프레임 시계를 읽어 정한다 — 재구성 없이 draw 단계만
 * 돈다. 막이 다 걷히면 컴포지션에서 빠지므로 그 뒤로는 시계도 안 읽는다.
 *
 * 터치를 먹지 않는다. 막 위를 눌러도 밑의 방이 받아서 연출을 건너뛴다.
 */
@Composable
fun HomeIntroVeil(intro: RoomIntro?, modifier: Modifier = Modifier) {
    if (intro == null) return
    // 처음 합성될 때 연출 중이었는지만 본다. 값은 state 가 아니라 이후 변화는 못 보지만,
    // 막은 어차피 0.2초 뒤에 빠진다.
    var showing by remember { mutableStateOf(intro.active) }
    if (!showing) return
    LaunchedEffect(Unit) {
        delay(IntroTimeline.VEIL_END_MS)
        showing = false
    }
    val clock = rememberFrameClock()
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = intro.veilAt(clock.value) }
            .background(CreamBg),
        contentAlignment = Alignment.Center,
    ) {
        SplashIcon()
    }
}

@Preview(name = "막 반쯤 걷힘", widthDp = 411, heightDp = 891)
@Composable
private fun HomeIntroVeilPreview() {
    DaengsTheme {
        HomeIntroVeil(RoomIntro(previewElapsedMs = IntroTimeline.VEIL_END_MS / 2))
    }
}
