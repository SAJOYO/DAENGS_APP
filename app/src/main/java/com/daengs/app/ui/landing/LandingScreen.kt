package com.daengs.app.ui.landing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.daengs.app.ui.DaengsLogo
import com.daengs.app.ui.DogAvatar
import com.daengs.app.ui.home.HomeDemoData
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted

/** 카카오가 정한 버튼 색. 가이드에서 벗어나면 안 되는 값이라 여기 박아 둔다. */
private val KakaoYellow = Color(0xFFFEE500)
private val KakaoLabel = Color(0xD9000000)

/**
 * 앱을 켜면 처음 뜨는 화면.
 *
 * **`둘러보기` 가 같이 있는 이유.** 서버가 꺼져 있거나 카카오 앱 키가 아직 없어도 앱을
 * 볼 수 있어야 개발과 데모가 돈다. 로그인을 필수로 만들면 그 순간 앱이 서버에 묶인다.
 * 나중에 로그인이 정말 필요한 기능(산책 기록 저장 같은 것)이 생기면 그때 그 기능
 * 앞에서 막는 편이 낫다.
 *
 * @param canLogin `local.properties` 에 앱 키와 서버 주소가 들어 있는가.
 *   없으면 버튼을 죽이고 왜 못 쓰는지 적어 준다 — 눌렀는데 아무 일도 안 일어나는 게
 *   제일 나쁘다.
 */
@Composable
fun LandingScreen(
    canLogin: Boolean,
    busy: Boolean,
    error: String?,
    onKakaoLogin: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(CreamBg)
            .systemBarsPadding()
            .padding(horizontal = 32.dp),
    ) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DogAvatar(HomeDemoData.DOG_BREED, Modifier.size(96.dp))
            Spacer(Modifier.height(22.dp))
            DaengsLogo(scale = 2.2f)
            Spacer(Modifier.height(14.dp))
            Text(
                "반려견과의 하루를 기록하고,\n작은 방을 함께 꾸며요",
                color = TextMuted,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
        }

        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (error != null) {
                Text(
                    error,
                    color = DaengPinkDeep,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (canLogin) KakaoYellow else KakaoYellow.copy(alpha = 0.35f))
                    .clickable(enabled = canLogin && !busy, onClick = onKakaoLogin),
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = KakaoLabel, strokeWidth = 2.dp)
                } else {
                    Text(
                        "카카오로 시작하기",
                        color = if (canLogin) KakaoLabel else KakaoLabel.copy(alpha = 0.4f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (!canLogin) {
                Text(
                    "카카오 앱 키와 서버 주소가 없습니다.\nlocal.properties 를 채우면 켜집니다 (README 참고)",
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "둘러보기",
                color = TextDark,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = !busy, onClick = onSkip)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LandingReadyPreview() {
    DaengsTheme {
        LandingScreen(canLogin = true, busy = false, error = null, onKakaoLogin = {}, onSkip = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun LandingUnconfiguredPreview() {
    DaengsTheme {
        LandingScreen(canLogin = false, busy = false, error = null, onKakaoLogin = {}, onSkip = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun LandingErrorPreview() {
    DaengsTheme {
        LandingScreen(
            canLogin = true,
            busy = false,
            error = "카카오 로그인 정보를 확인할 수 없습니다.",
            onKakaoLogin = {},
            onSkip = {},
        )
    }
}
