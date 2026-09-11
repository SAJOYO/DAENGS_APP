package com.daengs.app.ui.walk

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.display.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MotionSpeedometerTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun checkStableBounds(fontScale: Float) {
        val value = mutableStateOf(MotionDisplay())
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                DaengsTheme { MotionSpeedometer(value.value) }
            }
        }
        val tags = listOf("motionSpeedometer", "motionSpeedReading", "motionSpeedSignal", "motionSpeedCaption", "motionSpeedDial")
        val bounds = tags.associateWith { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        val states = listOf(
            MotionDisplay(1.2, DisplayFreshness.LIVE, DisplaySignal.RECEIVING),
            MotionDisplay(1.2, DisplayFreshness.HELD, DisplaySignal.RECEIVING),
            MotionDisplay(1.2, DisplayFreshness.STALE, DisplaySignal.DELAYED),
            MotionDisplay(0.0, DisplayFreshness.LIVE, DisplaySignal.DELAYED),
            MotionDisplay(99.9, DisplayFreshness.LIVE, DisplaySignal.RECEIVING),
            MotionDisplay(9999.0, DisplayFreshness.LIVE, DisplaySignal.RECEIVING),
            MotionDisplay(99.9, DisplayFreshness.PAUSED, DisplaySignal.WAITING),
            MotionDisplay(99.9, DisplayFreshness.FINAL, DisplaySignal.WAITING),
        )
        for (state in states) {
            compose.runOnIdle { value.value = state }
            tags.forEach { assertEquals("$it at $state", bounds[it], compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot) }
            compose.onNodeWithText("GPS").assertIsDisplayed()
            compose.onNodeWithText("—").assertDoesNotExist()
            compose.onAllNodes(hasClickAction()).assertCountEquals(0)
        }
    }

    @Test fun allStatesKeepTheSameCardNumberLampAndCaptionBounds() = checkStableBounds(1f)
    @Test fun largeFontKeepsTheSameBoundsAcrossStates() = checkStableBounds(2f)

    @Test fun remountReadsTheSessionValueAndHeldStateDoesNotChangeVisibleStatus() {
        val epoch = DisplayEpoch(0, "source", "boot", 0)
        val owner = MotionDisplaySession("walk", epoch, 0)
        owner.onSamples(listOf(DisplaySpeedSample("walk", epoch, 0, 1.2)), 0)
        val mounted = mutableStateOf(true)
        val display = mutableStateOf(owner.display.value)
        compose.setContent { DaengsTheme { if (mounted.value) MotionSpeedometer(display.value) } }
        compose.runOnIdle { mounted.value = false }
        compose.onNodeWithTag("motionSpeedometer").assertDoesNotExist()
        owner.onTick(4_000_000_000L)
        compose.runOnIdle { display.value = owner.display.value; mounted.value = true }
        compose.onNodeWithText("1.2").assertIsDisplayed()
        compose.onNodeWithText("속도 측정 중").assertIsDisplayed()
        compose.onNodeWithTag("motionSpeedReading").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, "마지막 측정값 유지"))
    }
}
