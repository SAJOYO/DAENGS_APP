package com.daengs.app.map.features.places

import com.daengs.app.location.GeoPoint
import com.daengs.app.place.DogSearchContext
import com.daengs.app.place.PlaceFailure
import com.daengs.app.place.PlaceKind
import com.daengs.app.place.PlaceSearchConditions
import com.daengs.app.place.PlaceSearchRequest
import com.daengs.app.place.PlaceSearchResponse
import com.daengs.app.place.PlaceSearchRepository
import java.net.UnknownHostException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceDiscoveryControllerTest {
    @Test
    fun `an older response cannot replace a newer category search`() = runTest {
        val first = CompletableDeferred<PlaceSearchResponse>()
        val second = CompletableDeferred<PlaceSearchResponse>()
        var calls = 0
        val repository = PlaceSearchRepository { _: PlaceSearchRequest ->
            calls++
            if (calls == 1) first.await() else second.await()
        }
        val controller = PlaceDiscoveryController(repository, dogContext = null, scope = this)
        val oldResponse = response("old")
        val newResponse = response("new")

        controller.search(GeoPoint(37.5, 127.0), listOf(PlaceKind.CAFE))
        runCurrent()
        controller.search(GeoPoint(37.6, 127.1), listOf(PlaceKind.HOSPITAL))
        runCurrent()

        second.complete(newResponse)
        runCurrent()
        first.complete(oldResponse)
        advanceUntilIdle()

        assertEquals(listOf(PlaceKind.HOSPITAL), controller.state.value.requestedKinds)
        assertEquals(GeoPoint(37.6, 127.1), controller.state.value.origin)
        assertSame(newResponse, controller.state.value.response)
    }

    @Test
    fun `retry repeats the same origin provenance, not a fresh device search`() = runTest {
        val repository = PlaceSearchRepository { _: PlaceSearchRequest -> response("ok") }
        val controller = PlaceDiscoveryController(repository, dogContext = null, scope = this)

        controller.search(
            origin = GeoPoint(35.1796, 129.0756),
            kinds = listOf(PlaceKind.CAFE),
            originMode = PlaceOriginMode.PINNED,
        )
        advanceUntilIdle()
        controller.retry()
        advanceUntilIdle()

        assertEquals(PlaceOriginMode.PINNED, controller.state.value.originMode)
        assertEquals(GeoPoint(35.1796, 129.0756), controller.state.value.origin)
    }

    @Test
    fun `an unsupported coordinate becomes a typed failure without calling the server`() = runTest {
        var calls = 0
        val repository = PlaceSearchRepository { _: PlaceSearchRequest ->
            calls++
            response("unexpected")
        }
        val controller = PlaceDiscoveryController(repository, dogContext = null, scope = this)

        controller.search(GeoPoint(37.7749, -122.4194), listOf(PlaceKind.CAFE))
        advanceUntilIdle()

        assertEquals(0, calls)
        assertEquals(
            PlaceSearchState.Failed(PlaceFailure.UnsupportedLocation),
            controller.state.value.search,
        )
    }

    @Test
    fun `search caps results and projects representative dog values into the request`() = runTest {
        var captured: PlaceSearchRequest? = null
        val repository = PlaceSearchRepository { request ->
            captured = request
            response("ok")
        }
        val controller = PlaceDiscoveryController(
            repository = repository,
            dogContext = DogSearchContext(weightKg = 7.5, ageYears = 3.0),
            scope = this,
        )

        controller.search(GeoPoint(37.5, 127.0), listOf(PlaceKind.CAFE))
        advanceUntilIdle()

        assertEquals(PLACE_RESULT_LIMIT, captured?.limitPerKind)
        assertEquals(7.5, captured?.dogWeightKg)
        assertEquals(3.0, captured?.dogAgeYears)
    }

    @Test
    fun `a network failure is not represented as an empty result`() = runTest {
        val repository = PlaceSearchRepository { _: PlaceSearchRequest ->
            throw UnknownHostException("raw system detail")
        }
        val controller = PlaceDiscoveryController(repository, dogContext = null, scope = this)

        controller.search(GeoPoint(37.5, 127.0), listOf(PlaceKind.RESTAURANT))
        advanceUntilIdle()

        assertEquals(PlaceSearchState.Failed(PlaceFailure.Offline), controller.state.value.search)
        assertEquals(null, controller.state.value.response)
        assertTrue(controller.state.value.error!!.contains("인터넷 연결"))
    }

    private fun response(id: String) = PlaceSearchResponse(
        // 응답 identity 표시는 사라졌다(결정 #73) — 구분자는 테스트 안에서만 쓰는 무게 값이다.
        conditions = PlaceSearchConditions(
            dogSize = null,
            dogWeightKg = id.length.toDouble(),
            dogAgeYears = null,
        ),
        groups = emptyList(),
    )
}
