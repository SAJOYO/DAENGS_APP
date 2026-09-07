package com.daengs.app.territory

import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class TerritoryPhotoQueueTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun photo(): File = temporary.newFile().apply { writeBytes(byteArrayOf(1, 2, 3)) }
    private fun attempt(repo: InMemoryTerritoryClaimRepository, site: String, session: String = "s1", pet: String = "p1") =
        repo.mark(ClaimSession(session, "actor", pet), SiteInteraction(session, site, ClaimAccess.READY), "encounter-$session-$site", 0)

    @Test fun `multiple saved captures settle independently and retry reuses the saved capture`() = runTest {
        val repo = InMemoryTerritoryClaimRepository(listOf(TerritoryClaimSite("A"), TerritoryClaimSite("B")))
        val queue = TerritoryPhotoQueue(repo, backgroundScope)
        val a = attempt(repo, "A")
        val b = attempt(repo, "B")
        val fileA = photo(); val fileB = photo()
        queue.submit(a.attemptId, fileA, PhotoSimulation.FAILURE)
        queue.submit(b.attemptId, fileB, PhotoSimulation.DELAY)
        val captureA = queue.jobs.value.first().captureId
        runCurrent(); advanceTimeBy(2000); runCurrent()
        assertEquals(ClaimPhotoStatus.RETRY_PENDING, queue.jobs.value.first().status)
        assertEquals(ClaimPhotoStatus.PENDING, queue.jobs.value.last().status)
        assertTrue(fileA.exists()); assertTrue(fileB.exists())
        queue.retry(a.attemptId); queue.retry(a.attemptId)
        runCurrent(); advanceTimeBy(2000); runCurrent()
        assertEquals(captureA, queue.jobs.value.first().captureId)
        assertEquals(ClaimCertification.VERIFIED, repo.site("A").occupancy!!.certification)
        assertFalse(fileA.exists()); assertTrue(fileB.exists())
        advanceTimeBy(11000); runCurrent()
        assertEquals(ClaimCertification.VERIFIED, repo.site("B").occupancy!!.certification)
        assertFalse(fileB.exists())
    }

    @Test fun `rival remains until verdict and rejection allows new capture on same attempt`() = runTest {
        val repo = InMemoryTerritoryClaimRepository(listOf(TerritoryClaimSite("A",
            TerritoryOccupancy("rival", "old", "old-attempt", ClaimCertification.VERIFIED, 0))))
        val a = attempt(repo, "A")
        val queue = TerritoryPhotoQueue(repo, backgroundScope)
        val file = photo()
        queue.submit(a.attemptId, file, PhotoSimulation.REJECT)
        val firstCapture = queue.jobs.value.single().captureId
        assertEquals("rival", repo.site("A").occupancy!!.ownerPetId)
        runCurrent(); advanceTimeBy(2000); runCurrent()
        assertEquals("rival", repo.site("A").occupancy!!.ownerPetId)
        assertFalse(file.exists())
        queue.submit(a.attemptId, photo(), PhotoSimulation.ACCEPT)
        assertNotEquals(firstCapture, queue.jobs.value.single().captureId)
        runCurrent(); advanceTimeBy(2000); runCurrent()
        assertEquals("p1", repo.site("A").occupancy!!.ownerPetId)
        assertEquals(a.attemptId, repo.attempt("s1", "A")!!.attemptId)
    }

    @Test fun `missing retry file requests reshoot and concurrent result does not overwrite`() = runTest {
        val repo = InMemoryTerritoryClaimRepository(listOf(TerritoryClaimSite("A")))
        val queue = TerritoryPhotoQueue(repo, backgroundScope)
        val a = attempt(repo, "A")
        val file = photo()
        queue.submit(a.attemptId, file, PhotoSimulation.FAILURE)
        runCurrent(); advanceTimeBy(2000); runCurrent()
        file.delete(); queue.retry(a.attemptId); runCurrent()
        assertEquals(ClaimPhotoStatus.REJECTED, queue.jobs.value.single().status)
        queue.submit(a.attemptId, photo(), PhotoSimulation.DELAY)
        val rival = attempt(repo, "A", "s2", "p2")
        queue.submit(rival.attemptId, photo(), PhotoSimulation.ACCEPT)
        runCurrent(); advanceTimeBy(15000); runCurrent()
        assertEquals("p2", repo.site("A").occupancy!!.ownerPetId)
        assertTrue(queue.jobs.value.first().conflict)
    }
}
