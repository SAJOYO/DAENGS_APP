package com.daengs.app.ui.places

import com.daengs.app.place.PerDogEvaluation
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Describe only the evaluated axis, never promise admission or a whole-party pass. */
fun dogEvaluationLabel(value: PerDogEvaluation): String {
    val access = when (value.dogAccess?.reason) {
        "weight_allowed" -> "체중 조건 충족"
        "weight_exceeded" -> "체중 제한 초과"
        "weight_boundary_unknown" -> "체중 경계 기준 확인 필요"
        "size_allowed" -> "크기 조건 충족"
        "size_exceeded" -> "크기 제한 불일치"
        "dog_disallowed" -> "강아지 동반 불가로 등록"
        "missing_dog_size" -> "크기 정보 확인 필요"
        "missing_dog_weight" -> "체중 정보 확인 필요"
        else -> "시설 조건 확인 필요"
    }
    val extra = when (value.restrictions?.get("reason")?.jsonPrimitive?.contentOrNull) {
        "age_denied" -> "나이 제한 불일치"
        "size_denied" -> "추가 크기 제한 불일치"
        "species_denied" -> "강아지 동반 제한"
        "missing_dog_age" -> "나이 정보 확인 필요"
        "no_blocking_condition" -> null
        else -> if (value.restrictions != null) "추가 조건 확인 필요" else null
    }
    return listOfNotNull(access, extra).joinToString(" · ")
}
