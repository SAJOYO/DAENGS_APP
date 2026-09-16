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
 * 보호자 목록 아래의 「새 돌보미 초대」.
 *
 * **주보호자 판정은 내 줄로 한다** — 목록에 주보호자가 있는지만 보면 늘 참이라
 * 돌보미에게도 버튼이 뜬다. 실제 배선은 `Pet.isGroupOwner` 를 넘기고
 * ([isOwnedBy] 는 목록만 있는 자리의 같은 판정이다), 화면은 그 값으로 가른다.
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
                isGroupOwner = members.isOwnedBy(currentUserId),
                onOpenInvites = onOpen,
            )
        }
    }

    @Test
    fun `주보호자에게 초대 자리가 뜬다`() {
        var opened = false
        screen(currentUserId = "owner", onOpen = { opened = true })

        compose.onNodeWithText("새 돌보미 초대").assertIsDisplayed()
        compose.onNodeWithText("새 돌보미 초대").performClick()

        assertTrue(opened)
    }

    @Test
    fun `돌보미에게는 안 뜬다`() {
        screen(currentUserId = "me")

        compose.onAllNodesWithText("새 돌보미 초대").assertCountEquals(0)
    }

    @Test
    fun `로그인 정보가 없으면 안 뜬다`() {
        screen(currentUserId = null)

        compose.onAllNodesWithText("새 돌보미 초대").assertCountEquals(0)
    }

    /** 목록에 주보호자가 "있다" 는 사실만으로 판정하면 돌보미가 통과한다. */
    @Test
    fun `주보호자 판정이 목록 존재가 아니라 내 줄을 본다`() {
        assertTrue(members.isOwnedBy("owner"))
        assertFalse(members.isOwnedBy("me"))
        assertFalse(members.isOwnedBy("stranger"))
        assertFalse(members.isOwnedBy(null))
    }
}
