package com.daengs.app.map.style

/** Resolved display snapshot. Widths deliberately preserve the existing physical pixel sizes. */
data class WalkRouteAppearance(
    val speedPolicy: WalkStylePolicy,
    val themeId: String,
    val stroke: WalkRouteStroke = WalkRouteStroke(),
)

data class WalkRouteStroke(val widthPx: Int = 14, val outlineWidthPx: Int = 2, val outlineArgb: Int = -1) {
    init { require(widthPx > 0 && outlineWidthPx >= 0) }
}
