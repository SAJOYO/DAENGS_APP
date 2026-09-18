package com.daengs.app.walk.diary

import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.WalkMomentType

/** One reading identity; an action enriches this scene rather than creating another map object. */
internal data class DiaryPresentedScene(
    val scene: DiaryScene,
    val ordinal: Int?,
    val action: WalkEntry? = null,
)

internal data class DiaryScenePresentation(val items: List<DiaryPresentedScene>)

/** Number before filtering map coordinates or grouping. Boundaries never consume a number. */
internal fun diaryScenePresentation(scenes: List<DiaryScene>): DiaryScenePresentation {
    var ordinal = 0
    return DiaryScenePresentation(scenes.map { scene ->
        DiaryPresentedScene(scene, if (scene.boundaryKind() == null) ++ordinal else null)
    })
}

/** Resolve only against the same read, using the existing unique, revision-checked source binding. */
internal fun DiaryWalk.scenePresentation(): DiaryScenePresentation {
    val actions = sourceEntries.asSequence()
        .filter { it.sessionId == summary.sessionId && it.id.isNotBlank() && it.type != WalkMomentType.NOTE }
        .map { it.id }.distinct()
        .mapNotNull { DiaryActionTarget(summary.sessionId, it).resolve(this) }
        .mapNotNull { reading -> reading.scene?.let { it.id to reading.entry } }
        .toMap()
    val numbered = diaryScenePresentation(scenes.filter { it.sessionId == summary.sessionId })
    return DiaryScenePresentation(numbered.items.map { it.copy(action = actions[it.scene.id]) })
}
