package com.daengs.app.map.features.territory

import com.daengs.app.location.GeoPoint
import com.daengs.app.territory.NearbyTerritorySitesRequest
import com.daengs.app.territory.TerritoryFailure
import com.daengs.app.territory.TerritorySite
import com.daengs.app.territory.TerritorySitePage
import com.daengs.app.territory.TerritorySiteRepository
import java.net.UnknownHostException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TerritoryBoardControllerTest {
    private val seoul = GeoPoint(37.5, 127.0)

    @Test
    fun `activating loads a full read page around the device`() = runTest {
        var captured: NearbyTerritorySitesRequest? = null
        val controller = TerritoryBoardController(
            repository = TerritorySiteRepository { request ->
                captured = request
                page("first")
            },
            scope = this,
        )

        controller.activate(seoul)
        advanceUntilIdle()

        assertEquals(3_000, captured?.radiusMeters)
        assertEquals(500, captured?.limit)
        assertEquals(seoul, controller.state.value.loadedOrigin)
        assertEquals("first", controller.state.value.sites.single().id)
    }

    @Test
    fun `device movement inside the prefetched kilometer does not reload`() = runTest {
        var calls = 0
        val controller = TerritoryBoardController(
            TerritorySiteRepository { calls++; page("site-$calls") },
            this,
        )

        controller.activate(seoul)
        advanceUntilIdle()
        controller.onDevicePosition(GeoPoint(37.504, 127.0))
        advanceUntilIdle()

        assertEquals(1, calls)
    }

    @Test
    fun `camera movement is debounced and replaces the page after one kilometer`() = runTest {
        var calls = 0
        val controller = TerritoryBoardController(
            TerritorySiteRepository { calls++; page("site-$calls") },
            this,
        )
        controller.activate(seoul)
        advanceUntilIdle()

        controller.onCameraSettled(GeoPoint(37.52, 127.0))
        advanceTimeBy(349)
        assertEquals(1, calls)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(2, calls)
        assertEquals("site-2", controller.state.value.sites.single().id)
    }

    @Test
    fun `returning inside the loaded area cancels a pending far camera query`() = runTest {
        var calls = 0
        val controller = TerritoryBoardController(
            TerritorySiteRepository { calls++; page("site-$calls") },
            this,
        )
        controller.activate(seoul)
        advanceUntilIdle()

        controller.onCameraSettled(GeoPoint(37.52, 127.0))
        controller.onCameraSettled(GeoPoint(37.501, 127.0))
        advanceUntilIdle()

        assertEquals(1, calls)
    }

    @Test
    fun `old content remains visible while refresh fails`() = runTest {
        var calls = 0
        val second = CompletableDeferred<TerritorySitePage>()
        val controller = TerritoryBoardController(
            TerritorySiteRepository {
                calls++
                if (calls == 1) page("old") else second.await()
            },
            this,
            cameraDebounceMillis = 0,
        )
        controller.activate(seoul)
        advanceUntilIdle()

        controller.onCameraSettled(GeoPoint(37.52, 127.0))
        runCurrent()
        assertTrue(controller.state.value.loading)
        assertEquals("old", controller.state.value.sites.single().id)
        second.completeExceptionally(UnknownHostException())
        advanceUntilIdle()

        assertFalse(controller.state.value.loading)
        assertEquals("old", controller.state.value.sites.single().id)
        assertEquals(TerritoryFailure.Offline, controller.state.value.failure)
    }

    @Test
    fun `deactivation cancels a response that arrives after leaving territory mode`() = runTest {
        val response = CompletableDeferred<TerritorySitePage>()
        val controller = TerritoryBoardController(
            TerritorySiteRepository { response.await() },
            this,
        )
        controller.activate(seoul)
        runCurrent()

        controller.deactivate()
        response.complete(page("late"))
        advanceUntilIdle()

        assertFalse(controller.state.value.loading)
        assertTrue(controller.state.value.sites.isEmpty())
        assertNull(controller.state.value.loadedOrigin)
    }

    private fun page(id: String) = TerritorySitePage(
        count = 1,
        truncated = false,
        sites = listOf(TerritorySite(id, seoul, 10.0)),
    )
}
