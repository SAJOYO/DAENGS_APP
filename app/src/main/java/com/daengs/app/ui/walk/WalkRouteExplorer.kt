package com.daengs.app.ui.walk

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.tooling.preview.Preview
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
    data class Scene(val id: String, val returnRange: Slice? = null) : WalkRouteSelection
    data class Passage(val result: RoutePassages?, val selectedId: String?) : WalkRouteSelection
    data class Replay(val elapsedMillis: Long, val range: Slice? = null) : WalkRouteSelection
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
    internal var adoptedRead: WalkDiaryReadView? = null
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
    val timeRange get() = when (val value = selection) {
        is WalkRouteSelection.Slice -> value
        is WalkRouteSelection.Replay -> value.range
        else -> null
    }
    val returnRange get() = (selection as? WalkRouteSelection.Scene)?.returnRange
    private fun resolveRange(selected: WalkRouteSelection.Slice): MeasurementTimeSlice? {
        val time = review?.timeline ?: return null
        return time.slice(time.position(selected.from) ?: return null, time.position(selected.until) ?: return null)
    }
    val selectedSlice get() = timeRange?.let(::resolveRange)
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
        if (open && !panelOpen && selectedSceneId != null) {
            if (!returnToRange()) overview()
        }
        panelOpen = open
        if (!open) {
            // The scene list owns reading; a chosen time range survives that tab visit.
            if (timeRange != null) replaceSelection(requireNotNull(timeRange)) else overview()
        }
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
        replaceSelection(WalkRouteSelection.Scene(id, timeRange ?: returnRange), fromMap); panelOpen = false
    }
    fun returnToRange(): Boolean {
        val range = returnRange ?: (selection as? WalkRouteSelection.Replay)?.range ?: return false
        if (resolveRange(range) == null) return false
        replaceSelection(range); panelOpen = true
        return true
    }
    internal fun invalidateSceneReturn() {
        val scene = selection as? WalkRouteSelection.Scene ?: return
        if (scene.returnRange != null) replaceSelection(scene.copy(returnRange = null), selectionFromMap, userChange = false)
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
            previous.id == completed.detail.measurement?.id && previous.resultDigest == completed.detail.measurement.resultDigest &&
                previous.ownerId == completed.detail.measurement.ownerId && review?.summary?.sessionId == completed.summary.sessionId } == true
        val retained = selection.takeIf { sameMeasurement && (it is WalkRouteSelection.Replay || it is WalkRouteSelection.Slice || it is WalkRouteSelection.Scene) }
        replaceSelection(retained ?: scene?.let { WalkRouteSelection.Scene(it) } ?: WalkRouteSelection.Overview,
            fromMap = scene != null && selectionFromMap, userChange = false)
        index = source; review = completed; activeDuration = duration
        val range = timeRange ?: returnRange
        if (range != null && resolveRange(range) == null) {
            replaceSelection(scene?.let { WalkRouteSelection.Scene(it) } ?: WalkRouteSelection.Overview,
                fromMap = scene != null && selectionFromMap, userChange = false)
        }
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
        val slice = selectedSlice
        if (timeRange != null && slice == null) return
        replaceSelection(WalkRouteSelection.Replay(value.coerceIn(slice?.from ?: 0, slice?.until ?: duration), timeRange))
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
    val canPlayback get() = index != null && duration > 0 && (timeRange == null || selectedSlice != null) && (selectedSlice?.let { slice ->
        review?.timeline?.nextPlayablePosition(slice.from, slice.until) != null
    } ?: true)
    fun togglePlayback() {
        if (!canPlayback) return
        if (playing) pause() else {
            val slice = selectedSlice
            val from = slice?.from ?: 0L; val until = slice?.until ?: duration
            val start = if (mode != RouteExplorerMode.REPLAY || elapsed >= until) from else elapsed.coerceAtLeast(from)
            val at = if (slice != null) review?.timeline?.nextPlayablePosition(start, until) ?: return else start
            seek(at)
            playing = true
        }
    }
    fun tick(delta: Long) {
        if (!playing) return
        val range = timeRange
        if (range != null && selectedSlice == null) { pause(); return }
        val until = selectedSlice?.until ?: duration
        val next = advanceRoutePlayback(elapsed, delta, until, playbackSpeed)
        val at = if (range != null && next < until) review?.timeline?.nextPlayablePosition(next, until) ?: until else next
        selection = WalkRouteSelection.Replay(at, range)
        if (elapsed >= until) playing = false
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
