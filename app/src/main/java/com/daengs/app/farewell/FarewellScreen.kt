package com.daengs.app.farewell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.daengs.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.common.DateWheel
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import java.time.LocalDate

/**
 * 아이를 배웅하는 자리.
 *
 * **삭제와 다른 일이라 화면도 다르다.** 삭제는 "지울까요?" 하고 묻는 창 하나면 되지만,
 * 배웅은 묻고 끝날 일이 아니다. 날을 적고, 편지를 읽고, 닫는 데까지가 한 흐름이다.
 *
 * **되돌릴 수 있게 해 둔다.** 되돌릴 길이 없으면 아무도 못 누른다 — 눌러도 되는 일로
 * 만들어야 필요한 사람이 누른다.
 */
@Composable
fun FarewellScreen(
    dogName: String,
    /** 이미 배웅한 아이면 그 날. null 이면 이제 배웅하는 것이다 */
    sentOn: LocalDate?,
    onSendOff: (LocalDate) -> Unit,
    onUndo: () -> Unit,
    onClose: () -> Unit,
) {
    // 이미 배웅한 아이는 편지부터 보여 준다. 다시 들어와 읽을 수 있어야 한다.
    var letter by remember(sentOn) { mutableStateOf(sentOn != null) }
    BackHandler(onBack = onClose)

    Column(
        Modifier
            .fillMaxSize()
            .background(CreamBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(
                "닫기",
                color = TextMuted,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onClose)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(10.dp))

        if (letter) {
            Letter(dogName)
            Spacer(Modifier.height(20.dp))
            Memories()
            Spacer(Modifier.height(14.dp))
            // 잘못 눌렀을 수 있다. 조용히, 그러나 찾을 수 있게 둔다.
            Text(
                "배웅을 되돌릴게요",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        onUndo()
                        onClose()
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        } else {
            Ask(dogName) { day ->
                onSendOff(day)
                letter = true
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

/**
 * 배웅할지 묻는다.
 *
 * **"삭제" 라는 말을 안 쓴다.** 지우는 일이 아니라고 화면 전체가 말하고 있어야 한다 —
 * 아이도 산책도 카드도 그대로 남는다는 것을 여기서 분명히 해 둔다.
 */
@Composable
private fun Ask(dogName: String, onConfirm: (LocalDate) -> Unit) {
    // **간 날은 오늘이 아닐 수 있다.** 한참 지나 앱을 켜기도 하고, 정리할 마음이
    // 들기까지 시간이 걸리기도 한다. 오늘로 박아 두면 그 날이 거짓이 된다.
    var day by remember { mutableStateOf(LocalDate.now()) }
    Text(
        "$dogName(이)를 배웅할까요?",
        color = TextDark,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        "지우는 게 아니에요. $dogName(이)는 목록에 그대로 있고, 함께한 산책도 카드도 남아요.",
        color = TextMuted,
        fontSize = 13.sp,
        lineHeight = 21.sp,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(22.dp))
    Text("떠난 날", color = TextMuted, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    DateWheel(
        value = day,
        onChange = { day = it },
        // 앞날은 못 고른다. 아직 오지 않은 날을 배웅한 날로 적을 수는 없다.
        years = (LocalDate.now().year - 30)..LocalDate.now().year,
    )
    Spacer(Modifier.height(20.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DaengPink)
            .clickable { onConfirm(day) },
        contentAlignment = Alignment.Center,
    ) {
        Text("배웅하기", color = CardWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * 편지에 쓰는 글씨체 — 그린프롬솔.
 *
 * **이 화면에서만 쓴다.** 앱의 다른 글씨는 시스템 기본이고, 여기만 손으로 쓴 결이다.
 * 편지는 아이가 남긴 말이라 앱 UI 와 같은 글씨로 찍히면 안내문처럼 읽힌다.
 *
 * 라이선스는 확인했다 — 임베딩 허용 (2026-09-02).
 */
private val LetterFont = FontFamily(Font(R.font.griun_fromsol))

/** 아이가 보내는 편지. 본문은 [FAREWELL_BODY] 그대로다. */
@Composable
private fun Letter(dogName: String) {
    Surface(color = CardWhite, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
            Text(
                FAREWELL_TITLE,
                color = TextDark,
                fontSize = 18.sp,
                fontFamily = LetterFont,
                lineHeight = 27.sp,
            )
            Spacer(Modifier.height(18.dp))
            FAREWELL_BODY.forEachIndexed { index, paragraph ->
                if (index > 0) Spacer(Modifier.height(16.dp))
                Text(
                    paragraph,
                    color = TextDark,
                    fontSize = 16.sp,
                    fontFamily = LetterFont,
                    // 손글씨 결은 줄 사이가 넉넉해야 읽힌다. 기본 간격이면 뭉친다.
                    lineHeight = 28.sp,
                )
            }
            Spacer(Modifier.height(22.dp))
            // 이름은 여기 한 줄에만 넣는다. 본문에 끼우면 아이가 제 이름을 부르는 꼴이 된다.
            Text(
                "from $dogName",
                color = DaengPink,
                fontSize = 16.sp,
                fontFamily = LetterFont,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

/**
 * 추억 남기기 자리.
 *
 * **빈 화면을 두지 않는다** — 무엇이 올 자리인지 말해 준다 (`StorageComingSoon` 과 같은 결).
 * 여기서 "곧" 이나 날짜를 말하지 않는다. 지키지 못할 약속이 다음 사람의 부채가 된다.
 */
@Composable
private fun Memories() {
    Surface(color = PinkFaint, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("추억 남기기", color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "함께한 사진과 이야기를 여기 모아 둘 수 있게 만들고 있어요.",
                color = TextMuted,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text("준비 중", color = DaengPink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Preview(name = "배웅 · 묻기", widthDp = 411, heightDp = 640)
@Composable
private fun FarewellAskPreview() {
    DaengsTheme {
        Column(Modifier.fillMaxSize().background(CreamBg).padding(22.dp)) {
            Ask("네옹") {}
        }
    }
}

@Preview(name = "배웅 · 편지", widthDp = 411, heightDp = 1100)
@Composable
private fun FarewellLetterPreview() {
    DaengsTheme {
        Column(
            Modifier.fillMaxSize().background(CreamBg).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Letter("네옹")
            Memories()
        }
    }
}

@Preview(name = "추억 남기기", widthDp = 411, heightDp = 220)
@Composable
private fun MemoriesPreview() {
    DaengsTheme {
        Box(Modifier.fillMaxSize().background(CreamBg).padding(22.dp)) { Memories() }
    }
}
