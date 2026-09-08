package com.daengs.app.walk.diary

import org.json.JSONObject

data class StoryboardSelection(
    val minimumTarget: Int, val selectedCount: Int, val minimumMet: Boolean,
    val coverageMet: Boolean, val longestUnreadMeters: Double, val maxUnreadMeters: Double,
    val deferredActionCount: Int,
) {
    fun description(): String = buildString {
        append("환경 조회 지점 ${selectedCount}곳 · 최소 목표 ${minimumTarget}곳")
        if (!minimumMet) append("\n서로 떨어진 유효 관측 구간이 부족해 목표를 채우지 못했어요. 짧은 경로·관측 공백·지점 간격 조건의 영향을 받을 수 있어요.")
        if (!coverageMet) append("\n보충하지 못한 가장 긴 경로 구간은 약 ${kotlin.math.round(longestUnreadMeters).toInt()}m예요. 빈 구간 목표 ${maxUnreadMeters.toInt()}m를 넘었어요.")
        if (deferredActionCount > 0) append("\n조회 지점 상한 8곳으로 행동 기록 ${deferredActionCount}개의 주변 조회를 보류했어요. 행동 기록은 남아 있어요.")
        append("\n조회 지점 수는 장면 수나 발견한 시설 수가 아니에요.")
    }

    companion object {
        fun parse(obj: JSONObject): StoryboardSelection {
            obj.exactKeys("minimum_target", "selected_count", "minimum_met", "shortfall_reason", "coverage_met",
                "longest_unread_m", "max_unread_m", "max_anchors", "deferred_action_count")
            val minimum = obj.strictLong("minimum_target", 1, 8).toInt()
            val count = obj.strictLong("selected_count", 0, 8).toInt()
            require(obj.get("minimum_met") is Boolean && obj.get("coverage_met") is Boolean)
            val met = obj.getBoolean("minimum_met")
            require(met == (count >= minimum))
            require(if (met) obj.isNull("shortfall_reason") else
                obj.getString("shortfall_reason") == "insufficient_distinct_valid_route")
            obj.strictLong("max_anchors", 8, 8)
            val deferred = obj.strictLong("deferred_action_count", 0, Int.MAX_VALUE.toLong()).toInt()
            require(obj.get("longest_unread_m") is Number && obj.get("max_unread_m") is Number)
            val longest = obj.getDouble("longest_unread_m"); val maxUnread = obj.getDouble("max_unread_m")
            require(longest.isFinite() && longest >= 0 && maxUnread.isFinite() && maxUnread > 0)
            return StoryboardSelection(minimum, count, met, obj.getBoolean("coverage_met"), longest, maxUnread, deferred)
        }
    }
}
