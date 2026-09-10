package com.daengs.app.ui.game

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.daengs.app.ui.theme.DaengsTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp")
class TerritoryGameRulesTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun emptyPoleWalkthroughShowsObjectActionsPhotoAndIncrementalPointsWithoutChangingOverview() {
        var exits = 0
        var maps = 0
        val dog = previewGamePet()
        compose.setContent { DaengsTheme {
            TerritoryGameScreen(previewGameOverview(), dog, listOf(dog), { null }, 0, false,
                { exits++ }, { maps++ }, {}, {})
        } }
        compose.onNodeWithText("회원·시즌별 100점", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("game-rules-open").performClick()
        compose.onNodeWithTag("game-guide-previous").assertIsNotEnabled()
        compose.onNodeWithTag("game-guide-next").performClick()
        screenshot("game-rules-select")
        compose.onNodeWithContentDescription("예시 전봇대 선택").performScrollTo().performClick()
        compose.onNodeWithTag("game-guide-next").assertTextContains("영역표시").performClick()
        compose.onNodeWithText("두부  ·  기본 +20점").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("game-guide-next").assertTextContains("영역표시 인증 촬영").performClick()
        compose.onNodeWithContentDescription("촬영 구도 예시: 해당 위치에서 두부가 잘 보이게 촬영해요").assertExists()
        compose.onNodeWithTag("game-guide-pole").assertDoesNotExist()
        screenshot("game-rules-photo")
        compose.onNodeWithTag("game-guide-next").assertTextContains("촬영하고 산책 계속").performClick()
        compose.onNodeWithText("두부  ·  20 + 80 = 기본 100점").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("game-guide-previous").performClick()
        compose.onNodeWithText("촬영 구도 예시").assertIsDisplayed()
        compose.onNodeWithTag("game-guide-next").performClick()
        compose.onNodeWithTag("game-guide-next").assertTextContains("처음부터 다시 보기").performClick()
        compose.onNodeWithTag("game-guide-previous").assertIsNotEnabled()
        compose.onNodeWithTag("game-rules-close").performClick()
        compose.onNodeWithText("1,240 점").assertIsDisplayed()
        assertEquals(0, exits)
        assertEquals(0, maps)
        compose.onNodeWithTag("game-rules-open").performClick()
        compose.runOnIdle { ShadowDialog.getLatestDialog().onBackPressed() }
        compose.onNodeWithTag("game-rules-dialog").assertDoesNotExist()
        compose.onNodeWithText("1,240 점").assertIsDisplayed()
        assertEquals(0, exits)
    }

    @Test fun takeoverUsesPhotoBeforeOwnershipChangesAndExplainsConditional120Points() {
        compose.setContent { DaengsTheme { TerritoryGameRulesDialog({}) } }
        compose.onNodeWithTag("game-guide-TAKEOVER").performClick()
        compose.onNodeWithTag("game-guide-owner").assertTextEquals("초코의 전봇대")
        compose.onNodeWithTag("game-guide-next").performClick()
        compose.onNodeWithTag("game-guide-pole").performScrollTo().performClick()
        compose.onNodeWithTag("game-guide-next").assertTextContains("영역표시 인증 촬영").performClick()
        compose.onNodeWithTag("game-guide-owner").assertTextEquals("초코의 전봇대")
        compose.onNodeWithTag("game-guide-next").performClick()
        compose.onNodeWithTag("game-guide-owner").assertTextEquals("두부의 전봇대")
        compose.onNodeWithText("초코 → 두부  ·  기본 100 + 탈취 20점").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("기본 20점을 이미 받았다면", substring = true).performScrollTo().assertIsDisplayed()
        // Rewind to the illustration for a reviewable screenshot, without changing example state.
        compose.onNodeWithTag("game-rules-body").performTouchInput { swipeDown() }
        screenshot("game-rules-takeover")
        compose.onNodeWithTag("game-guide-EMPTY").performScrollTo().performClick()
        compose.onNodeWithTag("game-guide-owner").assertTextEquals("아직 주인이 없어요")
        compose.onNodeWithTag("game-guide-previous").assertIsNotEnabled()
    }

    @Test fun openDialogTopicAndExampleStepRestoreTogether() {
        val restoration = StateRestorationTester(compose)
        val dog = previewGamePet()
        restoration.setContent { DaengsTheme {
            TerritoryGameScreen(previewGameOverview(), dog, listOf(dog), { null }, 0, false, {}, {}, {}, {})
        } }
        compose.onNodeWithTag("game-rules-open").performClick()
        compose.onNodeWithTag("game-guide-TAKEOVER").performClick()
        repeat(3) { compose.onNodeWithTag("game-guide-next").performClick() }
        compose.onNodeWithTag("game-rules-tab-1").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("game-rules-tab-1").assertIsSelected()
        compose.onNodeWithTag("game-rules-tab-0").performClick()
        compose.onNodeWithTag("game-guide-next").assertTextContains("촬영하고 산책 계속")
        compose.onNodeWithTag("game-guide-owner").assertTextEquals("초코의 전봇대")
        compose.onNodeWithTag("game-rules-close").performClick()
        compose.onNodeWithText("1,240 점").assertIsDisplayed()
    }

    private fun screenshot(name: String) {
        val output = java.io.File("build/reports/territory-game/$name.png")
        checkNotNull(output.parentFile).mkdirs()
        compose.runOnIdle {
            val view = checkNotNull(ShadowDialog.getLatestDialog().window).decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            output.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
