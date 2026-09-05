package com.daengs.app.place

import com.daengs.app.location.GeoPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceSearchBatchTest {
    private fun sample() = javaClass.getResourceAsStream("/place_search_lab_sample.json")!!.bufferedReader().use {
        Json.parseToJsonElement(it.readText()).jsonObject.toPlaceSearchResponse()
    }
    private fun requests() = PlaceKind.entries.chunked(6).map {
        PlaceSearchRequest(GeoPoint(37.54,127.05), kinds = it, limitPerKind = 50, radiusMeters = 5000, nameQuery = "카페")
    }
    private fun response(request: PlaceSearchRequest): PlaceSearchResponse {
        val sample = sample()
        return sample.copy(groups = request.kinds.map { sample.groups.first().copy(kind = it) })
    }
    @Test fun threeBatchesPreserveGroupsButOverviewDeduplicatesPlaces() = runTest {
        val recorded = mutableListOf<PlaceSearchRequest>()
        val combined = searchPlaceBatches(PlaceSearchRepository { recorded += it; response(it) }, requests())
        assertEquals(3, recorded.size)
        assertEquals(PlaceKind.entries, combined.groups.map { it.kind })
        assertEquals(sample().groups.first().results.size, combined.overviewHits(false).size)
        val distances = combined.overviewHits(false).map { it.place.distanceMeters }
        assertEquals(distances.sorted(), distances)
        assertTrue(recorded.all { it.radiusMeters == 5000 && it.nameQuery == "카페" && it.kinds.size == 6 })
    }
    @Test fun oneFailedBatchCannotPublishSuccessAndCancelsSibling() = runTest {
        var cancelled = false
        val source = PlaceSearchRepository { request ->
            if (request.kinds.first() == PlaceKind.entries.first()) {
                try { awaitCancellation() } finally { cancelled = true }
            } else error("batch failed")
        }
        try { searchPlaceBatches(source, requests()); fail("must fail atomically") }
        catch (_: IllegalStateException) { }
        assertTrue(cancelled)
    }
    @Test fun missingGroupIsAnInvalidWholeResponse() = runTest {
        try {
            searchPlaceBatches(PlaceSearchRepository { response(it).copy(groups = emptyList()) }, requests())
            fail("must reject missing categories")
        } catch (_: kotlinx.serialization.SerializationException) { }
    }
}
