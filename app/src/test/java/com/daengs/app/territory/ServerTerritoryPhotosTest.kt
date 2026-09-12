package com.daengs.app.territory

import com.daengs.app.territory.support.CLAIM
import com.daengs.app.territory.support.DOG
import com.daengs.app.territory.support.MemoryActions
import com.daengs.app.territory.support.PhotoServer
import com.daengs.app.territory.support.SITE
import com.daengs.app.territory.support.WALK
import com.daengs.app.auth.Session
import com.daengs.app.location.*
import com.daengs.app.walk.*
import com.daengs.app.map.features.territory.*
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerTerritoryPhotosTest {
    @Test fun `failed token refresh releases admission draft for a fresh retry`() = runTest {
        val dao = MemoryActions(); val server = PhotoServer(); val state = walking(); val dir = directory()
        try {
            val sync = TerritoryActionSync(dao, server, { auth }, { "owner" }, { state }, backgroundScope, {})
            val photos = ServerTerritoryPhotos(sync, server, server, File(dir, "proof"), { throw IOException("refresh offline") }, { "owner" }, { state }, backgroundScope, {})
            sync.photos = photos
            val mark = mark(sync, state)
            assertFalse(photos.checkAccess(mark))
            val id = UUID.randomUUID().toString()
            assertNull(photos.reserve(state, mark, photoCaptureBody(id, WALK, SITE, DOG, fix(), 3100), 1))
            assertFalse(dao.all().single { it.kind == "PHOTO" }.photoActive())
            assertTrue(server.photos.isEmpty())
        } finally { dir.deleteRecursively() }
    }
    @Test fun `changing walks while admission is pending cancels capture`() = runTest {
        val dao = MemoryActions(); val server = PhotoServer(); var state = walking(); val dir = directory()
        try {
            val sync = TerritoryActionSync(dao, server, { auth }, { "owner" }, { state }, backgroundScope, {})
            val photos = ServerTerritoryPhotos(sync, server, server, File(dir, "proof"), { auth }, { "owner" }, { state }, backgroundScope, {})
            sync.photos = photos
            val mark = mark(sync, state)
            server.afterAdmission = { state = state.copy(activeSessionId = UUID.randomUUID().toString()) }
            val id = UUID.randomUUID().toString()
            assertNull(photos.reserve(state, mark, photoCaptureBody(id, WALK, SITE, DOG, fix(), 3100), 1))
            assertFalse(dao.all().single { it.kind == "PHOTO" }.photoActive())
            assertTrue(server.photos.isEmpty())
        } finally { dir.deleteRecursively() }
    }
    @Test fun `v2 refuses protected admission before capture and keeps reason distinct from upload failure`() = runTest {
        val dao = MemoryActions(); val server = PhotoServer(); val state = walking(); val dir = directory()
        try {
            val sync = TerritoryActionSync(dao, server, { auth }, { "owner" }, { state }, backgroundScope, {})
            val photos = ServerTerritoryPhotos(sync, server, server, File(dir, "proof"), { auth }, { "owner" }, { state }, backgroundScope, {})
            sync.photos = photos
            val mark = mark(sync, state)
            server.accessAction = "WAIT"
            server.accessReason = "protected"
            val accessError = runCatching { photos.checkAccess(mark) }.exceptionOrNull()
            assertTrue(accessError is TerritoryCaptureBlocked)
            assertTrue(accessError!!.message!!.contains("보호"))
            server.admissionFailure = "protected"
            val id = UUID.randomUUID().toString()
            val error = runCatching { photos.reserve(state, mark, photoCaptureBody(id, WALK, SITE, DOG, fix(), 3100), 1) }.exceptionOrNull()
            assertTrue(error is TerritoryCaptureBlocked)
            assertTrue(error!!.message!!.contains("보호"))
            val refused = dao.all().single { it.kind == "PHOTO" }
            assertFalse(refused.photoActive())
            assertEquals("protected", refused.failure)
            assertFalse(refused.photoGuidance().contains("전송"))
            assertTrue(server.photos.isEmpty())
            assertNull(server.uploadedBytes)
            server.admissionFailure = null; server.accessAction = "PHOTO_UPGRADE"; server.accessReason = null
            assertTrue(photos.checkAccess(mark))
            val retry = UUID.randomUUID().toString()
            assertEquals(retry, photos.reserve(state, mark, photoCaptureBody(retry, WALK, SITE, DOG, fix(), 3100), 2))
            val admission = server.calls.last()
            assertTrue(admission.second.endsWith("/challenges/$retry"))
            assertEquals(2L, JSONObject(admission.third!!).getLong("expected_site_version"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun `protected verified verdict is a game rejection rather than a broken transfer`() {
        val row = TerritoryOperation(identity = "photo:x", ownerId = "owner", sessionId = WALK,
            kind = "PHOTO", body = "{}", state = "CONFIRMED", response = """{"stage":"COMPLETE"}""", failure = "protected")
        assertTrue(row.photoGuidance().contains("인증은 완료"))
        assertFalse(row.photoGuidance().contains("전송을 복구"))
    }
    @Test fun `pending verdict does not delay a fresh mark until photo retry`() = runTest {
        val dao = MemoryActions(); val server = PhotoServer(); val state = walking(); val dir = directory()
        try {
            val sync = TerritoryActionSync(dao, server, { auth }, { "owner" }, { state }, backgroundScope, {})
            val photos = ServerTerritoryPhotos(sync, server, server, File(dir, "proof"),
                { auth }, { "owner" }, { state }, backgroundScope, {})
            sync.photos = photos
            val mark = mark(sync, state)
            val id = UUID.randomUUID().toString()
            photos.reserve(state, mark, photoCaptureBody(id, WALK, SITE, DOG, fix(), 3100))
            photos.save(id, source(dir)).await(); runCurrent()
            assertFalse(photos.deliverBound()) // A real pending verdict, not a mocked scheduler result.
            val polls = server.calls.count { it.second == "/claims/$CLAIM" && it.first == "GET" }
            val second = "territory-site:hex-v1:140:2:2"
            val original = markBody(WALK, second, DOG, fix())
            val before = testScheduler.currentTime
            sync.submit(state, second, DOG, original)
            runCurrent() // No deliver() call and no advance to the 30-second retry.
            assertEquals(before, testScheduler.currentTime)
            assertEquals(original, server.claims.committed[second]!!.first)
            assertEquals(polls, server.calls.count { it.second == "/claims/$CLAIM" && it.first == "GET" })
            assertEquals("POLLING", dao.all().first { it.kind == "PHOTO" }.photoStage())
        } finally { dir.deleteRecursively() }
    }

    @Test fun `lost ticket reuses capture while account switch pauses upload under the old owner`() = runTest {
        val dao = MemoryActions(); val server = PhotoServer(); val state = walking(); var owner = "owner"; val dir = directory()
        try {
            val sync = TerritoryActionSync(dao, server, { auth.copy(appUserId = owner) }, { owner }, { state }, backgroundScope, {})
            val photos = ServerTerritoryPhotos(sync, server, server, File(dir, "proof"), { auth.copy(appUserId = owner) }, { owner }, { state }, backgroundScope, {})
            sync.photos = photos; val mark = mark(sync, state)
            val id = UUID.randomUUID().toString()
            photos.reserve(state, mark, photoCaptureBody(id, WALK, SITE, DOG, fix(), 3100))
            server.loseTicket = true
            photos.save(id, source(dir)).await(); runCurrent()
            assertEquals(1, server.photos.size)
            owner = "other"
            val before = server.calls.size
            assertTrue(sync.deliver()); assertTrue(photos.deliverBound())
            assertEquals(before, server.calls.size)
            owner = "owner"; server.decision = "VERIFIED"
            sync.deliver(); photos.deliverBound()
            assertEquals(1, server.photos.size)
            assertEquals(ClaimPhotoStatus.VERIFIED, sync.receipt.value!!.claim.photoStatus)
        } finally { dir.deleteRecursively() }
    }

    @Test fun `unfinished camera reservation recovers as recapture and cannot hold session end forever`() = runTest {
        val dao = MemoryActions(); val server = PhotoServer(); var state = walking(); val dir = directory()
        try {
            val sync = TerritoryActionSync(dao, server, { auth }, { "owner" }, { state }, backgroundScope, {})
            val photos = ServerTerritoryPhotos(sync, server, server, File(dir, "proof"), { auth }, { "owner" }, { state }, backgroundScope, {})
            sync.photos = photos; val mark = mark(sync, state)
            val id = UUID.randomUUID().toString()
            photos.reserve(state, mark, photoCaptureBody(id, WALK, SITE, DOG, fix(), 3100))
            state = WalkTrackingState(); sync.recover(); assertTrue(sync.deliver())
            assertEquals("capture_failed", dao.all().first { it.kind == "PHOTO" }.failure)
            assertTrue(server.photos.isEmpty())
            assertEquals("ENDED", server.claims.phase)
        } finally { dir.deleteRecursively() }
    }

    private val auth = Session("owner", "token", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
    private fun walking() = WalkTrackingState(ownerId = "owner", activeSessionId = WALK,
        activeSessionStartedAtMillis = 1000, activeDogIds = listOf(DOG),
        trail = TrailSnapshot(state = TrackingState.RECORDING), latestMomentFix = fix())
    private fun fix(accuracy: Float = 2f) = LocationSample(GeoPoint(37.5, 127.0), 3000, 3_000_000_000, accuracy)
    private suspend fun mark(sync: TerritoryActionSync, state: WalkTrackingState): TerritoryOperation {
        sync.syncTracking(); sync.deliver()
        sync.submit(state, SITE, DOG, markBody(WALK, SITE, DOG, fix()))
        sync.deliver()
        return sync.rows().last()
    }
    private fun directory() = kotlin.io.path.createTempDirectory("territory-test-").toFile()
    private fun source(dir: File) = File(dir, "camera.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }

    @Test fun `shutter reserves immutable evidence and binding releases walk end before verdict`() = runTest {
        val dao = MemoryActions(); val server = PhotoServer(); var state = walking(); val dir = directory()
        try {
            val sync = TerritoryActionSync(dao, server, { auth }, { "owner" }, { state }, backgroundScope, {})
            val photos = ServerTerritoryPhotos(sync, server, server, File(dir, "proof"), { auth }, { "owner" }, { state }, backgroundScope, {})
            sync.photos = photos
            val mark = mark(sync, state)
            val id = UUID.randomUUID().toString()
            val capture = photoCaptureBody(id, WALK, SITE, DOG, fix(), 3100)
            assertEquals(id, photos.reserve(state, mark, capture))
            assertNull(photos.reserve(state, mark, photoCaptureBody(UUID.randomUUID().toString(), WALK, SITE, DOG, fix(), 3200)))
            state = WalkTrackingState(); sync.syncTracking()
            assertFalse(sync.deliver()); assertEquals("RECORDING", server.claims.phase)
            assertTrue(photos.save(id, source(dir)).await()); runCurrent()
            assertEquals("ENDED", server.claims.phase)
            assertEquals(1, server.photos.size)
            assertEquals(capture, server.photos.values.single().body)
            assertEquals("PENDING", parseTerritoryClaim(sync.rows().first { it.kind == "MARK" }.response!!, mark.body).photoStatus.name)
            server.decision = "VERIFIED"
            assertTrue(photos.deliverBound())
            assertEquals(ClaimPhotoStatus.VERIFIED, sync.receipt.value!!.claim.photoStatus)
            assertEquals(ClaimCertification.VERIFIED, sync.receipt.value!!.claim.site.occupancy!!.certification)
            assertFalse(File(dir, "proof/$id.jpg").exists())
        } finally { dir.deleteRecursively() }
    }

    @Test fun `lost binding response and restart reuse capture then finish after end without replaying success`() = runTest {
        val dao = MemoryActions(); val server = PhotoServer(); var state = walking(); val dir = directory()
        try {
            fun sync() = TerritoryActionSync(dao, server, { auth }, { "owner" }, { state }, backgroundScope, {})
            fun photos(s: TerritoryActionSync) = ServerTerritoryPhotos(s, server, server, File(dir, "proof"), { auth }, { "owner" }, { state }, backgroundScope, {}).also { s.photos = it }
            val first = sync(); val delivery = photos(first); val mark = mark(first, state)
            val id = UUID.randomUUID().toString(); val capture = photoCaptureBody(id, WALK, SITE, DOG, fix(), 3100)
            delivery.reserve(state, mark, capture); server.loseBinding = true
            delivery.save(id, source(dir)).await(); runCurrent()
            assertEquals("PENDING", dao.all().last().state)
            state = WalkTrackingState(); server.decision = "VERIFIED"
            val restarted = sync(); val recovered = photos(restarted)
            restarted.recover(); assertTrue(restarted.deliver()); assertTrue(recovered.deliverBound())
            assertEquals("ENDED", server.claims.phase)
            assertEquals(1, server.photos.size)
            assertTrue(server.calls.filter { it.second == "/attempts" }.all { it.third == capture })
            assertFalse(restarted.receipt.value!!.animate)
        } finally { dir.deleteRecursively() }
    }

    @Test fun `expired URL lost PUT and lost confirm preserve bytes and refresh only the same ticket`() = runTest {
        val dao = MemoryActions(); val server = PhotoServer(); val state = walking(); val dir = directory()
        try {
            val sync = TerritoryActionSync(dao, server, { auth }, { "owner" }, { state }, backgroundScope, {})
            val photos = ServerTerritoryPhotos(sync, server, server, File(dir, "proof"), { auth }, { "owner" }, { state }, backgroundScope, {})
            sync.photos = photos; val mark = mark(sync, state)
            val id = UUID.randomUUID().toString()
            photos.reserve(state, mark, photoCaptureBody(id, WALK, SITE, DOG, fix(), 3100))
            server.expireUpload = true
            photos.save(id, source(dir)).await(); runCurrent()
            assertTrue(File(dir, "proof/$id.jpg").exists())
            server.loseUpload = true; assertFalse(photos.deliverBound())
            server.loseConfirm = true; assertFalse(photos.deliverBound())
            server.decision = "VERIFIED"; assertTrue(photos.deliverBound())
            assertEquals(1, server.photos.size)
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), server.uploadedBytes)
            assertEquals(2, server.calls.count { it.second.endsWith("/confirm") })
        } finally { dir.deleteRecursively() }
    }

    @Test fun `failed or rejected verdict allows a new photo of the same claim and never reconfirms terminal photo`() = runTest {
        val dao = MemoryActions(); val server = PhotoServer(); val state = walking(); val dir = directory()
        try {
            val sync = TerritoryActionSync(dao, server, { auth }, { "owner" }, { state }, backgroundScope, {})
            val photos = ServerTerritoryPhotos(sync, server, server, File(dir, "proof"), { auth }, { "owner" }, { state }, backgroundScope, {})
            sync.photos = photos; val mark = mark(sync, state)
            for (decision in listOf("FAILED", "REJECTED", "VERIFIED")) {
                val id = UUID.randomUUID().toString()
                assertEquals(id, photos.reserve(state, mark, photoCaptureBody(id, WALK, SITE, DOG, fix(), 3100)))
                server.decision = decision
                photos.save(id, source(dir)).await(); runCurrent()
            }
            assertEquals(3, server.photos.size)
            assertEquals(1, server.claims.committed.size)
            assertEquals(3, server.calls.count { it.second.endsWith("/confirm") })
            assertEquals(ClaimPhotoStatus.VERIFIED, sync.receipt.value!!.claim.photoStatus)
        } finally { dir.deleteRecursively() }
    }

    @Test fun `site changed is verified evidence without ownership win or another photo in this claim`() = runTest {
        val dao = MemoryActions(); val server = PhotoServer(); val state = walking(); val dir = directory()
        try {
            val sync = TerritoryActionSync(dao, server, { auth }, { "owner" }, { state }, backgroundScope, {})
            val photos = ServerTerritoryPhotos(sync, server, server, File(dir, "proof"), { auth }, { "owner" }, { state }, backgroundScope, {})
            sync.photos = photos; val mark = mark(sync, state)
            val id = UUID.randomUUID().toString()
            photos.reserve(state, mark, photoCaptureBody(id, WALK, SITE, DOG, fix(), 3100))
            server.siteChanged = true; server.decision = "VERIFIED"
            photos.save(id, source(dir)).await(); runCurrent()
            assertEquals("site_changed", sync.receipt.value!!.claim.resolutionCode)
            assertFalse(sync.receipt.value!!.claim.site.occupancy!!.isMine)
            assertFalse(sync.receipt.value!!.animate)
            assertNull(photos.reserve(state, mark, photoCaptureBody(UUID.randomUUID().toString(), WALK, SITE, DOG, fix(), 3200)))
        } finally { dir.deleteRecursively() }
    }

    @Test fun `twenty metre marking and ten metre camera are separate and shutter revalidates GPS and pause`() = runTest {
        val dao = MemoryActions(); val server = PhotoServer(); var state = walking(); val dir = directory()
        try {
            val sync = TerritoryActionSync(dao, server, { auth }, { "owner" }, { state }, backgroundScope, {})
            sync.photos = ServerTerritoryPhotos(sync, server, server, File(dir, "proof"), { auth }, { "owner" }, { state }, backgroundScope, {})
            val provider = ServerTerritoryGameProvider(TerritoryOccupancyClient { _, _ -> listOf(SharedTerritorySite(SITE, 0, null)) }, { auth }, { "owner" }, sync)
            val board = TerritoryBoardState(sites = listOf(TerritorySite(SITE, fix().point, 0.0)), selectedSiteId = SITE)
            fun view() = provider.snapshot(board, state, true, emptyMap(), 3_000_000_000)
            provider.refresh(board.sites); sync.deliver(); runCurrent()
            state = state.copy(latestMomentFix = fix(15f))
            assertTrue(view().canMark); assertFalse(view().canPhotograph)
            state = walking()
            val target = provider.prepareCapture(SITE, board, state, true, emptyMap(), 3_000_000_000, 3000)!!
            runCurrent()
            assertNotNull(target.serverClaimId)
            state = state.copy(trail = TrailSnapshot(state = TrackingState.PAUSED))
            assertNull(provider.beginCapture(target, board, state, true, emptyMap(), 3_000_000_000, 3100))
            state = walking().copy(latestMomentFix = fix(15f))
            assertNull(provider.beginCapture(target, board, state, true, emptyMap(), 3_000_000_000, 3100))
            state = walking()
            val capture = provider.beginCapture(target, board, state, true, emptyMap(), 3_000_000_000, 3100)!!
            server.decision = "VERIFIED"
            provider.saveCapture(capture, source(dir))!!.await(); runCurrent()
            assertTrue(view().confirmedMarkVerified)
            assertFalse(view().canPhotograph)
        } finally { dir.deleteRecursively() }
    }
}
