package com.daengs.app.walk.diary

import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.WalkPhoto
import com.daengs.app.walk.WalkSummary

/** Title projection only: no entry parsing, user-edit reconciliation, or full diary assembly. */
internal fun diaryTitle(sessionId: String, input: DiaryBoardSource): String? {
    if (input.relationalSelected) return input.relational?.published?.bundle
        ?.takeIf { it.clientSessionId == sessionId }?.title
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
    if (input.source.relationalSelected) return relationalDiaryWalk(walk, input, photos, draftPayload)
        .withBoundaryScenes(StoryboardDraft.parse(draftPayload))
    val publication = input.source.publication
    val live = input.entries
    if (publication != null && publication.publishedBundle == null)
        // The pending board still owns prose/photos. Original actions can be inspected independently.
        return DiaryWalk(walk, emptyList(), "", preparing = true,
            sourceEntries = live.filter { it.type != com.daengs.app.walk.WalkMomentType.NOTE })

    val analysis = if (publication?.publishedBundle != null) StoryboardAnalysisView(
        LocalDiaryBoard.withUserChanges(
            GeoStoryboardBundle.parse(publication.publishedBundle),
            requireNotNull(publication.baseBundle), live, input.photoIds,
        ), true, "",
    ) else input.source.analysis

    return diaryWalk(walk, live, photos, StoryboardDraft.parse(draftPayload), analysis, observations, measurement)
        .copy(published = publication != null, sourceEntries = live)
        .withBoundaryScenes(StoryboardDraft.parse(draftPayload))
}
