package com.daengs.app.map.features.places

import com.daengs.app.location.GeoPoint
import com.daengs.app.place.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceFilterControllerTest {
    private class Repository : PlaceSearchRepository {
        var legacyCalls = 0
        val filtered = mutableListOf<PlaceFilterRequest>()
        var execute: suspend (PlaceFilterRequest) -> PlaceFilterResponse = { filterResponseFixture(it) }
        override suspend fun search(request: PlaceSearchRequest): PlaceSearchResponse { legacyCalls++; return PlaceSearchResponse(null, emptyList(), request.dogs) }
        override suspend fun filterCapabilities() = filterCapabilitiesFixture()
        override suspend fun searchFiltered(request: PlaceFilterRequest): PlaceFilterResponse { filtered += request; return execute(request) }
    }
    private val criteria = PlaceFilterCriteria(listOf(PlaceKind.CAFE), all = listOf(PlaceFilterAtom("p", "operations.parking", "eq", JsonPrimitive(false))))

    @Test fun aiAdoptsExecutedFiltersWithoutCallingEitherSearchAgainAndRejectsStaleBase() = runTest {
        val repo = Repository()
        val controller = PlaceDiscoveryController(repo, null, this)
        controller.search(GeoPoint(37.5, 127.0), listOf(PlaceKind.CAFE)); controller.loadFilterCapabilities(); advanceUntilIdle()
        controller.applyFilters(criteria); advanceUntilIdle()
        val base = controller.captureFilterBase()!!
        val next = criteria.copy(all = emptyList())
        val executed = filterResponseFixture(PlaceFilterRequest(base.request, next, base.revision + 1))
        assertTrue(controller.acceptFilterEdit(base, executed))
        assertEquals(next, controller.state.value.filters)
        assertSame(executed, controller.state.value.filterResponse)
        assertEquals(1, repo.filtered.size); assertEquals(1, repo.legacyCalls)
        assertFalse(controller.acceptFilterEdit(base, executed))
        val latest = controller.captureFilterBase()!!
        controller.updateDogs(listOf(PlaceDogSnapshot("dog", "changed"))); advanceUntilIdle()
        assertFalse(controller.matchesFilterBase(latest))
    }

    @Test fun initialAiSnapshotPreservesNameParkingAndCannotSilentlyLimitAllCategories() = runTest {
        val controller = PlaceDiscoveryController(Repository(), null, this)
        controller.search(GeoPoint(37.5, 127.0), listOf(PlaceKind.CAFE), preferParking = true, nameQuery = "이름")
        controller.loadFilterCapabilities(); advanceUntilIdle()
        val base = controller.captureFilterBase()!!
        assertEquals("이름", base.request.nameQuery); assertEquals(1, base.criteria.preferences.size)
        assertTrue(controller.matchesFilterBase(base))
        controller.search(GeoPoint(37.5, 127.0), PlaceKind.entries); advanceUntilIdle()
        assertNull(controller.captureFilterBase())
    }

    @Test fun filtersCanBeClearedEvenAfterMovingOutsideTheSupportedArea() = runTest {
        val repo = Repository()
        val controller = PlaceDiscoveryController(repo, null, this)
        controller.search(GeoPoint(37.5, 127.0), listOf(PlaceKind.CAFE)); controller.loadFilterCapabilities(); advanceUntilIdle()
        controller.applyFilters(criteria); advanceUntilIdle()
        controller.search(GeoPoint(35.0, 120.0), listOf(PlaceKind.CAFE)); advanceUntilIdle()
        controller.applyFilters(null)
        assertNull(controller.state.value.filters)
        assertNull(controller.state.value.filterResponse)
        assertEquals(1, repo.legacyCalls)
    }

    @Test fun failedEditPreservesAppliedTreeAndDoesNotFallBack() = runTest {
        val repo = Repository()
        val controller = PlaceDiscoveryController(repo, null, this)
        controller.search(GeoPoint(37.5, 127.0), listOf(PlaceKind.CAFE)); controller.loadFilterCapabilities(); advanceUntilIdle()
        controller.applyFilters(criteria); advanceUntilIdle()
        val applied = controller.state.value
        repo.execute = { throw PlaceApiException(422, "contradictory_filters") }
        controller.applyFilters(criteria.copy(all = emptyList())); advanceUntilIdle()
        assertSame(applied.filterResponse, controller.state.value.filterResponse)
        assertEquals(criteria, controller.state.value.filters)
        assertNotNull(controller.state.value.filterError)
        assertEquals(1, repo.legacyCalls)
    }

    @Test fun cancelledEditCannotOverwriteNewerSearchEvenWhenTransportIgnoresCancellation() = runTest {
        val repo = Repository()
        val controller = PlaceDiscoveryController(repo, null, this)
        controller.search(GeoPoint(37.5, 127.0), listOf(PlaceKind.CAFE)); controller.loadFilterCapabilities(); advanceUntilIdle()
        val late = CompletableDeferred<Unit>()
        repo.execute = { request -> withContext(NonCancellable) { late.await(); filterResponseFixture(request) } }
        controller.applyFilters(criteria); runCurrent()
        controller.cancelFilterEdit()
        controller.search(GeoPoint(37.6, 127.1), listOf(PlaceKind.RESTAURANT)); runCurrent()
        late.complete(Unit); advanceUntilIdle()
        assertNull(controller.state.value.filters)
        assertEquals(listOf(PlaceKind.RESTAURANT), controller.state.value.requestedKinds)
    }

    @Test fun radiusLocationDogChangesAndRetryKeepTheAppliedFilterTree() = runTest {
        val repo = Repository()
        val controller = PlaceDiscoveryController(repo, null, this)
        controller.search(GeoPoint(37.5, 127.0), listOf(PlaceKind.CAFE)); controller.loadFilterCapabilities(); advanceUntilIdle()
        controller.applyFilters(criteria); advanceUntilIdle()
        controller.search(GeoPoint(37.6, 127.1), listOf(PlaceKind.CAFE), radiusMeters = 5000, nameQuery = "새 이름"); advanceUntilIdle()
        controller.updateDogs(listOf(PlaceDogSnapshot("pet", "r2", weightKg = 12.0))); advanceUntilIdle()
        controller.retry(); advanceUntilIdle()
        assertEquals(4, repo.filtered.size)
        assertTrue(repo.filtered.all { it.criteria == criteria })
        assertEquals(5000, repo.filtered.last().request.radiusMeters)
        assertEquals("새 이름", repo.filtered.last().request.nameQuery)
        assertEquals("r2", repo.filtered.last().request.dogs.single().revision)
        assertEquals(1, repo.legacyCalls)
        assertEquals(4, repo.filtered.map { it.requestId }.distinct().size)
    }
}
