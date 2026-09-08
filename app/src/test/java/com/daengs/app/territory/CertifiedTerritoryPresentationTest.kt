package com.daengs.app.territory

import com.daengs.app.auth.Session
import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.map.features.territory.*
import com.daengs.app.walk.*
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CertifiedTerritoryPresentationTest {
    @Test fun `camera is blocked by server protection and same walk retry opens after fresh expiry read`() = runTest {
        val auth = Session("owner", "token", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
        val fix = LocationSample(GeoPoint(37.5, 127.0), 2000, 2_000_000_000, 2f)
        val state = WalkTrackingState(ownerId = "owner", activeSessionId = WALK,
            activeSessionStartedAtMillis = 1000, activeDogIds = listOf(DOG),
            trail = TrailSnapshot(state = TrackingState.RECORDING), latestMomentFix = fix)
        val dao = MemoryActions(); val server = PhotoServer()
        val dir = kotlin.io.path.createTempDirectory("certified-view-").toFile()
        try {
            val sync = TerritoryActionSync(dao, server, { auth }, { "owner" }, { state }, backgroundScope, {})
            sync.photos = ServerTerritoryPhotos(sync, server, server, File(dir, "proof"), { auth }, { "owner" }, { state }, backgroundScope, {})
            sync.submit(state, SITE, DOG, markBody(WALK, SITE, DOG, fix)); sync.deliver(); runCurrent()
            val mark = dao.all().single { it.kind == "MARK" }
            val response = JSONObject(mark.response!!).put("photo_status", "VERIFIED").put("resolution_code", "site_changed")
            dao.update(mark.copy(response = response.toString())); runCurrent()
            val board = TerritoryBoardState(sites = listOf(TerritorySite(SITE, fix.point, 0.0)), selectedSiteId = SITE)
            var remote = SharedTerritorySite(SITE, 2,
                SharedTerritoryOccupancy(DOG2, "두부", false, ClaimCertification.VERIFIED, 2000, 602000),
                serverNowMillis = 2000, policyVersion = "certified-protection-v2")
            val provider = ServerTerritoryGameProvider(TerritoryOccupancyClient { _, _ -> listOf(remote) }, { auth }, { "owner" }, sync)
            fun view() = provider.snapshot(board, state, true, mapOf(DOG to "보리"), 2_000_000_000)
            provider.refresh(board.sites)
            assertFalse(view().canPhotograph)
            assertTrue(view().guidance.contains("보호 중"))
            // A stale cached countdown cannot authorize capture even when its local timer elapsed.
            remote = remote.copy(receivedAtNanos = System.nanoTime() - 700_000_000_000)
            provider.refresh(board.sites)
            assertFalse(view().canPhotograph)
            remote = remote.copy(serverNowMillis = 602000)
            provider.refresh(board.sites)
            assertTrue(view().canPhotograph)
            assertTrue(view().guidance.contains("새 사진"))
            remote = remote.copy(occupancy = remote.occupancy!!.copy(certification = ClaimCertification.UNVERIFIED, protectedUntilMillis = null), serverNowMillis = 2000)
            provider.refresh(board.sites)
            assertTrue(view().canPhotograph)
            // A policy change after this board read must retain its reason through prepareCapture.
            for ((reason, action, message) in listOf(
                Triple("protected", "WAIT", "보호"),
                Triple("season_ended", "UNAVAILABLE", "시즌"),
            )) {
                server.accessReason = reason; server.accessAction = action
                val error = runCatching {
                    provider.prepareCapture(SITE, board, state, true, mapOf(DOG to "보리"), 2_000_000_000, 2000)
                }.exceptionOrNull()
                assertTrue(error is TerritoryCaptureBlocked)
                assertTrue(error!!.message!!.contains(message))
                assertFalse(dao.all().any { it.kind == "PHOTO" })
                assertTrue(server.photos.isEmpty())
            }
        } finally { dir.deleteRecursively() }
    }
}
