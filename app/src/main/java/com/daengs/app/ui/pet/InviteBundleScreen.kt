package com.daengs.app.ui.pet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import com.daengs.app.pet.CreatedInviteBundle
import com.daengs.app.pet.InviteBundle
import com.daengs.app.pet.InviteLink
import com.daengs.app.pet.InvitePetBrief
import com.daengs.app.pet.InviteStatus
import com.daengs.app.pet.MAX_ACTIVE_INVITE_BUNDLES
import com.daengs.app.pet.MAX_PETS_PER_INVITE
import com.daengs.app.pet.Pet
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
 * 여러 아이를 한 링크로 부르는 자리.
 *
 * **강아지 카드에서 들어온다** — 「함께 돌보는 사람」 → 「보호자 초대」. 들어온 아이가
 * 미리 골라진 채로 열리고, 거기서 다른 아이를 더 얹을 수 있다.
 *
 * 목록과 상한은 **주보호자 전체** 기준이라 어느 아이로 들어왔든 같은 것을 본다. 서버가
 * 활성 초대를 강아지당이 아니라 주보호자당 3묶음으로 세기 때문이다 — 강아지별로 세면
 * 화면이 `0/3` 이라고 해 놓고 만들 때 409 가 난다.
 *
 * [PetInvitesScreen] 은 단일 초대 시절의 화면이다. **지금은 어디서도 안 연다** — 서버가
 * 구 경로를 남겨 둬서 파일도 남겼을 뿐이다.
 *
 * 고를 수 있는 아이는 **[Pet.isGroupOwner] 가 true 인 것만**이다. `isOwner` 로 거르면
 * 연결된 공동 보호자의 아이가 목록에 뜨고, 골라 봐야 서버가 막는다.
 *
 * @param pets 내 강아지 전부. **여기서 거르지 않고 넘긴다** — 화면이 "고를 수 없는 아이"
 *   까지 알아야 왜 안 보이는지 말해 줄 수 있다.
 * @param linkAvailable 지금 환경에서 쓸 수 있는 링크를 만들 수 있나([InviteLink.available]).
 * @param linkOf 토큰 → 링크. 테스트와 `@Preview` 가 가짜 링크를 넣는 자리다.
 */
