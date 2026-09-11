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
import com.daengs.app.pet.PetMember
import com.daengs.app.ui.common.DaengsTextAction
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted

/**
 * 한 아이를 함께 돌보는 사람들. **이번 단계는 읽기 전용이다** — 초대·내보내기·나가기·대표
 * 넘기기는 다음이고, 여기서는 누가 있는지만 본다.
 *
 * **순서를 앱이 다시 매기지 않는다.** 서버가 대표를 맨 앞에 놓아 주므로([PetMemberHolder]
 * 머리말) 받은 대로 그린다.
 *
 * @param members 받아 온 구성원. **null 이면 아직 못 받은 것이고 빈 목록과 다르다**
 * @param currentUserId 지금 로그인한 사람. 자기 줄에 표시를 붙이는 데만 쓴다
 */
@Composable
fun PetMembersScreen(
    members: List<PetMember>?,
    modifier: Modifier = Modifier,
    petName: String? = null,
    currentUserId: String? = null,
    busy: Boolean = false,
    error: String? = null,
    /**
     * 초대를 관리하러 간다. **대표일 때만 넘긴다** — 목록에 대표가 있는지가 아니라
     * 지금 로그인한 사람의 줄이 `isOwner` 인지로 가른다
     * ([isOwnedBy][com.daengs.app.pet.isOwnedBy]). null 이면 그 줄이 안 뜬다.
     */
    onOpenInvites: (() -> Unit)? = null,
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
            else -> members.forEach { MemberRow(it, isMe = currentUserId != null && it.appUserId == currentUserId) }
        }

        // 대표에게만 뜬다. 돌보미에게는 부를 수 있는 API 가 없어서(전부 404) 자리도 두지 않는다.
        onOpenInvites?.let { open ->
            Surface(
                color = CardWhite,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = open)
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                        .testTag("open-invites"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("보호자 초대", color = TextDark, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("관리", color = DaengPinkDeep, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun StateLine(text: String, tag: String) {
    Text(text, color = TextMuted, fontSize = 14.sp, modifier = Modifier.testTag(tag))
}

@Composable
private fun MemberRow(member: PetMember, isMe: Boolean) {
    Surface(color = CardWhite, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        // **이름이 없다고 "이전 보호자" 로 그리지 않는다.** 이 목록에 실리는
                        // 사람은 전부 지금 구성원이라, 그 말은 케어 기록 쪽 규칙이고 여기서는 틀리다.
                        member.nickname ?: "이름을 확인할 수 없는 보호자",
                        color = TextDark,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (isMe) {
                        Spacer(Modifier.width(6.dp))
                        Text("나", color = DaengPink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            RoleBadge(isOwner = member.isOwner)
        }
    }
}

@Composable
private fun RoleBadge(isOwner: Boolean) {
    Surface(color = PinkFaint, shape = RoundedCornerShape(8.dp)) {
        Text(
            if (isOwner) "대표" else "돌보미",
            color = DaengPinkDeep,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Preview
@Composable
private fun PetMembersPreview() {
    DaengsTheme {
        PetMembersScreen(
            members = listOf(
                PetMember("u1", "아빠", isOwner = true),
                PetMember("u2", "나연", isOwner = false),
                PetMember("u3", null, isOwner = false),
            ),
            petName = "네옹",
            currentUserId = "u2",
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
