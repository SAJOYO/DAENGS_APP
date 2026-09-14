package com.daengs.app.ui.walk

import com.daengs.app.ui.walk.detail.WalkDiaryReadView
import com.daengs.app.ui.walk.detail.acceptsScene
import com.daengs.app.ui.walk.detail.focusFor
import com.daengs.app.walk.diary.DiaryScene
import com.daengs.app.walk.routeexplorer.SceneBindingKey

/** Caller updates its read view in the same Compose snapshot as this selection state. */
internal fun WalkRouteExplorerState.adopt(view: WalkDiaryReadView) {
    if (review !== view.route.review) replaceRoute(view.route.index, view.route.review, view.route.detail.summary.activeDurationMillis)
    if (!view.scenesLoading && selectedSceneId != null && view.diary?.scenes.orEmpty().none { it.id == selectedSceneId }) overview()
    if (!view.scenesLoading) {
        if (returnRange != null) {
            fun event(read: WalkDiaryReadView?): String? {
                val scene = read?.diary?.scenes?.singleOrNull { it.id == selectedSceneId } ?: return null
                return SceneBindingKey.revisions(scene, read.diary.sourceEntries.singleOrNull { it.id == scene.entryId }).first
            }
            if (adoptedRead != null && event(adoptedRead) != event(view)) invalidateSceneReturn()
        }
        adoptedRead = view
    }
}

/** Explicit scene-neighborhood action, resolved from this exact adopted source snapshot. */
internal fun WalkRouteExplorerState.selectSceneNeighborhood(view: WalkDiaryReadView, scene: DiaryScene,
    beforeMillis: Long = 30_000, afterMillis: Long = 30_000): Boolean {
    val range = sceneNeighborhood(view, scene, beforeMillis, afterMillis) ?: return false
    selectTimeRange(range.first, range.last)
    return true
}

internal fun WalkRouteExplorerState.sceneNeighborhood(view: WalkDiaryReadView, scene: DiaryScene,
    beforeMillis: Long = 30_000, afterMillis: Long = 30_000): LongRange? {
    if (adoptedRead !== view || view.scenesLoading || !view.acceptsScene(scene) || beforeMillis < 0 || afterMillis < 0) return null
    val timeline = view.route.review.timeline ?: return null
    val at = view.focusFor(scene)?.let(timeline::scenePosition) ?: return null
    val duration = timeline.durationMillis ?: return null
    val from = at - minOf(beforeMillis, at)
    val until = at + minOf(afterMillis, duration - at)
    return (from..until).takeIf { until > from }
}
