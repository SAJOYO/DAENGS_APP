package com.daengs.app.walk.diary

import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.WalkPhoto
import com.daengs.app.walk.WalkSummary

/** Title projection only: no entry parsing, user-edit reconciliation, or full diary assembly. */
internal fun diaryTitle(sessionId: String, input: DiaryBoardSource): String? {
    val bundle = if (input.publication != null)
        input.publication.publishedBundle?.let(GeoStoryboardBundle::parse)
    else input.analysis.bundle
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
    val publication = input.source.publication
    val live = input.entries
    if (publication != null && publication.publishedBundle == null)
        return DiaryWalk(walk, emptyList(), "", preparing = true)

    val analysis = if (publication?.publishedBundle != null) StoryboardAnalysisView(
        LocalDiaryBoard.withUserChanges(
            GeoStoryboardBundle.parse(publication.publishedBundle),
            requireNotNull(publication.baseBundle), live, input.photoIds,
        ), true, "",
    ) else input.source.analysis

    return diaryWalk(walk, live, photos, StoryboardDraft.parse(draftPayload), analysis, observations, measurement)
        .copy(published = publication != null, sourceEntries = live)
}
