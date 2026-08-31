package com.daengs.app.ui.my

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
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
import com.daengs.app.BuildConfig
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.DogAvatar
import com.daengs.app.ui.home.HomeDemoData
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted

/**
 * 마이 탭.
 *
 * **[Screen.My][com.daengs.app.MainActivity] 로 밀지 않고 홈 안에서 탭만 바꾼다.**
 * 하단 바는 홈의 `Scaffold` 가 그리므로, 밀어서 열면 마이 탭에서 하단 바가
 * 사라진다 — 자기 탭에서 탭 바가 없어지는 건 고장으로 읽힌다. 도감·산책기록이
 * 밀어서 여는 화면인 것은 그 둘이 전체화면 목적지라서다.
 *
 * 여기 있는 것은 **계정에 관한 것**뿐이다. 강아지 정보(이름·나이 같은)는 아직
 * 받는 항목이 정해지지 않아서 보여 주기만 하고 고치는 칸은 안 만든다 — 무엇을
 * 받을지 정해지기 전에 입력칸부터 만들면 그 모양에 끌려간다.
 *
 * @param signedIn 카카오로 로그인한 상태인가. **출시 빌드에서는 늘 true 다** —
 *   로그인이 필수라 랜딩을 건너뛸 길이 없다. 디버그의 "둘러보기"로 들어왔을 때만
 *   false 이고, 그때는 계정 항목 대신 로그인 버튼이 뜬다.
 * @param onSignIn 둘러보기 상태에서 로그인하러 갈 때. 랜딩으로 되돌리면 기존
 *   카카오 경로를 그대로 쓴다.
 */
@Composable
fun MyScreen(
    breed: DogBreed,
    signedIn: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(CreamBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        ProfileHead(breed)
        Spacer(Modifier.height(20.dp))

        if (signedIn) {
            Section {
                MyRow("로그아웃", onClick = onSignOut)
            }
        } else {
            // 눌러도 아무 일 없는 버튼을 두지 않는다 — 로그인 안 한 사람에게
            // "로그아웃"은 비활성이 아니라 **없는 것**이 맞다
            // (LandingScreen 의 canLogin 안내와 같은 규칙).
            Section {
                MyRow("카카오로 로그인", onClick = onSignIn, tint = DaengPink)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "둘러보는 중이에요. 로그인하면 기록이 저장돼요.",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(24.dp))
        Text(
            "v${BuildConfig.VERSION_NAME}",
            color = TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun ProfileHead(breed: DogBreed) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DogAvatar(breed, Modifier.size(88.dp))
        Spacer(Modifier.height(10.dp))
        Text(HomeDemoData.DOG_NAME, color = TextDark, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(HomeDemoData.ROOM_LABEL, color = TextMuted, fontSize = 13.sp)
    }
}

/** 카드 한 장. 안의 줄들이 같은 흰 바탕을 나눠 쓴다. */
@Composable
private fun Section(content: @Composable () -> Unit) {
    Surface(color = CardWhite, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(content = { content() })
    }
}

/**
 * 누르는 줄 하나.
 *
 * Material3 `Button` 을 안 쓴다 — 이 저장소는 `Surface`·`Box` 에 `.clickable` 을
 * 붙여 직접 짠다 (`LandingScreen` 과 같은 결).
 */
@Composable
private fun MyRow(
    label: String,
    onClick: () -> Unit,
    tint: Color = TextDark,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = tint, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        DaengsIconView(DaengsIcon.ChevronRight, Modifier.size(16.dp), tint = TextMuted)
    }
}

@Preview(widthDp = 411, heightDp = 700, showBackground = true)
@Composable
private fun MyScreenSignedInPreview() {
    DaengsTheme { MyScreen(HomeDemoData.DOG_BREED, signedIn = true, onSignIn = {}, onSignOut = {}) }
}

@Preview(widthDp = 411, heightDp = 700, showBackground = true)
@Composable
private fun MyScreenBrowsingPreview() {
    DaengsTheme { MyScreen(HomeDemoData.DOG_BREED, signedIn = false, onSignIn = {}, onSignOut = {}) }
}
