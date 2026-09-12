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
    @Test fun `기존 GPS 행동의 v1 Room 형식과 원본 시각 좌표를 보존한다`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WalkDatabase::class.java).build()
        try {
            val dao = db.walkDao()
            val log = RoomWalkFixLog(dao)
            log.openSession(RecordedSession("walk", startedAtMillis = 0, dogIds = listOf("dog")))
            val action = requireNotNull(capture())
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

    @Test fun `v1 생성은 위치 없음 추정값과 오래된 GPS를 받지 않는다`() {
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
