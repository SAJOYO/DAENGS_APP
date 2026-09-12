package com.daengs.app.ui.walk

import com.daengs.app.walk.diary.DiarySlotEvidence
import org.json.JSONObject

internal fun slotEvidenceTitle(role: String): String = when (role) {
    "scene_registered_point_distance" -> "등록 지점까지의 거리"
    "scene_geometry_distance" -> "공간 형상까지의 거리"
    "scene_area_context" -> "주변 영역의 집계"
    "scene_address_reference" -> "위치 설명"
    "before_scene_motion" -> "이 기록에 앞선 움직임"
    "scene_motion" -> "이 기록 구간의 움직임"
    "regional_observation" -> "지역 환경 관측"
    "grid_temperature_observation" -> "격자 기온 관측"
    else -> "장면의 배경 자료"
}

/** Show the supplied facts, without turning a nearby point into a visit or slow motion into a stop. */
internal fun slotEvidenceDescription(evidence: DiarySlotEvidence): String {
    val facts = JSONObject(evidence.facts)
    val labels = mapOf(
        "name" to "이름", "distance_m" to "거리 (m)", "radius_m" to "집계 반경 (m)",
        "temperature_c" to "기온 (°C)", "wind_mps" to "풍속 (m/s)",
        "observed_at" to "관측 시각", "observation_age_s" to "기록보다 앞선 시간 (초)",
        "grid" to "격자 (nx, ny)", "provider" to "관측 출처",
        "issued_at" to "공급자 기준 시각", "retrieved_at" to "자료 조회 시각",
        "precipitation_mm" to "강수량 (mm)", "sky" to "하늘 상태", "interpretation" to "관측 의미",
        "temporal_relation" to "기록과의 시간 관계",
        "sido" to "시·도", "sigungu" to "시·군·구", "dong" to "행정동",
        "registered_count" to "등록 업소 수", "other_count" to "기타 업종 수",
    )
    val lines = labels.mapNotNull { (key, label) ->
        if (!facts.has(key) || facts.isNull(key)) null else "$label: ${facts.get(key)}"
    }.toMutableList()
    facts.optJSONArray("categories")?.let { categories ->
        lines += "주변 구성: " + (0 until categories.length()).joinToString(" · ") {
            val item = categories.getJSONObject(it)
            "${item.getString("name")} ${item.getInt("count")}곳"
        }
    }
    if (facts.has("complete") && !facts.getBoolean("complete")) lines += "수집된 일부 자료에 한한 결과예요."
    // Timestamps keep the provider's explicit offset. Full evidence is available in the raw view.
    return lines.takeIf { it.isNotEmpty() }?.joinToString("\n") ?: facts.toString(2)
}
