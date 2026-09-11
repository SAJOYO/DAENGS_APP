package com.daengs.app.ui.pet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.pet.AcceptOutcome
import com.daengs.app.pet.AcceptedInvite
import com.daengs.app.pet.InvitePaste
import com.daengs.app.ui.common.DaengsTextAction
import com.daengs.app.ui.common.DaengsWideButton
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted

/**
 * 초대받기 — 카톡에서 복사한 링크를 붙여넣어 공동 보호자가 되는 자리.
 *
 * **앱이 클립보드를 몰래 읽지 않는다.** 사용자가 직접 붙여넣은 글만 다룬다.
 *
 * **수락 전에 아이 정보를 보여 주지 않는다.** 서버에 미리보기 API 가 없어서, 이름이나
 * 대표를 지어내면 그건 화면이 꾸며낸 값이 된다. 대신 수락하면 무슨 일이 생기는지를 말한다.
 */
@Composable
fun InviteAcceptScreen(
    pasted: String,
    parsed: InvitePaste.Result,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    outcome: AcceptOutcome? = null,
    canAccept: Boolean = false,
    onPaste: (String) -> Unit = {},
    onAccept: () -> Unit = {},
    onDone: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    BackHandler { onBack() }

    val joined = outcome as? AcceptOutcome.Joined

    Column(
        modifier
            .fillMaxSize()
            .background(CreamBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp)
            .testTag("invite-accept"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "공동 돌봄 초대받기",
                color = TextDark,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            DaengsTextAction("닫기", onBack, tint = TextMuted)
        }

        if (joined != null) {
            Joined(pet = joined.pet, onDone = onDone)
            return@Column
        }

        Text(
            "받은 초대 링크를 붙여넣어 주세요. 카카오톡에서 복사한 메시지를 통째로 붙여넣어도 괜찮아요.",
            color = TextMuted,
            fontSize = 13.sp,
        )

        PasteField(value = pasted, onChange = onPaste, enabled = !busy)

        // 입력 상태를 그대로 말해 준다 — 왜 버튼이 안 눌리는지 화면이 설명해야 한다.
        when (parsed) {
            is InvitePaste.Result.Empty -> Unit
            is InvitePaste.Result.Found -> Notice("초대 링크를 찾았어요.", tag = "accept-link-ok", tint = DaengPink)
            is InvitePaste.Result.NoLink -> Notice(
                "초대 링크를 찾지 못했어요. 받은 메시지를 다시 복사해 붙여넣어 주세요.",
                tag = "accept-no-link",
            )
            is InvitePaste.Result.Ambiguous -> Notice(
                "초대 링크가 여러 개예요. 참여할 초대 하나만 붙여넣어 주세요.",
                tag = "accept-ambiguous",
            )
        }

        Guidance()

        DaengsWideButton(
            label = "초대 수락하기",
            onClick = onAccept,
            enabled = canAccept,
            busy = busy,
            accent = true,
            modifier = Modifier.testTag("accept-submit"),
        )

        outcome?.let { Failure(it) }
    }
}

