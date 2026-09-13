package com.daengs.app.ui.walk

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.daengs.app.ui.theme.DaengsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WalkRecordsLabNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `walk card opens the production session diary and returns to the same list`() {
        compose.setContent { DaengsTheme { CompositionLocalProvider(LocalInspectionMode provides true) {
            WalkRecordsLab()
        } } }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("records-walk-sample-record-1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("records-walk-sample-record-1").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("일기 메뉴").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("산책 지도 설정").assertExists()
        compose.onNodeWithContentDescription("산책 목록으로").assertExists()
        compose.onNodeWithTag("records-layer-diagnostics").assertDoesNotExist()
        compose.onNodeWithText("목록으로").assertDoesNotExist()
        compose.waitUntil(10000) { compose.onAllNodesWithText("산책 시작").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("산책 시작").performClick()
        compose.onNodeWithContentDescription("산책 목록으로").performClick()
        compose.onNodeWithTag("records-view-walks").assertIsSelected()
        compose.onNodeWithTag("records-walk-sample-record-1").assertExists()
    }
}
