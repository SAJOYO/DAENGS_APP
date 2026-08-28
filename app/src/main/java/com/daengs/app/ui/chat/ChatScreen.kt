package com.daengs.app.ui.chat

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.DogAvatar
import com.daengs.app.ui.home.HomeDemoData
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.PinkSoft
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted

/**
 * 대화 UI 전용 화면. 실제 RAG 호출은 아직 연결하지 않아, 전송된 질문만 기기 안에서
 * 보여 준다. 네트워크 계약이 붙을 때 이 화면의 [sentMessages] 자리에만 연결하면 된다.
 */
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    avatar: DogBreed = HomeDemoData.DOG_BREED,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val sentMessages = remember { mutableStateListOf<String>() }
    val scroll = rememberScrollState()

    Column(modifier.fillMaxSize().background(CreamBg).imePadding()) {
        ChatHeader(onBack = onBack, avatar = avatar)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (sentMessages.isEmpty()) {
                AssistantBubble(
                    "반려견의 산책, 건강, 생활을 무엇이든 물어보세요.\n답변 데이터 연결은 준비 중이에요.",
                    avatar,
                )
                Text("추천 질문", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                HomeDemoData.SUGGESTIONS.take(2).forEach { question ->
                    Surface(
                        color = CardWhite,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, PinkSoft),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { draft = question },
                    ) {
                        Text(question, color = TextDark, fontSize = 14.sp, modifier = Modifier.padding(14.dp))
                    }
                }
            } else {
                sentMessages.forEach { message ->
                    UserBubble(message)
                    AssistantBubble("AI 답변 연결을 준비 중이에요.", avatar)
                }
            }
        }
        ChatInput(
            value = draft,
            onValueChange = { draft = it },
            onSend = {
                val text = draft.trim()
                if (text.isNotEmpty()) {
                    sentMessages += text
                    draft = ""
                }
            },
        )
    }
}

@Composable
private fun ChatHeader(onBack: () -> Unit, avatar: DogBreed) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(50)).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Text("‹", color = TextDark, fontSize = 34.sp, lineHeight = 30.sp) }
        DogAvatar(avatar, Modifier.size(38.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("댕스 AI", color = TextDark, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("반려견 생활 도우미", color = TextMuted, fontSize = 12.sp)
        }
        Box(Modifier.size(9.dp).background(DaengPink, RoundedCornerShape(50)))
    }
}

@Composable
private fun AssistantBubble(text: String, avatar: DogBreed) {
    Row(verticalAlignment = Alignment.Top) {
        DogAvatar(avatar, Modifier.size(32.dp))
        Spacer(Modifier.width(8.dp))
        Surface(color = CardWhite, shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp)) {
            Text(text, color = TextDark, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(14.dp))
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(color = PinkSoft, shape = RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp)) {
            Text(text, color = TextDark, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(14.dp))
        }
    }
}

@Composable
private fun ChatInput(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit) {
    Surface(color = CardWhite, shadowElevation = 4.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.weight(1f).height(48.dp).background(PinkFaint, RoundedCornerShape(24.dp)).padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Text("메시지를 입력하세요", color = TextMuted, fontSize = 14.sp)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = androidx.compose.ui.text.TextStyle(color = TextDark, fontSize = 14.sp),
                    cursorBrush = SolidColor(DaengPink),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(50)).background(DaengPink)
                    .clickable(enabled = value.isNotBlank(), onClick = onSend),
                contentAlignment = Alignment.Center,
            ) { DaengsIconView(DaengsIcon.Send, Modifier.size(22.dp), tint = CardWhite) }
        }
    }
}

@Preview(widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun ChatScreenPreview() {
    DaengsTheme { ChatScreen({}) }
}
