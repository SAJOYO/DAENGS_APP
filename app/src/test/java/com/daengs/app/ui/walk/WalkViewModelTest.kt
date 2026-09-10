package com.daengs.app.ui.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.location.LocationSource
import com.daengs.app.location.LocationUpdateConfig
import com.daengs.app.map.shell.MapPurpose
import com.daengs.app.map.features.territory.TerritoryGameController
import com.daengs.app.map.layers.territory.TerritoryMarkerOccupancy
import com.daengs.app.territory.InMemoryTerritoryClaimRepository
import com.daengs.app.territory.TerritorySite
import com.daengs.app.pet.Pet
import com.daengs.app.territory.NearbyTerritorySitesRequest
import com.daengs.app.territory.TerritorySitePage
import com.daengs.app.territory.TerritorySiteRepository
import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.RecordedSession
import com.daengs.app.walk.RecordedWalkAction
import com.daengs.app.walk.RecordedWeather
import com.daengs.app.walk.TrackingState
import com.daengs.app.walk.TrailSnapshot
import com.daengs.app.walk.WalkEvent
import com.daengs.app.walk.WalkFixLog
import com.daengs.app.walk.WalkHistory
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.WalkTrackingController
import com.daengs.app.walk.WalkTrackingState
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalkViewModelTest {
    @Test fun `game map entry and screen reentry preserve active walk and participants`() = runTest {
        val recording = WalkTrackingState(ownerId = "user", activeSessionId = "walk-running",
            activeDogIds = listOf("dog-1", "dog-2"), trail = TrailSnapshot(state = TrackingState.RECORDING))
        val controller = FakeWalkController().apply { publish(recording) }
        val vm = viewModel(controller, CountingLocationSource())
        vm.activate(true, true); runCurrent()
        vm.deactivate(); runCurrent()
        vm.activate(true, true)
        vm.onAction(WalkAction.ChangeMapPurpose(MapPurpose.TERRITORY)); runCurrent()
        assertEquals(MapPurpose.TERRITORY, vm.state.value.map.purpose)
        assertEquals(recording, controller.state.value)
        assertEquals(recording.activeSessionId, vm.state.value.tracking.activeSessionId)
        assertEquals(recording.activeDogIds, vm.state.value.tracking.activeDogIds)
        assertEquals(emptyList<String>(), controller.sessionCommands)
    }

    @Test fun `unchanged viewport occupancy survives nearby GPS quality changes`() = runTest {
        val fix = LocationSample(GeoPoint(37.5, 127.0), 1000, 1_000_000_000L, 3f)
        val good = WalkTrackingState(ownerId = "user", activeSessionId = "walk", activeDogIds = listOf("dog-1"),
            trail = TrailSnapshot(state = TrackingState.RECORDING), lastSample = fix, latestMomentFix = fix)
        val controller = FakeWalkController().apply { publish(good) }
        var completedReads = 0
        var cancelledReads = 0
        val game = sharedGame { _, ids ->
            try { kotlinx.coroutines.delay(5_000) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { cancelledReads++; throw cancelled }
            completedReads++
            ids.map { com.daengs.app.territory.SharedTerritorySite(it, 1, null) }
        }
        val vm = viewModel(controller, CountingLocationSource(), TerritorySiteRepository {
            val site = if (it.radiusMeters == 300) TerritorySite("near", fix.point, 0.0)
                else TerritorySite("far", GeoPoint(37.55, 127.0), 0.0)
            TerritorySitePage(1, false, listOf(site))
        }, game)
        vm.activate(true, true)
        vm.onAction(WalkAction.ChangeMapPurpose(MapPurpose.TERRITORY)); runCurrent()
        vm.onAction(WalkAction.CameraMoved)
        vm.onAction(WalkAction.SelectTerritorySite("far")); runCurrent()
        repeat(12) { index ->
            advanceTimeBy(1_000)
            controller.publish(good.copy(trail = good.trail.copy(skippedLowAccuracy = if (index % 2 == 0) 1 else 0)))
            runCurrent()
        }
        assertEquals("far", vm.state.value.territoryGame.targetId)
        assertEquals(true, vm.state.value.territoryGame.target!!.occupancyKnown)
        assertEquals(0, cancelledReads)
        assertEquals(2, completedReads)
    }

    @Test fun `slow occupancy read queues only the latest viewport and never overlaps requests`() = runTest {
        val pending = mutableListOf<kotlinx.coroutines.CompletableDeferred<Unit>>()
        val requested = mutableListOf<List<String>>()
        val game = sharedGame { _, ids ->
            requested += ids
            kotlinx.coroutines.CompletableDeferred<Unit>().also(pending::add).await()
            ids.map { com.daengs.app.territory.SharedTerritorySite(it, 1, null) }
        }
        var nextId = "A"
        val vm = viewModel(FakeWalkController(), CountingLocationSource(), TerritorySiteRepository {
            TerritorySitePage(1, false, listOf(TerritorySite(nextId, it.origin, 0.0)))
        }, game)
        vm.activate(true, true)
        vm.onAction(WalkAction.ChangeMapPurpose(MapPurpose.TERRITORY)); runCurrent()
        assertEquals(listOf(listOf("A")), requested)
        vm.onAction(WalkAction.CameraMoved)
        for ((id, latitude) in listOf("B" to 37.52, "C" to 37.54)) {
            nextId = id
            vm.onAction(WalkAction.CameraSettled(GeoPoint(latitude, 127.0)))
            advanceTimeBy(350); runCurrent()
        }
        vm.onAction(WalkAction.SelectTerritorySite("C")); runCurrent()
        assertEquals("C", vm.state.value.territory.selectedSiteId)
        assertEquals(1, requested.size)
        pending[0].complete(Unit); runCurrent()
        assertEquals(listOf(listOf("A"), listOf("C")), requested)
        assertFalse(vm.state.value.territoryGame.target!!.occupancyKnown)
        pending[1].complete(Unit); runCurrent()
        assertEquals(listOf(listOf("A"), listOf("C")), requested)
        assertEquals(true, vm.state.value.territoryGame.target!!.occupancyKnown)
    }

    @Test fun `visibility loss cancels occupancy and discards pending lists before restarting`() = runTest {
        for (hide in listOf<(WalkViewModel) -> Unit>(
            { it.updateSharedReadsForeground(false) },
            { it.onAction(WalkAction.ChangeMapPurpose(MapPurpose.WALK)) },
            { it.deactivate() },
        )) {
            var requests = 0
            var cancellations = 0
            val game = sharedGame { _, _ ->
                requests++
                try { awaitCancellation() }
                finally { cancellations++ }
            }
            val vm = viewModel(FakeWalkController(), CountingLocationSource(), TerritorySiteRepository {
                TerritorySitePage(1, false, listOf(TerritorySite(it.origin.toString(), it.origin, 0.0)))
            }, game)
            vm.activate(true, true)
            vm.onAction(WalkAction.ChangeMapPurpose(MapPurpose.TERRITORY)); runCurrent()
            vm.onAction(WalkAction.CameraMoved)
            vm.onAction(WalkAction.CameraSettled(GeoPoint(37.52, 127.0)))
            advanceTimeBy(350); runCurrent()
            hide(vm); runCurrent()
            advanceTimeBy(30_000); runCurrent()
            assertEquals(1, requests)
            assertEquals(1, cancellations)
            vm.updateSharedReadsForeground(true)
            vm.activate(true, true)
            vm.onAction(WalkAction.ChangeMapPurpose(MapPurpose.TERRITORY)); runCurrent()
            assertEquals(2, requests)
            vm.deactivate(); runCurrent()
        }
    }

    private fun sharedGame(client: com.daengs.app.territory.TerritoryOccupancyClient) =
        com.daengs.app.map.features.territory.ServerTerritoryGameProvider(client,
            { com.daengs.app.auth.Session("user", "token", "refresh", Long.MAX_VALUE, Long.MAX_VALUE) }, { "user" })

    @Test fun `nearby candidate cannot become an implicit mark or capture target`() = runTest {
        val fix = LocationSample(GeoPoint(37.5, 127.0), 1000, 1_000_000_000L, 3f)
        val controller = FakeWalkController().apply {
            publish(WalkTrackingState(activeSessionId = "walk", activeDogIds = listOf("dog-1"),
                trail = TrailSnapshot(state = TrackingState.RECORDING), lastSample = fix, latestMomentFix = fix))
        }
        val claims = InMemoryTerritoryClaimRepository(emptyList())
        val vm = viewModel(controller, CountingLocationSource(), TerritorySiteRepository {
            val site = if (it.radiusMeters == 300) TerritorySite("near", fix.point, 0.0)
                else TerritorySite("far", GeoPoint(37.55, 127.0), 0.0)
            TerritorySitePage(1, false, listOf(site))
        }, TerritoryGameController(claims))
        vm.activate(true, true)
        vm.onAction(WalkAction.ChangeMapPurpose(MapPurpose.TERRITORY)); runCurrent()
        vm.onAction(WalkAction.SelectTerritorySite("far")); runCurrent()
        assertEquals("near", vm.state.value.territoryGame.nearbyTargetId)
        assertEquals("far", vm.state.value.territoryGame.targetId)
        assertFalse(vm.state.value.territoryGame.canMark)
        assertFalse(vm.state.value.territoryGame.canPhotograph)
        vm.onAction(WalkAction.MarkTerritory("near"))
        vm.onAction(WalkAction.PhotographTerritory("near")); runCurrent()
        assertEquals(null, claims.attempt("walk", "near"))
        assertEquals("far", vm.state.value.territoryGame.targetId)
        assertEquals(setOf("near"), vm.state.value.territoryGame.visibleRangeSiteIds)
        vm.onAction(WalkAction.SelectTerritorySite("near")); runCurrent()
        assertEquals("near", vm.state.value.territoryGame.targetId)
        assertEquals(listOf("far", "near"), vm.state.value.territory.sites.map { it.id })
        assertEquals(true, vm.state.value.territoryGame.canMark)
        vm.onAction(WalkAction.MarkTerritory("near")); runCurrent()
        assertEquals(true, claims.attempt("walk", "near") != null)
    }

    @Test fun `nearby feed follows GPS while a distant viewport and manual card stay selected`() = runTest {
        val a = TerritorySite("A", GeoPoint(37.5, 127.0), 999.0)
        val b = TerritorySite("B", GeoPoint(37.5004, 127.0), 999.0)
        val far = TerritorySite("far", GeoPoint(37.55, 127.0), 0.0)
        val samples = MutableStateFlow(LocationSample(a.point, 1000, 1_000_000_000L, 3f))
        var subscriptions = 0
        val source = object : LocationSource {
            override suspend fun currentLocation() = samples.value
            override fun locationUpdates(config: LocationUpdateConfig) = flow {
                subscriptions++
                samples.collect { emit(it) }
            }
        }
        val requests = mutableListOf<NearbyTerritorySitesRequest>()
        val occupancyReads = mutableListOf<List<String>>()
        val session = com.daengs.app.auth.Session("user", "token", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
        val game = com.daengs.app.map.features.territory.ServerTerritoryGameProvider(
            com.daengs.app.territory.TerritoryOccupancyClient { _, ids ->
                occupancyReads += ids
                ids.map { com.daengs.app.territory.SharedTerritorySite(it, 1, null) }
            }, { session }, { "user" })
        val vm = viewModel(FakeWalkController(), source, TerritorySiteRepository {
            requests += it
            val sites = if (it.radiusMeters == 300) listOf(a, b) else listOf(far)
            TerritorySitePage(sites.size, false, sites)
        }, game)
        vm.activate(true, true)
        vm.onAction(WalkAction.ChangeMapPurpose(MapPurpose.TERRITORY)); runCurrent()
        vm.onAction(WalkAction.CameraMoved)
        vm.onAction(WalkAction.CameraSettled(far.point))
        advanceTimeBy(350); runCurrent()
        vm.onAction(WalkAction.SelectTerritorySite("far")); runCurrent()
        assertEquals("far", vm.state.value.territoryGame.targetId)
        assertEquals("A", vm.state.value.territoryGame.nearbyTargetId)
        assertEquals(true, vm.state.value.territoryGame.nearbyTarget!!.occupancyKnown)
        assertEquals(setOf("far", "A", "B"), occupancyReads.last().toSet())
        assertEquals(1, requests.count { it.radiusMeters == 300 })
        assertEquals(far.point, vm.state.value.territory.loadedOrigin)
        samples.value = samples.value.copy(point = b.point)
        runCurrent()
        assertEquals("B", vm.state.value.territoryGame.nearbyTargetId)
        assertEquals("far", vm.state.value.territory.selectedSiteId)
        assertEquals("far", vm.state.value.territoryGame.targetId)
        assertEquals(far.point, vm.state.value.territory.loadedOrigin)
        assertEquals(listOf("far", "A", "B"), vm.state.value.toMapPresentation().scene.territorySites.map { it.id })
        assertEquals(setOf("A", "B"), vm.state.value.territoryGame.visibleRangeSiteIds)
        assertFalse(vm.state.value.territoryGame.canMark)
        assertFalse(vm.state.value.territoryGame.canPhotograph)
        assertEquals(1, subscriptions)
        vm.onAction(WalkAction.ClearTerritory); runCurrent()
        assertEquals(null, vm.state.value.territoryGame.targetId)
        assertEquals("B", vm.state.value.territoryGame.nearbyTargetId)
    }

    @Test fun `nearby target expires without GPS emissions and clears on permission or visibility loss`() = runTest {
        val sample = LocationSample(GeoPoint(37.5, 127.0), 1000, 1_000_000_000L, 3f)
        val controller = FakeWalkController()
        fun tracking(fix: LocationSample) = WalkTrackingState(activeSessionId = "walk", activeDogIds = listOf("dog-1"),
            trail = TrailSnapshot(state = TrackingState.RECORDING), lastSample = fix, latestMomentFix = fix)
        controller.publish(tracking(sample))
        var reads = 0
        val vm = viewModel(controller, CountingLocationSource(), TerritorySiteRepository {
            if (it.radiusMeters == 300) reads++
            TerritorySitePage(1, false, listOf(TerritorySite("A", sample.point, 0.0)))
        }, TerritoryGameController(InMemoryTerritoryClaimRepository(emptyList())),
            clock = { 2_000_000_000L + testScheduler.currentTime * 1_000_000 })
        vm.activate(true, true)
        vm.onAction(WalkAction.ChangeMapPurpose(MapPurpose.TERRITORY)); runCurrent()
        assertEquals("A", vm.state.value.territoryGame.nearbyTargetId)
        advanceTimeBy(10_001); runCurrent()
        assertEquals(null, vm.state.value.territoryGame.nearbyTargetId)
        assertEquals(emptySet<String>(), vm.state.value.territoryGame.visibleRangeSiteIds)
        assertEquals(1, reads)
        controller.publish(tracking(sample.copy(elapsedRealtimeNanos = 12_000_000_000L))); runCurrent()
        assertEquals("A", vm.state.value.territoryGame.nearbyTargetId)
        vm.updatePermission(true, false); runCurrent()
        assertEquals(null, vm.state.value.territoryGame.nearbyTargetId)
        vm.updatePermission(true, true); runCurrent()
        assertEquals("A", vm.state.value.territoryGame.nearbyTargetId)
        vm.updateSharedReadsForeground(false); runCurrent()
        val beforeHidden = reads
        advanceTimeBy(2_000); runCurrent()
        assertEquals(null, vm.state.value.territoryGame.nearbyTargetId)
        assertEquals(beforeHidden, reads)
        vm.updateSharedReadsForeground(true); runCurrent()
        assertEquals("A", vm.state.value.territoryGame.nearbyTargetId)
        vm.onAction(WalkAction.ChangeMapPurpose(MapPurpose.WALK)); runCurrent()
        assertEquals(null, vm.state.value.territoryGame.nearbyTargetId)
        vm.onAction(WalkAction.ChangeMapPurpose(MapPurpose.TERRITORY)); runCurrent()
        assertEquals("A", vm.state.value.territoryGame.nearbyTargetId)
        vm.deactivate(); runCurrent()
        assertEquals(null, vm.state.value.territoryGame.nearbyTargetId)
        assertEquals(emptySet<String>(), vm.state.value.territoryGame.visibleRangeSiteIds)
    }

    @Test fun `photo preflight rejection displays policy message without opening camera`() = runTest {
        for (reason in listOf("protected", "season_ended")) {
            val blocked = com.daengs.app.territory.TerritoryCaptureBlocked(reason)
            val local = TerritoryGameController(InMemoryTerritoryClaimRepository(emptyList()))
            val provider = object : com.daengs.app.map.features.territory.TerritoryGameProvider by local {
                override val onlinePhotos = true
                override suspend fun prepareCapture(siteId: String, board: com.daengs.app.map.features.territory.TerritoryBoardState,
                    tracking: WalkTrackingState, permitted: Boolean, petNames: Map<String, String>, nowNanos: Long, atMillis: Long): com.daengs.app.map.features.territory.TerritoryCaptureTarget? = throw blocked
            }
            val vm = viewModel(FakeWalkController(), CountingLocationSource(), TerritorySiteRepository {
                TerritorySitePage(1, false, listOf(TerritorySite("A", GeoPoint(37.5, 127.0), 0.0)))
            }, provider)
            val effects = mutableListOf<WalkEffect>()
            backgroundScope.launch { vm.effects.collect { effects += it } }
            vm.activate(true, true); vm.onAction(WalkAction.ChangeMapPurpose(MapPurpose.TERRITORY)); runCurrent()
            vm.onAction(WalkAction.SelectTerritorySite("A")); runCurrent()
            vm.onAction(WalkAction.PhotographTerritory("A")); runCurrent()
            assertEquals(blocked.message, vm.state.value.momentNotice)
            assertEquals(false, effects.any { it is WalkEffect.CaptureTerritory })
        }
    }

    @Test fun `async territory submission never reopens a card closed while saving`() = runTest {
        val stored = kotlinx.coroutines.CompletableDeferred<String>()
        val local = TerritoryGameController(InMemoryTerritoryClaimRepository(emptyList()))
        val provider = object : com.daengs.app.map.features.territory.TerritoryGameProvider by local {
            override suspend fun submitMark(siteId: String, board: com.daengs.app.map.features.territory.TerritoryBoardState,
                tracking: WalkTrackingState, permitted: Boolean, petNames: Map<String, String>, nowNanos: Long, atMillis: Long): String = stored.await()
        }
        val controller = FakeWalkController()
        val vm = viewModel(controller, CountingLocationSource(), TerritorySiteRepository {
            TerritorySitePage(1, false, listOf(TerritorySite("A", GeoPoint(37.5, 127.0), 0.0)))
        }, provider)
        vm.activate(true, true); vm.onAction(WalkAction.ChangeMapPurpose(MapPurpose.TERRITORY)); runCurrent()
        vm.onAction(WalkAction.SelectTerritorySite("A")); runCurrent()
        vm.onAction(WalkAction.MarkTerritory("A")); runCurrent()
        vm.onAction(WalkAction.ClearTerritory); runCurrent()
        stored.complete("saved"); runCurrent()
        assertEquals(null, vm.state.value.territory.selectedSiteId)
        assertEquals(null, vm.state.value.momentNotice)
    }

    @Test
    fun `server occupancy reaches map before walking and refresh stops when hidden`() = runTest {
        val site = TerritorySite("A", GeoPoint(37.5, 127.0), 0.0)
        var calls = 0
        val session = com.daengs.app.auth.Session("user", "token", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
        val shared = com.daengs.app.map.features.territory.ServerTerritoryGameProvider(
            com.daengs.app.territory.TerritoryOccupancyClient { _, _ ->
                calls++
                listOf(com.daengs.app.territory.SharedTerritorySite("A", 2,
                    com.daengs.app.territory.SharedTerritoryOccupancy("dog", "두부", false,
                        com.daengs.app.territory.ClaimCertification.VERIFIED, 1000)))
            }, { session }, { "user" },
        )
        val controller = FakeWalkController()
        val screenLocation = object : LocationSource {
            override suspend fun currentLocation() = LocationSample(site.point, 1000, 1_000_000_000L, 3f)
            override fun locationUpdates(config: LocationUpdateConfig) = emptyFlow<LocationSample>()
        }
        val vm = viewModel(controller, screenLocation,
            TerritorySiteRepository { TerritorySitePage(1, false, listOf(site)) }, shared)
        vm.activate(true, true)
        runCurrent()
        assertEquals(0, calls)
        vm.onAction(WalkAction.ChangeMapPurpose(MapPurpose.TERRITORY))
        runCurrent()
        vm.onAction(WalkAction.SelectTerritorySite("A"))
        runCurrent()
        assertEquals(1, calls)
        assertEquals("두부 · 인증", vm.state.value.territoryGame.target!!.occupancyLabel)
        assertEquals(com.daengs.app.territory.TerritoryProximityRange.IN_RANGE,
            vm.state.value.territoryGame.target!!.proximity.range)
        assertEquals(0.0, vm.state.value.territoryGame.target!!.distanceMeters!!, 0.0001)
        assertFalse(vm.state.value.territoryGame.canMark)
        assertFalse(vm.state.value.territoryGame.canPhotograph)
        val marker = vm.state.value.toMapPresentation().scene.territorySites.single()
        assertEquals(TerritoryMarkerOccupancy.VERIFIED, marker.occupancy)
        assertEquals(20.0, marker.radiusMeters)
        assertEquals(com.daengs.app.territory.TerritoryProximityRange.IN_RANGE, marker.proximity)
        assertFalse(marker.ready)
        assertEquals(null, marker.feedback)
        assertEquals(null, controller.state.value.activeSessionId)
        advanceTimeBy(15_000); runCurrent()
        assertEquals(2, calls)
        vm.onAction(WalkAction.ChangeMapPurpose(MapPurpose.WALK))
        runCurrent(); advanceTimeBy(30_000); runCurrent()
        assertEquals(2, calls)
        vm.onAction(WalkAction.ChangeMapPurpose(MapPurpose.TERRITORY))
        runCurrent()
        assertEquals(3, calls)
        vm.updateSharedReadsForeground(false); runCurrent(); advanceTimeBy(30_000); runCurrent()
        assertEquals(3, calls)
        vm.updateSharedReadsForeground(true); runCurrent()
        assertEquals(4, calls)
        vm.deactivate(); runCurrent(); advanceTimeBy(30_000); runCurrent()
        assertEquals(4, calls)
    }

    @Test
    fun `saved photo settles after map is hidden and walk ends then appears certified`() = runTest {
        val fix = LocationSample(GeoPoint(37.5, 127.0), 1000, 1_000_000_000L, 3f)
        val controller = FakeWalkController().apply {
            publish(WalkTrackingState(
                activeSessionId = "walk-id", activeDogIds = listOf("dog-1"),
                trail = TrailSnapshot(state = TrackingState.RECORDING), lastSample = fix, latestMomentFix = fix,
            ))
        }
        val board = TerritorySiteRepository { TerritorySitePage(1, false, listOf(TerritorySite("A", fix.point, 0.0))) }
        val claims = InMemoryTerritoryClaimRepository(emptyList())
        val queue = com.daengs.app.territory.TerritoryPhotoQueue(claims, backgroundScope)
        val viewModel = viewModel(controller, CountingLocationSource(), board, TerritoryGameController(claims), queue)
        viewModel.updatePets(listOf(pet("dog-1")))
        viewModel.activate(true, true)
        viewModel.onAction(WalkAction.ChangeMapPurpose(MapPurpose.TERRITORY))
        runCurrent()
        val target = com.daengs.app.map.features.territory.TerritoryCaptureTarget(
            com.daengs.app.territory.ClaimSession("walk-id", "local-territory-preview", "dog-1"), "A",
        )
        val attempt = checkNotNull(viewModel.beginTerritoryCapture(target))
        val file = java.io.File.createTempFile("territory-capture", ".jpg").apply { writeBytes(byteArrayOf(1)) }
        try {
            viewModel.submitTerritoryPhoto(attempt, file, com.daengs.app.territory.PhotoSimulation.ACCEPT)
            viewModel.onAction(WalkAction.ChangeMapPurpose(MapPurpose.WALK))
            controller.publish(WalkTrackingState())
            viewModel.deactivate()
            runCurrent()
            advanceTimeBy(2000)
            runCurrent()
            assertEquals(com.daengs.app.territory.ClaimPhotoStatus.VERIFIED, viewModel.territoryPhotos.value.single().status)
            viewModel.activate(true, true)
            viewModel.onAction(WalkAction.ChangeMapPurpose(MapPurpose.TERRITORY))
            runCurrent()
            assertEquals(TerritoryMarkerOccupancy.VERIFIED, viewModel.state.value.toMapPresentation().scene.territorySites.single().occupancy)
            assertEquals(false, file.exists())
        } finally { file.delete() }
    }

    @Test
    fun `territory action updates scene and map toggle preserves recording and ownership`() = runTest {
        val fix = LocationSample(GeoPoint(37.5, 127.0), 1000, 1_000_000_000L, 3f)
        val controller = FakeWalkController().apply {
            publish(WalkTrackingState(
                activeSessionId = "walk-id", activeDogIds = listOf("dog-1"),
                trail = TrailSnapshot(state = TrackingState.RECORDING), lastSample = fix, latestMomentFix = fix,
            ))
        }
        val repo = TerritorySiteRepository {
            TerritorySitePage(1, false, listOf(TerritorySite("A", fix.point, 999.0)))
        }
        val viewModel = viewModel(controller, CountingLocationSource(), repo,
            TerritoryGameController(InMemoryTerritoryClaimRepository(emptyList())))
        viewModel.updatePets(listOf(pet("dog-1")))
        viewModel.activate(true, true)
        viewModel.onAction(WalkAction.ChangeMapPurpose(MapPurpose.TERRITORY))
        runCurrent()
        assertEquals(null, viewModel.state.value.territoryGame.target)
        viewModel.onAction(WalkAction.MarkTerritory("A"))
        runCurrent()
        assertEquals(null, viewModel.state.value.territoryGame.sites.single().claim.occupancy)
        viewModel.onAction(WalkAction.SelectTerritorySite("A"))
        runCurrent()
        assertEquals(true, viewModel.state.value.territoryGame.canMark)
        assertEquals(com.daengs.app.map.layers.territory.TerritoryFeedbackKind.READY,
            viewModel.state.value.territoryGame.feedback?.kind)
        viewModel.onAction(WalkAction.MarkTerritory("A"))
        runCurrent()
        val marker = viewModel.state.value.toMapPresentation().scene.territorySites.single()
        assertEquals(TerritoryMarkerOccupancy.UNVERIFIED, marker.occupancy)
        assertEquals(com.daengs.app.map.layers.territory.TerritoryFeedbackKind.MARKED, marker.feedback?.kind)
        assertEquals(true, marker.selected)
        assertEquals(20.0, marker.radiusMeters!!, 0.0)
        assertEquals(false, viewModel.state.value.territoryGame.canMark)
        viewModel.onAction(WalkAction.ChangeMapPurpose(MapPurpose.WALK))
        runCurrent()
        assertEquals(0, viewModel.state.value.toMapPresentation().scene.territorySites.size)
        assertEquals(null, viewModel.state.value.territoryGame.feedback)
        assertEquals(TrackingState.RECORDING, controller.state.value.trail.state)
        viewModel.onAction(WalkAction.ChangeMapPurpose(MapPurpose.TERRITORY))
        runCurrent()
        assertEquals("dog-1", viewModel.state.value.territoryGame.sites.single().claim.occupancy!!.ownerPetId)
        assertEquals(emptyList<Any>(), controller.state.value.momentGroups)
    }

    @Test
    fun `screen location feed stops while tracking service owns the walk`() = runTest {
        val source = CountingLocationSource()
        val controller = FakeWalkController()
        val viewModel = viewModel(controller, source)

        viewModel.activate(permissionGranted = true, precisePermission = true)
        runCurrent()
        assertEquals(WalkLocationOwner.SCREEN, viewModel.state.value.location.owner)
        assertEquals(1, source.starts)

        controller.publish(
            WalkTrackingState(trail = TrailSnapshot(state = TrackingState.RECORDING)),
        )
        runCurrent()
        assertEquals(WalkLocationOwner.TRACKING_SERVICE, viewModel.state.value.location.owner)
        assertEquals(1, source.stops)

        controller.publish(WalkTrackingState())
        runCurrent()
        assertEquals(WalkLocationOwner.SCREEN, viewModel.state.value.location.owner)
        assertEquals(2, source.starts)
    }

    @Test
    fun `start action freezes the selected dog ids into the service command`() = runTest {
        val controller = FakeWalkController()
        val viewModel = viewModel(controller, CountingLocationSource())
        val first = pet("dog-1")
        val second = pet("dog-2")

        // 두 마리면 **아무도 안 골라진 채로 시작한다** (`WalkDogPick.defaultWalkDogs`).
        // 그래서 여기서 누른 한 마리만 명령에 실린다.
        viewModel.updatePets(listOf(first, second))
        runCurrent()
        viewModel.onAction(WalkAction.ToggleDog(second.id))
        viewModel.onAction(WalkAction.StartConfirmed)

        assertEquals(listOf(second.id), controller.startedDogIds)
    }

    /**
     * **두 마리 이상이면 아무도 안 골라져 있어야 한다.**
     *
     * 예전에는 전부 골라진 채로 시작했다. 화면은 "누구와 나갈까요?" 라고 묻고 골라진
     * 표시는 연분홍/흰색 차이뿐이라, 데려갈 아이를 고르려고 누른 것이 **빼는 동작**이
     * 되어 나머지 아이들과 다녀온 것으로 기록됐다 (비공개 테스트에서 실제로 났다).
     */
    @Test
    fun `two dogs start with nothing selected`() = runTest {
        val viewModel = viewModel(FakeWalkController(), CountingLocationSource())

        viewModel.updatePets(listOf(pet("dog-1"), pet("dog-2")))
        runCurrent()

        assertEquals(emptySet<String>(), viewModel.state.value.selection.selectedDogIds)
    }

    /** 한 마리면 고를 것이 없다. 매번 누르게 하면 탭만 는다. */
    @Test
    fun `one dog is selected for you`() = runTest {
        val viewModel = viewModel(FakeWalkController(), CountingLocationSource())
        val only = pet("dog-1")

        viewModel.updatePets(listOf(only))
        runCurrent()

        assertEquals(setOf(only.id), viewModel.state.value.selection.selectedDogIds)
    }

    @Test
    fun `opening the screen during a walk does not request a second location fix`() = runTest {
        val source = CountingLocationSource()
        val controller = FakeWalkController().apply {
            publish(
                WalkTrackingState(
                    trail = TrailSnapshot(state = TrackingState.RECORDING),
                    lastSample = LocationSample(GeoPoint(37.5, 127.0), capturedAtMillis = 0),
                ),
            )
        }
        val viewModel = viewModel(controller, source)

        viewModel.activate(permissionGranted = true, precisePermission = true)
        runCurrent()

        assertEquals(WalkLocationOwner.TRACKING_SERVICE, viewModel.state.value.location.owner)
        assertEquals(0, source.currentLocationCalls)
        assertEquals(0, source.starts)
    }

    @Test
    fun `leaving while locating cancels loading and allows a fresh request on return`() = runTest {
        val source = SuspendingLocationSource()
        val viewModel = viewModel(FakeWalkController(), source)

        viewModel.activate(permissionGranted = true, precisePermission = true)
        runCurrent()
        assertEquals(true, viewModel.state.value.location.locating)
        assertEquals(1, source.currentLocationCalls)

        viewModel.deactivate()
        runCurrent()
        assertEquals(false, viewModel.state.value.location.locating)
        assertEquals(1, source.cancellations)

        viewModel.activate(permissionGranted = true, precisePermission = true)
        runCurrent()
        assertEquals(true, viewModel.state.value.location.locating)
        assertEquals(2, source.currentLocationCalls)
    }

    @Test
    fun `service takeover cancels an in flight screen location request`() = runTest {
        val source = SuspendingLocationSource()
        val controller = FakeWalkController()
        val viewModel = viewModel(controller, source)

        viewModel.activate(permissionGranted = true, precisePermission = true)
        runCurrent()
        controller.publish(
            WalkTrackingState(
                trail = TrailSnapshot(state = TrackingState.RECORDING),
                lastSample = LocationSample(GeoPoint(37.51, 127.01), capturedAtMillis = 1),
            ),
        )
        runCurrent()

        assertEquals(1, source.cancellations)
        assertEquals(false, viewModel.state.value.location.locating)
        assertEquals(WalkLocationOwner.TRACKING_SERVICE, viewModel.state.value.location.owner)
        assertEquals(GeoPoint(37.51, 127.01), viewModel.state.value.location.currentPosition)
    }

    @Test
    fun `revoking permission clears cached location and blocks stale territory reload`() = runTest {
        val territory = CountingTerritoryRepository()
        val viewModel = viewModel(
            controller = FakeWalkController(),
            source = CountingLocationSource(),
            territoryRepository = territory,
        )

        viewModel.activate(permissionGranted = true, precisePermission = true)
        runCurrent()
        viewModel.onAction(WalkAction.ChangeMapPurpose(MapPurpose.TERRITORY))
        runCurrent()
        assertEquals(1, territory.calls)

        viewModel.updatePermission(granted = false, precise = false)
        runCurrent()
        assertEquals(null, viewModel.state.value.location.currentPosition)
        assertEquals(null, viewModel.state.value.toMapPresentation().scene.currentPosition)

        viewModel.deactivate()
        viewModel.activate(permissionGranted = false, precisePermission = false)
        runCurrent()
        assertEquals(1, territory.calls)
    }

    @Test
    fun `granting permission while visible immediately locates the device`() = runTest {
        val source = CountingLocationSource()
        val viewModel = viewModel(FakeWalkController(), source)

        viewModel.activate(permissionGranted = false, precisePermission = false)
        runCurrent()
        assertEquals(0, source.currentLocationCalls)

        viewModel.updatePermission(granted = true, precise = true)
        runCurrent()

        assertEquals(1, source.currentLocationCalls)
        assertEquals(GeoPoint(37.5, 127.0), viewModel.state.value.location.currentPosition)
    }

    @Test
    fun `remote selection preserves queried neighbourhood and pan stops framing`() = runTest {
        val remote = GeoPoint(37.55, 127.0)
        val queries = mutableListOf<GeoPoint>()
        val repo = TerritorySiteRepository { request ->
            queries.add(request.origin)
            val id = if (request.origin == remote) "remote" else "home"
            TerritorySitePage(1, false, listOf(TerritorySite(id, request.origin, 0.0)))
        }
        val vm = viewModel(FakeWalkController(), CountingLocationSource(), repo,
            TerritoryGameController(InMemoryTerritoryClaimRepository(emptyList())))
        vm.activate(true, true)
        vm.onAction(WalkAction.ChangeMapPurpose(MapPurpose.TERRITORY))
        runCurrent()
        vm.onAction(WalkAction.CameraMoved)
        vm.onAction(WalkAction.CameraSettled(remote))
        advanceTimeBy(400); runCurrent()
        val queryCount = queries.size
        vm.onAction(WalkAction.SelectTerritorySite("remote"))
        runCurrent()
        assertEquals("remote", vm.state.value.territory.selectedSiteId)
        assertEquals(remote, vm.state.value.territory.loadedOrigin)
        assertEquals(queryCount, queries.size)
        assertEquals(false, vm.state.value.location.followDevice)
        assertEquals(listOf(remote, GeoPoint(37.5, 127.0)), vm.state.value.toMapPresentation().fitBounds)
        vm.onAction(WalkAction.CameraMoved)
        runCurrent()
        assertEquals(null, vm.state.value.toMapPresentation().fitBounds)
        assertEquals("remote", vm.state.value.territory.selectedSiteId)
        vm.onAction(WalkAction.SelectTerritorySite("remote"))
        runCurrent()
        vm.onAction(WalkAction.Locate)
        runCurrent()
        assertEquals(false, vm.state.value.map.frameSelectedTerritory)
        assertEquals(true, vm.state.value.location.followDevice)
        assertEquals(GeoPoint(37.5, 127.0), vm.state.value.territory.loadedOrigin)
    }

    @Test
    fun `screen location quality reaches UI before walking and clears with permission`() = runTest {
        val sample = LocationSample(GeoPoint(37.5, 127.0), 1000, 1_000_000_000L, 3f)
        val source = object : LocationSource {
            override suspend fun currentLocation() = sample
            override fun locationUpdates(config: LocationUpdateConfig): Flow<LocationSample> = emptyFlow()
        }
        val vm = viewModel(FakeWalkController(), source)
        vm.activate(true, true)
        runCurrent()
        assertEquals(null, vm.state.value.tracking.latestMomentFix)
        assertEquals(sample, vm.state.value.location.sample)
        assertEquals(true, walkGpsPresentation(true, true, null, vm.state.value.location.sample, 2_000_000_000L).good)
        vm.updatePermission(false, false)
        runCurrent()
        assertEquals(null, vm.state.value.location.sample)
    }

    private fun TestScope.viewModel(
        controller: FakeWalkController,
        source: LocationSource,
        territoryRepository: TerritorySiteRepository = TerritorySiteRepository {
            TerritorySitePage(count = 0, truncated = false, sites = emptyList())
        },
        game: com.daengs.app.map.features.territory.TerritoryGameProvider? = null,
        photoQueue: com.daengs.app.territory.TerritoryPhotoQueue? = null,
        clock: () -> Long = { 2_000_000_000L },
    ) = WalkViewModel(
        walkController = controller,
        history = WalkHistory(EmptyWalkFixLog),
        locationSource = source,
        territoryRepository = territoryRepository,
        externalScope = backgroundScope,
        territoryGame = game,
        nowNanos = clock,
        photoQueue = photoQueue,
    )

    private class CountingLocationSource : LocationSource {
        var starts = 0
        var stops = 0
        var currentLocationCalls = 0

        override suspend fun currentLocation(): LocationSample {
            currentLocationCalls++
            return LocationSample(
                point = GeoPoint(37.5, 127.0),
                capturedAtMillis = 0,
            )
        }

        override fun locationUpdates(config: LocationUpdateConfig): Flow<LocationSample> = flow {
            starts++
            try {
                awaitCancellation()
            } finally {
                stops++
            }
        }
    }

    private class SuspendingLocationSource : LocationSource {
        var currentLocationCalls = 0
        var cancellations = 0

        override suspend fun currentLocation(): LocationSample {
            currentLocationCalls++
            try {
                awaitCancellation()
            } finally {
                cancellations++
            }
        }

        override fun locationUpdates(
            config: LocationUpdateConfig,
        ): Flow<LocationSample> = emptyFlow()
    }

    private class CountingTerritoryRepository : TerritorySiteRepository {
        var calls = 0

        override suspend fun nearby(
            request: NearbyTerritorySitesRequest,
        ): TerritorySitePage {
            calls++
            return TerritorySitePage(count = 0, truncated = false, sites = emptyList())
        }
    }

    private class FakeWalkController : WalkTrackingController {
        private val mutableState = MutableStateFlow(WalkTrackingState())
        override val state = mutableState.asStateFlow()
        private val mutableEvents = MutableSharedFlow<WalkEvent>(extraBufferCapacity = 4)
        override val events = mutableEvents.asSharedFlow()
        var startedDogIds: List<String>? = null
        val sessionCommands = mutableListOf<String>()

        fun publish(state: WalkTrackingState) {
            mutableState.value = state
        }

        override fun start(dogIds: List<String>) {
            sessionCommands += "start"
            startedDogIds = dogIds
        }

        override fun pause() { sessionCommands += "pause" }
        override fun resume() { sessionCommands += "resume" }
        override fun stop() { sessionCommands += "stop" }
        override fun recordMoment(type: WalkMomentType) = Unit
        override fun dismissCompletion() = Unit
    }

    private fun pet(id: String) = Pet(
        id = id,
        name = id,
        breed = "maltese",
        sex = null,
        neutered = null,
        weightKg = null,
        birthDate = LocalDate.of(2020, 1, 1),
        birthDateKind = Pet.BirthDateKind.BIRTHDAY,
        isPrimary = id == "dog-1",
    )
}

private data object EmptyWalkFixLog : WalkFixLog {
    override suspend fun openSession(session: RecordedSession) = Unit
    override suspend fun append(sessionId: String, fix: RecordedFix) = Unit
    override suspend fun appendAction(action: RecordedWalkAction) = Unit
    override suspend fun closeSession(sessionId: String, endedAtMillis: Long) = Unit
    override suspend fun stampWeather(sessionId: String, weather: RecordedWeather) = Unit
    override suspend fun deleteSession(sessionId: String) = Unit
    override suspend fun unfinishedSessions(): List<RecordedSession> = emptyList()
    override suspend fun finishedSessions(): List<RecordedSession> = emptyList()
    override suspend fun sessionsPendingAnalysis(): List<RecordedSession> = emptyList()
    override suspend fun markRawUploaded(
        sessionId: String,
        serverWalkId: String,
        changedAtMillis: Long,
    ) = Unit

    override suspend fun markDerived(sessionId: String, changedAtMillis: Long) = Unit
    override suspend fun forgetDog(dogId: String) = Unit
    override suspend fun forgetEverything() = Unit
    override suspend fun session(sessionId: String): RecordedSession? = null
    override suspend fun fixes(sessionId: String): List<RecordedFix> = emptyList()
    override suspend fun actions(sessionId: String): List<RecordedWalkAction> = emptyList()
}
