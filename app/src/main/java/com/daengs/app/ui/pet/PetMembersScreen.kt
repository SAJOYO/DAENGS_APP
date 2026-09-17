package com.daengs.app.ui.pet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.daengs.app.pet.PetMember
import com.daengs.app.ui.common.DaengsTextAction
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.PinkSoft
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted

/**
 * 한 아이를 함께 돌보는 사람들 — 보고, 부르고, 내보내고, 나가는 자리.
 *
 * **순서를 앱이 다시 매기지 않는다.** 서버가 주보호자를 맨 앞에 놓아 주므로
 * ([PetMemberHolder][com.daengs.app.pet.PetMemberHolder] 머리말) 받은 대로 그린다.
 *
 * **아래 버튼은 역할마다 하나다.** 주보호자에게는 부르는 일(초대)만, 돌보미에게는
 * 나가는 일만 있다 — 둘을 같이 띄우면 「초대」와 「나가기」가 나란히 앉아, 자기가
 * 무엇을 할 수 있는 사람인지가 화면에서 안 읽힌다. 돌보미가 초대를 눌러 봐야
 * 서버가 막는 것도 같은 이유다.
 *
 * @param members 받아 온 구성원. **null 이면 아직 못 받은 것이고 빈 목록과 다르다**
 * @param currentUserId 지금 로그인한 사람. 자기 줄을 가리는 데만 쓴다. **이름으로 가르지
 *   않는다** — 같은 이름을 쓰는 보호자가 있으면 남의 줄에 내보내기가 뜬다
 * @param isGroupOwner 지금 보는 사람이 이 아이의 그룹 주보호자인가. 아래 버튼과 줄의
 *   내보내기를 이 값이 가른다 (`Pet.isGroupOwner` — 행의 대표인 `isOwner` 가 아니다)
 * @param actionBusy 내보내기·나가기가 도는 중인가. 목록 조회의 [busy] 와 다른 값이다
 * @param actionError 내보내기·나가기가 실패한 이유. **목록은 그대로 두고** 이 줄만 붙는다
 */
@Composable
fun PetMembersScreen(
    members: List<PetMember>?,
    modifier: Modifier = Modifier,
    petName: String? = null,
    currentUserId: String? = null,
    busy: Boolean = false,
    error: String? = null,
    isGroupOwner: Boolean = false,
    actionBusy: Boolean = false,
    actionError: String? = null,
    /**
     * 새 돌보미를 부르러 간다. **주보호자일 때만 넘긴다.** null 이면 그 버튼이 안 뜬다.
     */
    onOpenInvites: (() -> Unit)? = null,
    /** 다른 보호자를 내보낸다. **주보호자일 때만 넘긴다.** null 이면 줄에 버튼이 안 붙는다 */
    onRemove: ((PetMember) -> Unit)? = null,
    /** 내가 이 아이의 공동 돌봄에서 나간다. **돌보미일 때만 넘긴다.** */
    onLeave: (() -> Unit)? = null,
    onBack: () -> Unit = {},
) {
    BackHandler { onBack() }

    // 확인 창은 **화면이 들고 있다** — 목록이 새로 오면서 줄이 다시 만들어져도 창이 안 닫힌다
    // (마이의 삭제 창과 같은 규칙).
    var removing by remember { mutableStateOf<PetMember?>(null) }
    var leaving by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .background(CreamBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp)
            .testTag("pet-members"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "보호자 목록",
                color = TextDark,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            DaengsTextAction("닫기", onBack, tint = TextMuted)
        }
        petName?.let { Text("${it}를 함께 돌보는 사람", color = TextMuted, fontSize = 13.sp) }

        when {
            // **실패를 빈 목록으로 그리지 않는다.** "못 불러왔다" 와 "아무도 없다" 는 다르다.
            error != null -> StateLine(error, tag = "pet-members-error")
            members == null -> StateLine(if (busy) "불러오는 중이에요" else "", tag = "pet-members-loading")
            members.isEmpty() -> StateLine("보호자가 없어요", tag = "pet-members-empty")
            else -> members.forEach { member ->
                val isMe = currentUserId != null && member.appUserId == currentUserId
                MemberRow(
                    member = member,
                    isMe = isMe,
                    // **자기 줄에는 안 붙인다.** 주보호자가 자기를 내보내는 일은 없고,
                    // 서버도 그 요청은 대표 승계를 먼저 하라고 막는다.
                    //
                    // **여기서 바로 보내지 않는다** — 확인 창을 먼저 띄운다.
                    onRemove = { removing = member }.takeIf { onRemove != null && isGroupOwner && !isMe },
                )
            }
        }

        // **실패해도 목록은 그대로다.** 여기 한 줄만 붙고, 사용자는 같은 버튼을 다시 누른다.
        actionError?.let { StateLine(it, tag = "pet-members-action-error", tint = DaengsColors.Error) }

        Spacer(Modifier.height(2.dp))
        // 역할마다 **하나만** — `when` 이라 둘이 같이 뜰 수 없다.
        when {
            isGroupOwner -> onOpenInvites?.let { open ->
                BottomAction("새 돌보미 초대", tag = "open-invites", busy = actionBusy, onClick = open)
            }
            else -> onLeave?.let {
                BottomAction(
                    "공동 돌봄 나가기",
                    tag = "leave-co-care",
                    busy = actionBusy,
                    danger = true,
                    onClick = { leaving = true },
                )
            }
        }
    }

    removing?.let { member ->
        RemoveMemberDialog(
            member = member,
            petName = petName,
            onConfirm = {
                removing = null
                onRemove?.invoke(member)
            },
            // **취소는 아무것도 보내지 않는다.** 창을 닫는 것이 전부다.
            onDismiss = { removing = null },
        )
    }
    if (leaving) {
        LeaveDialog(
            petName = petName,
            onConfirm = {
                leaving = false
                onLeave?.invoke()
            },
            onDismiss = { leaving = false },
        )
    }
}

