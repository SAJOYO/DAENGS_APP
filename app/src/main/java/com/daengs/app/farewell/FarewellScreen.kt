package com.daengs.app.farewell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.DaengsIcon
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.window.Dialog
import com.daengs.app.ui.common.SettingDivider
import com.daengs.app.ui.common.SettingRow
import com.daengs.app.ui.common.SettingSection
import com.daengs.app.ui.theme.DaengsColors
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.daengs.app.miniroom.art.drawPawStamp
import com.daengs.app.ui.common.DaengsTextAction
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
    /** 얼굴. 아이의 자리 맨 위에 선다 */
    face: (@Composable () -> Unit)? = null,
    /** 함께 있을 때 적어 둔 것. 견종·성별·몸무게 같은 줄들 */
    profile: List<Pair<String, String>> = emptyList(),
    /** 기록에서 지운다. **아이의 자리 안에만 둔다** — 목록에 지우기가 붙어 있으면 안 된다 */
    onDelete: (() -> Unit)? = null,
) {
    // 배웅한 아이는 **그 아이의 자리**부터 연다. 편지는 거기서 고른다 — 들어올 때마다
    // 편지가 먼저 펼쳐지면, 잠깐 얼굴만 보러 온 사람에게도 매번 그 글이 열린다.
    //
    // ⚠️ **[sentOn] 을 키로 잡으면 안 된다.** 배웅을 마치면 `sentOn` 이 null 에서
    // 날짜로 바뀌는데, 그 순간 키가 달라져 이 값이 초기값으로 되돌아간다 — 방금 보낸
    // 사람에게 편지 대신 견종·몸무게 표가 떴다.
    var letter by remember { mutableStateOf(false) }
    BackHandler {
        if (letter) letter = false else onClose()
    }

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
            // **편지에서 닫으면 그 아이의 자리로 돌아온다.** 마이로 튕겨 나가면
            // 얼굴을 한 번 더 보려고 목록을 다시 뒤져야 한다. 뒤로가기와 같은 길이다.
            Text(
                if (letter) "← 돌아가기" else "닫기",
                color = TextMuted,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { if (letter) letter = false else onClose() }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(10.dp))

        when {
            letter -> {
                Letter(dogName)
                // 배웅을 마친 흐름의 끝이 여기다. 편지를 읽고 나면 다음에 올 것을
                // 알려 준다 — 그 아이의 자리에도 같은 자리가 있다.
                Spacer(Modifier.height(20.dp))
                Memories()
            }

            sentOn != null -> Home(
                dogName = dogName,
                sentOn = sentOn,
                face = face,
                profile = profile,
                onLetter = { letter = true },
                onUndo = {
                    onUndo()
                    onClose()
                },
                onDelete = onDelete,
            )

            else -> Ask(dogName) { day ->
                onSendOff(day)
                letter = true
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

/**
 * 배웅한 아이의 자리.
 *
 * **고치는 화면이 아니다.** 여기 오는 사람은 몸무게를 바꾸러 오지 않는다. 함께 있을 때
 * 적어 둔 것은 **읽기만** 하고, 할 수 있는 일은 편지와 추억뿐이다.
 */
@Composable
private fun ColumnScope.Home(
    dogName: String,
    sentOn: LocalDate,
    face: (@Composable () -> Unit)?,
    profile: List<Pair<String, String>>,
    onLetter: () -> Unit,
    onUndo: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    face?.invoke()
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(dogName, color = TextDark, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        DaengsIconView(DaengsIcon.Rainbow, Modifier.size(22.dp))
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "%d년 %d월 %d일에 배웅했어요".format(sentOn.year, sentOn.monthValue, sentOn.dayOfMonth),
        color = TextMuted,
        fontSize = 13.sp,
    )

    if (profile.isNotEmpty()) {
        Spacer(Modifier.height(20.dp))
        Surface(color = CardWhite, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                profile.forEachIndexed { i, (label, value) ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text(label, color = TextMuted, fontSize = 13.sp, modifier = Modifier.width(78.dp))
                        Text(value, color = TextDark, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(18.dp))
    BigButton("편지 보기", filled = true, onClick = onLetter)
    Spacer(Modifier.height(10.dp))
    // 추억은 아직 없다. 눌리지 않게 두고 무엇이 올 자리인지만 말한다.
    BigButton("추억 보기 · 준비 중", filled = false, onClick = null)

    // **되돌릴 수 없는 일은 맨 아래 줄로 내린다.** 로그아웃·회원 탈퇴가 쓰는 그 모양이다 —
    // 화면 한가운데의 버튼으로 두면 편지를 보러 온 손이 먼저 닿는다.
    Spacer(Modifier.height(34.dp))
    SettingSection {
        SettingRow("배웅을 되돌릴게요", onClick = onUndo, tint = TextMuted)
        onDelete?.let {
            SettingDivider()
            SettingRow("기록에서 지울게요", onClick = { confirmDelete = true }, tint = DaengsColors.Error)
        }
    }

    if (confirmDelete && onDelete != null) {
        ConfirmDelete(
            dogName = dogName,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/**
 * 기록에서 지우기 전에 한 번 더 묻는다.
 *
 * **배웅과 지우기는 다르다.** 배웅은 있었던 일을 적어 두는 것이고 지우기는 없던 일로
 * 만드는 것이다. 여기서 안 물으면 아이의 자리를 보러 왔다가 그 자리를 잃는다.
 *
 * 되돌릴 수 없는 쪽(지우기)이 왼쪽이고 취소가 오른쪽이다 — 삭제 창·탈퇴 창과 같은 배치다.
 */
@Composable
private fun ConfirmDelete(dogName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = CardWhite, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(22.dp)) {
                Text(
                    "$dogName(을)를 기록에서 지울까요?",
                    color = TextDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "배웅한 날도, $dogName(와)과만 나간 산책도 함께 사라지고 되돌릴 수 없어요.",
                    color = TextMuted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(18.dp))
                // **둘이 같은 글씨여야 한다** — 마이의 삭제 창과 같은 이유다.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    DaengsTextAction("지우기", onConfirm, tint = DaengsColors.Error)
                    Spacer(Modifier.width(6.dp))
                    DaengsTextAction("취소", onDismiss)
                }
            }
        }
    }
}

@Composable
private fun BigButton(label: String, filled: Boolean, onClick: (() -> Unit)?) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (filled) DaengPink else CardWhite)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (filled) CardWhite else TextMuted,
            fontSize = 15.sp,
            fontWeight = if (filled) FontWeight.Bold else FontWeight.Normal,
        )
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
        Column(
            Modifier
                // **발자국은 글자 아래에 깐다.** 편지지가 아니라 지나간 자국이라
                // 위에 얹으면 글을 가린다. 아주 옅게, 몇 개만.
                .drawBehind { pawTrail() }
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
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
 * 편지지에 남은 발자국.
 *
 * **자리를 난수로 뽑지 않는다.** 다시 그릴 때마다 자국이 옮겨 다니면 종이가 아니라
 * 화면으로 보인다. 카드 크기 대비 비율로 박아 둔다.
 *
 * 글자를 가리지 않게 **가장자리 쪽으로** 몰고, 크기와 기울기를 조금씩 달리해 한 마리가
 * 걸어간 것처럼 둔다.
 */
private fun DrawScope.pawTrail() {
    val ink = DaengPink.copy(alpha = 0.10f)
    // (가로 %, 세로 %, 크기 %, 기울기°)
    val steps = listOf(
        Triple(0.86f, 0.09f, 0.055f) to -18f,
        Triple(0.10f, 0.34f, 0.042f) to 12f,
        Triple(0.90f, 0.62f, 0.048f) to 24f,
        Triple(0.14f, 0.88f, 0.038f) to -8f,
    )
    steps.forEach { (at, tilt) ->
        val center = Offset(size.width * at.first, size.height * at.second)
        rotate(tilt, center) {
            drawPawStamp(center, size.width * at.third, ink)
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
