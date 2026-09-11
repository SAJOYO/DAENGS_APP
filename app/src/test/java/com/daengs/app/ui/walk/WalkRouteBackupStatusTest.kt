package com.daengs.app.ui.walk

import android.app.Application
import androidx.compose.foundation.layout.Column
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
        var state by mutableStateOf(WalkRouteBackupState.PENDING)
        var requested by mutableStateOf(false)
        var requests = 0
        compose.setContent { DaengsTheme { WalkRouteBackupNotice(state, requested = requested, onRequest = { requests++; requested = true }) } }
        compose.onNodeWithText("경로 백업 대기").assertIsDisplayed()
        compose.onNodeWithTag("route-backup-request").performClick().assertIsNotEnabled()
        compose.onNodeWithText("경로 백업 완료").assertDoesNotExist()
        compose.onNodeWithText("전송을 요청했어요. 연결되면 다시 시도해요.").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, requests); state = WalkRouteBackupState.COMPLETE }
        compose.onNodeWithText("경로 백업 완료").assertIsDisplayed()
        compose.onNodeWithTag("route-backup-request").assertDoesNotExist()
    }

    @Test fun `checking and failure tell the user local route remains saved`() {
        compose.setContent { DaengsTheme { Column {
            WalkRouteBackupNotice(WalkRouteBackupState.CHECKING, onRequest = {})
            WalkRouteBackupNotice(WalkRouteBackupState.NEEDS_RETRY, error = true, onRequest = {})
        } } }
        compose.onNodeWithText("경로 백업 확인 중").assertIsDisplayed()
        compose.onNodeWithText("경로는 기기에 저장되어 있어요.").assertIsDisplayed()
        compose.onNodeWithText("전송을 요청하지 못했어요. 다시 시도해 주세요.").assertIsDisplayed()
    }
}
