package com.daengs.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.DogAvatar
import com.daengs.app.ui.DogFace
import com.daengs.app.ui.PawAvatar
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted

/** 홈에서는 대화 기록을 쌓지 않고 전체 대화 화면으로 들어가는 한 줄 진입점만 둔다. */
@Composable
fun ChatbotCard(
    onOpenChat: () -> Unit,
    /**
     * 마이크를 눌렀을 때. **여기서 듣지 않는다** — 채팅을 열면서 거기서 듣기가
     * 시작된다 (#451). 인식 로직이 `ChatScreen` 한 곳에만 있어야 설정·권한·정지
     * 규칙이 갈라지지 않는다.
     *
     * null 이면 마이크를 아예 안 그린다 — 미리보기와 옛 호출부가 그대로 돈다.
     */
    onVoice: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /**
     * 챗봇 얼굴의 견종. **null 이면 발자국이다** — 대표가 믹스라 그림이 없는 경우다.
     * 예전에는 데모 강아지 한 마리로 떨어져서, 믹스를 키우는 사람은 홈에서 남의 개
     * 얼굴을 봤다.
     */
    avatar: DogBreed? = null,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = CardWhite,
        modifier = modifier.fillMaxWidth(),
    ) {
        // ⚠️ **칸 높이는 [HomeScreen.CardSlotHeight] 로 고정이다** — 88dp 다.
        // 안에 든 것: 패딩 7×2 + 제목줄 25 + Spacer 5 + 입력줄 44.
        //
        // **인벤토리 패널은 더 이상 이 칸을 같이 쓰지 않는다** (제 높이 114dp). 예전에는
        // 같이 써서 여기서 줄일 수 없었고, 그 때문에 인벤토리 이름표가 잘려 있었다.
        // 예시 질문 칩을 뺀 뒤 아래가 휑해져서 남은 높이를 위아래로 고르게 편다.
        Column(
            Modifier.fillMaxHeight().padding(horizontal = 16.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(HomeDemoData.CHAT_TITLE, color = TextDark, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.width(6.dp))
                DaengsIconView(DaengsIcon.Paw, Modifier.size(15.dp), tint = DaengPink)
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpenChat).padding(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("대화 열기", color = TextMuted, fontSize = 12.sp)
                    DaengsIconView(DaengsIcon.ChevronRight, Modifier.size(15.dp), tint = TextMuted)
                }
            }
            Spacer(Modifier.height(5.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(onClick = onOpenChat)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // **똑똑이 판이다.** 이 자리는 내 개가 아니라 챗봇이다
                // (`ChatScreen.ChatFace` 와 같은 이유).
                if (avatar != null) DogAvatar(avatar, Modifier.size(38.dp), face = DogFace.Smart)
                else PawAvatar(size = 38.dp)
                Spacer(Modifier.width(10.dp))
                Text(HomeDemoData.CHAT_PLACEHOLDER, color = TextMuted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                // **마이크는 전송 왼쪽이다.** 문구가 `weight(1f)` 라 자리는 알아서 나온다.
                // 테두리 없이 두는 것은 전송(분홍 채움)과 위계를 나누기 위해서다 —
                // 둘 다 채우면 무엇이 주 행동인지 안 보인다.
                if (onVoice != null) {
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(50)).clickable(onClick = onVoice),
                        contentAlignment = Alignment.Center,
                    ) {
                        DaengsIconView(DaengsIcon.Mic, Modifier.size(20.dp), tint = TextMuted)
                    }
                    Spacer(Modifier.width(2.dp))
                }
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(50)).background(DaengPink),
                    contentAlignment = Alignment.Center,
                ) {
                    DaengsIconView(DaengsIcon.Send, Modifier.size(20.dp), tint = CardWhite)
                }
            }
        }
    }
}

@Preview(widthDp = 411, showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun ChatbotCardPreview() {
    // **이름 인자로 부른다.** 위치 인자로 두면 [ChatbotCard] 에 인자가 하나 끼어들 때마다
    // 조용히 자리가 밀린다 — `onVoice` 를 넣다가 실제로 깨졌다 (#451).
    DaengsTheme {
        ChatbotCard(
            onOpenChat = {},
            onVoice = {},
            // **칸 높이를 준다.** 안 주면 카드가 자기 크기대로 그려져서 실제 홈과 다르게
            // 보인다 — 여백을 조정할 때 이 미리보기를 보고 판단하게 되므로 어긋나면 안 된다.
            modifier = Modifier.padding(14.dp).height(CardSlotHeight),
            avatar = HomeDemoData.DOG_BREED,
        )
    }
}
