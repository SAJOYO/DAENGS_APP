package com.daengs.app.ui.walk

import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import com.daengs.app.walk.detail.WalkDetailSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject

internal class DiaryReadingMemory(val drawer: DiaryDrawerState, val list: LazyListState) {
    val explorer = androidx.compose.foundation.ScrollState(0)
    var pendingExplorerOffset by mutableStateOf<Int?>(null)
    var explorerDetails by mutableStateOf(false)
    var groupIds by mutableStateOf<List<String>>(emptyList())
    val groupList = LazyListState()
    fun inspect(ids: List<String>) { groupIds = ids.distinct() }
    var bodyScene by mutableStateOf<String?>(null)
    var bodyIndex by mutableIntStateOf(0)
    var bodyOffset by mutableIntStateOf(0)
    var pendingList by mutableStateOf<JSONObject?>(null)
    var restoredBody by mutableStateOf<JSONObject?>(null)
    fun activity() = listOf(drawer.currentValue, drawer.targetValue, list.firstVisibleItemIndex,
        list.firstVisibleItemScrollOffset, bodyScene, bodyIndex, bodyOffset, explorer.value, pendingExplorerOffset,
        groupIds, groupList.firstVisibleItemIndex, groupList.firstVisibleItemScrollOffset, explorerDetails)
    fun snapshot(): JSONObject = JSONObject().put("drawer", drawer.currentValue.name).apply {
        put("explorerOffset", pendingExplorerOffset ?: explorer.value)
        put("explorerLayout", 2)
        put("explorerDetails", explorerDetails)
        put("group", org.json.JSONArray(groupIds))
        put("groupIndex", groupList.firstVisibleItemIndex); put("groupOffset", groupList.firstVisibleItemScrollOffset)
        if (pendingList != null) put("list", pendingList) else list.layoutInfo.visibleItemsInfo.firstOrNull()?.let {
            put("list", JSONObject().put("key", it.key.toString()).put("offset", list.firstVisibleItemScrollOffset))
        }
        if (restoredBody != null) put("body", restoredBody)
        else bodyScene?.let { put("body", JSONObject().put("id", it).put("index", bodyIndex).put("offset", bodyOffset)) }
    }
    suspend fun restore(value: JSONObject) {
        // Old offsets included the controls and route sections; they no longer address this reading region.
        pendingExplorerOffset = if (value.optInt("explorerLayout") == 2) value.optInt("explorerOffset", 0).coerceIn(0, 100_000) else 0
        explorerDetails = value.optBoolean("explorerDetails", false)
        val ids = value.optJSONArray("group")
        groupIds = if (ids == null) emptyList() else (0 until ids.length()).map { ids.getString(it) }.distinct()
        groupList.requestScrollToItem(value.optInt("groupIndex").coerceIn(0,100_000), value.optInt("groupOffset").coerceIn(0,100_000))
        pendingList = value.optJSONObject("list")
        restoredBody = value.optJSONObject("body")
        val drawerValue = DiaryDrawerValue.entries.firstOrNull { it.name == value.optString("drawer") }
        if (drawerValue != null) drawer.drag.snapTo(drawerValue)
    }
}

@Composable
internal fun rememberDiaryReadingMemory(): DiaryReadingMemory {
    val drawer = rememberDiaryDrawerState(DiaryDrawerValue.Browsing, true)
    val list = rememberLazyListState()
    return remember(drawer, list) { DiaryReadingMemory(drawer, list) }
}

/** Reads once; later evidence can validate the address, but cannot overwrite a user's newer action. */
@Composable
internal fun RememberWalkExplorationPersistence(source: WalkDetailSource, owner: String,
    view: WalkDiaryReadView?, explorer: WalkRouteExplorerState, reading: DiaryReadingMemory) {
    val currentView by rememberUpdatedState(view)
    LaunchedEffect(source, owner, explorer, reading) {
        var touchedReading = false
        var restored = false
        val initialRevision = explorer.userRevision
        val initialReading = reading.activity()
        val trackReading = launch { snapshotFlow { reading.activity() }.drop(1).collect { touchedReading = true } }
        try {
            val payload = try { source.loadExploration() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { null }
            var consumed = initialRevision != 0
            snapshotFlow { Triple(currentView, explorer.userRevision, WalkExplorationBookmarkSnapshot(explorer, reading)) }
                .conflate().collect { (read, revision, _) ->
                    if (!source.isCurrentAccount()) throw CancellationException("account changed")
                    if (read == null || read.scenesLoading || explorer.review !== read.route.review) return@collect
                    if (!consumed && revision == initialRevision && !touchedReading && reading.activity() == initialReading && payload != null) {
                        val saved = WalkExplorationBookmark.decode(payload, owner, read)
                        if (saved != null) {
                            consumed = true; restored = true
                            explorer.restoreSelection(saved.selection, saved.panel, saved.speed)
                            reading.restore(saved.reading)
                        }
                    }
                    if (revision != initialRevision || touchedReading) consumed = true
                    if (restored || revision != initialRevision || touchedReading || initialRevision != 0) {
                        WalkExplorationBookmark.encode(owner, read, explorer, reading.snapshot())?.let {
                            try { source.saveExploration(it) }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { /* A reading checkpoint must not block the diary. */ }
                        }
                    }
                }
        } finally {
            trackReading.cancel()
            // Persist the displayed position on exit, even when the composition's coroutine is cancelled.
            if (restored || explorer.userRevision != initialRevision || touchedReading || initialRevision != 0) {
                val read = currentView
                if (read != null && source.isCurrentAccount()) {
                    val saved = WalkExplorationBookmark.encode(owner, read, explorer, reading.snapshot())
                    if (saved != null) withContext(NonCancellable) { runCatching { source.saveExploration(saved) } }
                }
            }
        }
    }
}

private data class WalkExplorationBookmarkSnapshot(val selection: WalkRouteSelection, val panel: Boolean,
    val speed: com.daengs.app.walk.routeexplorer.RoutePlaybackSpeed, val reading: List<Any?>,
    val review: com.daengs.app.walk.routeexplorer.CompletedRouteReview?) {
    constructor(state: WalkRouteExplorerState, memory: DiaryReadingMemory) :
        this(state.selection, state.panelOpen, state.playbackSpeed, memory.activity(), state.review)
}
