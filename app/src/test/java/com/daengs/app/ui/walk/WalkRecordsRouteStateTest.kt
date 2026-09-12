package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.daengs.app.auth.AccountScope
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.walk.records.RetainedWalkRecords
import com.daengs.app.ui.walk.records.WalkRecordsRouteState
import com.daengs.app.ui.walk.records.rememberWalkRecordsRouteState
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WalkRecordsRouteStateTest {
    @get:Rule val compose = createComposeRule()
    private var currentRecordsInstance: Any? = null

    @Test fun `opening detail disposes records and restores their last saved values on return`() {
        compose.setContent { Harness(AccountScope("owner", 1)) }
        compose.onNodeWithText("조건 바꾸기").performClick()
        val before = currentRecordsInstance
        compose.onNodeWithText("상세 열기").performClick()
        compose.onNodeWithText("상세 walk-2").assertIsDisplayed()
        compose.onNodeWithText("page 2 · selected walk-2").assertDoesNotExist()
        compose.onNodeWithText("기록으로").performClick()
        compose.onNodeWithText("page 2 · selected walk-2").assertIsDisplayed()
        assertNotSame(before, currentRecordsInstance)
    }

    @Test fun `activity restoration reads active records and keeps their snapshot while detail is open`() {
        val restore = StateRestorationTester(compose)
        restore.setContent { Harness(AccountScope("owner", 1)) }
        compose.onNodeWithText("조건 바꾸기").performClick()
        // No explicit navigation capture occurred: the holder saver must read the active registry.
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("page 2 · selected walk-2").assertIsDisplayed()
        compose.onNodeWithText("상세 열기").performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("상세 walk-2").assertIsDisplayed()
        compose.onNodeWithText("기록으로").performClick()
        compose.onNodeWithText("page 2 · selected walk-2").assertIsDisplayed()
    }

    @Test fun `same owner relogin and another account both discard open detail and retained records`() {
        var scope by mutableStateOf(AccountScope("owner", 1))
        compose.setContent { Harness(scope) }
        compose.onNodeWithText("조건 바꾸기").performClick()
        compose.onNodeWithText("상세 열기").performClick()
        compose.runOnIdle { scope = AccountScope("owner", 2) }
        compose.onNodeWithText("상세 walk-2").assertDoesNotExist()
        compose.onNodeWithText("page 1 · selected none").assertIsDisplayed()
        compose.onNodeWithText("조건 바꾸기").performClick()
        compose.onNodeWithText("상세 열기").performClick()
        compose.runOnIdle { scope = AccountScope("other", 2) }
        compose.onNodeWithText("상세 walk-2").assertDoesNotExist()
        compose.onNodeWithText("page 1 · selected none").assertIsDisplayed()
    }

    @Test fun `restoration refuses saved values from the previous login scope`() {
        // Deliberately not snapshot state: switch the restore input without recomposing/saving a
        // new holder first. rememberSaveable's input-reset behavior alone cannot protect this case.
        var scope = AccountScope("owner", 1)
        val restore = StateRestorationTester(compose)
        restore.setContent { Harness(scope) }
        compose.onNodeWithText("조건 바꾸기").performClick()
        compose.onNodeWithText("상세 열기").performClick()
        compose.runOnIdle { scope = AccountScope("owner", 2) }
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("상세 walk-2").assertDoesNotExist()
        compose.onNodeWithText("page 1 · selected none").assertIsDisplayed()

        // A restarted process can have the same owner and generation zero. Only configuration
        // restoration in this process is accepted; a new process begins with clean UI state.
        val account = AccountScope("owner", 0)
        val original = WalkRecordsRouteState.initial(account).also { it.open("walk-2") }
        val oldProcessSaver = WalkRecordsRouteState.saver(account, processEpoch = "first-process")
        val canSave = object : SaverScope { override fun canBeSaved(value: Any) = true }
        val saved = with(oldProcessSaver) { requireNotNull(canSave.save(original)) }
        assertNotNull(oldProcessSaver.restore(saved))
        assertNull(WalkRecordsRouteState.saver(account, processEpoch = "new-process").restore(saved))
    }

    @Composable
    private fun Harness(scope: AccountScope) {
        val state = rememberWalkRecordsRouteState(scope)
        DaengsTheme {
            if (state.openedSessionId != null) {
                Column {
                    Text("상세 ${state.openedSessionId}")
                    TextButton(onClick = state::closeDetail) { Text("기록으로") }
                }
            } else RetainedWalkRecords(state) { Records(state) }
        }
    }

    @Composable
    private fun Records(state: WalkRecordsRouteState) {
        currentRecordsInstance = remember { Any() }
        var page by rememberSaveable { mutableIntStateOf(1) }
        var selected by rememberSaveable { mutableStateOf("none") }
        Column {
            Text("page $page · selected $selected")
            TextButton(onClick = { page = 2; selected = "walk-2" }) { Text("조건 바꾸기") }
            TextButton(onClick = { state.open(selected) }) { Text("상세 열기") }
        }
    }
}