@Composable
private fun StateLine(text: String, tag: String, tint: Color = TextMuted) {
    Text(text, color = tint, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.testTag(tag))
}

@Composable
private fun MemberRow(member: PetMember, isMe: Boolean, onRemove: (() -> Unit)?) {
    Surface(
        color = CardWhite,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("member-${member.appUserId}"),
    ) {
        Row(
            // **줄 높이를 「내보내기」 버튼 높이에 맞춰 둔다.** 버튼에는 누르기 좋게 위아래
            // 여백이 있어, 안 맞추면 버튼이 있는 줄만 조금 높아져 목록이 들쭉날쭉해 보였다.
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp).heightIn(min = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // **사람에 붙은 것(이름 · 나 · 역할)은 한 덩어리로 왼쪽에 모은다.** 역할 배지를
            // 오른쪽 끝에 두면 「내보내기」가 있는 줄만 배지가 안쪽으로 밀려, 줄마다 배지
            // 자리가 달라 보였다. 오른쪽 끝은 **동작 하나만** 쓴다.
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    // **이름이 없다고 "이전 보호자" 로 그리지 않는다.** 이 목록에 실리는
                    // 사람은 전부 지금 구성원이라, 그 말은 케어 기록 쪽 규칙이고 여기서는 틀리다.
                    member.nickname ?: "이름을 확인할 수 없는 보호자",
                    color = TextDark,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    // 긴 이름은 **이름만** 줄인다 — 배지와 「내보내기」는 늘 보여야 한다.
                    // `fill = false` 라 짧은 이름 뒤에 빈칸이 생기지 않고 배지가 바로 붙는다.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).testTag("member-name-${member.appUserId}"),
                )
                if (isMe) {
                    Spacer(Modifier.width(6.dp))
                    Text("나", color = DaengPink, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
                Spacer(Modifier.width(8.dp))
                RoleBadge(isOwner = member.isOwner, modifier = Modifier.testTag("role-${member.appUserId}"))
            }
            onRemove?.let {
                Spacer(Modifier.width(8.dp))
                // 되돌리기 어려운 쪽이라 빨강이다. 누르면 바로 나가지 않고 한 번 더 묻는다.
                DaengsTextAction(
                    "내보내기",
                    it,
                    modifier = Modifier.testTag("remove-${member.appUserId}"),
                    tint = DaengsColors.Error,
                )
            }
        }
    }
}

