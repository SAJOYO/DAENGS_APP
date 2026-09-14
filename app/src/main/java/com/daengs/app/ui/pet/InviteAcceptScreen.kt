package com.daengs.app.ui.pet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.RadioButton
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
import com.daengs.app.pet.AcceptResult
import com.daengs.app.pet.InvitePaste
import com.daengs.app.pet.PreviewOutcome
import com.daengs.app.pet.PetChoice
import com.daengs.app.pet.InvitePreviewPet
import com.daengs.app.pet.InvitePreview
import com.daengs.app.pet.InvitePetBrief
import com.daengs.app.ui.common.DaengsTextAction
import com.daengs.app.ui.common.DaengsWideButton
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengPinkDeep
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
 * 흐름은 넷이다: 붙여넣기 → 미리보기 → 아이마다 고르기 → 한 번에 수락.
 *
 * **아이 정보를 앱이 지어내지 않는다.** 이름과 연결 후보는 전부 미리보기 응답에서 온다.
 * 미리보기를 못 받으면([PreviewOutcome.Unsupported] — 저쪽에 그 경로가 없는 옛 서버)
 * 고르는 자리를 아예 안 낸다. 그때 선택을 보내면 서버가 **조용히 무시하고 200** 을 내서,
 * 사용자가 고른 연결이 사라진 채 전부 새로 참여해 버린다.
 */
@Composable
fun InviteAcceptScreen(
    pasted: String,
    parsed: InvitePaste.Result,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    outcome: AcceptOutcome? = null,
    canAccept: Boolean = false,
    /**
     * 미리보기 결과. **null 이면 아직 안 물어본 것**이고, 그때 화면은 붙여넣기까지만
     * 그린다. 부르는 쪽이 링크를 찾자마자 자동으로 물어본다.
     */
    preview: PreviewOutcome? = null,
    /** 아이마다 고른 것. 키가 없으면 아직 안 고른 것이다. */
    choices: Map<String, PetChoice> = emptyMap(),
    /** 다른 항목이 이미 가져간 기존 아이. 그 후보를 잠근다. */
    takenBy: (String) -> Set<String> = { emptySet() },
    onPaste: (String) -> Unit = {},
    onChoose: (String, PetChoice) -> Unit = { _, _ -> },
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

        val invite = (preview as? PreviewOutcome.Ready)?.preview

        when (preview) {
            null, is PreviewOutcome.Ready, PreviewOutcome.Unsupported -> Unit
            // 미리보기가 실패한 이유는 수락 실패와 같은 말로 그린다 — 사용자가 볼 때
            // "링크가 죽었다" 는 어느 단계에서 알았든 같은 사실이다.
            PreviewOutcome.NotFound -> Notice(
                "사용할 수 없는 초대예요. 링크가 잘못됐거나 다른 분이 이미 사용했어요.",
                tag = "accept-error",
                tint = DaengsColors.Error,
            )
            PreviewOutcome.Expired -> Notice(
                "만료된 초대예요. 대표 보호자에게 새 초대를 요청해 주세요.",
                tag = "accept-error",
                tint = DaengsColors.Error,
            )
            is PreviewOutcome.Failed -> Notice(preview.message, tag = "accept-error", tint = DaengsColors.Error)
        }

        invite?.let { InvitedBy(it) }

        invite?.pets?.forEach { pet ->
            InvitedPetCard(
                pet = pet,
                candidates = invite.linkCandidates,
                chosen = choices[pet.petId],
                taken = takenBy(pet.petId),
                enabled = !busy,
                onChoose = { onChoose(pet.petId, it) },
            )
        }

        Guidance(linking = choices.values.any { it is PetChoice.Link })

        DaengsWideButton(
            label = acceptLabel(invite?.pets?.size ?: 0),
            onClick = onAccept,
            enabled = canAccept,
            busy = busy,
            accent = true,
            modifier = Modifier.testTag("accept-submit"),
        )

        outcome?.let { Failure(it) }
    }
}

/** 묶음이면 몇 마리인지 버튼이 말한다 — 한 번 누르면 전부 들어온다는 것을 알아야 한다. */
private fun acceptLabel(count: Int): String =
    if (count > 1) "${count}마리 모두 공동 돌봄 시작하기" else "초대 수락하기"

@Composable
private fun InvitedBy(preview: InvitePreview) {
    val who = preview.invitedByNickname?.takeIf { it.isNotBlank() }
    Text(
        if (who != null) "${who}님이 보낸 초대예요." else "받은 초대예요.",
        color = TextDark,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.testTag("accept-invited-by"),
    )
}

/**
 * 초대된 아이 하나와 그 아이를 어떻게 받을지.
 *
 * **고르지 않으면 수락이 안 된다.** 묶음에서 선택이 빠지면 서버가 409 로 막는데, 그
 * 오류는 사용자가 고칠 수 있는 말이 아니다 — 화면에서 먼저 고르게 한다.
 */
