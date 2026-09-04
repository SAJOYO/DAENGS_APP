package com.daengs.app.ui.chat

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.daengs.app.chat.ChatSession
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecentChatsContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `알려진 capability 만 정확한 배지로 보이고 진단과 모르는 값은 숨긴다`() {
        compose.setContent {
            RecentSessionList(
                sessions = listOf(
                    ChatSession(
                        id = "session",
                        petId = "pet",
                        title = "섞인 대화",
                        agentCategories = listOf("training", "life", "walk", "skin", "gait", "new-agent"),
                        createdAtMs = 1,
                        lastMessageAtMs = 2,
                    ),
                ),
                selectedSessionId = null,
                onSelect = {},
                onDelete = {},
            )
        }

        compose.onNodeWithText("훈련").assertIsDisplayed()
        compose.onNodeWithText("제도").assertIsDisplayed()
        compose.onNodeWithText("산책").assertIsDisplayed()
        compose.onAllNodesWithText("skin").assertCountEquals(0)
        compose.onAllNodesWithText("gait").assertCountEquals(0)
        compose.onAllNodesWithText("new-agent").assertCountEquals(0)
    }
}
