package com.daengs.app.ui.walk

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.saveable.SaverScope
import com.daengs.app.auth.AccountScope
import com.daengs.app.walk.WalkTrackingState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WalkSessionDestinationTest {
    @get:Rule val compose = createComposeRule()
    private val account = AccountScope("owner", 4)
    private val completed = WalkTrackingState(ownerId = "owner", completedSessionId = "walk")

    @Test fun `completion replaces live content and survives acknowledgement without double opening`() {
        val state = WalkSessionDestination(account)
        var tracking by mutableStateOf(WalkTrackingState(ownerId = "owner", activeSessionId = "walk"))
        var liveMounts = 0
        var liveDisposals = 0
        var acknowledgements = 0
        compose.setContent {
            WalkSessionCompletionGate(state, tracking, {
                acknowledgements++; tracking = WalkTrackingState()
            }, {}, { id, _ -> Text("저장된 $id") }, {
                DisposableEffect(Unit) { liveMounts++; onDispose { liveDisposals++ } }
                Text("실시간 산책")
            })
        }
        compose.runOnIdle { tracking = completed.copy(finishingSessionId = "walk") }
        compose.onNodeWithText("실시간 산책").assertExists()
        compose.runOnIdle { tracking = completed }
        compose.onNodeWithText("저장된 walk").assertExists()
        compose.onNodeWithText("실시간 산책").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, acknowledgements)
            assertEquals(1, liveMounts)
            assertEquals(1, liveDisposals)
            assertEquals("walk", state.sessionId)
        }
    }

    @Test fun `entering a pending completed walk never mounts permission or live subtree`() {
        val destination = WalkSessionDestination(account)
        compose.setContent {
            WalkSessionCompletionGate(destination, completed, {}, {},
                { _, _ -> Text("상세") }, { error("live subtree must not be mounted") })
        }
        compose.onNodeWithText("상세").assertExists()
    }

    @Test fun `another owner discarded walk and late previous completion cannot open detail`() {
        val state = WalkSessionDestination(account)
        assertNull(state.destination(completed.copy(ownerId = "other")))
        state.accept(WalkTrackingState(ownerId = "owner", activeSessionId = "new"))
        assertNull(state.destination(completed))
        assertNull(state.destination(WalkTrackingState(ownerId = "owner", errorMessage = "폐기")))
        assertNull(state.destination(completed.copy(activeSessionId = "new")))
        assertEquals("new", state.destination(completed.copy(completedSessionId = "new")))
    }

    @Test fun `saved destination restores only within the same login and process`() {
        val state = WalkSessionDestination(account).apply { accept(completed) }
        val saver = WalkSessionDestination.saver(account, "process")
        val canSave = object : SaverScope { override fun canBeSaved(value: Any) = true }
        val saved = with(saver) { canSave.save(state) }!!
        assertEquals("walk", saver.restore(saved)!!.sessionId)
        assertNull(WalkSessionDestination.saver(account.copy(generation = 5), "process").restore(saved))
        assertNull(WalkSessionDestination.saver(account.copy(ownerId = "other"), "process").restore(saved))
        assertNull(WalkSessionDestination.saver(account, "restarted").restore(saved))
        state.close()
        assertNull(state.destination(WalkTrackingState()))
    }
}
