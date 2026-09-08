package com.daengs.app.ui.walk

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.daengs.app.ui.theme.DaengsTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WalkSpeedometerTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun captureDial(): android.graphics.Bitmap {
        val bounds = compose.onNodeWithTag("speedDial").fetchSemanticsNode().boundsInWindow
        return compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            android.graphics.Bitmap.createBitmap(bitmap, bounds.left.toInt(), bounds.top.toInt(),
                bounds.width.toInt(), bounds.height.toInt()).also { bitmap.recycle() }
        }
    }

    @Test fun missingFixKeepsReadingNeedleAndLayoutUntilRecovery() {
        val speed = mutableStateOf<Float?>(1.2f)
        compose.setContent { DaengsTheme { WalkSpeedometer(speed.value) } }
        val before = compose.onNodeWithTag("speedReading").fetchSemanticsNode().boundsInRoot
        val lampBefore = compose.onNodeWithTag("speedSignalLamp").fetchSemanticsNode().boundsInRoot
        val dialBefore = captureDial()
        compose.runOnIdle { speed.value = null }
        compose.onNodeWithText("1.2").assertIsDisplayed()
        compose.onNodeWithText("m/s").assertIsDisplayed()
        compose.onNodeWithText("—").assertDoesNotExist()
        compose.onNodeWithTag("speedSignalLamp").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, "속도 신호 대기. 마지막 수신 값 유지"))
        assertEquals(before, compose.onNodeWithTag("speedReading").fetchSemanticsNode().boundsInRoot)
        assertEquals(lampBefore, compose.onNodeWithTag("speedSignalLamp").fetchSemanticsNode().boundsInRoot)
        assertTrue(dialBefore.sameAs(captureDial()))
        compose.mainClock.advanceTimeBy(2000)
        compose.onNodeWithText("1.2").assertIsDisplayed()
        compose.runOnIdle { speed.value = .8f }
        compose.onNodeWithText("0.8").assertIsDisplayed()
        compose.onNodeWithTag("speedSignalLamp").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, "현재 수신 속도"))
    }

    @Test fun firstFixAndRealZeroKeepTheSameInstrumentVisible() {
        val speed = mutableStateOf<Float?>(null)
        compose.setContent { DaengsTheme { WalkSpeedometer(speed.value) } }
        compose.onNodeWithText("0.0").assertIsDisplayed()
        compose.onNodeWithText("m/s").assertIsDisplayed()
        compose.onNodeWithTag("speedSignalLamp").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, "속도 신호 대기. 측정 전 초기값"))
        compose.runOnIdle { speed.value = 0f }
        compose.onNodeWithText("0.0").assertIsDisplayed()
        compose.onNodeWithTag("speedSignalLamp").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, "현재 수신 속도"))
    }
}
