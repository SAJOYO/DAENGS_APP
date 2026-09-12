package com.daengs.app.ui.pet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.pet.CreatedInvite
import com.daengs.app.pet.InviteLink
import com.daengs.app.pet.InviteShare
import com.daengs.app.pet.InviteStatus
import com.daengs.app.pet.MAX_ACTIVE_INVITES
import com.daengs.app.pet.PetInvite
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 대표가 초대를 만들고 관리하는 자리.
 *
 * **링크는 만든 직후 한 번만 보인다.** 서버가 해시만 들고 있어 목록으로는 링크를 되살릴 수
 * 없다([CreatedInvite] 머리말). 그래서 목록 줄에는 공유·복사를 두지 않고, 잃어버린 초대는
 * 취소하고 새로 만드는 길만 안내한다.
 *
 * @param isOwner 대표인가. **false 면 아무 요청도 시작하지 않는다** — 서버도 404 로 막지만,
 *   화면이 먼저 막아야 돌보미의 기기에서 헛된 요청이 안 나간다.
 * @param linkAvailable 지금 환경에서 쓸 수 있는 링크를 만들 수 있나([InviteLink.available]).
 * @param linkOf 토큰 → 링크. 테스트와 `@Preview` 가 가짜 링크를 넣는 자리다.
 */
