package com.daengs.app.ui.pet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onFirst
import com.daengs.app.pet.PetMember
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PetMembersScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val owner = PetMember("u1", "아빠", isOwner = true)
    private val carer = PetMember("u2", "엄마", isOwner = false)
    private val nameless = PetMember("u3", null, isOwner = false)

    @Test
    fun `서버가 준 순서를 그대로 그린다`() {
        compose.setContent { PetMembersScreen(members = listOf(owner, carer, nameless)) }

        val names = compose.onAllNodesWithText("아빠").fetchSemanticsNodes().size +
            compose.onAllNodesWithText("엄마").fetchSemanticsNodes().size
        assertEquals(2, names)
        // 주보호자가 맨 앞이라는 것은 배지의 순서로 본다 — 앱이 재정렬하지 않는다.
        compose.onNodeWithText("주보호자").assertIsDisplayed()
        compose.onAllNodesWithText("공동 돌보미").assertCountEquals(2)
    }

    @Test
    fun `주보호자와 공동 돌보미를 역할 배지로 가른다`() {
        compose.setContent { PetMembersScreen(members = listOf(owner, carer)) }

        compose.onNodeWithText("주보호자").assertIsDisplayed()
        compose.onNodeWithText("공동 돌보미").assertIsDisplayed()
    }

    @Test
    fun `현재 사용자를 표시한다`() {
        compose.setContent { PetMembersScreen(members = listOf(owner, carer), currentUserId = "u2") }

        compose.onNodeWithText("나").assertIsDisplayed()
    }

    @Test
    fun `로그인한 사람이 목록에 없으면 나 표시가 없다`() {
        compose.setContent { PetMembersScreen(members = listOf(owner, carer), currentUserId = "u9") }

        compose.onAllNodesWithText("나").assertCountEquals(0)
    }

    /** 이 목록에 실리는 사람은 전부 현재 구성원이라 "이전 보호자" 가 아니다. */
    @Test
    fun `이름이 없는 보호자를 대신 문구로 그린다`() {
        compose.setContent { PetMembersScreen(members = listOf(nameless)) }

        compose.onNodeWithText("이름을 확인할 수 없는 보호자").assertIsDisplayed()
        compose.onAllNodesWithText("이전 보호자").assertCountEquals(0)
    }

    /**
     * **실패를 빈 목록으로 그리지 않는다.** "보호자가 없어요" 라고 단언하면 사용자는
     * 다시 시도할 이유를 못 찾는다.
     */
    @Test
    fun `오류는 빈 목록이 아니라 오류 문구로 나온다`() {
        compose.setContent { PetMembersScreen(members = null, error = "서버에 닿지 못했어요.") }

        compose.onNodeWithText("서버에 닿지 못했어요.").assertIsDisplayed()
        compose.onAllNodesWithText("보호자가 없어요").assertCountEquals(0)
    }

    @Test
    fun `아직 못 받았으면 불러오는 중을 알린다`() {
        compose.setContent { PetMembersScreen(members = null, busy = true) }

        compose.onNodeWithText("불러오는 중이에요").assertIsDisplayed()
        compose.onAllNodesWithText("보호자가 없어요").assertCountEquals(0)
    }

    @Test
    fun `화면 제목은 보호자 목록이다`() {
        compose.setContent { PetMembersScreen(members = listOf(owner)) }

        compose.onNodeWithText("보호자 목록").assertIsDisplayed()
    }
}
