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
        // ⚠️ **칸 높이는 [HomeScreen.CardSlotHeight] 로 고정이다** — 인벤토리 패널과
        // 같이 쓰는 자리라 여기서 줄일 수 없다. 예시 질문 칩을 뺀 뒤 아래가 휑해져서
        // 남은 높이를 위아래로 고르게 편다. 칸을 줄여 방을 키우는 것은 별도 과제다
        // (STATUS.md 의 열린 질문 ⑤).
        Column(
            Modifier.fillMaxHeight().padding(horizontal = 16.dp, vertical = 11.dp),
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
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .clickable(onClick = onOpenChat)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // **똑똑이 판이다.** 이 자리는 내 개가 아니라 챗봇이다
                // (`ChatScreen.ChatFace` 와 같은 이유).
                if (avatar != null) DogAvatar(avatar, Modifier.size(40.dp), face = DogFace.Smart)
                else PawAvatar(size = 40.dp)
                Spacer(Modifier.width(10.dp))
                Text(HomeDemoData.CHAT_PLACEHOLDER, color = TextMuted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(50)).background(DaengPink),
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
    DaengsTheme { ChatbotCard({}, Modifier.padding(14.dp), avatar = HomeDemoData.DOG_BREED) }
}
