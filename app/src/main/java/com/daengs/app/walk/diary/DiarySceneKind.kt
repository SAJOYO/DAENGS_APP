package com.daengs.app.walk.diary

import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.WalkMomentType

/** A source category, never an interpretation of editable prose or a location. */
enum class DiarySceneKind(val label: String) {
    SNIFFING("킁킁 기록"), EXCRETION("배설 기록"), BARKING("짖기 기록"), NOTE("직접 남긴 메모"),
    PHOTO("사진 기록"), DWELL("머무른 구간"), FAST("이동이 빨라진 구간"), SLOW("이동이 느려진 구간"),
    GENERAL("산책 장면"),
}

/** The caller must supply the entries from the same DiaryWalk emission, not a live entry stream. */
internal fun diarySceneKind(scene: DiaryScene, entries: List<WalkEntry>, sessionId: String): DiarySceneKind {
    val unknown = DiarySceneKind.GENERAL
    if (scene.sessionId != sessionId || scene.needsReview ||
        scene.source?.let { !it.available || it.hidden } == true) return unknown
    val reference = scene.source?.entryReference
    if (reference != null && scene.entryId != null && reference.entryId != scene.entryId) return unknown
    val entryId = scene.entryId ?: reference?.entryId
    val recordKind = scene.content?.recordKind
    if (entryId != null) {
        if (entryId.isBlank()) return unknown
        val entry = entries.singleOrNull { it.sessionId == sessionId && it.id == entryId } ?: return unknown
        if (reference != null && (reference.revision?.let { it != entry.baseVersion?.revision?.toLong() } == true ||
                reference.petId?.let { it != entry.petId } == true || reference.isNote != (entry.type == WalkMomentType.NOTE))) return unknown
        val expected = if (entry.type == WalkMomentType.NOTE) "note" else "behavior"
        if (recordKind != null && recordKind != expected) return unknown
        return when (entry.type) {
            WalkMomentType.SNIFFING -> DiarySceneKind.SNIFFING
            WalkMomentType.EXCRETION -> DiarySceneKind.EXCRETION
            WalkMomentType.BARKING -> DiarySceneKind.BARKING
            WalkMomentType.NOTE -> DiarySceneKind.NOTE
        }
    }
    scene.photo?.let { photo ->
        if (photo.sessionId != sessionId || photo.id.isBlank() ||
            scene.content?.photoId?.let { it != photo.id } == true ||
            recordKind != null && recordKind != "photo") return unknown
        return DiarySceneKind.PHOTO
    }
    // recordKind is validated by the diary parser; "behavior" alone contains no subtype.
    return when (recordKind) {
        "note" -> DiarySceneKind.NOTE
        "photo" -> DiarySceneKind.PHOTO // The media may exist only on the capturing device.
        "observed_dwell" -> DiarySceneKind.DWELL
        "observed_fast" -> DiarySceneKind.FAST
        "observed_slow" -> DiarySceneKind.SLOW
        else -> unknown
    }
}

internal fun DiaryWalk.sceneKinds(): Map<String, DiarySceneKind> = scenes.groupBy { it.id }.mapValues { (_, group) ->
    group.singleOrNull()?.let { diarySceneKind(it, sourceEntries, summary.sessionId) } ?: DiarySceneKind.GENERAL
}
