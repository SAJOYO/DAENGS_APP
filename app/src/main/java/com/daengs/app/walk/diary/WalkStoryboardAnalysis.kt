package com.daengs.app.walk.diary

import com.daengs.app.walk.store.WalkEntryRow
import com.daengs.app.walk.store.WalkSceneAnalysisRow
import com.daengs.app.walk.sync.storyboardEntryStamp

data class StoryboardAnalysisView(
    val bundle: GeoStoryboardBundle?,
    val canReview: Boolean,
    val notice: String,
)

/** A cached source is readable after failure, but only a successful current attempt can be reviewed. */
fun storyboardAnalysisView(analysis: WalkSceneAnalysisRow?, entries: List<WalkEntryRow>,
    photos: com.daengs.app.walk.store.WalkPhotoSyncRow? = null,
    images: List<com.daengs.app.walk.store.WalkPhotoRow> = emptyList(),
): StoryboardAnalysisView {
    val clean = entries.none { it.dirty || it.pinDirty || it.pendingRequest != null || it.syncError != null }
    val stamp = if (analysis?.bundleEntryStamp?.startsWith("diary:") == true)
        com.daengs.app.walk.sync.diaryInputStamp(entries, photos, images) else storyboardEntryStamp(entries)
    // Do not resurrect deleted/changed actions from the old bundle while showing current entries.
    val bundle = if (clean && analysis?.bundleEntryStamp == stamp)
        analysis.bundle?.let { runCatching { GeoStoryboardBundle.parse(it) }.getOrNull() } else null
    val current = bundle != null && analysis?.status == "ready" && analysis.entryStamp == stamp
    val notice = when {
        bundle != null && !current -> if (analysis?.status == "running")
            "최신 분석을 확인하고 있어요. 이전에 저장한 장면을 보여줘요."
            else "최신 분석을 확인하지 못했어요. 이전에 저장한 장면을 보여줘요."
        current -> bundle?.diary?.description().orEmpty()
        !clean -> "기록 동기화 후 장면을 다시 분석해요."
        analysis?.status == "running" -> "산책 장면을 분석하고 있어요."
        analysis?.status == "failed" -> "장면 분석에 실패했어요. 다시 시도할 수 있어요."
        else -> ""
    }
    return StoryboardAnalysisView(bundle, current, notice)
}
