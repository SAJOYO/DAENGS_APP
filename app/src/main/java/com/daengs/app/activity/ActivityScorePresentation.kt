package com.daengs.app.activity

import java.math.BigDecimal
import java.math.RoundingMode

fun activityScoreBreakdown(score: ActivityScore): String? {
    val base = score.baseBonus ?: return null
    val takeover = score.takeoverBonus ?: return null
    val holding = BigDecimal(score.holdingUnits).divide(BigDecimal("36000000000"), 1, RoundingMode.DOWN)
        .stripTrailingZeros().toPlainString()
    return "기본 점령 ${base}점 · 탈취 보너스 ${takeover}점 · 보유 ${holding}점"
}
