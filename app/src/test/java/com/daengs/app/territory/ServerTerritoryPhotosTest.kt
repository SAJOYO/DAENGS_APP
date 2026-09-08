package com.daengs.app.territory

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

internal class PhotoServer : TerritoryActionClient, TerritoryPhotoUploader {
    val claims = ClaimServer()
    data class Photo(val id: String, val body: String, var uploaded: Boolean = false, var status: String = "PENDING_UPLOAD")
    val photos = linkedMapOf<String, Photo>()
    val calls = mutableListOf<Triple<String, String, String?>>()
    var currentPhoto: Photo? = null
    var loseTicket = false
    var loseBinding = false
    var loseUpload = false
    var loseConfirm = false
    var expireUpload = false
    var decision: String? = null
    var siteChanged = false
    var rejectBinding: String? = null
    var uploadedBytes: ByteArray? = null
    var admissionFailure: String? = null
    var accessAction = "PHOTO_UPGRADE"
    var afterAdmission: (() -> Unit)? = null
    private fun ticket(photo: Photo) = JSONObject(photo.body)
        .put("attempt_id", photo.id).put("status", photo.status)
        .put("upload_url", if (photo.status == "PENDING_UPLOAD") "https://storage.invalid/${photo.id}?ticket=${calls.size}" else JSONObject.NULL)
        .put("upload_headers", JSONObject().put("Content-Type", "image/jpeg"))
        .put("expires_in_seconds", 300).toString()
    private fun claim(): String {
        val value = JSONObject(claims.committed[SITE]!!.second)
        currentPhoto?.let { photo ->
            if (photo.status == "VISION_PENDING" && decision != null) photo.status = decision!!
            value.put("current_photo_id", photo.id).put("photo_status", when (photo.status) {
                "VERIFIED" -> "VERIFIED"; "REJECTED" -> "REJECTED"; "FAILED" -> "RETRY_PENDING"; else -> "PENDING"
            })
            if (photo.status == "VERIFIED") {
                val site = value.getJSONObject("site")
                site.put("version", 2)
                if (siteChanged) {
                    value.put("resolution_code", "site_changed")
                    site.getJSONObject("occupancy").put("is_mine", false).put("owner_pet_id", DOG2)
                } else site.getJSONObject("occupancy").put("certification", "VERIFIED")
            }
        }
        return value.toString()
    }
    override suspend fun request(token: String, method: String, path: String, body: String?): String {
        calls += Triple(method, path, body)
        if (path.endsWith("/photo-access")) return JSONObject().put("allowed_action", accessAction).toString()
        if (path.contains("/challenges/")) {
            admissionFailure?.let { throw TerritoryActionException(409, it) }
            afterAdmission?.invoke()
            return JSONObject().put("challenge_id", path.substringAfterLast('/')).toString()
        }
        if (path == "/attempts") {
            val captureId = JSONObject(body!!).getString("client_capture_id")
            val photo = photos.getOrPut(captureId) { Photo(UUID.randomUUID().toString(), body) }
            check(photo.body == body)
            if (loseTicket) { loseTicket = false; throw IOException("ticket response lost") }
            return ticket(photo)
        }
        if (method == "PUT" && path.startsWith("/claims/")) {
            val photo = photos.values.single { it.id == path.substringAfterLast('/') }
            if (currentPhoto != photo) {
                rejectBinding?.let { throw TerritoryActionException(409, it) }
                if (claims.phase != "RECORDING") throw TerritoryActionException(409, "NOT_RECORDING")
                currentPhoto = photo
            }
            if (loseBinding) { loseBinding = false; throw IOException("binding response lost") }
            return claim()
        }
        if (path.endsWith("/confirm")) {
            val photo = photos.values.single { path == "/attempts/${it.id}/confirm" }
            if (!photo.uploaded) throw TerritoryActionException(409, "photo_not_uploaded")
            if (photo.status == "PENDING_UPLOAD") photo.status = "VISION_PENDING"
            if (loseConfirm) { loseConfirm = false; throw TerritoryActionException(503, null) }
            return ticket(photo)
        }
        if (method == "GET" && path == "/claims/$CLAIM") return claim()
        return claims.request(token, method, path, body)
    }
    override suspend fun upload(url: String, headers: Map<String, String>, file: File) {
        calls += Triple("UPLOAD", url, null)
        val photo = photos.values.single { url.contains(it.id) }
        check(currentPhoto == photo) { "must bind before upload" }
        assertEquals(mapOf("Content-Type" to "image/jpeg"), headers)
        if (expireUpload) { expireUpload = false; throw TerritoryUploadException(403) }
        val bytes = file.readBytes()
        if (uploadedBytes != null && photo.uploaded) assertArrayEquals(uploadedBytes, bytes)
        photo.uploaded = true; uploadedBytes = bytes
        if (loseUpload) { loseUpload = false; throw IOException("upload response lost") }
    }
}

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
            assertFalse(photos.checkAccess(mark))
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
            server.admissionFailure = null; server.accessAction = "PHOTO_UPGRADE"
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
