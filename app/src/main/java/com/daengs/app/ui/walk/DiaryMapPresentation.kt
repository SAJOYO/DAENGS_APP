package com.daengs.app.ui.walk

import com.daengs.app.map.layers.moments.MomentMarkerState
import com.daengs.app.map.shell.MapScene
import com.daengs.app.walk.diary.DiaryScene
import com.daengs.app.walk.diary.DiaryScenePresentation
import com.daengs.app.walk.diary.diaryScenePresentation
import com.daengs.app.walk.distanceTo

/** Plain-scene callers keep the same numbering; diary readers pass their bound presentation below. */
internal fun diarySceneMarkers(scenes: List<DiaryScene>, selectedId: String?, inspected: List<String> = emptyList()): List<MomentMarkerState> =
    diarySceneMarkers(diaryScenePresentation(scenes), selectedId, inspected)

/** One marker per scene. Behavior changes its artwork, never its identity, ordinal or selection. */
internal fun diarySceneMarkers(presentation: DiaryScenePresentation, selectedId: String?, inspected: List<String> = emptyList()): List<MomentMarkerState> =
    presentation.items.mapNotNull { item ->
        val ordinal = item.ordinal ?: return@mapNotNull null
        val scene = item.scene
        val point = scene.point?.takeIf { it.isDiaryLocation() } ?: return@mapNotNull null
        MomentMarkerState(scene.id, point, item.action?.type?.label ?: "$ordinal",
            selected = scene.id == selectedId, aboveRouteEndpoints = true,
            behaviors = item.action?.let { setOf(it.type) }.orEmpty(),
            diaryPin = com.daengs.app.map.layers.moments.DiaryPinAppearance(ordinal,
                inspected = scene.id in inspected, dimmed = inspected.isNotEmpty() && scene.id !in inspected))
    }

/** Display-only suppression at the same observed position; route and stay data stay intact. */
internal fun diaryDisplayScene(scene: MapScene): MapScene = scene.copy(
    allowRegionalOverview = true,
    detachedDiaryPins = true,
    completedRoute = scene.completedRoute.copy(
        start = scene.completedRoute.start?.copy(compact = true),
        end = scene.completedRoute.end?.copy(compact = true)),
    stayStamps = scene.stayStamps.filterNot { stay ->
        scene.moments.any { it.point.distanceTo(stay.point) <= 1.0 }
    },
)