@Composable
fun PetInvitesScreen(
    petName: String?,
    isOwner: Boolean,
    invites: List<PetInvite>?,
    justCreated: CreatedInvite?,
    statusOf: (PetInvite) -> InviteStatus,
    activeCount: Int,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    error: String? = null,
    linkAvailable: Boolean = InviteLink.available(),
    linkOf: (String) -> String? = { InviteLink.of(it) },
    zone: ZoneId = ZoneId.systemDefault(),
    onCreate: () -> Unit = {},
    onCancel: (PetInvite) -> Unit = {},
    onDismissCreated: () -> Unit = {},
    onShare: (String) -> Unit = {},
    onCopy: (String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    BackHandler { onBack() }

    Column(
        modifier
            .fillMaxSize()
            .background(CreamBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp)
            .testTag("pet-invites"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "보호자 초대",
                color = TextDark,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            DaengsTextAction("닫기", onBack, tint = TextMuted)
        }

        if (!isOwner) {
            // 여기서 끝낸다 — 아래 목록·생성으로 내려가지 않으므로 요청도 안 나간다.
            Text(
                "대표 보호자만 초대를 만들 수 있어요.",
                color = TextMuted,
                fontSize = 14.sp,
                modifier = Modifier.testTag("invites-not-owner"),
            )
            return@Column
        }

        petName?.let { Text("${it}를 함께 돌볼 사람을 초대해요", color = TextMuted, fontSize = 13.sp) }

        justCreated?.let { created ->
            InviteTicket(
                petName = petName,
                link = linkOf(created.token),
                onShare = onShare,
                onCopy = onCopy,
                onDismiss = onDismissCreated,
            )
        }

        if (!linkAvailable) {
            Notice(
                "초대 링크를 사용할 수 없는 개발 환경입니다. 운영 서버에 연결했을 때만 초대를 만들 수 있어요.",
                tag = "invites-env-blocked",
            )
        }

        CreateRow(
            activeCount = activeCount,
            enabled = linkAvailable && !busy && activeCount < MAX_ACTIVE_INVITES,
            onCreate = onCreate,
        )

        error?.let { Notice(it, tag = "invites-error") }

        when {
            // **실패를 빈 목록으로 그리지 않는다.** "못 불러왔다" 와 "아직 없다" 는 다르다.
            error != null && invites == null -> Unit
            invites == null -> Text(
                if (busy) "불러오는 중이에요" else "",
                color = TextMuted,
                fontSize = 14.sp,
                modifier = Modifier.testTag("invites-loading"),
            )
            invites.isEmpty() -> Text(
                "아직 보낸 초대가 없어요.",
                color = TextMuted,
                fontSize = 14.sp,
                modifier = Modifier.testTag("invites-empty"),
            )
            else -> invites.forEach { invite ->
                InviteRow(
                    invite = invite,
                    status = statusOf(invite),
                    zone = zone,
                    busy = busy,
                    onCancel = { onCancel(invite) },
                )
            }
        }

        Spacer(Modifier.height(2.dp))
        Text(
            "보안을 위해 초대 링크는 생성 직후 한 번만 확인할 수 있습니다.",
            color = TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.testTag("invites-link-once-notice"),
        )
        Text(
            "링크를 잃어버렸다면 그 초대를 취소하고 새로 만들어 주세요.",
            color = TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun CreateRow(activeCount: Int, enabled: Boolean, onCreate: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DaengsWideButton(
            label = "초대 만들기",
            onClick = onCreate,
            enabled = enabled,
            modifier = Modifier.testTag("invites-create"),
        )
        Text(
            "살아 있는 초대 ${activeCount}/$MAX_ACTIVE_INVITES",
            color = if (activeCount >= MAX_ACTIVE_INVITES) DaengPinkDeep else TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.testTag("invites-active-count"),
        )
        if (activeCount >= MAX_ACTIVE_INVITES) {
            Text(
                "살아 있는 초대가 가득 찼어요. 쓰지 않는 초대를 취소하면 새로 만들 수 있어요.",
                color = TextMuted,
                fontSize = 12.sp,
            )
        }
    }
}

/** 방금 만든 초대장. **링크가 보이는 유일한 자리다.** */
@Composable
private fun InviteTicket(
    petName: String?,
    link: String?,
    onShare: (String) -> Unit,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        color = CardWhite,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().testTag("invite-ticket"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${petName ?: "우리 아이"}의 공동 돌봄 초대장",
                    color = TextDark,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                DaengsTextAction("닫기", onDismiss, tint = TextMuted)
            }
            Text(
                "초대 링크를 눌러 공동 보호자로 참여할 수 있어요.",
                color = TextMuted,
                fontSize = 13.sp,
            )
            Surface(color = PinkFaint, shape = RoundedCornerShape(8.dp)) {
                Text(
                    "24시간 동안 사용할 수 있어요",
                    color = DaengPinkDeep,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            if (link == null) {
                Text(
                    "이 환경에서는 초대 링크를 만들 수 없어요.",
                    color = TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.testTag("ticket-no-link"),
                )
            } else {
                // **링크 글자 자체는 안 보여 준다.** 화면에 띄워 두면 스크린샷·어깨너머로
                // 토큰이 샌다. 내보내는 길은 공유와 복사 둘뿐이다.
                val message = InviteShare.message(petName, link)
                DaengsWideButton(
                    label = "초대 링크 공유하기",
                    onClick = { onShare(message) },
                    modifier = Modifier.testTag("ticket-share"),
                )
                DaengsTextAction("링크 복사", { onCopy(link) }, tint = DaengPinkDeep)
            }
        }
    }
}

@Composable
private fun InviteRow(
    invite: PetInvite,
    status: InviteStatus,
    zone: ZoneId,
    busy: Boolean,
    onCancel: () -> Unit,
) {
    Surface(color = CardWhite, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                StatusBadge(status)
                Text(
                    when (status) {
                        InviteStatus.ACTIVE -> "${format(invite.expiresAtMs, zone)}까지"
                        InviteStatus.ACCEPTED -> "${format(invite.acceptedAtMs ?: invite.createdAtMs, zone)}에 수락됨"
                        InviteStatus.EXPIRED -> "${format(invite.expiresAtMs, zone)}에 만료됨"
                    },
                    color = TextMuted,
                    fontSize = 12.sp,
                )
            }
            // **활성 초대만 취소한다.** 수락된 초대를 지우면 그 사람이 나가는 것이 아니라
            // 재시도 복구용 영수증만 사라지고, 만료된 초대는 서버가 알아서 치운다.
            if (status == InviteStatus.ACTIVE) {
                Spacer(Modifier.width(8.dp))
                DaengsTextAction(
                    if (busy) "취소 중" else "취소",
                    { if (!busy) onCancel() },
                    tint = DaengsColors.Error,
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: InviteStatus) {
    val (label, color) = when (status) {
        InviteStatus.ACTIVE -> "대기 중" to DaengPink
        InviteStatus.ACCEPTED -> "사용됨" to DaengsColors.TextSecondary
        InviteStatus.EXPIRED -> "만료" to DaengsColors.TextSecondary
    }
    Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun Notice(text: String, tag: String) {
    Surface(color = PinkFaint, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text,
            color = TextDark,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).testTag(tag),
        )
    }
}

private val TIME = DateTimeFormatter.ofPattern("M월 d일 HH:mm")

private fun format(epochMs: Long, zone: ZoneId): String =
    runCatching { Instant.ofEpochMilli(epochMs).atZone(zone).format(TIME) }.getOrDefault("")

// -- @Preview -----------------------------------------------------------------

private const val PREVIEW_LINK = "https://daengapi.weareithero.cloud/invite#preview-token"

private fun previewInvite(id: String, createdAt: Long, expiresAt: Long, acceptedAt: Long? = null) =
    PetInvite(id = id, createdAtMs = createdAt, expiresAtMs = expiresAt, acceptedAtMs = acceptedAt)

private const val NOW = 1_757_000_000_000L
private const val HOUR = 3_600_000L

@Preview(name = "초대 없음")
@Composable
private fun InvitesEmptyPreview() {
    DaengsTheme {
        PetInvitesScreen(
            petName = "네옹", isOwner = true, invites = emptyList(), justCreated = null,
            statusOf = { InviteStatus.ACTIVE }, activeCount = 0,
            linkAvailable = true, linkOf = { PREVIEW_LINK },
        )
    }
}

@Preview(name = "활성·사용됨·만료")
@Composable
private fun InvitesMixedPreview() {
    val invites = listOf(
        previewInvite("a", NOW - HOUR, NOW + 20 * HOUR),
        previewInvite("b", NOW - 30 * HOUR, NOW - 6 * HOUR, acceptedAt = NOW - 28 * HOUR),
        previewInvite("c", NOW - 40 * HOUR, NOW - 16 * HOUR),
    )
    DaengsTheme {
        PetInvitesScreen(
            petName = "네옹", isOwner = true, invites = invites, justCreated = null,
            statusOf = { it.status(NOW) }, activeCount = 1,
            linkAvailable = true, linkOf = { PREVIEW_LINK },
        )
    }
}

@Preview(name = "생성 직후 초대장")
@Composable
private fun InviteTicketPreview() {
    DaengsTheme {
        PetInvitesScreen(
            petName = "네옹", isOwner = true,
            invites = listOf(previewInvite("a", NOW, NOW + 24 * HOUR)),
            justCreated = CreatedInvite("a", "p1", "preview-token", NOW + 24 * HOUR),
            statusOf = { it.status(NOW) }, activeCount = 1,
            linkAvailable = true, linkOf = { PREVIEW_LINK },
        )
    }
}

@Preview(name = "상한 도달")
@Composable
private fun InvitesFullPreview() {
    val invites = List(3) { previewInvite("i$it", NOW - HOUR, NOW + 20 * HOUR) }
    DaengsTheme {
        PetInvitesScreen(
            petName = "네옹", isOwner = true, invites = invites, justCreated = null,
            statusOf = { it.status(NOW) }, activeCount = 3,
            linkAvailable = true, linkOf = { PREVIEW_LINK },
        )
    }
}

@Preview(name = "불러오는 중")
@Composable
private fun InvitesLoadingPreview() {
    DaengsTheme {
        PetInvitesScreen(
            petName = "네옹", isOwner = true, invites = null, justCreated = null,
            statusOf = { InviteStatus.ACTIVE }, activeCount = 0, busy = true,
            linkAvailable = true, linkOf = { PREVIEW_LINK },
        )
    }
}

@Preview(name = "오류")
@Composable
private fun InvitesErrorPreview() {
    DaengsTheme {
        PetInvitesScreen(
            petName = "네옹", isOwner = true, invites = null, justCreated = null,
            statusOf = { InviteStatus.ACTIVE }, activeCount = 0,
            error = "서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.",
            linkAvailable = true, linkOf = { PREVIEW_LINK },
        )
    }
}

@Preview(name = "개발 환경")
@Composable
private fun InvitesDevEnvPreview() {
    DaengsTheme {
        PetInvitesScreen(
            petName = "네옹", isOwner = true, invites = emptyList(), justCreated = null,
            statusOf = { InviteStatus.ACTIVE }, activeCount = 0,
            linkAvailable = false, linkOf = { null },
        )
    }
}
