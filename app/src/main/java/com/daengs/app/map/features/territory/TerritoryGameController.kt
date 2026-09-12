package com.daengs.app.map.features.territory

import com.daengs.app.territory.ClaimAccess
import com.daengs.app.territory.ClaimCertification
import com.daengs.app.territory.ClaimDisposition
import com.daengs.app.territory.ClaimSession
import com.daengs.app.territory.ClaimPhotoStatus
import com.daengs.app.territory.InMemoryTerritoryClaimRepository
import com.daengs.app.territory.SiteInteraction
import com.daengs.app.territory.TerritoryClaimSite
import com.daengs.app.territory.TerritorySite
import com.daengs.app.territory.TerritoryProximity
import com.daengs.app.location.LocationSample
import com.daengs.app.territory.evaluateClaimAccess
import com.daengs.app.walk.TrackingState
import com.daengs.app.walk.WalkTrackingState
import kotlin.math.roundToInt

/** Local play tuning, not the online capture contract. Inject when field-testing distances. */
data class TerritoryGamePolicy(val radiusMeters: Double = 20.0, val maxFixAgeNanos: Long = 10_000_000_000L)

data class TerritoryCaptureTarget(val session: ClaimSession, val siteId: String, val serverClaimId: String? = null)

enum class TerritoryOccupancyReadState { LOADING, READY, FAILED, LOGIN_REQUIRED }

data class TerritoryGameSite(
    val site: TerritorySite,
    val claim: TerritoryClaimSite,
    val ownerLabel: String,
    val interaction: SiteInteraction?,
    val distanceMeters: Double?,
    val attempted: Boolean,
    val occupancyKnown: Boolean = true,
    val occupancyReadState: TerritoryOccupancyReadState = if (occupancyKnown) TerritoryOccupancyReadState.READY else TerritoryOccupancyReadState.LOADING,
    val isOwnedByMe: Boolean? = null,
    val proximity: TerritoryProximity = TerritoryProximity(),
    val sharedState: com.daengs.app.territory.SharedTerritorySite? = null,
    val leaseLabel: String? = null,
) {
    val occupancyLabel: String get() = if (!occupancyKnown) when (occupancyReadState) {
        TerritoryOccupancyReadState.FAILED -> "점유 조회 실패"
        TerritoryOccupancyReadState.LOGIN_REQUIRED -> "로그인 필요"
        else -> "점유 확인 전"
    } else when (claim.occupancy?.certification) {
        null -> "미점유"
        ClaimCertification.UNVERIFIED -> "$ownerLabel · 미인증"
        ClaimCertification.VERIFIED -> "$ownerLabel · 인증"
    }
}

enum class TerritoryWalkPhase { BROWSING, WALKING, PAUSED }

data class TerritoryGameState(
    val enabled: Boolean = false,
    val phase: TerritoryWalkPhase = TerritoryWalkPhase.BROWSING,
    val claimingPetId: String? = null,
    val eligiblePets: Map<String, String> = emptyMap(),
    val petLocked: Boolean = false,
    val sites: List<TerritoryGameSite> = emptyList(),
    val targetId: String? = null,
    val representativeLabel: String? = null,
    val radiusMeters: Double = 20.0,
    val canMark: Boolean = false,
    val actionLabel: String = "영역표시",
    val guidance: String = "점령지를 선택해 주세요",
    val canPhotograph: Boolean = false,
    val photoActionLabel: String = "영역표시 인증 촬영",
    val photoStatus: ClaimPhotoStatus? = null,
    val feedback: com.daengs.app.map.layers.territory.TerritoryFeedback? = null,
    val readOnly: Boolean = false,
    val confirmedMarkId: String? = null,
    val confirmedMarkSiteId: String? = null,
    val confirmedMarkVerified: Boolean = false,
    val onlinePhotos: Boolean = false,
    val nearbyTargetId: String? = null,
    /** Automatically exposed map ranges; never a selection or an action target. */
    val visibleRangeSiteIds: Set<String> = emptySet(),
) {
    val target: TerritoryGameSite? get() = sites.firstOrNull { it.site.id == targetId }
    val nearbyTarget: TerritoryGameSite? get() = sites.firstOrNull { it.site.id == nearbyTargetId }
}