@Composable
fun InviteBundleScreen(
    pets: List<Pet>,
    selected: List<String>,
    invites: List<InviteBundle>?,
    justCreated: CreatedInviteBundle?,
    statusOf: (InviteBundle) -> InviteStatus,
    activeCount: Int,
    modifier: Modifier = Modifier,
    canCreate: Boolean = false,
    busy: Boolean = false,
    error: String? = null,
    linkAvailable: Boolean = InviteLink.available(),
    /** 이 링크가 디버그끼리만 통하는가([InviteLink.devOnly]). 화면이 그 사실을 적는다. */
    linkDevOnly: Boolean = InviteLink.devOnly(),
    linkOf: (String) -> String? = { InviteLink.of(it) },
    zone: ZoneId = ZoneId.systemDefault(),
    onToggle: (String) -> Unit = {},
    onCreate: () -> Unit = {},
    onCancel: (InviteBundle) -> Unit = {},
    onDismissCreated: () -> Unit = {},
    onShare: (String) -> Unit = {},
    onCopy: (String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    BackHandler { onBack() }

    val choosable = pets.filter { it.isGroupOwner && it.farewellOn == null }

    Column(
        modifier
            .fillMaxSize()
            .background(CreamBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp)
            .testTag("invite-bundle"),
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

        if (choosable.isEmpty()) {
            // 여기서 끝낸다 — 아래 생성으로 내려가지 않으므로 요청도 안 나간다.
            Text(
                "초대할 수 있는 아이가 없어요. 대표 보호자인 아이만 초대를 만들 수 있어요.",
                color = TextMuted,
                fontSize = 14.sp,
                modifier = Modifier.testTag("bundle-no-owned"),
            )
            return@Column
        }

        Text(
            "함께 돌볼 사람에게 보낼 아이를 골라요. 한 번에 ${MAX_PETS_PER_INVITE}마리까지 한 링크로 보낼 수 있어요.",
            color = TextMuted,
            fontSize = 13.sp,
        )

        justCreated?.let { created ->
            InviteTicket(
                petName = namesOf(created.petIds, pets),
                link = linkOf(created.token),
                onShare = onShare,
                onCopy = onCopy,
                onDismiss = onDismissCreated,
            )
        }

        if (!linkAvailable) {
            Notice(
                "초대 링크를 사용할 수 없는 환경입니다. 서버 주소를 확인해 주세요.",
                tag = "bundle-env-blocked",
            )
        } else if (linkDevOnly) {
            // **링크 글자만 봐서는 구별이 안 된다.** 호스트가 운영과 같아서, 받는 사람이
            // 출시 앱이면 그 서버에 없는 토큰이라 404 를 받는다.
            Notice(
                "개발 서버로 만드는 초대예요. 같은 개발 빌드끼리만 수락할 수 있어요.",
                tag = "bundle-dev-link",
                tint = DaengPinkDeep,
            )
        }

        Surface(color = CardWhite, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 6.dp)) {
                choosable.forEach { pet ->
                    PickRow(
                        pet = pet,
                        checked = pet.id in selected,
                        // 상한에 닿으면 **고른 것만** 누를 수 있다 — 빼는 길이 막히면
                        // 사용자가 다시 고를 방법이 없다.
                        enabled = !busy && (pet.id in selected || selected.size < MAX_PETS_PER_INVITE),
                        onToggle = { onToggle(pet.id) },
                    )
                }
            }
        }

        val hidden = pets.size - choosable.size
        if (hidden > 0) {
            Text(
                "공동 돌봄으로 참여했거나 배웅한 아이 ${hidden}마리는 초대할 수 없어요.",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.testTag("bundle-hidden-note"),
            )
        }

        CreateRow(
            selectedCount = selected.size,
            activeCount = activeCount,
            enabled = linkAvailable && canCreate,
            onCreate = onCreate,
        )

        error?.let { Notice(it, tag = "bundle-error", tint = DaengsColors.Error) }

        when {
            // **실패를 빈 목록으로 그리지 않는다.** "못 불러왔다" 와 "아직 없다" 는 다르다.
            error != null && invites == null -> Unit
            invites == null -> Text(
                if (busy) "불러오는 중이에요" else "",
                color = TextMuted,
                fontSize = 14.sp,
                modifier = Modifier.testTag("bundle-loading"),
            )
            invites.isEmpty() -> Text(
                "아직 보낸 초대가 없어요.",
                color = TextMuted,
                fontSize = 14.sp,
                modifier = Modifier.testTag("bundle-empty"),
            )
            else -> invites.forEach { invite ->
                BundleRow(
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
            modifier = Modifier.testTag("bundle-link-once-notice"),
        )
        Text(
            "초대를 취소하면 그 링크에 담긴 아이 전부가 함께 취소돼요.",
            color = TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun PickRow(pet: Pet, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("bundle-pick-${pet.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = DaengPinkDeep),
        )
        Text(
            pet.name,
            color = if (enabled || checked) TextDark else TextMuted,
            fontSize = 15.sp,
            fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun CreateRow(selectedCount: Int, activeCount: Int, enabled: Boolean, onCreate: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DaengsWideButton(
            label = if (selectedCount > 0) "${selectedCount}마리 초대 링크 만들기" else "초대 링크 만들기",
            onClick = onCreate,
            enabled = enabled,
            accent = true,
            modifier = Modifier.testTag("bundle-create"),
        )
        Text(
            // **묶음 수를 센다.** 세 마리를 한 링크로 부른 것은 한 자리다.
            "살아 있는 초대 $activeCount/$MAX_ACTIVE_INVITE_BUNDLES",
            color = if (activeCount >= MAX_ACTIVE_INVITE_BUNDLES) DaengPinkDeep else TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.testTag("bundle-active-count"),
        )
        if (activeCount >= MAX_ACTIVE_INVITE_BUNDLES) {
            Text(
                "살아 있는 초대가 ${MAX_ACTIVE_INVITE_BUNDLES}개예요. 하나를 취소하면 새로 만들 수 있어요.",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.testTag("bundle-at-limit"),
            )
        }
    }
}

@Composable
private fun BundleRow(
    invite: InviteBundle,
    status: InviteStatus,
    zone: ZoneId,
    busy: Boolean,
    onCancel: () -> Unit,
) {
    Surface(
        color = CardWhite,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("bundle-row-${invite.id}"),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    // 어느 아이들을 부른 링크인지가 이 줄의 본래 쓸모다.
                    invite.pets.joinToString("·") { it.name }.ifBlank { "이름 없는 초대" },
                    color = TextDark,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).testTag("bundle-row-names-${invite.id}"),
                )
                StatusBadge(status)
            }
            Text(
                "${format(invite.createdAtMs, zone)} 만듦 · ${format(invite.expiresAtMs, zone)} 까지",
                color = TextMuted,
                fontSize = 12.sp,
            )
            if (status == InviteStatus.ACTIVE) {
                DaengsTextAction(
                    "초대 취소",
                    onCancel,
                    tint = if (busy) TextMuted else DaengsColors.Error,
                    modifier = Modifier.testTag("bundle-cancel-${invite.id}"),
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: InviteStatus) {
    val (label, tint) = when (status) {
        InviteStatus.ACTIVE -> "대기 중" to DaengPinkDeep
        InviteStatus.ACCEPTED -> "사용됨" to TextMuted
        InviteStatus.EXPIRED -> "만료됨" to TextMuted
    }
    Surface(color = if (status == InviteStatus.ACTIVE) PinkFaint else CreamBg, shape = RoundedCornerShape(8.dp)) {
        Text(
            label,
            color = tint,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun Notice(text: String, tag: String, tint: androidx.compose.ui.graphics.Color = TextMuted) {
    Text(text, color = tint, fontSize = 13.sp, modifier = Modifier.testTag(tag))
}

/** 초대장 제목에 쓸 이름들. 목록에서 못 찾은 아이는 건너뛴다. */
private fun namesOf(petIds: List<String>, pets: List<Pet>): String =
    petIds.mapNotNull { id -> pets.firstOrNull { it.id == id }?.name }
        .joinToString("·")
        .ifBlank { "우리 아이" }

private fun format(epochMs: Long, zone: ZoneId): String =
    DateTimeFormatter.ofPattern("M/d HH:mm").withZone(zone).format(Instant.ofEpochMilli(epochMs))

// -- 미리보기 ------------------------------------------------------------------

private fun previewPet(id: String, name: String, groupOwner: Boolean = true) = Pet(
    id = id, name = name, breed = "dog_beagle",
    sex = null, neutered = null, weightKg = null,
    birthDate = null, birthDateKind = null,
    isPrimary = id == "p1", isOwner = true, isGroupOwner = groupOwner,
)

private fun previewBundle(id: String, names: List<String>, acceptedAt: Long? = null) = InviteBundle(
    id = id,
    pets = names.mapIndexed { i, n -> InvitePetBrief("b$i", n, null, false) },
    createdAtMs = 1_757_000_000_000L,
    expiresAtMs = 1_757_086_400_000L,
    acceptedAtMs = acceptedAt,
)

@Preview(name = "고르는 중", showBackground = true)
@Composable
private fun BundlePickingPreview() {
    DaengsTheme {
        InviteBundleScreen(
            pets = listOf(previewPet("p1", "롱이"), previewPet("p2", "몽이"), previewPet("p3", "콩이")),
            selected = listOf("p1", "p2"),
            invites = emptyList(),
            justCreated = null,
            statusOf = { InviteStatus.ACTIVE },
            activeCount = 0,
            canCreate = true,
            linkAvailable = true,
            linkOf = { "https://example.test/invite#$it" },
        )
    }
}

@Preview(name = "묶음 목록", showBackground = true)
@Composable
private fun BundleListPreview() {
    DaengsTheme {
        InviteBundleScreen(
            pets = listOf(previewPet("p1", "롱이"), previewPet("p2", "몽이")),
            selected = emptyList(),
            invites = listOf(
                previewBundle("i1", listOf("롱이", "몽이")),
                previewBundle("i2", listOf("콩이"), acceptedAt = 1_757_050_000_000L),
            ),
            justCreated = null,
            statusOf = { if (it.id == "i1") InviteStatus.ACTIVE else InviteStatus.ACCEPTED },
            activeCount = 1,
            linkAvailable = true,
            linkOf = { "https://example.test/invite#$it" },
        )
    }
}

@Preview(name = "만든 직후", showBackground = true)
@Composable
private fun BundleCreatedPreview() {
    DaengsTheme {
        InviteBundleScreen(
            pets = listOf(previewPet("p1", "롱이"), previewPet("p2", "몽이")),
            selected = emptyList(),
            invites = listOf(previewBundle("i1", listOf("롱이", "몽이"))),
            justCreated = CreatedInviteBundle("i1", listOf("p1", "p2"), "sample_TOKEN-1", 1_757_086_400_000L),
            statusOf = { InviteStatus.ACTIVE },
            activeCount = 1,
            linkAvailable = true,
            linkOf = { "https://example.test/invite#$it" },
        )
    }
}

@Preview(name = "상한", showBackground = true)
@Composable
private fun BundleAtLimitPreview() {
    DaengsTheme {
        InviteBundleScreen(
            pets = listOf(previewPet("p1", "롱이")),
            selected = listOf("p1"),
            invites = List(3) { previewBundle("i$it", listOf("롱이")) },
            justCreated = null,
            statusOf = { InviteStatus.ACTIVE },
            activeCount = 3,
            canCreate = false,
            linkAvailable = true,
            linkOf = { "https://example.test/invite#$it" },
        )
    }
}

@Preview(name = "고를 아이 없음", showBackground = true)
@Composable
private fun BundleNoOwnedPreview() {
    DaengsTheme {
        InviteBundleScreen(
            pets = listOf(previewPet("p1", "롱이", groupOwner = false)),
            selected = emptyList(),
            invites = emptyList(),
            justCreated = null,
            statusOf = { InviteStatus.ACTIVE },
            activeCount = 0,
        )
    }
}
