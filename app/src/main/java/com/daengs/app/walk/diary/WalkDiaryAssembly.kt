package com.daengs.app.walk.diary

import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.WalkPhoto
import com.daengs.app.walk.WalkSummary
import com.daengs.app.walk.store.WalkDiaryPublicationRow
import com.daengs.app.walk.store.WalkEntryRow
import com.daengs.app.walk.store.WalkPhotoRow
import com.daengs.app.walk.store.WalkPhotoSyncRow
import com.daengs.app.walk.store.WalkSceneAnalysisRow

/** Values from one reader emission. Loading and account/session checks belong to the reader. */
internal data class DiaryBoardInput(
    val entries: List<WalkEntryRow>,
    val analysis: WalkSceneAnalysisRow?,
    val photoSync: WalkPhotoSyncRow?,
    val photoRows: List<WalkPhotoRow>,
    val publication: WalkDiaryPublicationRow?,
)

/** Title projection only: no entry parsing, user-edit reconciliation, or full diary assembly. */
internal fun diaryTitle(sessionId: String, input: DiaryBoardInput): String? {
    val bundle = if (input.publication != null)
        input.publication.publishedBundle?.let(GeoStoryboardBundle::parse)
    else storyboardAnalysisView(input.analysis, input.entries, input.photoSync, input.photoRows).bundle
    return bundle?.takeIf { it.sessionId == sessionId }?.title
}

/** Pure assembly from loaded values; never queries storage or starts publication/generation. */
internal fun assembleDiary(
    walk: WalkSummary,
    input: DiaryBoardInput,
    photos: List<WalkPhoto>,
    draftPayload: String?,
    observations: List<RecordedFix>,
    measurement: com.daengs.app.walk.WalkMeasurementDetail? = null,
): DiaryWalk {
    val publication = input.publication
    val live = input.entries.mapNotNull { it.entry() }
    if (publication != null && publication.publishedBundle == null)
        return DiaryWalk(walk, emptyList(), "", preparing = true)

    val analysis = if (publication?.publishedBundle != null) StoryboardAnalysisView(
        LocalDiaryBoard.withUserChanges(
            GeoStoryboardBundle.parse(publication.publishedBundle),
            requireNotNull(publication.baseBundle), live, input.photoRows.map { it.id }.toSet(),
        ), true, "",
    ) else storyboardAnalysisView(input.analysis, input.entries, input.photoSync, input.photoRows)

    return diaryWalk(walk, live, photos, StoryboardDraft.parse(draftPayload), analysis, observations, measurement)
        .copy(published = publication != null, sourceEntries = live)
}
