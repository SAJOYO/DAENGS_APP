package com.daengs.app.walk.diary

data class StoryboardAnalysisView(
    val bundle: GeoStoryboardBundle?,
    val canReview: Boolean,
    val notice: String,
)

/** A cached source is readable after failure, but only a successful current attempt can be reviewed. */
fun storyboardAnalysisView(analysis: StoryboardAnalysisInput?, clean: Boolean, stamp: String,
): StoryboardAnalysisView {
    // Do not resurrect deleted/changed actions from the old bundle while showing current entries.
    val bundle = if (clean && analysis?.bundleEntryStamp == stamp)
        analysis.bundle?.let { runCatching { GeoStoryboardBundle.parse(it) }.getOrNull() } else null
    val current = bundle != null && analysis?.status == "ready" && analysis.entryStamp == stamp
    val notice = when {
        bundle != null && !current -> if (analysis?.status in setOf("pending", "running"))
            "최신 분석을 확인하고 있어요. 이전에 저장한 장면을 보여줘요."
            else "최신 분석을 확인하지 못했어요. 이전에 저장한 장면을 보여줘요."
        current -> bundle?.diary?.description().orEmpty()
        !clean -> "기록 동기화 후 장면을 다시 분석해요."
        analysis?.status == "pending" -> "산책 장면을 준비하고 있어요."
        analysis?.status == "running" -> "산책 장면을 분석하고 있어요."
        analysis?.status == "failed" -> "장면 분석에 실패했어요. 다시 시도할 수 있어요."
        else -> ""
    }
    return StoryboardAnalysisView(bundle, current, notice)
}
