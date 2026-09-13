package com.daengs.app.ui.walk

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.daengs.app.location.GeoPoint
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.*
import com.daengs.app.walk.routeexplorer.*
import com.daengs.app.walk.trajectory.ObservedRouteSection
import com.daengs.app.walk.trajectory.RecordContext
import com.daengs.app.walk.trajectory.RecordContextKind
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class RouteExplorerMode { OVERVIEW, SECTION, AUXILIARY, SCENE, PASSAGE, REPLAY, CONTEXT, SLICE }

/** Exactly one selection owns the map emphasis. Camera requests remain separate user actions. */
internal sealed interface WalkRouteSelection {
    data object Overview : WalkRouteSelection
    data class Section(val index: Int) : WalkRouteSelection
    data class Auxiliary(val id: String) : WalkRouteSelection
    data class Gap(val id: String) : WalkRouteSelection
    data class Transition(val id: String) : WalkRouteSelection
    data class Event(val id: String) : WalkRouteSelection
    data class Scene(val id: String) : WalkRouteSelection
    data class Passage(val result: RoutePassages?, val selectedId: String?) : WalkRouteSelection
    data class Replay(val elapsedMillis: Long) : WalkRouteSelection
    data class Slice(val from: MeasurementTimeAddress, val until: MeasurementTimeAddress) : WalkRouteSelection
}

@Stable
internal class WalkRouteExplorerState(private val scope: CoroutineScope, activeDuration: Long,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default) {
    var activeDuration by mutableLongStateOf(activeDuration)
    var index by mutableStateOf<RouteExplorerIndex?>(null)
    var review by mutableStateOf<CompletedRouteReview?>(null)
    var panelOpen by mutableStateOf(false)
        private set
    var selection by mutableStateOf<WalkRouteSelection>(WalkRouteSelection.Overview)
        private set
    var selectionFromMap by mutableStateOf(false)
        private set
    var analyzing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var preparationError by mutableStateOf<String?>(null)
    var playing by mutableStateOf(false)
        private set
    var playbackSpeed by mutableStateOf(RoutePlaybackSpeed.ONE)
        private set
    private var selectionJob: Job? = null
    private var selectionRevision = 0
    var userRevision by mutableIntStateOf(0); private set
    val duration get() = review?.timeline?.durationMillis ?: review?.context?.takeIf { it.available }?.let { it.durationMillis ?: 0 }
        ?: maxOf(activeDuration, index?.durationMillis ?: 0)
    val mode get() = when (selection) {
        WalkRouteSelection.Overview -> RouteExplorerMode.OVERVIEW
        is WalkRouteSelection.Section -> RouteExplorerMode.SECTION
        is WalkRouteSelection.Auxiliary -> RouteExplorerMode.AUXILIARY
        is WalkRouteSelection.Gap, is WalkRouteSelection.Transition, is WalkRouteSelection.Event -> RouteExplorerMode.CONTEXT
        is WalkRouteSelection.Scene -> RouteExplorerMode.SCENE
        is WalkRouteSelection.Passage -> RouteExplorerMode.PASSAGE
        is WalkRouteSelection.Replay -> RouteExplorerMode.REPLAY
        is WalkRouteSelection.Slice -> RouteExplorerMode.SLICE
    }
    val elapsed get() = (selection as? WalkRouteSelection.Replay)?.elapsedMillis ?: 0L
    val passages get() = (selection as? WalkRouteSelection.Passage)?.result
    val selectedPassId get() = (selection as? WalkRouteSelection.Passage)?.selectedId
    val selectedSceneId get() = (selection as? WalkRouteSelection.Scene)?.id
    val selectedSection get() = (selection as? WalkRouteSelection.Section)?.let { selected ->
        review?.sections?.firstOrNull { it.index == selected.index }
    }
    val selectedSlice get() = (selection as? WalkRouteSelection.Slice)?.let { selected ->
        val time = review?.timeline ?: return@let null
        val from = time.position(selected.from) ?: return@let null
        val until = time.position(selected.until) ?: return@let null
        time.slice(from, until)
    }
    val highlightPaths get() = selectedSlice?.walking ?: listOfNotNull(selectedSection?.path ?: selectedPass?.path)
    val selectedAuxiliary get() = (selection as? WalkRouteSelection.Auxiliary)?.let { selected ->
        review?.observed?.sections?.firstOrNull { it.id == selected.id }
    }
    val selectedPass get() = passages?.passes?.firstOrNull { it.id == selectedPassId }
    val selectedContext get() = when (val target = selection) {
        is WalkRouteSelection.Gap -> review?.context?.context(target.id)
        is WalkRouteSelection.Transition -> review?.context?.context(target.id)
        is WalkRouteSelection.Event -> review?.context?.context(target.id)
        else -> null
    }
    val replayFrame get() = if (mode != RouteExplorerMode.REPLAY) null else
        review?.timeline?.takeIf { it.durationMillis != null }?.frameAt(elapsed) ?:
            review?.context?.takeIf { it.available }?.frameAt(elapsed) ?: index?.frameAt(elapsed)

