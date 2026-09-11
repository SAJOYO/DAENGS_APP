package com.daengs.app.ui.walk

import com.daengs.app.map.features.territory.*
import com.daengs.app.territory.*
import kotlin.math.ceil

internal data class TerritoryCardPresentation(
    val title: String,
    val badge: String,
    val subtitle: String,
    val distance: String,
    val reward: String?,
    val rewardDetail: String?,
)

/** Public occupancy does not contain a member's remaining reward budget or a public score. */
internal fun territoryCardPresentation(game: TerritoryGameState): TerritoryCardPresentation? {
    val target = game.target ?: return null
    val occupancy = target.claim.occupancy.takeIf { target.occupancyKnown }
    val distance = (target.proximity.distanceMeters ?: target.distanceMeters)
        ?.takeIf { it.isFinite() && it >= 0 }
        ?.let { "내 위치에서 ${ceil(it).toLong()}m" } ?: "현재 위치 확인 중"
    if (!target.occupancyKnown) return TerritoryCardPresentation(
        target.occupancyLabel, "점유 확인", "잠시 후 점유 상태를 다시 확인해 주세요", distance, null, null)
    val mine = target.isOwnedByMe == true
    val firstSeason = target.sharedState?.policyVersion == FIRST_SEASON_POLICY
    // Renewal belongs to the selected pet's actual action, not merely to the same member.
    val renewal = game.photoActionLabel == "사진으로 유지 연장"
    return TerritoryCardPresentation(
        title = if (occupancy == null) "아직 주인이 없어요" else target.ownerLabel.ifBlank { "이름 없는 강아지" },
        badge = when { occupancy == null -> "미점유"; mine -> "내 점령지"; else -> "다른 강아지의 영역" },
        subtitle = when {
            occupancy == null -> "우리 강아지의 영역으로 만들어보세요"
            occupancy.certification == ClaimCertification.VERIFIED -> "사진 인증 완료"
            else -> "영역 표시 · 미인증"
        },
        distance = distance,
        reward = when { !firstSeason -> null; renewal -> "유지 연장 · 0점"; else -> "장소별 시즌 보상 · 최대 100점" },
        rewardDetail = when {
            !firstSeason -> null
            renewal -> "새 사진으로 유지 시간을 연장해요"
            occupancy != null && target.isOwnedByMe == false -> "인증 탈취 보너스 20점 별도 · 이미 받은 보상 제외"
            else -> "일반 20점 · 인증하면 총 100점까지 · 이미 받은 보상 제외"
        },
    )
}

/** Permission comes exclusively from the provider; titles never enable an action. */
internal fun territoryPhotoButtonLabel(game: TerritoryGameState): String = when {
    game.photoStatus in setOf(ClaimPhotoStatus.REJECTED, ClaimPhotoStatus.RETRY_PENDING) -> "다시 촬영"
    game.photoActionLabel == "사진으로 유지 연장" -> "사진으로 유지 연장 · 0점"
    !game.onlinePhotos -> game.photoActionLabel
    game.target?.claim?.occupancy == null -> "강아지 인증하고 점령"
    game.target?.isOwnedByMe == true -> "강아지 인증하기"
    else -> "강아지 인증하고 도전"
}

internal fun territoryBrowsingGuidance(game: TerritoryGameState): String =
    if (!game.readOnly || game.guidance in setOf("점유 정보 · 둘러보기", "점령지를 선택해 주세요"))
        "산책을 시작하고 가까이 가면 점령할 수 있어요"
    else game.guidance
