package com.daengs.app.territory

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.auth.Session
import com.daengs.app.location.*
import com.daengs.app.walk.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TerritoryActionStoreTest {
    @Test fun `Room reopen keeps ambiguous original request then recovery completes it before closing abandoned walk`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.deleteDatabase("daengs_territory.db")
        val server = ClaimServer()
        val auth = Session("owner", "token", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
        val state = WalkTrackingState(ownerId = "owner", activeSessionId = WALK,
            activeSessionStartedAtMillis = 1000, activeDogIds = listOf(DOG), trail = TrailSnapshot(state = TrackingState.RECORDING))
        val original = markBody(WALK, SITE, DOG, LocationSample(GeoPoint(37.5, 127.0), 2000, accuracyMeters = 2f))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val db = TerritoryActionDatabase.open(context)
        val sync = TerritoryActionSync(db.actions(), server, { auth }, { "owner" }, { state }, scope, {})
        try {
            sync.syncTracking(); sync.deliver(); sync.submit(state, SITE, DOG, original)
            server.failMarkAfterCommit = true
            assertFalse(sync.deliver())
            val saved = db.actions().all().last()
            assertTrue(saved.sent)
            assertEquals(-1L, db.actions().insert(saved.copy(sequence = 0, body = "must-not-replace")))
        } finally { scope.coroutineContext.job.cancelAndJoin(); db.close() }

        val restartedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val reopened = TerritoryActionDatabase.open(context)
        try {
            assertEquals(original, reopened.actions().all().last().body)
            val restarted = TerritoryActionSync(reopened.actions(), server, { auth }, { "owner" },
                { WalkTrackingState() }, restartedScope, {})
            restarted.recover(); assertTrue(restarted.deliver())
            assertEquals("ENDED", server.phase)
            assertNull(restarted.receipt.value)
            assertTrue(reopened.actions().all().all { it.state == "CONFIRMED" })
            assertEquals(listOf(original, original), server.calls.filter { it.first == "POST" }.map { it.third })
        } finally { restartedScope.coroutineContext.job.cancelAndJoin(); reopened.close() }
    }
}
