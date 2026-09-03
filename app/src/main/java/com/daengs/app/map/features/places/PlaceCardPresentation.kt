package com.daengs.app.map.features.places

import com.daengs.app.place.DogAccessState
import com.daengs.app.place.MedicalFacts
import com.daengs.app.place.PlaceKey
import com.daengs.app.place.PlaceKind
import com.daengs.app.place.PlaceResult
import com.daengs.app.place.PlaceSearchHit
import com.daengs.app.place.supportsParkingPreference

enum class PlaceTextTone {
    DEFAULT,
    MUTED,
    SUCCESS,
    WARNING,
    ERROR,
}

data class PlaceTextPresentation(
    val text: String,
    val tone: PlaceTextTone,
)

data class PlacePhoneAction(
    val label: String,
    val phone: String,
)

data class PlaceCardPresentation(
    val key: PlaceKey,
    val name: String,
    val meta: String,
    val parking: PlaceTextPresentation? = null,
    val dogAccess: PlaceTextPresentation? = null,
    val operation: PlaceTextPresentation? = null,
    val todayHours: String? = null,
    val sourceDate: String? = null,
    val hours: String? = null,
    val address: String? = null,
    val phoneAction: PlacePhoneAction? = null,
)

fun PlaceSearchHit.toCardPresentation(): PlaceCardPresentation {
    val result = place
    val medical = result.facts.medical
    val phone = result.facts.phone
    val hospital = result.match.kind == PlaceKind.HOSPITAL
    val operation = when {
        hospital -> PlaceTextPresentation(
            text = hospitalOperationLabel(medical, hasPhone = !phone.isNullOrBlank()),
            tone = if (medical?.openNow == null || phone.isNullOrBlank()) {
                PlaceTextTone.WARNING
            } else {
                PlaceTextTone.MUTED
            },
        )
        medical != null -> PlaceTextPresentation(
            text = openNowLabel(medical.openNow),
            tone = PlaceTextTone.DEFAULT,
        )
        else -> null
    }
    return PlaceCardPresentation(
        key = result.key,
        name = result.name,
        meta = "${categoryLabel(result.match.kind)} · ${formatPlaceMeters(result.distanceMeters)}",
        parking = if (result.match.kind.supportsParkingPreference()) {
            PlaceTextPresentation(
                text = parkingLabel(result.facts.parking),
                tone = when (result.facts.parking) {
                    true -> PlaceTextTone.SUCCESS
                    false -> PlaceTextTone.MUTED
                    null -> PlaceTextTone.WARNING
                },
            )
        } else {
            null
        },
        dogAccess = evaluations.dogAccess?.let { evaluation ->
            PlaceTextPresentation(
                text = "${dogAccessLabel(evaluation.state)} · " +
                    dogAccessReasonLabel(evaluation.reason),
                tone = when (evaluation.state) {
                    DogAccessState.COMPATIBLE -> PlaceTextTone.SUCCESS
                    DogAccessState.INCOMPATIBLE -> PlaceTextTone.ERROR
                    DogAccessState.UNKNOWN -> PlaceTextTone.WARNING
                },
            )
        },
        operation = operation,
        todayHours = todayHoursLabel(medical).takeIf { hospital },
        sourceDate = hospitalSourceDateLabel(result).takeIf { hospital },
        hours = result.facts.hoursText?.let { "영업시간 $it" },
        address = result.facts.address,
        phoneAction = phone?.let {
            PlacePhoneAction(callActionLabel(result.match.kind, it), it)
        },
    )
}

fun parkingLabel(value: Boolean?): String = when (value) {
    true -> "주차 가능"
    false -> "주차 불가"
    null -> "주차 정보 없음"
}

fun dogAccessLabel(state: DogAccessState): String = when (state) {
    DogAccessState.COMPATIBLE -> "입장 조건상 가능"
    DogAccessState.INCOMPATIBLE -> "조건 불일치"
    DogAccessState.UNKNOWN -> "정보 부족 · 확인 필요"
}

fun dogAccessReasonLabel(reason: String): String = when (reason) {
    "size_allowed" -> "크기 등급 허용"
    "size_exceeded" -> "크기 등급 초과"
    "weight_allowed" -> "무게 제한 허용"
    "weight_exceeded" -> "무게 제한 초과"
    "weight_boundary_unknown" -> "미만·이하 경계 확인 필요"
    "dog_disallowed" -> "개 입장 불가"
    "missing_dog_size" -> "개 크기 미상"
    "missing_dog_weight" -> "개 무게 미상"
    "missing_restriction" -> "시설 제한 정보 없음"
    else -> reason
}

fun hospitalOperationLabel(medical: MedicalFacts?, hasPhone: Boolean): String {
    val status = openNowLabel(medical?.openNow)
    val action = if (hasPhone) "방문 전 전화 확인" else "전화번호 정보 없음"
    return "$status · $action"
}

fun todayHoursLabel(medical: MedicalFacts?): String? = medical?.hoursToday
    ?.takeIf(List<*>::isNotEmpty)
    ?.joinToString(prefix = "오늘 ", separator = " · ") { range ->
        "${range.opensAt}~${range.closesAt}"
    }

fun hospitalSourceDateLabel(place: PlaceResult): String? = place.classifications
    .firstOrNull { classification ->
        classification.source == place.match.source && classification.kind == PlaceKind.HOSPITAL
    }
    ?.asOf
    ?.let { "인허가 정보 기준 $it" }

fun callActionLabel(kind: PlaceKind, phone: String): String = when (kind) {
    PlaceKind.HOSPITAL -> "병원에 전화해 확인 · $phone"
    else -> "전화 $phone"
}

private fun openNowLabel(value: Boolean?): String = when (value) {
    true -> "현재 영업으로 계산됨"
    false -> "현재 영업 종료로 계산됨"
    null -> "현재 영업 여부 미상"
}

internal fun formatPlaceMeters(meters: Int): String =
    if (meters >= 1_000) "%.1fkm".format(meters / 1_000.0) else "${meters}m"
