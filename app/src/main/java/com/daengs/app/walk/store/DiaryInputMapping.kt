package com.daengs.app.walk.store

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.diary.DiaryBoardInput
import com.daengs.app.walk.diary.DiaryBoardSource
import com.daengs.app.walk.diary.DiaryPhotoInput
import com.daengs.app.walk.diary.DiaryPublicationInput
import com.daengs.app.walk.diary.StoryboardAnalysisInput
import com.daengs.app.walk.diary.StoryboardAnalysisView
import com.daengs.app.walk.sync.diaryInputStamp
import com.daengs.app.walk.sync.storyboardEntryStamp

/** Preserve the versioned storage stamps, including tombstones and pending mutations. */
fun storedStoryboardAnalysisView(analysis: WalkSceneAnalysisRow?, entries: List<WalkEntryRow>,
    photos: WalkPhotoSyncRow? = null, images: List<WalkPhotoRow> = emptyList(),
): StoryboardAnalysisView {
    val clean = entries.none { it.dirty || it.pinDirty || it.pendingRequest != null || it.syncError != null }
    val stamp = if (analysis?.bundleEntryStamp?.startsWith("diary:") == true)
        diaryInputStamp(entries, photos, images) else storyboardEntryStamp(entries)
    return com.daengs.app.walk.diary.storyboardAnalysisView(analysis?.let {
        StoryboardAnalysisInput(it.entryStamp, it.status, it.bundle, it.bundleEntryStamp)
    }, clean, stamp)
}

internal fun diaryBoardSource(entries: List<WalkEntryRow>, analysis: WalkSceneAnalysisRow?,
    photos: WalkPhotoSyncRow?, images: List<WalkPhotoRow>, publication: WalkDiaryPublicationRow?,
    walk: WalkSessionRow? = null, ownerId: String = "",
): DiaryBoardSource {
    if (analysis?.entryStamp?.startsWith(RelationalDiaryStorage.STAMP_PREFIX) == true) {
        return DiaryBoardSource(StoryboardAnalysisView(null, false, ""), null, relationalSelected = true,
            relational = RelationalDiaryStorage.project(analysis, entries, photos, images, walk, ownerId),
            relationalStatus = if (analysis.entryStamp == RelationalDiaryStorage.stamp(entries, photos, images)) analysis.status else "stale")
    }
    return DiaryBoardSource(
    // Publication has precedence: do not inspect legacy analysis or hash its inputs here.
    if (publication == null) storedStoryboardAnalysisView(analysis, entries, photos, images)
    else StoryboardAnalysisView(null, false, ""),
    publication?.let { DiaryPublicationInput(it.baseBundle, it.publishedBundle) },
)
}

internal fun diaryBoardInput(entries: List<WalkEntryRow>, analysis: WalkSceneAnalysisRow?,
    photos: WalkPhotoSyncRow?, images: List<WalkPhotoRow>, publication: WalkDiaryPublicationRow?,
    walk: WalkSessionRow? = null, ownerId: String = "",
): DiaryBoardInput = DiaryBoardInput(
    entries.mapNotNull { it.entry() },
    diaryBoardSource(entries, analysis, photos, images, publication, walk, ownerId),
    images.map { it.id }.toSet(),
)

fun WalkPhotoRow.toDiaryPhotoInput(): DiaryPhotoInput =
    DiaryPhotoInput(id, capturedAtMillis, GeoPoint(lat, lng))
