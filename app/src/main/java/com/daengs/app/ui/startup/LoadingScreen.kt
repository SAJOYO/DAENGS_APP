package com.daengs.app.ui.startup

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.daengs.app.R
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextMuted
import kotlinx.coroutines.delay

/**
 * 강아지 목록을 기다리는 동안의 화면.
 *
 * **시스템 스플래시와 같은 그림이다.** 크림 배경에 런처 아이콘 — `themes.xml` 의
 * `Theme.Daengs.Splash` 가 쓰는 그 둘이다. 같아야 스플래시에서 여기로 넘어오는 것이
 * **한 화면처럼** 보인다. 다른 그림을 쓰면 켤 때마다 화면이 두 번 바뀐다.
 *
 * 예전에는 저장된 토큰이 있으면 곧장 홈이었다. 목록이 아직 없으면 방이 데모로
 * 채워져서, **남의 강아지 넉 마리가 서 있다가 사라지고 날씨가 바뀌었다.**
 */
@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    // 오래 걸리면 한 줄 붙인다. **무엇을 기다리는지 모르는 정지 화면이 제일 나쁘다** —
    // 그렇다고 처음부터 띄우면 잘 되는 날에도 느려 보인다.
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SLOW_AFTER_MS)
        slow = true
    }

    Box(
        modifier.fillMaxSize().background(CreamBg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // **스플래시와 같은 크기로 보여야 한다.** 시스템 스플래시는 아이콘의
            // 가운데 2/3 만 보여 주므로(적응형 아이콘 규격), 240 을 그려야 그림이
            // 스플래시와 같은 160 으로 보인다. 160 을 그리면 스플래시에서 넘어오는
            // 순간 아이콘이 한 번 작아진다.
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(240.dp),
            )
            Spacer(Modifier.height(8.dp))
            if (slow) {
                Text(
                    "조금만 기다려 주세요…",
                    color = TextMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** 이만큼 지나면 기다리는 중이라고 알린다. */
private const val SLOW_AFTER_MS = 6_000L

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun LoadingScreenPreview() {
    DaengsTheme { LoadingScreen() }
}
