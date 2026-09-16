package com.daengs.app.walk.diary

import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.WalkMomentType

/** An original action address. Scene identity is resolved from the current diary read. */
data class DiaryActionTarget(val sessionId: String, val entryId: String) {
    init { require(sessionId.isNotBlank() && entryId.isNotBlank()) }
}

internal data class DiaryActionReading(val target: DiaryActionTarget, val entry: WalkEntry, val scene: DiaryScene?)

/** Never match by position, time, prose or ordinal; ambiguous/stale scenes retain the original. */
internal fun DiaryActionTarget.resolve(diary: DiaryWalk): DiaryActionReading? {
    if (diary.summary.sessionId != sessionId) return null
    val entry = diary.sourceEntries.singleOrNull { it.sessionId == sessionId && it.id == entryId }
        ?.takeUnless { it.type == WalkMomentType.NOTE } ?: return null
    val scene = diary.scenes.filter {
        (it.entryId ?: it.source?.entryReference?.entryId) == entryId &&
            diarySceneKind(it, diary.sourceEntries, sessionId) in setOf(
                DiarySceneKind.SNIFFING, DiarySceneKind.EXCRETION, DiarySceneKind.BARKING)
    }.singleOrNull()?.takeIf { candidate -> diary.scenes.count { it.id == candidate.id } == 1 }
    return DiaryActionReading(this, entry, scene)
}
