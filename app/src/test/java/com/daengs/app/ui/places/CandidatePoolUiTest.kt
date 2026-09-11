package com.daengs.app.ui.places

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.daengs.app.place.*
import com.daengs.app.place.support.conversationFixture
import com.daengs.app.ui.theme.DaengsTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp")
class CandidatePoolUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun currentPoolIsVisibleAndAllPlacesUsesAnExplicitRevisionBoundAction() {
        val result = conversationFixture("manual").toConversationResult().copy(searchPool = "new_candidates")
        var edit: ConversationFilterEdit? = null
        compose.setContent {
            DaengsTheme {
                ConversationFiltersDialog(ConversationUiState(result = result), { edit = it }, {})
            }
        }
        compose.onNodeWithText("검색 대상: 새 후보").assertExists()
        compose.onNodeWithText("전체 장소로 전환").performClick()
        compose.runOnIdle {
            assertEquals(result.sessionId, edit!!.sessionId)
            assertEquals(result.revision, edit!!.revision)
            assertEquals("all_places", edit!!.searchPool)
            assertTrue(edit!!.removeAll.isEmpty() && edit!!.removeAny.isEmpty())
        }
    }
}