@Composable
private fun InvitedPetCard(
    pet: InvitePreviewPet,
    candidates: List<InvitePetBrief>,
    chosen: PetChoice?,
    taken: Set<String>,
    enabled: Boolean,
    onChoose: (PetChoice) -> Unit,
) {
    Surface(
        color = CardWhite,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("accept-pet-${pet.petId}"),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(pet.name.ifBlank { "이름 없는 아이" }, color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)

            if (pet.alreadyMember) {
                // 오류가 아니다 — 서버가 그냥 지나간다. 고를 것이 없으니 자리를 안 낸다.
                Text(
                    "이미 이 아이의 보호자예요. 수락해도 그대로예요.",
                    color = TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.testTag("accept-already-${pet.petId}"),
                )
                return@Column
            }

            Text("이미 직접 등록한 같은 강아지가 있나요?", color = TextMuted, fontSize = 13.sp)

            ChoiceRow(
                label = "아니요. 새 공동 보호자로 참여할게요.",
                selected = chosen is PetChoice.Join,
                enabled = enabled,
                tag = "accept-join-${pet.petId}",
                onClick = { onChoose(PetChoice.Join) },
            )

            if (candidates.isEmpty()) {
                Text(
                    "연결할 수 있는 내 강아지가 없어요.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.testTag("accept-no-candidates-${pet.petId}"),
                )
            } else {
                Text("네. 제가 등록한 강아지와 연결할게요.", color = TextMuted, fontSize = 13.sp)
                candidates.forEach { candidate ->
                    val mine = (chosen as? PetChoice.Link)?.existingPetId == candidate.petId
                    ChoiceRow(
                        label = candidate.name,
                        selected = mine,
                        // **다른 줄이 가져간 아이는 못 고른다.** 서버가 422 로 막는데
                        // 그때는 어느 줄을 고쳐야 하는지 알 수 없다.
                        enabled = enabled && (mine || candidate.petId !in taken),
                        tag = "accept-link-${pet.petId}-${candidate.petId}",
                        onClick = { onChoose(PetChoice.Link(candidate.petId)) },
                    )
                }
            }

            if (chosen == null) {
                Text(
                    "하나를 골라 주세요.",
                    color = DaengPinkDeep,
                    fontSize = 12.sp,
                    modifier = Modifier.testTag("accept-need-choice-${pet.petId}"),
                )
            }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 2.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(selectedColor = DaengPinkDeep),
        )
        Text(
            label,
            color = if (enabled || selected) TextDark else TextMuted,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
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
private fun Guidance(linking: Boolean = false) {
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
            if (linking) {
                // **연결은 과거까지 연다.** 지금부터가 아니라 이미 쌓인 기록도 함께
                // 보이므로, 고르기 전에 알려 줘야 되돌릴 수 없는 선택이 되지 않는다.
                Text(
                    "• 연결하면 그 아이의 지난 케어·산책 기록도 함께 보게 돼요. 이름과 프로필 사진은 각자 쓰던 것을 그대로 써요.",
                    color = TextDark,
                    fontSize = 13.sp,
                    modifier = Modifier.testTag("accept-link-warning"),
                )
            }
        }
    }
}

/**
 * 수락 결과. **항목마다 한 줄이다** — 앵커 하나만 그리면 여러 마리를 받았을 때 나머지가
 * 사라진다. 줄이 없는 옛 응답에서는 [AcceptedInvite.rows] 가 앵커로 한 줄을 세운다.
 *
 * **일부만 성공한 것처럼 그리지 않는다.** 서버가 하나라도 실패하면 아무것도 남기지
 * 않으므로, 여기 오면 전부 된 것이다.
 */
@Composable
private fun Joined(pet: AcceptedInvite, onDone: () -> Unit) {
    Surface(color = CardWhite, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(18.dp).testTag("accept-joined"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val rows = pet.rows
            Text(
                if (rows.size > 1) "${rows.size}마리의 공동 보호자가 되었어요" else "${rows[0].name.ifBlank { "그 아이" }}의 공동 보호자가 되었어요",
                color = TextDark,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            rows.forEach { row ->
                Text(
                    "· ${row.name.ifBlank { "이름 없는 아이" }} — ${resultLabel(row.result)}",
                    color = TextMuted,
                    fontSize = 13.sp,
                    // **[AcceptedPet.displayPetId] 로 태그를 단다.** 이후 케어·산책
                    // 요청에 쓸 id 가 이것이라, 화면도 같은 것을 가리켜야 헷갈리지 않는다.
                    modifier = Modifier.testTag("accept-row-${row.displayPetId}"),
                )
            }
            Text("이제 홈과 강아지 목록에서 함께 볼 수 있어요.", color = TextMuted, fontSize = 13.sp)
            DaengsWideButton(label = "확인", onClick = onDone, accent = true, modifier = Modifier.testTag("accept-done"))
        }
    }
}

private fun resultLabel(result: AcceptResult): String = when (result) {
    AcceptResult.LINKED -> "내 강아지와 연결했어요"
    AcceptResult.JOINED -> "새로 참여했어요"
    AcceptResult.ALREADY_MEMBER -> "이미 보호자였어요"
    AcceptResult.ALREADY_OWNER -> "내가 대표인 아이예요"
    // 서버가 값을 늘려도 화면이 지어내지 않는다.
    AcceptResult.UNKNOWN -> "참여했어요"
}

@Composable
private fun Failure(outcome: AcceptOutcome) {
    val message = when (outcome) {
        is AcceptOutcome.Joined -> return
        // 404 와 410 을 한 문장으로 묶지 않는다 — 뒤쪽은 새 초대를 받으면 되고 앞쪽은 아니다.
        is AcceptOutcome.NotFound -> "사용할 수 없는 초대예요. 링크가 잘못됐거나 다른 분이 이미 사용했어요."
        is AcceptOutcome.Expired -> "만료된 초대예요. 대표 보호자에게 새 초대를 요청해 주세요."
        is AcceptOutcome.Conflict -> outcome.message // 서버가 사용자에게 보여 줄 문장으로 써 놨다.
        // 422 는 사용자 잘못이 아니다 — 화면이 중복 선택을 막고 있으므로, 여기까지 왔으면
        // 미리보기 이후 상태가 바뀐 것이다. 다시 불러오라고만 말한다.
        is AcceptOutcome.Invalid -> outcome.message
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
