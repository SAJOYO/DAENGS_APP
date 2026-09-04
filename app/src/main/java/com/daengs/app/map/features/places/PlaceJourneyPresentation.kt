package com.daengs.app.map.features.places

import com.daengs.app.journey.JourneyItem
import com.daengs.app.journey.JourneyLeg
import com.daengs.app.journey.JourneyMode
import com.daengs.app.journey.JourneyRouteStatus
import com.daengs.app.map.features.journey.PlaceJourneyState

data class PrimaryJourney(
    val mode: JourneyMode,
    val leg: JourneyLeg,
)

sealed interface JourneyActionPresentation {
    data object Ready : JourneyActionPresentation
    data object Loading : JourneyActionPresentation
    data class Failed(val message: String) : JourneyActionPresentation
    data object Unavailable : JourneyActionPresentation
    data class Handoff(
        val summary: String,
        val actionLabel: String,
        val url: String,
    ) : JourneyActionPresentation
}

fun PlaceJourneyState?.toActionPresentation(): JourneyActionPresentation = when {
    this == null -> JourneyActionPresentation.Ready
    loading -> JourneyActionPresentation.Loading
    error != null -> JourneyActionPresentation.Failed(error)
    else -> item?.let(::primaryJourney)?.let { primary ->
        JourneyActionPresentation.Handoff(
            summary = journeySummary(primary),
            actionLabel = "네이버 지도에서 ${journeyModeLabel(primary.mode)} 길찾기",
            url = requireNotNull(primary.leg.handoff).naver,
        )
    } ?: JourneyActionPresentation.Unavailable
}

fun primaryJourney(item: JourneyItem): PrimaryJourney? {
    val ordered = (item.modePriority + JourneyMode.entries).distinct()
    return ordered.asSequence()
        .mapNotNull { mode -> item.legs[mode]?.let { leg -> PrimaryJourney(mode, leg) } }
        .firstOrNull { it.leg.status != JourneyRouteStatus.UNAVAILABLE && it.leg.handoff != null }
        ?: ordered.asSequence()
            .mapNotNull { mode -> item.legs[mode]?.let { leg -> PrimaryJourney(mode, leg) } }
            .firstOrNull { it.leg.handoff != null }
}

fun journeySummary(journey: PrimaryJourney): String {
    val mode = journeyModeLabel(journey.mode)
    val leg = journey.leg
    if (leg.status == JourneyRouteStatus.UNAVAILABLE) {
        return "$mode 경로 정보 없음 · 지도앱에서 확인"
    }
    val duration = leg.minutes?.let { "약 ${it}분" } ?: "시간 정보 없음"
    val distance = leg.meters?.let(::formatPlaceMeters) ?: "거리 정보 없음"
    val status = when (leg.status) {
        JourneyRouteStatus.MEASURED -> "제공사 실측"
        JourneyRouteStatus.ESTIMATE -> "추정"
        JourneyRouteStatus.UNAVAILABLE -> error("handled above")
    }
    return "$mode $duration · $distance · $status"
}

fun journeyModeLabel(mode: JourneyMode): String = when (mode) {
    JourneyMode.WALK -> "도보"
    JourneyMode.CAR -> "자동차"
    JourneyMode.TRANSIT -> "대중교통"
}
