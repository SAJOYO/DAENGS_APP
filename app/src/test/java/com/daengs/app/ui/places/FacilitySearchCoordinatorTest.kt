package com.daengs.app.ui.places

import com.daengs.app.place.*
import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FacilitySearchCoordinatorTest {
    @Test fun nextProposalKeepsConfirmedResultsUntilAnotherConfirmation() = runTest {
        val coordinator = FacilitySearchCoordinator(Repository(), backgroundScope)
        coordinator.search("owner", facilityQuery()); runCurrent()
        coordinator.choose(FacilityChoice.Confirm("lens:cafe")); runCurrent()
        val applied = coordinator.state.value.confirmedResponse
        coordinator.search("owner", facilityQuery().copy(query = "다른 조건")); runCurrent()
        assertNull(coordinator.state.value.response!!.confirmedLens)
        assertEquals(applied, coordinator.state.value.confirmedResponse)
        coordinator.cancelPending()
        assertEquals(applied, coordinator.state.value.confirmedResponse)
    }

    private open class Repository : FacilityRepository {
        override suspend fun discover(owner: String, query: FacilityQuery) = facilityResponse().copy(request = query)
        override suspend fun act(owner: String, previous: FacilityResponse, action: FacilityAction) = previous.copy(
            revision = previous.revision + 1, confirmedLensId = (action.choice as? FacilityChoice.Confirm)?.lensId)
    }

    @Test fun refinementThenConfirmationUsesCurrentRevisionAndSelectsARealResult() = runTest {
        val actions = mutableListOf<FacilityAction>()
        val response = facilityResponse().copy(signals = listOf(FacilitySignal("cost", "비용", "needs_selection", true, "가격 미지원",
            listOf(FacilityOption("near", "가까운 곳", "proxy", "거리 기준")), null)))
        val coordinator = FacilitySearchCoordinator(object : Repository() {
            override suspend fun discover(owner: String, query: FacilityQuery) = response.copy(request = query)
            override suspend fun act(owner: String, previous: FacilityResponse, action: FacilityAction): FacilityResponse {
                actions += action
                return super.act(owner, previous, action)
            }
        }, backgroundScope)
        coordinator.search("owner", facilityQuery()); runCurrent()
        coordinator.choose(FacilityChoice.Refine("cost", "near")); runCurrent()
        coordinator.choose(FacilityChoice.Confirm("lens:cafe")); runCurrent()
        assertEquals(listOf(1, 2), actions.map { it.expectedRevision })
        assertNotNull(coordinator.state.value.confirmedLens)
        assertNotNull(coordinator.state.value.selectedPlaceKey)
        assertEquals(3, coordinator.state.value.response!!.revision)
    }

    @Test fun actionTimeoutRetryKeepsIdAndDoubleTapDoesNotDuplicate() = runTest {
        val actions = mutableListOf<FacilityAction>()
        val coordinator = FacilitySearchCoordinator(object : Repository() {
            override suspend fun act(owner: String, previous: FacilityResponse, action: FacilityAction): FacilityResponse {
                actions += action
                if (actions.size == 1) throw java.net.SocketTimeoutException()
                return super.act(owner, previous, action)
            }
        }, backgroundScope)
        coordinator.search("owner", facilityQuery()); runCurrent()
        coordinator.choose(FacilityChoice.Confirm("lens:cafe"))
        coordinator.choose(FacilityChoice.Confirm("lens:cafe")); runCurrent()
        assertEquals(1, actions.size)
        assertTrue(coordinator.state.value.canRetry)
        coordinator.retry(); runCurrent()
        assertEquals(actions[0], actions[1])
        assertNotNull(coordinator.state.value.confirmedLens)
    }

    @Test fun lateReplyCannotRestoreInvalidatedOrNewerSearch() = runTest {
        val old = CompletableDeferred<FacilityResponse>()
        val coordinator = FacilitySearchCoordinator(object : Repository() {
            override suspend fun discover(owner: String, query: FacilityQuery) =
                if (query.query == "old") withContext(NonCancellable) { old.await() } else super.discover(owner, query)
        }, backgroundScope)
        coordinator.search("owner", facilityQuery().copy(query = "old")); runCurrent()
        coordinator.invalidate("조건 변경")
        coordinator.search("owner", facilityQuery().copy(query = "new")); runCurrent()
        old.complete(facilityResponse()); runCurrent()
        assertEquals("new", coordinator.state.value.response!!.request.query)
        coordinator.invalidate()
        assertNull(coordinator.state.value.response)
        assertFalse(coordinator.state.value.canRetry)
    }

    @Test fun expirationRequiresANewSearchWithoutReusingContinuation() = runTest {
        var searches = 0
        var actions = 0
        val coordinator = FacilitySearchCoordinator(object : Repository() {
            override suspend fun discover(owner: String, query: FacilityQuery): FacilityResponse {
                searches++
                return super.discover(owner, query).copy(expiresAt = Instant.EPOCH)
            }
            override suspend fun act(owner: String, previous: FacilityResponse, action: FacilityAction): FacilityResponse {
                actions++; error("expired action must not be sent")
            }
        }, backgroundScope)
        coordinator.search("owner", facilityQuery()); runCurrent()
        coordinator.choose(FacilityChoice.Confirm("lens:cafe")); runCurrent()
        assertEquals(0, actions)
        assertNull(coordinator.state.value.response)
        assertTrue(coordinator.state.value.error!!.contains("오래됐어요"))
        coordinator.retry(); runCurrent()
        assertEquals(2, searches)
    }

    @Test fun rejectedContinuationRestartsTheQueryInsteadOfRetryingTheAction() = runTest {
        for (status in listOf(409, 410, 422)) {
            val requests = mutableListOf<FacilityQuery>()
            var actions = 0
            val coordinator = FacilitySearchCoordinator(object : Repository() {
                override suspend fun discover(owner: String, query: FacilityQuery): FacilityResponse {
                    requests += query
                    return super.discover(owner, query)
                }
                override suspend fun act(owner: String, previous: FacilityResponse, action: FacilityAction): FacilityResponse {
                    actions++
                    throw FacilityException(status)
                }
            }, backgroundScope)
            coordinator.search("owner", facilityQuery()); runCurrent()
            coordinator.choose(FacilityChoice.Confirm("lens:cafe")); runCurrent()
            assertNull(coordinator.state.value.response)
            assertTrue(coordinator.state.value.canRetry)
            coordinator.retry(); runCurrent()
            assertEquals(1, actions)
            assertEquals(2, requests.size)
            assertNotEquals(requests[0].clientRequestId, requests[1].clientRequestId)
            assertEquals(requests[0].query, requests[1].query)
        }
    }

    @Test fun loginMissingOrUnavailableOptionNeverCallsRepository() = runTest {
        var calls = 0
        val coordinator = FacilitySearchCoordinator(object : Repository() {
            override suspend fun discover(owner: String, query: FacilityQuery): FacilityResponse { calls++; return super.discover(owner, query) }
            override suspend fun act(owner: String, previous: FacilityResponse, action: FacilityAction): FacilityResponse { calls++; return previous }
        }, backgroundScope)
        coordinator.search(null, facilityQuery()); runCurrent()
        assertEquals(0, calls)
        assertTrue(coordinator.state.value.error!!.contains("로그인"))
        coordinator.search("owner", facilityQuery()); runCurrent()
        coordinator.choose(FacilityChoice.Refine("unknown", "unsupported")); runCurrent()
        assertEquals(1, calls)
    }
}
