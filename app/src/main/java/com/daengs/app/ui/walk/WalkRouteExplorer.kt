package com.daengs.app.ui.walk

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.daengs.app.walk.WalkSessionDetail
import com.daengs.app.walk.routeexplorer.CompletedRouteReview
import com.daengs.app.walk.routeexplorer.RouteExplorerIndex
import com.daengs.app.walk.routeexplorer.RoutePlaybackSpeed
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun walkRouteExplorerSaver(scope: CoroutineScope) = Saver<WalkRouteExplorerState, Any>(
    save = { listOf(it.selectedSceneId.orEmpty(), it.playbackSpeed.name, it.panelOpen) },
    restore = { saved ->
        // Accept the scene-only value saved by earlier app versions as well.
        val values = saved as? List<*>
        val id = (saved as? String) ?: (values?.getOrNull(0) as? String).orEmpty()
        WalkRouteExplorerState(scope, 0).apply {
            restoreSelection(if (id.isNotEmpty()) WalkRouteSelection.Scene(id) else WalkRouteSelection.Overview,
                values?.getOrNull(2) as? Boolean ?: false,
                RoutePlaybackSpeed.entries.firstOrNull { it.name == values?.getOrNull(1) } ?: RoutePlaybackSpeed.ONE)
        }
    },
)

@Composable
internal fun rememberWalkRouteExplorer(sessionId: String, detail: WalkSessionDetail?): WalkRouteExplorerState {
    val scope = rememberCoroutineScope()
    val state = rememberSaveable(sessionId, saver = walkRouteExplorerSaver(scope)) { WalkRouteExplorerState(scope, 0) }
    LaunchedEffect(detail, state) {
        if (detail != null) try {
            val (index, review) = withContext(Dispatchers.Default) {
                RouteExplorerIndex(detail.route) to CompletedRouteReview(detail)
            }
            state.replaceRoute(index, review, detail.summary.activeDurationMillis)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { state.preparationError = "동선 탐색을 준비하지 못했어요. 상세를 다시 열어 주세요." }
    }
    // Start a fresh interval at the displayed position when the speed changes. A pending old
    // interval must not be retroactively multiplied by the newly selected speed.
    LaunchedEffect(state, state.playing, state.playbackSpeed) {
        if (state.playing) {
            var previous = SystemClock.elapsedRealtime()
            while (isActive && state.playing) {
                delay(100)
                val now = SystemClock.elapsedRealtime()
                state.tick(now - previous); previous = now
            }
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, state) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) state.pause()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); state.pause() }
    }
    return state
}

private val ROUTE_EXPLORER_CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.KOREAN)

internal fun formatRouteExplorerClock(atMillis: Long): String =
    Instant.ofEpochMilli(atMillis).atZone(ZoneId.systemDefault()).format(ROUTE_EXPLORER_CLOCK)
