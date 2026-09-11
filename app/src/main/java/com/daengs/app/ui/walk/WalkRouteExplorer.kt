package com.daengs.app.ui.walk

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.daengs.app.location.GeoPoint
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.WalkSessionRoute
import com.daengs.app.walk.routeexplorer.*
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class RouteExplorerMode { OVERVIEW, PASSAGE, REPLAY }

@Stable
internal class WalkRouteExplorerState(private val scope: CoroutineScope, private val activeDuration: Long,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default) {
    var index by mutableStateOf<RouteExplorerIndex?>(null)
    var panelOpen by mutableStateOf(false)
        private set
    var mode by mutableStateOf(RouteExplorerMode.OVERVIEW)
        private set
    var passages by mutableStateOf<RoutePassages?>(null)
        private set
    var selectedPassId by mutableStateOf<String?>(null)
        private set
    var analyzing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var playing by mutableStateOf(false)
        private set
    var elapsed by mutableLongStateOf(0)
        private set
    private var selectionJob: Job? = null
    private var selectionRevision = 0
    val duration get() = maxOf(activeDuration, index?.durationMillis ?: 0)
    val selectedPass get() = passages?.passes?.firstOrNull { it.id == selectedPassId }
    val replayFrame get() = if (mode == RouteExplorerMode.REPLAY) index?.frameAt(elapsed) else null

    fun choosePanel(open: Boolean) {
        panelOpen = open
        if (!open) overview()
    }
    fun pause() { playing = false }
    fun overview() {
        selectionRevision++; selectionJob?.cancel(); analyzing = false; playing = false
        passages = null; selectedPassId = null; mode = RouteExplorerMode.OVERVIEW
    }
    fun selectPass(id: String) {
        if (passages?.passes?.any { it.id == id } != true) return
        pause(); selectedPassId = id; mode = RouteExplorerMode.PASSAGE
    }
    fun inspect(point: GeoPoint) {
        val source = index ?: return
        val revision = ++selectionRevision
        selectionJob?.cancel(); pause(); panelOpen = true; mode = RouteExplorerMode.PASSAGE
        passages = null; selectedPassId = null; analyzing = true; error = null
        selectionJob = scope.launch {
            try {
                val result = withContext(computeDispatcher) { source.passagesAt(point) }
                if (index === source && revision == selectionRevision) {
                    passages = result; selectedPassId = result.passes.firstOrNull()?.id
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "이 지점의 통과 구간을 확인하지 못했어요." }
            finally { if (revision == selectionRevision) analyzing = false }
        }
    }
    fun seek(value: Long) {
        selectionRevision++; selectionJob?.cancel(); analyzing = false; pause(); mode = RouteExplorerMode.REPLAY
        passages = null; selectedPassId = null; elapsed = value.coerceIn(0, duration)
    }
    fun togglePlayback() {
        if (index == null || duration <= 0) return
        if (playing) pause() else {
            seek(if (elapsed >= duration) 0 else elapsed)
            playing = true
        }
    }
    fun tick(delta: Long) {
        if (!playing) return
        elapsed = advanceRoutePlayback(elapsed, delta, duration)
        if (elapsed >= duration) playing = false
    }
}

@Composable
internal fun rememberWalkRouteExplorer(route: WalkSessionRoute?, activeDuration: Long): WalkRouteExplorerState {
    val scope = rememberCoroutineScope()
    val state = remember(route, activeDuration) { WalkRouteExplorerState(scope, activeDuration) }
    LaunchedEffect(route, state) {
        if (route != null) try {
            state.index = withContext(Dispatchers.Default) { RouteExplorerIndex(route) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { state.error = "동선 탐색을 준비하지 못했어요. 상세를 다시 열어 주세요." }
    }
    LaunchedEffect(state, state.playing) {
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
        onDispose { lifecycle.removeObserver(observer); state.overview() }
    }
    return state
}

@Composable
internal fun WalkRouteExplorerPanel(state: WalkRouteExplorerState, onOverview: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { state.overview(); onOverview() }) { Text("전체 동선") }
            Button(onClick = state::togglePlayback, enabled = state.index != null && state.duration > 0) {
                Text(if (state.playing) "일시정지" else "동선 재생")
            }
        }
        Spacer(Modifier.height(4.dp))
        state.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (state.index == null && state.error == null) Text("동선 탐색을 준비하고 있어요.")
        else if (state.analyzing) Text("선택한 길을 지난 시각을 확인하고 있어요.")
        else when (state.mode) {
            RouteExplorerMode.OVERVIEW -> Text("지도에서 겹친 길을 누르면 통과 시각을 골라 볼 수 있어요.",
                style = MaterialTheme.typography.bodyMedium)
            RouteExplorerMode.PASSAGE -> {
                val result = state.passages
                if (result?.uncertain == true) Text("위치 오차나 기록 간격 때문에 통과를 확실하게 구분하기 어려워요.")
                else if (result?.passes.isNullOrEmpty()) Text("이 지점에서 길게 이어진 통과 구간을 찾지 못했어요.")
                else {
                    Text("선택한 길 · " + result!!.passes.size + "회 통과", style = MaterialTheme.typography.titleSmall)
                    result.passes.forEachIndexed { index, pass ->
                        TextButton(onClick = { state.selectPass(pass.id) }, modifier = Modifier.fillMaxWidth()) {
                            Text((if (pass.id == state.selectedPassId) "● " else "○ ") + (index + 1) + "번째 통과 · " +
                                formatRouteExplorerClock(pass.startedAtMillis) + "–" + formatRouteExplorerClock(pass.endedAtMillis))
                        }
                    }
                }
            }
            RouteExplorerMode.REPLAY -> {
                Slider(value = state.elapsed.toFloat(), onValueChange = { state.seek(it.toLong()) },
                    valueRange = 0f..state.duration.coerceAtLeast(1).toFloat())
                Text(formatWalkDuration(state.elapsed) + " / " + formatWalkDuration(state.duration),
                    style = MaterialTheme.typography.titleSmall)
                val frame = state.replayFrame
                Text(if (frame?.inGap != false) "이 시각에는 이어지는 위치 기록이 없어요."
                    else "기록 시각 " + formatRouteExplorerClock(requireNotNull(frame.recordedAtMillis)),
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (state.mode != RouteExplorerMode.REPLAY) {
            Spacer(Modifier.height(8.dp))
            WalkSpeedLegend()
        }
        Spacer(Modifier.height(20.dp))
    }
}

private val ROUTE_EXPLORER_CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.KOREAN)

private fun formatRouteExplorerClock(atMillis: Long): String =
    Instant.ofEpochMilli(atMillis).atZone(ZoneId.systemDefault()).format(ROUTE_EXPLORER_CLOCK)

@Preview(showBackground = true, widthDp = 360, heightDp = 280)
@Composable
private fun RouteExplorerPanelPreview() {
    val scope = rememberCoroutineScope()
    DaengsTheme { WalkRouteExplorerPanel(remember { WalkRouteExplorerState(scope, 1_800_000) }, {}) }
}
