package com.daengs.app.walk

/** 완료 직후와 지난 산책 화면이 함께 읽는 저장된 한 세션. */
data class WalkSessionDetail(
    val summary: WalkSummary,
    val route: WalkSessionRoute,
)
