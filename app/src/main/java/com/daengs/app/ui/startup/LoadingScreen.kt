package com.daengs.app.ui.startup

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
            SplashIcon()
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

/**
 * 시스템 스플래시가 보여 주는 **그 아이콘, 그 크기.** 로딩 화면과 홈 첫 진입의 크림 막이
 * 같이 쓴다 — 로딩 마지막 프레임과 홈 첫 프레임이 같아야 이음새가 안 보인다.
 *
 * 시스템 스플래시는 아이콘의 **가운데 2/3 만** 기기 모양(원·스퀘어클)으로
 * 잘라서 보여 준다. 배경 색을 따로 안 준 아이콘은 **288 판에 192 가
 * 보이는** 규격이다 — 갤럭시 노트20 에서 재 보니 188dp 였다.
 *
 * 그래서 **창이 [SPLASH_ICON_DP], 그림이 그 1.5배**다. 그림을 창 가운데
 * 두면 보이는 것이 정확히 가운데 2/3 이고, 창을 자르면 스플래시가 하는
 * 일과 같아진다. `requiredSize` 인 이유는 부모가 창 크기로 죄기 때문이다.
 *
 * 여기서 안 자르고 그냥 그렸을 때가 문제였다 — **아이콘의 네모 가장자리가
 * 크림 배경 위에 드러나서**, 스플래시(둥근 모양)에서 넘어오는 순간 모양이
 * 바뀐 것처럼 보였다. 크기도 160 으로 잡아 두어 한 번 작아졌다.
 *
 * 원으로 자르는 것은 **이 앱의 얼굴이 다 원**이라서다 (`ui/DogAvatar.kt`).
 * 스플래시 모양은 기기마다 다른데(원·스퀘어클), 원이 제일 덜 튄다.
 */
@Composable
fun SplashIcon(modifier: Modifier = Modifier) {
    Box(
        modifier.size(SPLASH_ICON_DP.dp).clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize((SPLASH_ICON_DP * 3 / 2).dp),
        )
    }
}

/**
 * 시스템 스플래시가 보여 주는 아이콘의 지름 (dp).
 *
 * 배경 색을 안 준 아이콘은 288 판에 192 가 보이는 규격이다. 그림은 이 값의 1.5배로
 * 그려야 가운데 2/3 이 이만큼이 된다.
 */
private const val SPLASH_ICON_DP = 192

private const val SLOW_AFTER_MS = 6_000L

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun LoadingScreenPreview() {
    DaengsTheme { LoadingScreen() }
}