@Composable
private fun PasteField(value: String, onChange: (String) -> Unit, enabled: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .background(CardWhite, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        if (value.isEmpty()) {
            Text("여기에 붙여넣기", color = TextMuted, fontSize = 14.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            textStyle = TextStyle(color = TextDark, fontSize = 14.sp),
            cursorBrush = SolidColor(DaengPink),
            modifier = Modifier.fillMaxWidth().testTag("accept-input"),
        )
    }
}

/**
 * 수락하면 무슨 일이 생기는지. **중복 강아지 안내가 여기 있다** — 각자 등록해 둔 아이는
 * 합쳐지지 않아서, 모르고 수락하면 같은 아이가 두 마리로 보인다.
 */
@Composable
private fun Guidance() {
    Surface(color = PinkFaint, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(14.dp).testTag("accept-guidance"),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("수락하면", color = TextDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("• 그 강아지의 공동 보호자가 되어 함께 기록하고 볼 수 있어요.", color = TextDark, fontSize = 13.sp)
            Text(
                "• 이미 직접 등록한 강아지가 있어도 자동으로 합쳐지지 않아요. 같은 아이라면 목록에 두 마리로 보일 수 있어요.",
                color = TextDark,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun Joined(pet: AcceptedInvite, onDone: () -> Unit) {
    Surface(color = CardWhite, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(18.dp).testTag("accept-joined"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "${pet.name.ifBlank { "그 아이" }}의 공동 보호자가 되었어요",
                color = TextDark,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text("이제 홈과 강아지 목록에서 함께 볼 수 있어요.", color = TextMuted, fontSize = 13.sp)
            DaengsWideButton(label = "확인", onClick = onDone, accent = true, modifier = Modifier.testTag("accept-done"))
        }
    }
}

@Composable
private fun Failure(outcome: AcceptOutcome) {
    val message = when (outcome) {
        is AcceptOutcome.Joined -> return
        // 404 와 410 을 한 문장으로 묶지 않는다 — 뒤쪽은 새 초대를 받으면 되고 앞쪽은 아니다.
        is AcceptOutcome.NotFound -> "사용할 수 없는 초대예요. 링크가 잘못됐거나 다른 분이 이미 사용했어요."
        is AcceptOutcome.Expired -> "만료된 초대예요. 대표 보호자에게 새 초대를 요청해 주세요."
        is AcceptOutcome.Conflict -> outcome.message // 서버가 사용자에게 보여 줄 문장으로 써 놨다.
        is AcceptOutcome.Failed -> outcome.message
    }
    Notice(message, tag = "accept-error", tint = DaengsColors.Error)
}

@Composable
private fun Notice(text: String, tag: String, tint: androidx.compose.ui.graphics.Color = TextMuted) {
    Text(text, color = tint, fontSize = 13.sp, modifier = Modifier.testTag(tag))
}

// -- @Preview -----------------------------------------------------------------

private const val PREVIEW_LINK = "https://daengapi.weareithero.cloud/invite#preview-token"

@Preview(name = "붙여넣기 전")
@Composable
private fun AcceptEmptyPreview() {
    DaengsTheme { InviteAcceptScreen(pasted = "", parsed = InvitePaste.Result.Empty) }
}

@Preview(name = "링크 찾음")
@Composable
private fun AcceptFoundPreview() {
    DaengsTheme {
        InviteAcceptScreen(
            pasted = PREVIEW_LINK,
            parsed = InvitePaste.Result.Found("preview-token"),
            canAccept = true,
        )
    }
}

@Preview(name = "링크 없음")
@Composable
private fun AcceptNoLinkPreview() {
    DaengsTheme { InviteAcceptScreen(pasted = "안녕하세요", parsed = InvitePaste.Result.NoLink) }
}

@Preview(name = "여러 초대")
@Composable
private fun AcceptAmbiguousPreview() {
    DaengsTheme { InviteAcceptScreen(pasted = "링크 둘", parsed = InvitePaste.Result.Ambiguous) }
}

@Preview(name = "수락 중")
@Composable
private fun AcceptBusyPreview() {
    DaengsTheme {
        InviteAcceptScreen(
            pasted = PREVIEW_LINK,
            parsed = InvitePaste.Result.Found("preview-token"),
            busy = true,
        )
    }
}

@Preview(name = "성공")
@Composable
private fun AcceptJoinedPreview() {
    DaengsTheme {
        InviteAcceptScreen(
            pasted = "",
            parsed = InvitePaste.Result.Empty,
            outcome = AcceptOutcome.Joined(AcceptedInvite("p1", "네옹")),
        )
    }
}

@Preview(name = "만료")
@Composable
private fun AcceptExpiredPreview() {
    DaengsTheme {
        InviteAcceptScreen(
            pasted = PREVIEW_LINK,
            parsed = InvitePaste.Result.Found("preview-token"),
            outcome = AcceptOutcome.Expired,
            canAccept = true,
        )
    }
}

@Preview(name = "409 — 상한")
@Composable
private fun AcceptConflictPreview() {
    DaengsTheme {
        InviteAcceptScreen(
            pasted = PREVIEW_LINK,
            parsed = InvitePaste.Result.Found("preview-token"),
            outcome = AcceptOutcome.Conflict("돌보는 아이가 너무 많습니다."),
            canAccept = true,
        )
    }
}
