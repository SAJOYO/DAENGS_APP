package com.daengs.app.ui.places.lab

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceSearchLabViewModelTest {
    @Test fun typingModeAndProfileSelectionDoNotRequestSearch() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var calls = 0
            val vm = PlaceSearchLabViewModel { calls++; emptyList() }
            advanceUntilIdle()
            assertEquals(1, calls)
            vm.edit("카페"); vm.toggleAi(); vm.dog("one"); vm.dog("two")
            vm.submit(); advanceUntilIdle()
            assertEquals(1, calls)
            assertEquals(setOf("one", "two"), vm.state.value.selectedDogIds)
            assertEquals("", vm.state.value.applied.query)
            vm.toggleAi(); vm.submit(); advanceUntilIdle()
            assertEquals(2, calls)
            assertEquals("카페", vm.state.value.applied.query)
            assertEquals(LabPhase.EMPTY, vm.state.value.phase)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun unsupportedCategoryAndFailedSourceAreDifferentStates() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = PlaceSearchLabViewModel { error("fixture unavailable") }
            advanceUntilIdle()
            assertEquals(LabPhase.ERROR, vm.state.value.phase)
            vm.category(com.daengs.app.place.PlaceKind.ETC)
            assertEquals(LabPhase.UNSAMPLED, vm.state.value.phase)
            assertTrue(vm.state.value.hits.isEmpty())
        } finally { Dispatchers.resetMain() }
    }
}
