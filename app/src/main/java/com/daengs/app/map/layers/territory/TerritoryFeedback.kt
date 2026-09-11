package com.daengs.app.map.layers.territory

import kotlin.math.sin

enum class TerritoryFeedbackKind(val label: String) {
    READY("점령 준비 · 영역표시할 수 있어요"),
    MARKED("영역표시 완료 · 미인증"),
    VERIFIED("사진 인증 완료"),
}

/** 판정과 분리된 유한 효과. 생성 시각을 보관해 화면 재생성 때 처음부터 재생하지 않는다. */
data class TerritoryFeedback(val id: Long, val siteId: String, val kind: TerritoryFeedbackKind,
    val startedNanos: Long) {
    val durationMillis: Int get() = if (kind == TerritoryFeedbackKind.READY) 700 else 1100
    fun progressAt(nowNanos: Long): Float =
        ((nowNanos - startedNanos).toDouble() / (durationMillis * 1_000_000)).toFloat().coerceIn(0f, 1f)
}

data class TerritoryFeedbackFrame(val markerScale: Float = 1f, val glow: Float = 0f,
    val pawAlpha: Float = 0f, val pawScale: Float = 1f)

fun territoryFeedbackFrame(kind: TerritoryFeedbackKind?, progress: Float): TerritoryFeedbackFrame {
    if (kind == null || progress !in 0f..1f || progress == 1f) return TerritoryFeedbackFrame()
    val pulse = sin(Math.PI * progress).toFloat()
    val scale = if (kind == TerritoryFeedbackKind.READY) 1f else (1f + .18f * pulse).coerceAtMost(1.18f)
    return TerritoryFeedbackFrame(scale, pulse,
        if (kind == TerritoryFeedbackKind.READY) 0f else (pulse * 1.6f).coerceAtMost(1f),
        .8f + .35f * progress)
}