    fun choosePanel(open: Boolean) {
        panelOpen = open
        if (!open) overview()
    }
    fun pause() { playing = false }
    fun choosePlaybackSpeed(speed: RoutePlaybackSpeed) { userRevision++; playbackSpeed = speed }
    private fun replaceSelection(value: WalkRouteSelection, fromMap: Boolean = false, userChange: Boolean = true) {
        if (userChange) userRevision++
        selectionRevision++; selectionJob?.cancel(); analyzing = false; playing = false
        error = null; selection = value; selectionFromMap = fromMap
    }
    fun overview() { replaceSelection(WalkRouteSelection.Overview) }
    fun selectScene(id: String, fromMap: Boolean = false) {
        replaceSelection(WalkRouteSelection.Scene(id), fromMap); panelOpen = false
    }
    fun closeScene() { if (selection is WalkRouteSelection.Scene) overview() }
    fun selectSection(index: Int) {
        if (review?.sections?.none { it.index == index } != false) return
        replaceSelection(WalkRouteSelection.Section(index)); panelOpen = true
    }
    fun selectAuxiliary(id: String) {
        if (review?.observed?.sections?.none { it.id == id } != false) return
        replaceSelection(WalkRouteSelection.Auxiliary(id)); panelOpen = true
    }
    fun selectContext(id: String, openExplorer: Boolean = true, fromMap: Boolean = false) {
        val context = review?.context?.context(id) ?: return
        replaceSelection(when (context.kind) {
            RecordContextKind.GAP -> WalkRouteSelection.Gap(id)
            RecordContextKind.TRANSITION -> WalkRouteSelection.Transition(id)
            else -> WalkRouteSelection.Event(id)
        }, fromMap)
        panelOpen = openExplorer
    }
    fun replaceRoute(source: RouteExplorerIndex, completed: CompletedRouteReview, duration: Long) {
        // Keep a scene identity, but derive its correspondence again against the new route/scene.
        val scene = selectedSceneId
        val sameMeasurement = review?.detail?.measurement?.let { previous ->
            previous.id == completed.detail.measurement?.id && previous.resultDigest == completed.detail.measurement.resultDigest } == true
        val retained = selection.takeIf { sameMeasurement && (it is WalkRouteSelection.Replay || it is WalkRouteSelection.Slice) }
        replaceSelection(retained ?: scene?.let { WalkRouteSelection.Scene(it) } ?: WalkRouteSelection.Overview,
            fromMap = scene != null && selectionFromMap, userChange = false)
        index = source; review = completed; activeDuration = duration
        preparationError = null
    }
    fun selectPass(id: String) {
        if (passages?.passes?.any { it.id == id } != true) return
        val result = passages ?: return
        replaceSelection(WalkRouteSelection.Passage(result, id))
    }
    fun inspect(point: GeoPoint) {
        val source = index ?: return
        replaceSelection(WalkRouteSelection.Passage(null, null))
        val revision = selectionRevision
        panelOpen = true; analyzing = true
        selectionJob = scope.launch {
            try {
                val result = withContext(computeDispatcher) { source.passagesAt(point) }
                if (index === source && revision == selectionRevision) {
                    selection = WalkRouteSelection.Passage(result, result.passes.firstOrNull()?.id)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (index === source && revision == selectionRevision)
                    error = "이 지점의 통과 구간을 확인하지 못했어요."
            }
            finally { if (revision == selectionRevision) analyzing = false }
        }
    }
    fun seek(value: Long) {
        replaceSelection(WalkRouteSelection.Replay(value.coerceIn(0, duration)))
    }
    fun selectTimeRange(from: Long, until: Long) {
        val timeline = review?.timeline ?: return
        if (timeline.slice(from, until) == null) return
        val a = timeline.address(from) ?: return; val b = timeline.address(until) ?: return
        replaceSelection(WalkRouteSelection.Slice(a, b)); panelOpen = true
    }
    fun restoreSelection(value: WalkRouteSelection, panel: Boolean, speed: RoutePlaybackSpeed) {
        replaceSelection(value, userChange = false)
        panelOpen = panel; playbackSpeed = speed
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
        selection = WalkRouteSelection.Replay(advanceRoutePlayback(elapsed, delta, duration, playbackSpeed))
        if (elapsed >= duration) playing = false
    }
}

internal fun walkRouteExplorerSaver(scope: CoroutineScope) = Saver<WalkRouteExplorerState, Any>(
    save = { listOf(it.selectedSceneId.orEmpty(), it.playbackSpeed.name) },
    restore = { saved ->
        // Accept the scene-only value saved by earlier app versions as well.
        val values = saved as? List<*>
        val id = (saved as? String) ?: (values?.getOrNull(0) as? String).orEmpty()
        WalkRouteExplorerState(scope, 0).apply {
            restoreSelection(if (id.isNotEmpty()) WalkRouteSelection.Scene(id) else WalkRouteSelection.Overview, false,
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

@Composable
internal fun WalkRouteExplorerPanel(state: WalkRouteExplorerState, onOverview: () -> Unit,
    onSection: (CompletedRouteSection) -> Unit = {},
    onAuxiliary: (ObservedRouteSection) -> Unit = {},
    onContext: (RecordContext) -> Unit = {},
    sliceScenes: List<com.daengs.app.walk.diary.DiaryScene> = emptyList(),
    onScene: (com.daengs.app.walk.diary.DiaryScene) -> Unit = {},
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { state.overview(); onOverview() }) { Text("전체 동선") }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                RoutePlaybackSpeedMenu(state.playbackSpeed, state::choosePlaybackSpeed)
                Button(onClick = state::togglePlayback, enabled = state.index != null && state.duration > 0) {
                    Text(if (state.playing) "일시정지" else "동선 재생")
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        state.review?.let { review ->
            Text("기록 " + formatRouteExplorerClock(review.summary.startedAtMillis) + "–" +
                (review.summary.endedAtMillis?.let(::formatRouteExplorerClock) ?: "진행 중"),
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            if (review.context.available && review.context.durationMillis == null && review.timeline?.durationMillis == null)
                Text("기록 시간의 순서를 확정하지 못해 자동 재생을 제공하지 않아요. 아래 전후 관계에서 범위를 열 수 있어요.",
                    style = MaterialTheme.typography.bodySmall)
            if (state.mode == RouteExplorerMode.CONTEXT) {
                state.selectedContext?.let { RecordContextDetail(it) }
                TextButton(onClick = { state.overview(); onOverview() }) { Text("기록 흐름 전체") }
            }
            if (state.mode in setOf(RouteExplorerMode.OVERVIEW, RouteExplorerMode.SECTION, RouteExplorerMode.AUXILIARY)) {
                review.sections.forEachIndexed { ordinal, section ->
                    OutlinedButton(onClick = { state.selectSection(section.index); onSection(section) },
                        modifier = Modifier.fillMaxWidth()) {
                        Text((if (state.selectedSection?.index == section.index) "● " else "○ ") +
                            "동선 ${ordinal + 1} · " + formatRouteExplorerClock(section.startedAtMillis) + "–" +
                            formatRouteExplorerClock(section.endedAtMillis))
                    }
                }
                review.observed.sections.forEachIndexed { ordinal, section ->
                    OutlinedButton(onClick = { state.selectAuxiliary(section.id); onAuxiliary(section) },
                        modifier = Modifier.fillMaxWidth()) {
                        Text((if (state.selectedAuxiliary?.id == section.id) "● " else "○ ") +
                            "관측 경로 ${ordinal + 1} · " + observedRouteLabel(section) + "\n" +
                            formatRouteExplorerClock(section.startedAtMillis) + "–" + formatRouteExplorerClock(section.endedAtMillis))
                    }
                }
                if (review.context.contexts.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("기록의 전후 관계", style = MaterialTheme.typography.titleSmall)
                    review.context.contexts.forEach { value ->
                        OutlinedButton(onClick = { state.selectContext(value.id); onContext(value) }, modifier = Modifier.fillMaxWidth()) {
                            Text(recordContextTitle(value) + "\n" + recordContextTime(value))
                        }
                    }
                }
            }
            if (review.sections.size > 1 && state.mode == RouteExplorerMode.OVERVIEW) Text("구간을 누르면 해당 동선으로 확대해요. 끊긴 사이는 연결하지 않아요.",
                style = MaterialTheme.typography.bodySmall)
            if (review.sections.isEmpty()) Text("이어지는 보행선이 없어요. 확인된 위치와 장면은 볼 수 있어요.",
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            if (review.timeline?.durationMillis != null && state.duration > 0) {
                MeasurementTimeControls(state, sliceScenes, onScene)
            }
        }
        state.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        state.preparationError?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (state.index == null && state.error == null && state.preparationError == null) Text("동선 탐색을 준비하고 있어요.")
        else if (state.analyzing) Text("선택한 길을 지난 시각을 확인하고 있어요.")
        else when (state.mode) {
            RouteExplorerMode.OVERVIEW -> Text("지도에서 겹친 길을 누르면 통과 시각을 골라 볼 수 있어요.",
                style = MaterialTheme.typography.bodyMedium)
            RouteExplorerMode.SECTION -> Text("선택한 동선과 진행 방향을 강조했어요. 겹친 길을 누르면 통과 시각을 고를 수 있어요.",
                style = MaterialTheme.typography.bodyMedium)
            RouteExplorerMode.AUXILIARY -> state.selectedAuxiliary?.let { section ->
                Text(observedRouteDescription(section) + if (section.directions.isEmpty())
                    " 이동 방향을 표시할 근거는 충분하지 않아요." else " 이동 근거가 있는 부분에 진행 방향을 표시해요.")
            }
            RouteExplorerMode.SCENE -> Unit // Scenes use the reading panel on the same selection state.
            RouteExplorerMode.CONTEXT -> Unit
            RouteExplorerMode.SLICE -> Text("선택한 시간 범위와 겹치는 원본 선분을 강조했어요. 경로가 끊긴 사이는 이어지지 않아요.")
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
                Text(if (frame?.inGap != false) "${frame?.recordedAtMillis?.let { formatRouteExplorerClock(it) + " · " }.orEmpty()}이 시각에는 재생할 위치 근거가 충분하지 않아요."
                    else frame.recordedAtMillis?.let { "기록 시각 " + formatRouteExplorerClock(it) } ?: "기기 시간으로 확인한 위치예요. 표시 시각은 확정하지 않아요.",
                    style = MaterialTheme.typography.bodyMedium)
                if (state.review?.context?.available == true && state.review?.context?.durationMillis == null && state.review?.timeline?.durationMillis == null)
                    Text("기록 시간의 순서를 확정하지 못해 자동 재생을 제공하지 않아요. 전후 관계에서 해당 범위를 열 수 있어요.")
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

internal fun formatRouteExplorerClock(atMillis: Long): String =
    Instant.ofEpochMilli(atMillis).atZone(ZoneId.systemDefault()).format(ROUTE_EXPLORER_CLOCK)

@Preview(showBackground = true, widthDp = 390, heightDp = 360)
@Preview(showBackground = true, widthDp = 320, heightDp = 360)
@Composable
private fun RouteExplorerPanelPreview() {
    val scope = rememberCoroutineScope()
    val state = remember {
        val summary = WalkSummary("preview", emptyList(), 0, 300_000, null, 80.0, 300_000,
            listOf(listOf(GeoPoint(37.5, 127.0), GeoPoint(37.5003, 127.0)),
                listOf(GeoPoint(37.51, 127.0), GeoPoint(37.5103, 127.0))).mapIndexed { segment, points ->
                points.mapIndexed { index, point -> com.daengs.app.location.LocationSample(
                    point, 10_000L + segment * 200_000 + index * 30_000, accuracyMeters = 3f) }
            }, null)
        val detail = WalkSessionDetail(summary, summary.toSessionRoute(), emptyList())
        WalkRouteExplorerState(scope, 300_000).apply {
            replaceRoute(RouteExplorerIndex(detail.route), CompletedRouteReview(detail), 300_000)
        }
    }
    DaengsTheme { WalkRouteExplorerPanel(state, {}) }
}