/** Map/UI adapter for the step-1 fake. Does not write behavior Pins or call the online API. */
class TerritoryGameController(
    private val repository: InMemoryTerritoryClaimRepository,
    private val policy: TerritoryGamePolicy = TerritoryGamePolicy(),
) : TerritoryGameProvider {
    private var preferredPetId: String? = null

    override fun selectPet(petId: String, siteId: String, tracking: WalkTrackingState) {
        val sessionId = tracking.activeSessionId ?: return
        if (tracking.trail.state != TrackingState.RECORDING || petId !in tracking.activeDogIds) return
        if (repository.attempt(sessionId, siteId) == null) preferredPetId = petId
    }

    override fun snapshot(
        board: TerritoryBoardState,
        tracking: WalkTrackingState,
        permitted: Boolean,
        petNames: Map<String, String>,
        nowNanos: Long,
        screenSample: LocationSample?,
    ): TerritoryGameState {
        repository.registerSites(board.sites.map { TerritoryClaimSite(it.id) })
        val session = session(tracking, board.selectedSiteId)
        val location = territoryLocationEvidence(tracking, permitted, nowNanos, policy.maxFixAgeNanos, screenSample)
        val trusted = location.trusted
        val sites = board.sites.map { site ->
            val claim = repository.site(site.id)
            val proximity = location.proximity(site, policy.radiusMeters)
            val distance = proximity.distanceMeters
            TerritoryGameSite(
                site, claim,
                claim.occupancy?.ownerPetId?.let { petNames[it] ?: "다른 강아지" }.orEmpty(),
                session?.let {
                    evaluateClaimAccess(
                        it.clientSessionId, site.id, tracking.trail.state == TrackingState.RECORDING,
                        trusted, distance ?: Double.NaN, proximity.accuracyMeters ?: Double.NaN,
                        policy.radiusMeters,
                    )
                },
                distance,
                session?.let { repository.attempt(it.clientSessionId, site.id) != null } ?: false,
                proximity = proximity,
            )
        }
        val target = sites.firstOrNull { it.site.id == board.selectedSiteId }

        val disposition = target?.let {
            com.daengs.app.territory.unverifiedClaimDisposition(it.claim, session?.claimingPetId.orEmpty())
        }
        val attempt = session?.let { s -> target?.let { repository.attempt(s.clientSessionId, it.site.id) } }
        val photoStatus = attempt?.photoStatus
        val canPhotograph = target?.interaction?.access == ClaimAccess.READY &&
            (photoStatus == null || photoStatus == ClaimPhotoStatus.NOT_SUBMITTED || photoStatus == ClaimPhotoStatus.REJECTED) &&
            (attempt != null || disposition != ClaimDisposition.ALREADY_OWNED ||
                target.claim.occupancy?.certification == ClaimCertification.UNVERIFIED)
        val guidance = when {
            tracking.trail.state != TrackingState.RECORDING -> "산책을 시작하거나 재개해 주세요"
            session == null -> "함께 걷는 강아지를 선택해 산책을 시작해 주세요"
            !trusted -> "정확한 현재 위치를 확인하고 있어요"
            target == null -> "지도에서 점령지를 찾아 주세요"
            photoStatus == ClaimPhotoStatus.PENDING -> "사진 확인 중 · 산책을 계속해도 돼요"
            photoStatus == ClaimPhotoStatus.RETRY_PENDING -> "사진은 보관 중이에요 · 판정을 다시 시도해 주세요"
            photoStatus == ClaimPhotoStatus.REJECTED -> "사진이 부적합해요 · 같은 장소에서 다시 촬영해 주세요"
            photoStatus == ClaimPhotoStatus.NOT_SUBMITTED && canPhotograph -> "사진을 찍어 영역표시를 인증할 수 있어요"
            target.attempted -> "이번 산책에서 이미 영역표시한 장소예요"
            target.interaction?.access == ClaimAccess.APPROACHING -> "${target.distanceMeters!!.roundToInt()}m · 가까이 가면 영역표시할 수 있어요"
            target.interaction?.access != ClaimAccess.READY -> "GPS 오차가 줄어들면 영역표시할 수 있어요"
            disposition == ClaimDisposition.PHOTO_REQUIRED -> "인증된 영역이에요 · 탈취에는 사진 인증이 필요해요"
            disposition == ClaimDisposition.POLICY_UNDECIDED -> "이미 다른 강아지가 표시한 영역이에요"
            disposition == ClaimDisposition.ALREADY_OWNED -> "이미 우리 강아지의 영역이에요"
            else -> "점령 준비 · 영역표시할 수 있어요"
        }
        return TerritoryGameState(
            enabled = true, sites = sites, targetId = target?.site?.id,
            phase = when {
                tracking.activeSessionId == null || tracking.trail.state == TrackingState.OFF -> TerritoryWalkPhase.BROWSING
                tracking.trail.state == TrackingState.PAUSED -> TerritoryWalkPhase.PAUSED
                else -> TerritoryWalkPhase.WALKING
            },
            claimingPetId = session?.claimingPetId,
            eligiblePets = tracking.activeDogIds.associateWith { petNames[it] ?: "강아지" },
            petLocked = attempt != null,
            representativeLabel = session?.claimingPetId?.let { petNames[it] ?: "대표 강아지" },
            radiusMeters = policy.radiusMeters,
            canMark = target?.interaction?.access == ClaimAccess.READY && !target.attempted &&
                disposition == ClaimDisposition.GRANTED,
            actionLabel = if (target?.attempted == true) "영역표시 완료" else "영역표시",
            guidance = guidance,
            canPhotograph = canPhotograph,
            photoStatus = photoStatus,
        )
    }

    override fun mark(
        siteId: String, board: TerritoryBoardState, tracking: WalkTrackingState,
        permitted: Boolean, petNames: Map<String, String>, nowNanos: Long, atMillis: Long,
    ): String {
        // Pin passes its displayed target ID; revalidate that exact target at tap time.
        if (board.sites.none { it.id == siteId }) return "점령지를 다시 선택해 주세요"
        val current = snapshot(board.copy(selectedSiteId = siteId), tracking, permitted, petNames, nowNanos)
        if (!current.canMark) return current.guidance
        val target = checkNotNull(current.target)
        val session = checkNotNull(session(tracking, siteId))
        repository.mark(
            session, checkNotNull(target.interaction),
            "local:${session.clientSessionId}:$siteId:${tracking.latestMomentFix?.elapsedRealtimeNanos}", atMillis,
        )
        return "${current.representativeLabel}의 영역으로 표시했어요"
    }

    override fun captureTarget(
        siteId: String, board: TerritoryBoardState, tracking: WalkTrackingState,
        permitted: Boolean, petNames: Map<String, String>, nowNanos: Long,
    ): TerritoryCaptureTarget? {
        if (board.sites.none { it.id == siteId }) return null
        val current = snapshot(board.copy(selectedSiteId = siteId), tracking, permitted, petNames, nowNanos)
        return if (current.canPhotograph) session(tracking, siteId)?.let { TerritoryCaptureTarget(it, siteId) } else null
    }

    /** Called at shutter time, not when opening the camera. Cancellation before shutter consumes nothing. */
    override fun captureAttempt(
        target: TerritoryCaptureTarget, board: TerritoryBoardState, tracking: WalkTrackingState,
        permitted: Boolean, petNames: Map<String, String>, nowNanos: Long, atMillis: Long,
    ): String? {
        if (captureTarget(target.siteId, board, tracking, permitted, petNames, nowNanos) != target) return null
        val current = snapshot(board.copy(selectedSiteId = target.siteId), tracking, permitted, petNames, nowNanos)
        return repository.mark(
            target.session, checkNotNull(current.target?.interaction),
            "local:${target.session.clientSessionId}:${target.siteId}:${tracking.latestMomentFix?.elapsedRealtimeNanos}", atMillis,
        ).attemptId
    }

    private fun session(tracking: WalkTrackingState, siteId: String?): ClaimSession? {
        val id = tracking.activeSessionId ?: return null
        val pinned = siteId?.let { repository.attempt(id, it)?.session?.claimingPetId }
        val pet = pinned?.takeIf { it in tracking.activeDogIds }
            ?: preferredPetId?.takeIf { it in tracking.activeDogIds }
            ?: tracking.activeDogIds.firstOrNull() ?: return null
        if (pinned != null && pinned != pet) return null
        // Local-only actor. Never forward this identity as an authenticated server account.
        return ClaimSession(id, "local-territory-preview", pet)
    }
}
