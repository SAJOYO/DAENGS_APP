package com.daengs.app.ui.places

import androidx.compose.ui.graphics.Color
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengPinkDeep

/** 검색 조작부의 기본 경계와 AI 검색 모드 색상. */
/**
 * 🔒 **앱 팔레트에서 가져온다** — `docs/design-locks.md` 0절 (2026-09-12).
 *
 * 전에는 AI 검색을 **파란 팔레트**(`#F1F9FF` · `#91C9ED` · `#327AA8`)로, 테두리와 시트 손잡이를
 * 차가운 회색(`#DEE1E5`)으로 칠했다. 크림 · 분홍 앱 한가운데에 다른 앱의 검색창이 뜬 것처럼
 * 보였다. AI 모드는 **분홍 테두리 · 연분홍 버튼**으로 갈린다 — 색의 계열이 아니라 진하기로.
 */
internal object PlaceSearchStyle {
    val Border = DaengsColors.BorderNeutral
    val AiBackground = DaengsColors.SurfaceMuted
    val AiBorder = DaengsColors.BrandPrimary
    val AiButton = DaengsColors.BrandPrimarySoft
    val AiForeground = DaengPinkDeep
}
