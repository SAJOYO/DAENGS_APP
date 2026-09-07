package com.daengs.app.territory

import org.junit.Assert.*
import org.junit.Test

class TerritoryClaimTest {
    private val session = ClaimSession("s1", "u1", "p1")
    private fun ready(session: ClaimSession = this.session, site: String = "A") =
        SiteInteraction(session.clientSessionId, site, ClaimAccess.READY)
    private fun repository() = InMemoryTerritoryClaimRepository(listOf(TerritoryClaimSite("A"), TerritoryClaimSite("B")))

    @Test
    fun `shared scenarios exercise the whole claim cycle`() {
        val repo = repository()
        val attempts = mutableMapOf<Pair<String, String>, ClaimAttempt>()
        val rows = checkNotNull(javaClass.getResourceAsStream("/territory-claim-scenarios.tsv"))
            .bufferedReader().use { it.readLines() }.drop(1)
        rows.forEach { row ->
            val c = row.split('\t')
            val actor = ClaimSession(c[2], "user-${c[3]}", c[3])
            val key = c[2] to c[4]
            val old = attempts[key]
            val attempt = when (c[1]) {
                "mark" -> repo.mark(actor, ready(actor, c[4]), "encounter-${c[2]}-${c[4]}", 100)
                "submit" -> repo.submitPhoto(old!!.attemptId, c[5])
                "resume" -> repo.resume(old!!.attemptId)
                "resolve" -> repo.resolvePhoto(old!!.attemptId, c[5], ClaimPhotoOutcome.valueOf(c[6]), 200)
                else -> error("Unknown fixture action")
            }
            if (old != null) assertEquals(c[0], old.attemptId, attempt.attemptId)
            attempts[key] = attempt
            val site = repo.site(c[4])
            assertEquals(c[0], c[7], site.occupancy!!.ownerPetId)
            assertEquals(c[0], c[8], site.occupancy.certification.name)
            assertEquals(c[0], c[9], attempt.disposition.name)
            assertEquals(c[0], c[10], attempt.photoStatus.name)
        }
        assertEquals(5, attempts.size)
        assertEquals(4L, repo.site("A").version)
    }

    @Test
    fun `own unverified site can be certified on a later walk`() {
        val repo = repository()
        repo.mark(session, ready(), "e1", 1)
        val later = session.copy(clientSessionId = "s2")
        val attempt = repo.mark(later, ready(later), "e2", 2)
        assertEquals(ClaimDisposition.ALREADY_OWNED, attempt.disposition)
        assertEquals(1L, repo.site("A").version)
        repo.submitPhoto(attempt.attemptId, "c1")
        repo.resolvePhoto(attempt.attemptId, "c1", ClaimPhotoOutcome.ACCEPTED, 3)
        assertEquals(ClaimCertification.VERIFIED, repo.site("A").occupancy!!.certification)
    }

    @Test
    fun `access is independent of occupancy and rejects unusable location`() {
        fun access(recording: Boolean = true, trusted: Boolean = true, distance: Double = 2.0, accuracy: Double = 1.0) =
            evaluateClaimAccess("s1", "A", recording, trusted, distance, accuracy, 10.0)
        assertEquals(ClaimAccess.READY, access().access)
        assertEquals(ClaimAccess.APPROACHING, access(distance = 12.0).access)
        assertEquals(ClaimAccessReason.NOT_RECORDING, access(recording = false).reason)
        assertEquals(ClaimAccessReason.UNTRUSTED_LOCATION, access(trusted = false).reason)
        assertEquals(ClaimAccess.UNAVAILABLE, access(accuracy = 9.0).access)
        assertEquals(ClaimAccess.UNAVAILABLE, access(distance = Double.NaN).access)
    }

    @Test
    fun `unready action does not consume opportunity and representative stays fixed`() {
        val repo = repository()
        assertThrows(IllegalArgumentException::class.java) {
            repo.mark(session, ready().copy(access = ClaimAccess.APPROACHING), "e1", 1)
        }
        assertNull(repo.site("A").occupancy)
        val attempt = repo.mark(session, ready(), "e1", 1)
        assertEquals(attempt, repo.mark(session, ready(), "e2", 2))
        assertThrows(IllegalArgumentException::class.java) {
            repo.mark(session.copy(claimingPetId = "another-dog"), ready(), "e2", 2)
        }
    }

    @Test
    fun `old capture callback cannot certify a reshoot or another site`() {
        val repo = repository()
        val a = repo.mark(session, ready(), "e1", 1)
        repo.submitPhoto(a.attemptId, "c1")
        repo.resolvePhoto(a.attemptId, "c1", ClaimPhotoOutcome.REJECTED, 2)
        repo.submitPhoto(a.attemptId, "c2")
        assertThrows(IllegalArgumentException::class.java) {
            repo.resolvePhoto(a.attemptId, "c1", ClaimPhotoOutcome.ACCEPTED, 3)
        }
        val b = repo.mark(session, ready(site = "B"), "e2", 3)
        assertThrows(IllegalArgumentException::class.java) { repo.submitPhoto(b.attemptId, "c1") }
        assertEquals(ClaimCertification.UNVERIFIED, repo.site("A").occupancy!!.certification)
    }

    @Test
    fun `concurrent result cannot silently overwrite a changed site`() {
        val repo = repository()
        val a = repo.mark(session, ready(), "e1", 1)
        repo.submitPhoto(a.attemptId, "c1")
        val rival = ClaimSession("s2", "u2", "p2")
        val b = repo.mark(rival, ready(rival), "e2", 2)
        repo.submitPhoto(b.attemptId, "c2")
        repo.resolvePhoto(b.attemptId, "c2", ClaimPhotoOutcome.ACCEPTED, 3)
        assertThrows(IllegalStateException::class.java) {
            repo.resolvePhoto(a.attemptId, "c1", ClaimPhotoOutcome.ACCEPTED, 4)
        }
        assertEquals("p2", repo.site("A").occupancy!!.ownerPetId)
    }
}
