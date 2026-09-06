package com.daengs.app.ui.places

import com.daengs.app.pet.Pet
import com.daengs.app.pet.PetHolder
import com.daengs.app.pet.PetList
import com.daengs.app.place.*
import com.daengs.app.journey.*
import com.daengs.app.location.*
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceProfilesTest {
    private fun pet(id: String, weight: Float? = 9f) = Pet(id, "보리", "mix", null, null,
        weight, null, null, isPrimary = true, updatedAt = "v1")

    @Test fun noImplicitPrimaryAndSameNamesRemainDistinct() {
        val initial = PlaceProfiles().receive("owner", listOf(pet("a"), pet("b", null)), false, null)
        assertTrue(initial.selectedIds.isEmpty())
        val selected = initial.toggle("a").toggle("b")
        assertEquals(listOf("a", "b"), selected.snapshots().map { it.ref })
        assertNull(selected.snapshots()[1].weightKg)
        assertNull(selected.snapshots()[1].size)
        assertEquals("v1", selected.snapshots()[0].revision)
    }

    @Test fun failureRetainsChoiceButRemovesEvaluationAndSuccessRemovesDeletedAndFarewell() {
        val original = PlaceProfiles().receive("owner", listOf(pet("a"), pet("b")), false, null).toggle("a").toggle("b")
        val failed = original.receive("owner", null, false, "failed")
        assertEquals(original.selectedIds, failed.selectedIds)
        assertTrue(failed.snapshots().isEmpty())
        assertEquals(failed, failed.toggle("a"))
        val recovered = failed.receive("owner", listOf(pet("b").copy(farewellOn = LocalDate.now())), false, null)
        assertTrue(recovered.selectedIds.isEmpty())
        assertTrue(recovered.pets.isEmpty())
        assertTrue(original.receive("other", listOf(pet("a")), false, null).selectedIds.isEmpty())
        assertTrue(original.receive(null, null, false, null).pets.isEmpty())
    }

    @Test fun lateProfileResponseCannotRestoreLoggedOutAccount() = runTest {
        val pending = CompletableDeferred<Result<PetList>>()
        val holder = PetHolder { pending.await() }
        val job = launch { holder.refresh("old") }
        runCurrent()
        holder.forget()
        pending.complete(Result.success(PetList(listOf(pet("a")), 5)))
        job.join()
        assertNull(holder.pets)
        assertFalse(holder.busy)
    }

    @Test fun latestProfileRefreshWins() = runTest {
        val old = CompletableDeferred<Result<PetList>>()
        val holder = PetHolder { token -> if (token == "old") old.await() else Result.success(PetList(listOf(pet("new")), 5)) }
        val job = launch { holder.refresh("old") }
        runCurrent()
        holder.refresh("new")
        old.complete(Result.success(PetList(listOf(pet("old")), 5)))
        job.join()
        assertEquals("new", holder.pets!!.single().id)
    }

    @Test fun cancelledProfileReadIsNotAnErrorOrEmptyRoster() = runTest {
        val holder = PetHolder { throw CancellationException("cancelled") }
        try { holder.refresh("token"); fail("must cancel") } catch (_: CancellationException) { }
        assertFalse(holder.busy)
        assertNull(holder.pets)
        assertNull(holder.error)
    }

    @Test fun profileUpdatesReevaluateAllBatchesAndKeepMapFilters() = runTest {
        val requests = mutableListOf<PlaceSearchRequest>()
        val point = GeoPoint(37.54, 127.05)
        val source = object : LocationSource {
            override suspend fun currentLocation() = LocationSample(point, 0)
            override fun locationUpdates(config: LocationUpdateConfig) = emptyFlow<LocationSample>()
        }
        val vm = PlacesViewModel(PlaceSearchRepository { request ->
            requests += request
            PlaceSearchResponse(null, request.kinds.map { kind ->
                PlaceSearchGroup(kind, PlaceSort(PlaceSortType.DISTANCE, emptyList(), emptyList(), null, emptyMap()), 50, false, emptyList())
            }, request.dogs)
        }, JourneyRepository { JourneyResponse("dog", emptyList()) }, source, backgroundScope)
        runCurrent(); vm.activate(true); runCurrent()
        vm.updateProfiles("owner", listOf(pet("a"), pet("b")), false, null)
        vm.onAction(PlacesAction.Search(null, true, "카페")); runCurrent()
        vm.onAction(PlacesAction.SetRadius(5000)); runCurrent()
        vm.onAction(PlacesAction.ToggleDog("a")); runCurrent()
        vm.onAction(PlacesAction.ToggleDog("b")); runCurrent()
        assertTrue(requests.takeLast(3).all { it.dogs.size == 2 && it.dogWeightKg == null })
        requests.clear()
        vm.updateProfiles("owner", listOf(pet("a", 11f).copy(updatedAt = "v2"), pet("b")), false, null)
        runCurrent()
        vm.onAction(PlacesAction.RetrySearch); runCurrent()
        vm.onAction(PlacesAction.SearchAt(GeoPoint(37.55, 127.06), null, true)); runCurrent()
        assertEquals(9, requests.size)
        assertTrue(requests.all { it.radiusMeters == 5000 && it.nameQuery == "카페" && it.preferParking && it.dogs.first().weightKg == 11.0 && it.dogs.first().revision == "v2" })
        vm.updateProfiles(null, null, false, null); runCurrent()
        assertTrue(requests.takeLast(3).all { it.dogs.isEmpty() })
    }

    @Test fun labelsDoNotTurnWeightPassIntoAdmissionPromise() {
        val evaluation = PerDogEvaluation("a", DogAccessEvaluation(DogAccessState.COMPATIBLE, "weight_allowed"),
            buildJsonObject { put("reason", "unresolved_condition") })
        assertEquals("체중 조건 충족 · 추가 조건 확인 필요", dogEvaluationLabel(evaluation))
    }
}
