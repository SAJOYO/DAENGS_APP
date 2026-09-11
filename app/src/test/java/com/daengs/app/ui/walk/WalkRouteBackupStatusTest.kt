package com.daengs.app.ui.walk

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.sync.WalkRouteBackupState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w320dp-h640dp")
class WalkRouteBackupStatusTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `request acknowledgement does not show backup complete and blocks duplicate taps`() {
        var state by mutableStateOf(WalkRouteBackupState.NEEDS_RETRY)
        var requested by mutableStateOf(false)
        var requests = 0
        compose.setContent { DaengsTheme { WalkRouteBackupIcon(state, requested = requested, onRequest = { requests++; requested = true }) } }
        compose.onNodeWithContentDescription("경로 백업 오류 · 다시 전송").assertIsDisplayed()
        compose.onNodeWithTag("route-backup-request").performClick().assertIsNotEnabled()
        compose.onNodeWithText("경로 백업 완료").assertDoesNotExist()
        compose.onNodeWithContentDescription("경로 백업 재전송 요청됨").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, requests); state = WalkRouteBackupState.COMPLETE }
        compose.onNodeWithTag("route-backup-request").assertDoesNotExist()
    }

    @Test fun `normal states take no space and failed request remains retryable`() {
        var state by mutableStateOf(WalkRouteBackupState.PENDING)
        var retries = 0
        compose.setContent { DaengsTheme {
            WalkRouteBackupIcon(state, error = true, onRequest = { retries++ })
        } }
        for (normal in listOf(WalkRouteBackupState.PENDING, WalkRouteBackupState.CHECKING, WalkRouteBackupState.COMPLETE)) {
            compose.runOnIdle { state = normal }
            compose.onNodeWithTag("route-backup-request").assertDoesNotExist()
            compose.onNodeWithTag("route-backup-status").assertDoesNotExist()
        }
        compose.runOnIdle { state = WalkRouteBackupState.NEEDS_RETRY }
        compose.onNodeWithContentDescription("경로 백업 요청 실패 · 다시 전송")
            .assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithText("경로 백업을 다시 확인해야 해요").assertDoesNotExist()
    }
}
