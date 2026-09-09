package com.daengs.app.walk.pin

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.walk.*
import com.daengs.app.walk.store.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LegacyActionPinTest {
    private val sample = LocationSample(GeoPoint(37.5, 127.0), 1000, 1_000_000_000, 5f)
    private fun capture(fix: LocationSample? = sample, elapsed: Long = 2_000_000_000) =
        legacyWalkAction(fix, "action", "walk", WalkMomentType.SNIFFING, 2000, elapsed)

    @Test fun `임시 기록은 실제 GPS 시각과 좌표를 유지하고 v1 Room 행으로 저장한다`() = runBlocking {
        assertTrue(ActionPinRollout.legacyCreation)
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WalkDatabase::class.java).build()
        try {
            val dao = db.walkDao()
            val log = RoomWalkFixLog(dao)
            log.openSession(RecordedSession("walk", startedAtMillis = 0, dogIds = listOf("dog")))
            val action = capture()!!
            log.appendAction(action)
            val row = dao.entry("action")!!
            assertFalse(row.isV2)
            assertNull(row.pinPayload)
            assertNull(row.pendingRequest)
            assertTrue(row.dirty)
            assertEquals(sample.point, row.entry()!!.point)
            assertEquals(1000L, row.entry()!!.locationCapturedAtMillis)
            assertEquals(2000L, row.entry()!!.recordedAtMillis)
            assertEquals("dog", row.entry()!!.petId)
        } finally { db.close() }
    }

    @Test fun `GPS가 없거나 오래되거나 부정확하면 기존처럼 핀 생성을 막는다`() {
        assertNotNull(capture(elapsed = 11_000_000_000))
        assertNull(capture(elapsed = 11_000_000_001))
        assertNull(capture(elapsed = 999_999_999))
        assertNull(capture(null))
        for (bad in listOf(sample.copy(isMock = true), sample.copy(elapsedRealtimeNanos = null),
            sample.copy(accuracyMeters = null), sample.copy(accuracyMeters = Float.NaN),
            sample.copy(accuracyMeters = 0f), sample.copy(accuracyMeters = -1f),
            sample.copy(accuracyMeters = 15.1f), sample.copy(capturedAtMillis = 2001))) {
            assertNull(capture(bad))
        }
    }
}
