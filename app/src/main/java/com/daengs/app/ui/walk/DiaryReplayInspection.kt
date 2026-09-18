package com.daengs.app.ui.walk

import androidx.compose.runtime.*
import com.daengs.app.location.GeoPoint
import com.daengs.app.ui.walk.detail.WalkDiaryReadView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Map inspection has its own selection/jobs and never owns or pauses the replay clock. */
@Stable
internal class DiaryReplayInspection(scope: CoroutineScope, dispatcher: CoroutineDispatcher = Dispatchers.Default) {
    val explorer = WalkRouteExplorerState(scope, 0, dispatcher)
    var active by mutableStateOf(false); private set
    var markerIds by mutableStateOf(emptySet<String>()); private set
    private var read: WalkDiaryReadView? = null

    fun adopt(current: WalkDiaryReadView?) {
        if (current === read) return
        clear(); read = current
        current?.let(explorer::adopt)
    }
    fun clear() { active=false; markerIds=emptySet(); explorer.overview() }
    fun selectMarkers(ids: Set<String>) {
        val current=read ?: return
        val valid=current.scenePresentation.items.filter { it.ordinal != null }.map { it.scene.id }.toSet()
        val chosen=ids.intersect(valid)
        if (chosen.isEmpty()) return
        clear(); markerIds=chosen; active=true
        val scene=current.diary?.scenes?.singleOrNull { it.id in chosen }
        if (scene != null) explorer.selectScene(scene.id, fromMap=true)
    }
    fun selectContext(id: String) {
        if (explorer.review?.context?.context(id) == null) return
        clear(); active=true; explorer.selectContext(id, fromMap=true)
    }
    fun inspect(point: GeoPoint) {
        if (explorer.index == null) return
        clear(); active=true; explorer.inspect(point)
    }
    fun events(): List<DiaryReplayEvent> {
        val current=read ?: return emptyList()
        return current.scenePresentation.items.filter { it.ordinal != null && it.scene.id in markerIds }.map {
            DiaryReplayEvent(it.scene.id, 0, it.scene, it.ordinal, it.action)
        }
    }
}
