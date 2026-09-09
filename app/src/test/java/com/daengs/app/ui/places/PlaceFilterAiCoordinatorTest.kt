package com.daengs.app.ui.places

import com.daengs.app.place.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceFilterAiCoordinatorTest {
    private class Repository : PlaceFilterEditRepository {
        val actions = mutableListOf<PlaceFilterEditAction>()
        var propose: suspend (PlaceFilterEditQuery) -> PlaceFilterEditResponse = { filterEditFixture(it) }
        var apply: suspend (PlaceFilterEditAction) -> PlaceFilterEditResponse = { appliedFilterEditFixture(it) }
        override suspend fun propose(owner: String, query: PlaceFilterEditQuery) = propose(query)
        override suspend fun apply(owner: String, action: PlaceFilterEditAction): PlaceFilterEditResponse { actions += action; return apply(action) }
    }

    @Test fun readyEditAutomaticallyAppliesButProtectedEditWaitsForUser() = runTest {
        val repo = Repository()
        var accepted = 0
        val coordinator = PlaceFilterAiCoordinator(repo, this, { true }, { _, _ -> accepted++; true })
        coordinator.enable(true)
        coordinator.search("owner", "주차 조건 빼줘", filterRequestFixture()); advanceUntilIdle()
        assertEquals(1, accepted); assertFalse(repo.actions.single().confirm)
        repo.propose = { filterEditFixture(it, confirmation = true) }
        coordinator.search("owner", "다른 곳", filterRequestFixture()); advanceUntilIdle()
        assertEquals(1, accepted); assertTrue(coordinator.state.value.response!!.needsConfirmation)
        coordinator.confirm(); advanceUntilIdle()
        assertEquals(2, accepted); assertTrue(repo.actions.last().confirm)
    }

    @Test fun unsupportedClauseNeverExecutesPartialFilters() = runTest {
        val repo = Repository().apply { propose = { filterEditFixture(it, unresolved = true) } }
        val coordinator = PlaceFilterAiCoordinator(repo, this, { true }, { _, _ -> error("must preserve results") })
        coordinator.search("owner", "조용한 곳", filterRequestFixture()); advanceUntilIdle()
        coordinator.confirm(); advanceUntilIdle()
        assertTrue(repo.actions.isEmpty())
        assertNull(coordinator.state.value.response!!.proposed)
    }

    @Test fun retryUsesSameActionIdAfterUncertainNetworkOutcome() = runTest {
        val repo = Repository().apply { apply = { throw java.io.IOException() } }
        var accepted = 0
        val coordinator = PlaceFilterAiCoordinator(repo, this, { true }, { _, _ -> accepted++; true })
        coordinator.search("owner", "주차 빼줘", filterRequestFixture()); advanceUntilIdle()
        assertTrue(coordinator.state.value.canRetry)
        repo.apply = { appliedFilterEditFixture(it) }
        coordinator.retry(); advanceUntilIdle()
        assertEquals(repo.actions.first(), repo.actions.last()); assertEquals(1, accepted)
    }

    @Test fun manualChangeBeforeProposalReturnsPreventsEvenAutomaticExecution() = runTest {
        val deferred = CompletableDeferred<Unit>()
        val repo = Repository().apply { propose = { deferred.await(); filterEditFixture(it) } }
        var matches = true
        val coordinator = PlaceFilterAiCoordinator(repo, this, { matches }, { _, _ -> error("stale") })
        coordinator.search("owner", "주차 빼줘", filterRequestFixture()); runCurrent()
        matches = false; deferred.complete(Unit); advanceUntilIdle()
        assertTrue(repo.actions.isEmpty()); assertNull(coordinator.state.value.response)
    }

    @Test fun logoutOrLocationChangeDiscardsLateAppliedResponseEvenWithoutTransportCancellation() = runTest {
        val deferred = CompletableDeferred<Unit>()
        val repo = Repository().apply { apply = { withContext(NonCancellable) { deferred.await(); appliedFilterEditFixture(it) } } }
        val coordinator = PlaceFilterAiCoordinator(repo, this, { true }, { _, _ -> error("stale") })
        coordinator.search("owner", "주차 빼줘", filterRequestFixture()); runCurrent()
        coordinator.invalidate(); deferred.complete(Unit); advanceUntilIdle()
        assertNull(coordinator.state.value.response); assertFalse(coordinator.state.value.loading)
    }
}
