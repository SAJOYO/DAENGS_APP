package com.daengs.app.ui.pet

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.daengs.app.pet.PetMember
import com.daengs.app.pet.isOwnedBy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 보호자 목록에서 초대 관리로 가는 자리.
 *
 * **대표 판정은 내 줄의 `isOwner` 로 한다** — 목록에 대표가 있는지만 보면 늘 참이라
 * 돌보미에게도 버튼이 뜬다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PetMembersInviteEntryTest {

    @get:Rule
    val compose = createComposeRule()

    private val owner = PetMember("owner", "아빠", isOwner = true)
    private val carer = PetMember("me", "나연", isOwner = false)
    private val members = listOf(owner, carer)

    private fun screen(currentUserId: String?, onOpen: () -> Unit = {}) {
        compose.setContent {
            PetMembersScreen(
                members = members,
                petName = "네옹",
                currentUserId = currentUserId,
                onOpenInvites = onOpen.takeIf { members.isOwnedBy(currentUserId) },
            )
        }
    }

    @Test
    fun `대표에게 초대 관리 자리가 뜬다`() {
        var opened = false
        screen(currentUserId = "owner", onOpen = { opened = true })

        compose.onNodeWithText("보호자 초대").assertIsDisplayed()
        compose.onNodeWithText("보호자 초대").performClick()

        assertTrue(opened)
    }

    @Test
    fun `돌보미에게는 안 뜬다`() {
        screen(currentUserId = "me")

        compose.onAllNodesWithText("보호자 초대").assertCountEquals(0)
    }

    @Test
    fun `로그인 정보가 없으면 안 뜬다`() {
        screen(currentUserId = null)

        compose.onAllNodesWithText("보호자 초대").assertCountEquals(0)
    }

    /** 목록에 대표가 "있다" 는 사실만으로 판정하면 돌보미가 통과한다. */
    @Test
    fun `대표 판정이 목록 존재가 아니라 내 줄을 본다`() {
        assertTrue(members.isOwnedBy("owner"))
        assertFalse(members.isOwnedBy("me"))
        assertFalse(members.isOwnedBy("stranger"))
        assertFalse(members.isOwnedBy(null))
    }
}
