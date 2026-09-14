package com.daengs.app.walk.diary

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.WalkEntry

/** Stored source facts required by analysis presentation, without persistence metadata. */
data class StoryboardAnalysisInput(
    val entryStamp: String,
    val status: String,
    val bundle: String?,
    val bundleEntryStamp: String?,
)

internal data class DiaryPublicationInput(val baseBundle: String?, val publishedBundle: String?)

/** Lightweight title source. Constructing it must not parse entry bodies or publication bases. */
internal data class DiaryBoardSource(
    val analysis: StoryboardAnalysisView,
    val publication: DiaryPublicationInput?,
)

/** Full assembly receives already decoded entries, distinct from the lightweight title source. */
internal data class DiaryBoardInput(
    val entries: List<WalkEntry>,
    val source: DiaryBoardSource,
    val photoIds: Set<String>,
)

/** A recorded photo's facts; local board generation does not need files or upload state. */
data class DiaryPhotoInput(val id: String, val capturedAtMillis: Long, val point: GeoPoint)
