package com.daengs.app.map.style

/** Exact plateau/blend edges, so even narrow or uneven remote bands stay visible. */
fun WalkStylePolicy.speedScaleStops(themeId: String): List<Pair<Float, Int>> {
    val speeds = listOf(0.0) + boundaries.flatMap {
        listOf(it - blendHalfWidth, it + blendHalfWidth)
    } + speedMax
    return speeds.map { (it / speedMax).toFloat() to color(it, themeId) }
}