@Composable
private fun RoleBadge(isOwner: Boolean, modifier: Modifier = Modifier) {
    Surface(color = PinkFaint, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Text(
            // **역할 이름을 강아지의 「대표」와 다르게 쓴다.** 카드의 「대표」는 대표 강아지라
            // 뜻이 아예 다른데, 같은 두 글자가 두 화면에 있으면 같은 것으로 읽힌다.
            if (isOwner) "주보호자" else "공동 돌보미",
            color = DaengPinkDeep,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/**
 * 화면 맨 아래의 한 가지 동작.
 *
 * `DaengsWideButton` 을 쓰지 않는 것은 **위험한 쪽에 빨강이 필요해서**다 — 그 부품은
 * 분홍과 흰색 둘뿐이고, 여기에 색을 하나 더 넣으면 장소 화면 버튼까지 같이 흔들린다.
 */
@Composable
private fun BottomAction(
    label: String,
    tag: String,
    busy: Boolean,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val tint = if (danger) DaengsColors.Error else DaengPinkDeep
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (danger) CardWhite else PinkSoft)
            .border(1.dp, if (danger) DaengsColors.Error.copy(alpha = 0.35f) else DaengPink.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable(enabled = !busy, onClick = onClick)
            .padding(vertical = 14.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), color = tint, strokeWidth = 2.dp)
        } else {
            Text(label, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * 내보내기 확인.
 *
 * **무엇을 잃는지 먼저 말한다.** 되돌리려면 그 사람을 다시 초대해야 하고, 그동안 그
 * 사람은 이 아이의 공동 기록을 못 본다 — 누르고 나서 알게 되면 늦다.
 *
 * 마이의 삭제 창과 같은 배치다. 되돌리기 어려운 쪽(내보내기)이 왼쪽이고 취소가 오른쪽이다.
 */
@Composable
private fun RemoveMemberDialog(
    member: PetMember,
    petName: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        tag = "remove-member-dialog",
        title = "${member.nickname?.let { "${it}님" } ?: "이 보호자"}을 공동 돌봄에서 내보낼까요?",
        body = "이후 ${petName ?: "이 아이"}의 공동 기록을 볼 수 없어요.",
        confirmLabel = "내보내기",
        confirmTag = "remove-member-confirm",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/**
 * 나가기 확인.
 *
 * **내가 쓴 기록이 지워지는 게 아니라는 것을 같이 말한다.** 안 적어 두면 나가기를
 * "내 산책까지 지우는 일"로 읽고 아무도 못 나간다.
 */
@Composable
private fun LeaveDialog(petName: String?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        tag = "leave-co-care-dialog",
        title = "${petName ?: "이 아이"}의 공동 돌봄에서 나갈까요?",
        body = "다른 보호자의 기록과 이후 새 공동 기록은 볼 수 없어요. 내가 작성한 기록은 그대로 남아요.",
        confirmLabel = "나가기",
        confirmTag = "leave-co-care-confirm",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun ConfirmDialog(
    tag: String,
    title: String,
    body: String,
    confirmLabel: String,
    confirmTag: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = CardWhite, shape = RoundedCornerShape(20.dp), modifier = Modifier.testTag(tag)) {
            Column(Modifier.padding(22.dp)) {
                Text(title, color = TextDark, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)
                Spacer(Modifier.height(10.dp))
                Text(body, color = TextMuted, fontSize = 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    DaengsTextAction(
                        confirmLabel,
                        onConfirm,
                        modifier = Modifier.testTag(confirmTag),
                        tint = DaengsColors.Error,
                    )
                    Spacer(Modifier.width(6.dp))
                    DaengsTextAction("취소", onDismiss)
                }
            }
        }
    }
}

/** 배지는 이름 바로 뒤, 「내보내기」는 오른쪽 끝 — 긴 이름은 이름만 줄어든다. */
@Preview(widthDp = 360)
@Composable
private fun PetMembersOwnerPreview() {
    DaengsTheme {
        PetMembersScreen(
            members = listOf(
                PetMember("u1", "아빠", isOwner = true),
                PetMember("u2", "나연", isOwner = false),
                PetMember("u3", null, isOwner = false),
                PetMember("u4", "이름이아주아주길어서한줄에다안들어가는보호자", isOwner = false),
            ),
            petName = "네옹",
            currentUserId = "u1",
            isGroupOwner = true,
            onOpenInvites = {},
            onRemove = {},
        )
    }
}

@Preview
@Composable
private fun PetMembersCarerPreview() {
    DaengsTheme {
        PetMembersScreen(
            members = listOf(
                PetMember("u1", "아빠", isOwner = true),
                PetMember("u2", "나연", isOwner = false),
            ),
            petName = "네옹",
            currentUserId = "u2",
            isGroupOwner = false,
            onLeave = {},
        )
    }
}

@Preview
@Composable
private fun PetMembersActionErrorPreview() {
    DaengsTheme {
        PetMembersScreen(
            members = listOf(
                PetMember("u1", "아빠", isOwner = true),
                PetMember("u2", "나연", isOwner = false),
            ),
            petName = "네옹",
            currentUserId = "u1",
            isGroupOwner = true,
            actionError = "주보호자만 다른 보호자를 내보낼 수 있습니다.",
            onOpenInvites = {},
            onRemove = {},
        )
    }
}

@Preview
@Composable
private fun PetMembersLoadingPreview() {
    DaengsTheme { PetMembersScreen(members = null, busy = true) }
}

@Preview
@Composable
private fun PetMembersErrorPreview() {
    DaengsTheme { PetMembersScreen(members = null, error = "서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.") }
}
