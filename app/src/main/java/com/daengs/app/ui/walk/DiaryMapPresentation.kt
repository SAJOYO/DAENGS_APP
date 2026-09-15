package com.daengs.app.ui.walk

import com.daengs.app.map.layers.moments.MomentMarkerState
import com.daengs.app.map.shell.MapScene
import com.daengs.app.walk.diary.DiaryScene
import com.daengs.app.walk.distanceTo

/** Preserve one input per scene; the provider groups projected positions using the shared map policy. */
internal fun diarySceneMarkers(scenes: List<DiaryScene>, selectedId: String?, inspected: List<String> = emptyList()): List<MomentMarkerState> =
    scenes.filterNot { it.isWalkBoundary() }.mapIndexedNotNull { index, scene -> scene.point?.takeIf { it.isDiaryLocation() }?.let { point ->
        MomentMarkerState(scene.id, point, "${index+1}", selected = scene.id == selectedId,
            aboveRouteEndpoints = true,
            diaryPin = com.daengs.app.map.layers.moments.DiaryPinAppearance(index+1,
                inspected = scene.id in inspected, dimmed = inspected.isNotEmpty() && scene.id !in inspected))
    } }

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
