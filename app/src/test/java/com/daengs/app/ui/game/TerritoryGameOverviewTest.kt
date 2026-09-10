package com.daengs.app.ui.game

import com.daengs.app.activity.*
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TerritoryGameOverviewTest {
    @Test fun `ready score keeps breakdown and displays server sampled season deadline`() = runTest {
        val result = loadGameOverview(gameTestRepository(GameTestClient()), GAME_PET_A) { 10_000 }
        assertEquals(GameOverviewStatus.READY, result.status)
        assertEquals("1,240", result.points)
        assertEquals(4L, result.owned)
        assertEquals(6L, result.takeovers)
        assertEquals(1000L, result.summary!!.score!!.baseBonus)
        assertEquals(120L, result.summary!!.score!!.takeoverBonus)
        assertEquals(GAME_PET_A, result.summary!!.petId)
        assertEquals("9월 시즌", result.seasonTitle())
        val end = result.receivedAtNanos + (result.season!!.endsMs - result.season!!.serverNowMs) * 1_000_000
        assertEquals("종료까지 1분", result.seasonTime(end - 1_000_000))
        assertTrue(result.seasonTime(end).contains("결산"))
    }

    @Test fun `disabled no season failed and pending never invent a zero score`() = runTest {
        val client = GameTestClient(); val repository = gameTestRepository(client)
        client.error = ActivityHttpException(503, "activity_disabled")
        assertEquals(GameOverviewStatus.PREPARING, loadGameOverview(repository, GAME_PET_A).status)
        client.error = null; client.season = null
        val absent = loadGameOverview(repository, GAME_PET_A)
        assertEquals(GameOverviewStatus.NO_SEASON, absent.status); assertNull(absent.points)
        client.error = IOException("offline")
        val failed = loadGameOverview(repository, GAME_PET_A)
        assertEquals(GameOverviewStatus.ERROR, failed.status); assertNull(failed.points)
        val pending = previewGameOverview().copy(summary = previewGameOverview().summary!!.copy(
            status = ActivityTerritoryStatus.PENDING, score = null))
        assertTrue(pending.aggregating); assertNull(pending.points); assertNull(pending.owned)
        val emptyStats = ActivityTerritoryStatistics("v", "g", 0, 0, 0, 0, 0, 0, 0, 0)
        assertEquals("0", pending.copy(summary = pending.summary!!.copy(
            status = ActivityTerritoryStatus.READY, statistics = emptyStats)).points)
    }

    @Test fun `summary failure keeps only season and authentication is distinct`() = runTest {
        val client = object : GameTestClient() {
            override suspend fun territorySummary(token: String, seasonId: String, petId: String): ActivityTerritorySummary =
                throw IOException("summary unavailable")
        }
        val result = loadGameOverview(gameTestRepository(client), GAME_PET_A)
        assertEquals(GameOverviewStatus.ERROR, result.status)
        assertNotNull(result.season); assertNull(result.summary)
        client.error = ActivityHttpException(401, null)
        assertEquals(GameOverviewStatus.SIGN_IN, loadGameOverview(gameTestRepository(client), GAME_PET_A).status)
    }
}
