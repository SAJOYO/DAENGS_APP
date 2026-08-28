package com.daengs.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.PinkSoft
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted

/** 홈에서는 대화 기록을 쌓지 않고 전체 대화 화면으로 들어가는 한 줄 진입점만 둔다. */
@Composable
fun ChatbotCard(
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier,
    avatar: DogBreed = HomeDemoData.DOG_BREED,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = CardWhite,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
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
                DogAvatar(avatar, Modifier.size(40.dp))
                Spacer(Modifier.width(10.dp))
                Text(HomeDemoData.CHAT_PLACEHOLDER, color = TextMuted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(50)).background(DaengPink),
                    contentAlignment = Alignment.Center,
                ) {
                    DaengsIconView(DaengsIcon.Send, Modifier.size(20.dp), tint = CardWhite)
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HomeDemoData.SUGGESTIONS.take(2).forEach { SuggestionChip(it, onOpenChat) }
            }
        }
    }
}

@Composable
private fun SuggestionChip(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = PinkFaint,
        border = BorderStroke(1.dp, PinkSoft),
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick),
    ) {
        Text(label, color = DaengPinkDeep, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
    }
}

@Preview(widthDp = 411, showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun ChatbotCardPreview() {
    DaengsTheme { ChatbotCard({}, Modifier.padding(14.dp)) }
}
