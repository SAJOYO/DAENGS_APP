package com.daengs.app.walk.diary

/** Local editable bookends; never generate prose or replace an existing scene/edit. */
internal fun DiaryWalk.withBoundaryScenes(draft: StoryboardDraft): DiaryWalk {
    if (preparing) return this
    val defaults = listOfNotNull(
        StoryboardScene("start",summary.startedAtMillis,"첫 장면","","",summary.startedAtMillis.toString()),
        summary.endedAtMillis?.let { StoryboardScene("end",it,"마지막 장면","","",it.toString()) },
    )
    val existing = scenes.mapNotNull { it.boundaryKind() }.toSet()
    val additions = defaults.filter { source ->
        val kind = if (source.id == "start") DiarySceneKind.START else DiarySceneKind.END
        kind !in existing && draft.edits.none { it.id.removePrefix("geo:") == source.id && it.hidden }
    }.map { source ->
        val edited = applyStoryboardEdits(listOf(source), draft).first { it.id == source.id }
        DiaryScene("${summary.sessionId}/${source.id}",summary.sessionId,source.atMillis,
            edited.title,edited.sceneBody(),null,"",needsReview=edited.needsReview,source=edited)
    }
    val ordered = (scenes + additions).map { scene ->
        when (scene.boundaryKind()) {
            DiarySceneKind.START -> scene.copy(atMillis=summary.startedAtMillis)
            DiarySceneKind.END -> scene.copy(atMillis=summary.endedAtMillis ?: scene.atMillis)
            else -> scene
        }
    }.sortedWith(compareBy<DiaryScene> {
        when (it.boundaryKind()) { DiarySceneKind.START -> 0; DiarySceneKind.END -> 2; else -> 1 }
    }.thenBy { it.atMillis }.thenBy { it.content?.order ?: Int.MAX_VALUE }.thenBy { it.id })
    return copy(scenes=ordered)
}
