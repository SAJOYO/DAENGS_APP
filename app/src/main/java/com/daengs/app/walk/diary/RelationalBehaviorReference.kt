package com.daengs.app.walk.diary

import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.diary.relational.RelationalDiaryCard
import com.daengs.app.walk.diary.relational.RelationalRecordContent

/** Explicit source identity only. Coincident time/location or generated prose cannot select a pin. */
internal fun relationalBehaviorReference(card: RelationalDiaryCard, entries: List<WalkEntry>,
    sessionId: String): StoryboardEntryReference? {
    val original = card.originals.filter { !it.deleted && it.content is RelationalRecordContent.Behavior }
        .singleOrNull() ?: return null
    val behavior = original.content as RelationalRecordContent.Behavior
    val ref = original.ref
    if (ref.store != "walk_entry" || ref.versionKind != "revision" || original.anchor != card.anchor) return null
    val revision = ref.version.toLongOrNull() ?: return null
    val entry = entries.singleOrNull { it.sessionId == sessionId && it.id == ref.id } ?: return null
    if (entry.syncPending || entry.syncError != null || entry.baseVersion?.revision?.toLong() != revision ||
        entry.type.behaviorCode != behavior.code || entry.petId != behavior.petId ||
        entry.recordedAtMillis != card.anchor.eventAt.toEpochMilli()) return null
    return StoryboardEntryReference(ref.id, revision, behavior.petId)
}
