package com.daengs.app.walk.diary

import com.daengs.app.walk.WalkPhoto
import com.daengs.app.walk.WalkSummary
import com.daengs.app.walk.WalkMomentType

/** Live originals remain readable without a valid generation. Never restore an invalid bundle. */
internal fun relationalOriginalScenes(walk: WalkSummary, input: DiaryBoardInput,
    photos: List<WalkPhoto>, draft: StoryboardDraft): List<DiaryScene> {
    val entries = input.entries.filter { it.sessionId == walk.sessionId }.map { entry ->
        // A resolved pin owns display location. An explicit unlocated pin must not fall back
        // to the older raw content coordinate.
        val point = if (entry.pin != null) entry.pin.point else entry.point
        val note = entry.type == WalkMomentType.NOTE
        val content = DiarySceneContent("", if (note) "note" else "behavior", point = point, locationLabel = "",
            locationMethod = entry.pin?.method, locationAtMillis = when {
                entry.pin?.method == "last_known" -> entry.pin.sceneReferences(entry.recordedAtMillis)?.singleOrNull()?.atMillis ?: entry.locationCapturedAtMillis
                entry.pin != null -> if (entry.pin.point != null) entry.recordedAtMillis else null
                else -> entry.locationCapturedAtMillis
            },
            positionState = entry.pin?.state)
        val source = StoryboardScene("original:entry:${entry.id}", entry.recordedAtMillis, entry.type.label,
            "", "", storyboardHash(entry.toJson().toString() + entry.pin?.payload.orEmpty()),
            entryReference = StoryboardEntryReference(entry.id, entry.baseVersion?.revision?.toLong(), entry.petId, note),
            diary = content, bodyScope = SceneBodyScope.SCENE)
        DiaryScene("${walk.sessionId}/${source.id}", walk.sessionId, source.atMillis, source.title, "", point, "",
            entryId = entry.id, content = content, source = source,
            originalNotes = if (note) listOfNotNull(entry.note) else emptyList())
    }
    val images = input.photos.map { image ->
        val photo = photos.singleOrNull { it.sessionId == walk.sessionId && it.id == image.id }
        val content = DiarySceneContent("", "photo", photoId = image.id, point = image.point, locationLabel = "",
            locationAtMillis = image.locationAtMillis,
            locationMethod = if (image.locationAtMillis == image.capturedAtMillis) "observed" else "last_known")
        val source = StoryboardScene("original:photo:${image.id}", image.capturedAtMillis, "사진 기록", "", "",
            storyboardHash(image.toString()), diary = content, bodyScope = SceneBodyScope.SCENE)
        DiaryScene("${walk.sessionId}/${source.id}", walk.sessionId, source.atMillis, source.title, "", image.point, "",
            photo = photo, content = content, source = source)
    }
    return (entries + images).mapNotNull { scene ->
        val source = requireNotNull(scene.source)
        val edit = draft.edits.singleOrNull { it.id == source.id } ?: return@mapNotNull scene
        if (edit.hidden) return@mapNotNull null
        val changed = source.copy(title = edit.title, body = edit.body, needsReview = edit.sourceFingerprint != source.fingerprint)
        scene.copy(title = changed.title, body = changed.body, needsReview = changed.needsReview, source = changed)
    }.sortedWith(compareBy<DiaryScene> { it.atMillis }.thenBy { it.id })
}
