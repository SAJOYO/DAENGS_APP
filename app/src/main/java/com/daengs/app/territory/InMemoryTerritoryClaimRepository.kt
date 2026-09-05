package com.daengs.app.territory

import java.util.UUID

/** Step-1 fake only: process-local state, synchronous actions, explicitly driven photo outcomes.
 * Not registered in production DI. Capture IDs represent same-session camera evidence supplied
 * by the future capture adapter; this fake does not verify GPS, images, or account permissions.
 */
class InMemoryTerritoryClaimRepository(
    initialSites: List<TerritoryClaimSite>,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : TerritoryClaimRepository {
    private val sites = initialSites.associateBy { it.siteId }.toMutableMap()
    private val attempts = mutableMapOf<String, ClaimAttempt>()
    private val bySessionSite = mutableMapOf<Pair<String, String>, String>()
    private val captureOwners = mutableMapOf<String, String>()

    init {
        require(sites.size == initialSites.size)
    }

    @Synchronized
    fun registerSites(newSites: List<TerritoryClaimSite>) {
        newSites.forEach { sites.putIfAbsent(it.siteId, it) }
    }

    @Synchronized
    fun attempt(sessionId: String, siteId: String): ClaimAttempt? =
        bySessionSite[sessionId to siteId]?.let(attempts::getValue)

    @Synchronized
    override fun site(siteId: String): TerritoryClaimSite = sites.getValue(siteId)

    @Synchronized
    override fun mark(
        session: ClaimSession,
        interaction: SiteInteraction,
        encounterId: String,
        atMillis: Long,
    ): ClaimAttempt {
        require(interaction.clientSessionId == session.clientSessionId)
        val key = session.clientSessionId to interaction.siteId
        bySessionSite[key]?.let {
            return attempts.getValue(it).also { attempt -> require(attempt.session == session) }
        }
        require(interaction.access == ClaimAccess.READY) { "site_not_ready" }
        require(encounterId.isNotBlank())
        val current = site(interaction.siteId)
        val disposition = unverifiedClaimDisposition(current, session.claimingPetId)
        val id = newId()
        require(id.isNotBlank() && id !in attempts)
        val next = if (disposition == ClaimDisposition.GRANTED) {
            current.copy(
                occupancy = TerritoryOccupancy(
                    session.claimingPetId, session.clientSessionId, id,
                    ClaimCertification.UNVERIFIED, atMillis,
                ),
                version = current.version + 1,
            )
        } else current
        val attempt = ClaimAttempt(id, session, current.siteId, encounterId, next.version, disposition)
        sites[current.siteId] = next
        attempts[id] = attempt
        bySessionSite[key] = id
        return attempt
    }

    @Synchronized
    override fun submitPhoto(attemptId: String, captureId: String): ClaimAttempt {
        val attempt = attempts.getValue(attemptId)
        require(captureId.isNotBlank())
        if (attempt.captureId == captureId) return resume(attemptId)
        require(attempt.photoStatus in setOf(ClaimPhotoStatus.NOT_SUBMITTED, ClaimPhotoStatus.REJECTED))
        require(captureId !in captureOwners) { "capture_already_used" }
        captureOwners[captureId] = attemptId
        return attempt.copy(captureId = captureId, photoStatus = ClaimPhotoStatus.PENDING).save()
    }

    @Synchronized
    override fun resume(attemptId: String): ClaimAttempt {
        val attempt = attempts.getValue(attemptId)
        return if (attempt.photoStatus == ClaimPhotoStatus.RETRY_PENDING) {
            attempt.copy(photoStatus = ClaimPhotoStatus.PENDING).save()
        } else attempt
    }

    /** Call separately from submitPhoto to keep waiting/rejection/retry visible in previews. */
    @Synchronized
    fun resolvePhoto(
        attemptId: String,
        captureId: String,
        outcome: ClaimPhotoOutcome,
        atMillis: Long,
    ): ClaimAttempt {
        val attempt = attempts.getValue(attemptId)
        require(attempt.captureId == captureId) { "stale_capture" }
        if (attempt.photoStatus == ClaimPhotoStatus.VERIFIED) return attempt
        require(attempt.photoStatus == ClaimPhotoStatus.PENDING)
        when (outcome) {
            ClaimPhotoOutcome.REJECTED -> return attempt.copy(photoStatus = ClaimPhotoStatus.REJECTED).save()
            ClaimPhotoOutcome.RETRYABLE_FAILURE ->
                return attempt.copy(photoStatus = ClaimPhotoStatus.RETRY_PENDING).save()
            ClaimPhotoOutcome.ACCEPTED -> Unit
        }
        val current = site(attempt.siteId)
        // Concurrent settlement policy is deliberately deferred to the online contract.
        check(current.version == attempt.expectedSiteVersion) { "site_changed" }
        val owner = current.occupancy
        val strengthening = owner?.sourceAttemptId == attemptId
        check(owner == null || strengthening || owner.sourceSessionId != attempt.session.clientSessionId) {
            "new_session_required"
        }
        sites[current.siteId] = current.copy(
            occupancy = TerritoryOccupancy(
                attempt.session.claimingPetId, attempt.session.clientSessionId, attemptId,
                ClaimCertification.VERIFIED,
                if (strengthening) owner!!.occupiedAtMillis else atMillis,
            ),
            version = current.version + 1,
        )
        return attempt.copy(
            disposition = ClaimDisposition.GRANTED,
            photoStatus = ClaimPhotoStatus.VERIFIED,
            expectedSiteVersion = current.version + 1,
        ).save()
    }

    private fun ClaimAttempt.save(): ClaimAttempt = also { attempts[attemptId] = it }
}
