package com.daengs.app.territory

/** Client session identity is retained when the walk is uploaded after recording. */
data class ClaimSession(
    val clientSessionId: String,
    val actorUserId: String,
    val claimingPetId: String,
) {
    init {
        require(listOf(clientSessionId, actorUserId, claimingPetId).all { it.isNotBlank() })
    }
}

enum class ClaimCertification { UNVERIFIED, VERIFIED }

data class TerritoryOccupancy(
    val ownerPetId: String,
    /** Shared reads omit these private source IDs; local claims retain their real identities. */
    val sourceSessionId: String?,
    val sourceAttemptId: String?,
    val certification: ClaimCertification,
    val occupiedAtMillis: Long,
)

/** Null occupancy is neutral. Location remains in TerritorySite, joined by siteId. */
data class TerritoryClaimSite(
    val siteId: String,
    val occupancy: TerritoryOccupancy? = null,
    val version: Long = 0,
)

enum class ClaimAccess { UNAVAILABLE, APPROACHING, READY }
enum class ClaimAccessReason { NOT_RECORDING, UNTRUSTED_LOCATION, OUT_OF_RANGE }

data class SiteInteraction(
    val clientSessionId: String,
    val siteId: String,
    val access: ClaimAccess,
    val reason: ClaimAccessReason? = null,
)

/** Distances/limits are supplied by the location adapter; no new product radius is chosen here. */
fun evaluateClaimAccess(
    sessionId: String,
    siteId: String,
    recording: Boolean,
    trustedLocation: Boolean,
    distanceMeters: Double,
    accuracyMeters: Double,
    radiusMeters: Double,
): SiteInteraction {
    require(radiusMeters.isFinite() && radiusMeters > 0)
    val reason = when {
        !recording -> ClaimAccessReason.NOT_RECORDING
        !trustedLocation || !distanceMeters.isFinite() || distanceMeters < 0 ||
            !accuracyMeters.isFinite() || accuracyMeters < 0 -> ClaimAccessReason.UNTRUSTED_LOCATION
        distanceMeters > radiusMeters -> ClaimAccessReason.OUT_OF_RANGE
        distanceMeters + accuracyMeters > radiusMeters -> ClaimAccessReason.UNTRUSTED_LOCATION
        else -> null
    }
    return SiteInteraction(
        sessionId, siteId,
        when (reason) {
            null -> ClaimAccess.READY
            ClaimAccessReason.OUT_OF_RANGE -> ClaimAccess.APPROACHING
            else -> ClaimAccess.UNAVAILABLE
        },
        reason,
    )
}

enum class ClaimDisposition { GRANTED, PHOTO_REQUIRED, POLICY_UNDECIDED, ALREADY_OWNED }
enum class ClaimPhotoStatus { NOT_SUBMITTED, PENDING, VERIFIED, REJECTED, RETRY_PENDING }
enum class ClaimPhotoOutcome { ACCEPTED, REJECTED, RETRYABLE_FAILURE }

data class ClaimAttempt(
    val attemptId: String,
    val session: ClaimSession,
    val siteId: String,
    val encounterId: String,
    val expectedSiteVersion: Long,
    val disposition: ClaimDisposition,
    val captureId: String? = null,
    val photoStatus: ClaimPhotoStatus = ClaimPhotoStatus.NOT_SUBMITTED,
)

fun unverifiedClaimDisposition(site: TerritoryClaimSite, petId: String): ClaimDisposition = when {
    site.occupancy == null -> ClaimDisposition.GRANTED
    site.occupancy.ownerPetId == petId -> ClaimDisposition.ALREADY_OWNED
    site.occupancy.certification == ClaimCertification.VERIFIED -> ClaimDisposition.PHOTO_REQUIRED
    else -> ClaimDisposition.POLICY_UNDECIDED
}

/** UI actions. An online implementation will obtain authoritative results from the server. */
interface TerritoryClaimRepository {
    fun site(siteId: String): TerritoryClaimSite
    fun mark(
        session: ClaimSession,
        interaction: SiteInteraction,
        encounterId: String,
        atMillis: Long,
    ): ClaimAttempt
    fun submitPhoto(attemptId: String, captureId: String): ClaimAttempt
    fun resume(attemptId: String): ClaimAttempt
}
