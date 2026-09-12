package com.daengs.app.walk.sync

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.walk.motion.MotionPolicies
import com.daengs.app.walk.store.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WalkRouteBackupSourceTest {
    private fun session() = WalkSessionRow("s", 1, "owner", 2, serverWalkId = "remote", syncState = "derived",
        motionPolicyJson = MotionPolicies.encode(MotionPolicies.freeze("s", measure = true)), coordinateOrigin = "captured")
    private fun base(done: Long? = 3) = WalkMotionBackupRow("s", "{}", "m", "e", done)
    private fun precision(done: Long? = null, verified: Long? = null, error: String? = null) =
        WalkMotionPrecisionRow("s", "{}", "pm", "pe", done, error, verified, if (verified != null) "{}" else null)

    @Test fun `raw delivery and upload acknowledgement are not precision verification`() {
        val s = session()
        assertEquals(WalkRouteBackupState.PENDING, routeBackupState(s.copy(syncState = "raw_uploaded"), base(), precision(4, 5)))
        assertEquals(WalkRouteBackupState.PENDING, routeBackupState(s, null, null))
        assertEquals(WalkRouteBackupState.PENDING, routeBackupState(s, base(), precision()))
        assertEquals(WalkRouteBackupState.CHECKING, routeBackupState(s, base(), precision(4)))
        assertEquals(WalkRouteBackupState.NEEDS_RETRY, routeBackupState(s, base(), precision(4, error = "VERIFICATION_REJECTED")))
        assertEquals(WalkRouteBackupState.COMPLETE, routeBackupState(s, base(), precision(4, 5)))
        assertEquals(WalkRouteBackupState.CHECKING, routeBackupState(s, base(), precision(4, 5).copy(verificationJson = null)))
    }

    @Test fun `legacy and coarse restore use their own receipt while unknown policies stay unconfirmed`() {
        val s = session()
        assertEquals(WalkRouteBackupState.COMPLETE, routeBackupState(s.copy(motionPolicyJson = null), null, null))
        assertEquals(WalkRouteBackupState.COMPLETE, routeBackupState(s.copy(coordinateOrigin = null), base(), null))
        assertEquals(WalkRouteBackupState.PENDING, routeBackupState(s.copy(coordinateOrigin = null), base(null), null))
        assertEquals(WalkRouteBackupState.NEEDS_RETRY, routeBackupState(s.copy(motionPolicyJson = "future"), base(), precision(4, 5)))
    }

    @Test fun `Room receipt changes refresh the same collector and deletion clears it`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(), WalkDatabase::class.java).build()
        val values = Channel<WalkRouteBackupState?>(Channel.UNLIMITED)
        var job: Job? = null
        try {
            db.walkDao().insertSession(session())
            db.walkDao().insertMotionBackup(base())
            val source = WalkRouteBackupSource(db, "owner", { true }, {})
            job = launch { source.observe("s").collect { values.send(it) } }
            suspend fun expect(value: WalkRouteBackupState?) = withTimeout(10_000) {
                do { val actual = values.receive() } while (actual != value)
            }
            expect(WalkRouteBackupState.PENDING)
            db.walkDao().insertMotionPrecision(precision(4))
            expect(WalkRouteBackupState.CHECKING)
            db.walkDao().updateMotionPrecision(precision(4, 5))
            expect(WalkRouteBackupState.COMPLETE)
            db.walkDao().deleteSession("s")
            expect(null)
        } finally { job?.cancelAndJoin(); db.close() }
    }

    @Test fun `queue success is not completion and stale account or deleted record cannot enqueue`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(), WalkDatabase::class.java).build()
        var current = true
        val requests = mutableListOf<String>()
        val source = WalkRouteBackupSource(db, "owner", { current }, { requests += it })
        try {
            db.walkDao().insertSession(session())
            assertTrue(source.request("s"))
            assertEquals(WalkRouteBackupState.PENDING, source.read("s"))
            assertNull(db.walkDao().motionPrecision("s"))
            current = false
            assertNull(source.read("s")); assertFalse(source.request("s"))
            current = true
            db.walkDao().deleteSession("s")
            assertFalse(source.request("s"))
            db.walkDao().insertSession(session().copy(ownerId = "other"))
            assertNull(source.read("s")); assertFalse(source.request("s"))
            assertEquals(listOf("s"), requests)
        } finally { db.close() }
    }
}
