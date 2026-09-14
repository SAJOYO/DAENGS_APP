package com.daengs.app.ui.walk

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.daengs.app.walk.routeexplorer.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WalkRouteExplorerLifecycleTest {
    @get:Rule val compose = createComposeRule()

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private fun WalkRouteExplorerState.startReplay() {
        activeDuration = 120_000
        index = RouteExplorerIndex(explorerRoute(straightExplorerPath()))
        seek(5_000)
        choosePlaybackSpeed(RoutePlaybackSpeed.EIGHT)
        togglePlayback()
        assertTrue(playing)
    }

    @Test fun `pause and stop retain cursor and speed and resume never starts playback`() {
        val owner = Owner()
        lateinit var state: WalkRouteExplorerState
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                state = rememberWalkRouteExplorer("lifecycle", null)
            }
        }
        compose.runOnIdle {
            owner.registry.currentState = Lifecycle.State.RESUMED
            state.startReplay()
            owner.registry.currentState = Lifecycle.State.STARTED
            assertFalse(state.playing)
            assertEquals(5_000L, state.elapsed)
            assertEquals(RoutePlaybackSpeed.EIGHT, state.playbackSpeed)

            // STARTED -> CREATED emits STOP alone, so this also checks the stop guard.
            state.togglePlayback()
            assertTrue(state.playing)
            owner.registry.currentState = Lifecycle.State.CREATED
            assertFalse(state.playing)
            assertEquals(5_000L, state.elapsed)
            owner.registry.currentState = Lifecycle.State.RESUMED
            assertFalse(state.playing)
            assertEquals(RoutePlaybackSpeed.EIGHT, state.playbackSpeed)
        }
    }

    @Test fun `changing lifecycle owner pauses the same state and releases the previous observer`() {
        val first = Owner()
        val second = Owner()
        var owner by mutableStateOf(first)
        lateinit var state: WalkRouteExplorerState
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                state = rememberWalkRouteExplorer("owner-change", null)
            }
        }
        val original = state
        compose.runOnIdle {
            first.registry.currentState = Lifecycle.State.RESUMED
            second.registry.currentState = Lifecycle.State.RESUMED
            state.startReplay()
            assertEquals(1, first.registry.observerCount)
            owner = second
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertSame(original, state)
            assertFalse(state.playing)
            assertEquals(0, first.registry.observerCount)
            assertEquals(1, second.registry.observerCount)
            state.togglePlayback()
            first.registry.currentState = Lifecycle.State.STARTED
            assertTrue(state.playing)
            second.registry.currentState = Lifecycle.State.STARTED
            assertFalse(state.playing)
        }
    }

    @Test fun `leaving composition pauses playback removes observer and reentry creates idle state`() {
        val owner = Owner()
        var mounted by mutableStateOf(true)
        lateinit var state: WalkRouteExplorerState
        compose.setContent {
            if (mounted) CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                state = rememberWalkRouteExplorer("reentry", null)
            }
        }
        val original = state
        compose.runOnIdle {
            owner.registry.currentState = Lifecycle.State.RESUMED
            state.startReplay()
            mounted = false
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertFalse(original.playing)
            assertEquals(0, owner.registry.observerCount)
            mounted = true
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertNotSame(original, state)
            assertFalse(state.playing)
            assertEquals(WalkRouteSelection.Overview, state.selection)
            assertEquals(1, owner.registry.observerCount)
        }
    }
}
