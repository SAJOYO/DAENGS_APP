package com.daengs.app.ui.pet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.daengs.app.pet.PetMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 보호자 관리 — 내보내기와 나가기.
 *
 * **역할마다 할 수 있는 일이 다르고, 화면에 그것 하나만 떠야 한다.** 주보호자에게는
 * 부르는 일과 내보내는 일이, 돌보미에게는 나가는 일만 있다. 둘이 같이 뜨면 자기가
 * 무엇을 할 수 있는 사람인지가 화면에서 안 읽히고, 눌러 봐야 서버가 막는다.
 *
 * **자기 줄은 가른다.** 주보호자가 자기를 내보내는 길은 없고, 그 판정은 언제나
 * `currentUserId` 로 한다 — 이름으로 가르면 같은 이름을 쓰는 보호자에게 남의 버튼이 붙는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PetMemberManageTest {

    @get:Rule
    val compose = createComposeRule()

    private val owner = PetMember("u1", "아빠", isOwner = true)
    private val carer = PetMember("u2", "가연", isOwner = false)
    private val other = PetMember("u3", "이모", isOwner = false)
    private val members = listOf(owner, carer, other)

    private fun screen(
        isGroupOwner: Boolean,
        currentUserId: String,
        onRemove: ((PetMember) -> Unit)? = null,
        onLeave: (() -> Unit)? = null,
        onOpenInvites: (() -> Unit)? = null,
        actionError: String? = null,
        list: List<PetMember>? = members,
    ) {
        compose.setContent {
            PetMembersScreen(
                members = list,
                petName = "노을이",
                currentUserId = currentUserId,
                isGroupOwner = isGroupOwner,
                actionError = actionError,
                onOpenInvites = onOpenInvites,
                onRemove = onRemove,
                onLeave = onLeave,
            )
        }
    }

    // -- ① 줄의 내보내기 ----------------------------------------------------------

    @Test
    fun `주보호자에게만 다른 돌보미 행의 내보내기가 뜬다`() {
        screen(isGroupOwner = true, currentUserId = "u1", onRemove = {}, onOpenInvites = {})

        compose.onNodeWithTag("remove-u2").assertIsDisplayed()
        compose.onNodeWithTag("remove-u3").assertIsDisplayed()
    }

    @Test
    fun `주보호자 자기 행에는 내보내기가 없다`() {
        screen(isGroupOwner = true, currentUserId = "u1", onRemove = {}, onOpenInvites = {})

        compose.onAllNodesWithTag("remove-u1").assertCountEquals(0)
    }

    @Test
    fun `돌보미 행에는 어느 줄에도 내보내기가 없다`() {
        screen(isGroupOwner = false, currentUserId = "u2", onRemove = {}, onLeave = {})

        compose.onAllNodesWithText("내보내기").assertCountEquals(0)
    }

    /** 배선이 없으면(주보호자가 아닌 채로 들어온 경우) 버튼도 없다. */
    @Test
    fun `내보내기 배선이 없으면 버튼이 안 뜬다`() {
        screen(isGroupOwner = true, currentUserId = "u1", onRemove = null, onOpenInvites = {})

        compose.onAllNodesWithText("내보내기").assertCountEquals(0)
    }

    // -- ② 하단 버튼은 역할마다 하나 ------------------------------------------------

    @Test
    fun `주보호자에게는 새 돌보미 초대 하나만 뜬다`() {
        screen(isGroupOwner = true, currentUserId = "u1", onRemove = {}, onLeave = {}, onOpenInvites = {})

        compose.onNodeWithTag("open-invites").assertIsDisplayed()
        compose.onAllNodesWithTag("leave-co-care").assertCountEquals(0)
    }

    @Test
    fun `돌보미에게는 공동 돌봄 나가기 하나만 뜬다`() {
        screen(isGroupOwner = false, currentUserId = "u2", onLeave = {}, onOpenInvites = {})

        compose.onNodeWithTag("leave-co-care").assertIsDisplayed()
        compose.onAllNodesWithTag("open-invites").assertCountEquals(0)
    }

    /** 배선이 둘 다 와도 역할이 하나를 고른다 — 같이 뜰 수 있는 상태 자체를 안 만든다. */
    @Test
    fun `초대와 나가기가 동시에 뜨지 않는다`() {
        var groupOwner by mutableStateOf(true)
        compose.setContent {
            PetMembersScreen(
                members = members,
                petName = "노을이",
                currentUserId = if (groupOwner) "u1" else "u2",
                isGroupOwner = groupOwner,
                onOpenInvites = {},
                onLeave = {},
            )
        }

        fun bottomButtons() = compose.onAllNodesWithTag("open-invites").fetchSemanticsNodes().size +
            compose.onAllNodesWithTag("leave-co-care").fetchSemanticsNodes().size

        assertEquals("주보호자에게도 버튼은 하나다", 1, bottomButtons())
        groupOwner = false
        compose.waitForIdle()
        assertEquals("돌보미에게도 버튼은 하나다", 1, bottomButtons())
    }

    // -- ③ 확인 창 ----------------------------------------------------------------

    @Test
    fun `내보내기 확인 창이 대상과 잃는 것을 말한다`() {
        screen(isGroupOwner = true, currentUserId = "u1", onRemove = {}, onOpenInvites = {})

        compose.onNodeWithTag("remove-u2").performClick()

        compose.onNodeWithText("가연님을 공동 돌봄에서 내보낼까요?").assertIsDisplayed()
        compose.onNodeWithText("이후 노을이의 공동 기록을 볼 수 없어요.").assertIsDisplayed()
    }

    @Test
    fun `나가기 확인 창이 남는 기록을 말한다`() {
        screen(isGroupOwner = false, currentUserId = "u2", onLeave = {})

        compose.onNodeWithTag("leave-co-care").performClick()

        compose.onNodeWithText("노을이의 공동 돌봄에서 나갈까요?").assertIsDisplayed()
        compose.onNodeWithText(
            "다른 보호자의 기록과 이후 새 공동 기록은 볼 수 없어요. 내가 작성한 기록은 그대로 남아요.",
        ).assertIsDisplayed()
    }

    /** **취소는 아무것도 보내지 않는다.** 확인 창이 곧 요청이면 창을 둘 이유가 없다. */
    @Test
    fun `내보내기를 취소하면 요청이 안 나간다`() {
        var removed: PetMember? = null
        screen(isGroupOwner = true, currentUserId = "u1", onRemove = { removed = it }, onOpenInvites = {})

        compose.onNodeWithTag("remove-u2").performClick()
        compose.onNodeWithText("취소").performClick()

        assertNull(removed)
        compose.onAllNodesWithTag("remove-member-dialog").assertCountEquals(0)
    }

    @Test
    fun `나가기를 취소하면 요청이 안 나간다`() {
        var left = 0
        screen(isGroupOwner = false, currentUserId = "u2", onLeave = { left++ })

        compose.onNodeWithTag("leave-co-care").performClick()
        compose.onNodeWithText("취소").performClick()

        assertEquals(0, left)
        compose.onAllNodesWithTag("leave-co-care-dialog").assertCountEquals(0)
    }

    @Test
    fun `확인을 누르면 그 줄의 사람으로 내보낸다`() {
        var removed: PetMember? = null
        screen(isGroupOwner = true, currentUserId = "u1", onRemove = { removed = it }, onOpenInvites = {})

        compose.onNodeWithTag("remove-u3").performClick()
        compose.onNodeWithTag("remove-member-confirm").performClick()

        assertEquals(other, removed)
    }

    @Test
    fun `확인을 누르면 나간다`() {
        var left = 0
        screen(isGroupOwner = false, currentUserId = "u2", onLeave = { left++ })

        compose.onNodeWithTag("leave-co-care").performClick()
        compose.onNodeWithTag("leave-co-care-confirm").performClick()

        assertEquals(1, left)
    }

    // -- ④ 성공과 실패 -------------------------------------------------------------

    /** 성공하면 **이 화면에 그대로 있고** 새로 온 목록이 그려진다 — 나가기와 다른 점이다. */
    @Test
    fun `내보내기에 성공하면 목록이 제자리에서 갱신된다`() {
        var list by mutableStateOf(members)
        compose.setContent {
            PetMembersScreen(
                members = list,
                petName = "노을이",
                currentUserId = "u1",
                isGroupOwner = true,
                onOpenInvites = {},
                onRemove = { list = list - it },
            )
        }

        compose.onNodeWithTag("remove-u2").performClick()
        compose.onNodeWithTag("remove-member-confirm").performClick()

        compose.onAllNodesWithText("가연").assertCountEquals(0)
        compose.onNodeWithText("이모").assertIsDisplayed()
        compose.onNodeWithTag("pet-members").assertIsDisplayed()
    }

    /** 실패하면 **목록을 그대로 두고** 이유만 붙는다 — 다시 누르면 된다. */
    @Test
    fun `실패하면 목록이 남고 오류가 보인다`() {
        screen(
            isGroupOwner = true,
            currentUserId = "u1",
            onRemove = {},
            onOpenInvites = {},
            actionError = "주보호자만 다른 보호자를 내보낼 수 있습니다.",
        )

        compose.onNodeWithText("주보호자만 다른 보호자를 내보낼 수 있습니다.").assertIsDisplayed()
        compose.onNodeWithText("가연").assertIsDisplayed()
        compose.onNodeWithText("이모").assertIsDisplayed()
        compose.onNodeWithTag("remove-u2").assertIsDisplayed()
    }

    /** 목록 오류 자리와 다른 줄이다 — 섞으면 화면이 "목록을 못 불러왔다" 로 바뀐다. */
    @Test
    fun `동작 오류는 목록 오류 자리를 쓰지 않는다`() {
        screen(isGroupOwner = true, currentUserId = "u1", onRemove = {}, onOpenInvites = {}, actionError = "서버 오류 (500)")

        compose.onNodeWithTag("pet-members-action-error").assertIsDisplayed()
        compose.onAllNodesWithTag("pet-members-error").assertCountEquals(0)
    }

    // -- ⑤ 말 --------------------------------------------------------------------

    /**
     * **서버 사정이 화면에 새지 않는다.** 이 기능은 안에서 행 하나를 지우는 일이지만,
     * 사용자에게는 "함께 돌보기를 그만두는" 일이다.
     */
    @Test
    fun `기술 용어가 화면에 없다`() {
        screen(isGroupOwner = false, currentUserId = "u2", onLeave = {})
        compose.onNodeWithTag("leave-co-care").performClick()

        for (word in listOf("identity", "pet_id", "논리 연결", "연결만 끊기")) {
            compose.onAllNodesWithText(word, substring = true).assertCountEquals(0)
        }
    }
}
