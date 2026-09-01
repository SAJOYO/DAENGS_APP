package com.daengs.app.walk.store

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.RecordedSession
import com.daengs.app.walk.WalkHistory
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 홈 요약과 "너무 짧은 산책" 판정을 **진짜 Room 과 진짜 좌표**로 확인한다.
 *
 * 순수 함수 쪽은 `WalkSummaryTest` 가 잡는다. 여기서 보는 것은 그 판정이 저장된
 * 원본 좌표를 거쳐서도 같은 답을 내는지, 그리고 미달일 때 **정말 지워지는지**다 —
 * 지워지지 않으면 서버에도 올라간다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WalkHistoryTest {
    private lateinit var db: WalkDatabase
    private lateinit var history: WalkHistory
    private lateinit var log: RoomWalkFixLog

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WalkDatabase::class.java,
        ).build()
        log = RoomWalkFixLog(db.walkDao())
        history = WalkHistory(log)
    }

    @After
    fun tearDown() = db.close()

    /** 문을 눌렀다 그냥 닫은 것. **좌표째로 지워야** 목록에도 서버에도 안 남는다. */
    @Test
    fun `너무 짧으면 지운다`() = runBlocking {
        walked("short", startedAt = 1_000L, meters = 2.0, seconds = 5)

        assertFalse(history.keepIfWalk("short"))
        assertNull(log.session("short"))
        assertTrue(log.fixes("short").isEmpty())
    }

    /** 걸은 산책은 손대지 않는다. */
    @Test
    fun `걸었으면 그대로 둔다`() = runBlocking {
        walked("real", startedAt = 1_000L, meters = 400.0, seconds = 600)

        assertTrue(history.keepIfWalk("real"))
        assertNotNull(log.session("real"))
    }

    /** 오늘 나선 것만, 산책으로 칠 만한 것만 합산된다. */
    @Test
    fun `홈 요약은 오늘 걸은 것을 합친다`() = runBlocking {
        walked("today-1", startedAt = todayAt(9), meters = 400.0, seconds = 600)
        walked("today-2", startedAt = todayAt(19), meters = 600.0, seconds = 900)
        // 오늘이지만 산책이 아니다.
        walked("today-tiny", startedAt = todayAt(13), meters = 2.0, seconds = 5)
        // 어제 것은 안 센다.
        walked("yesterday", startedAt = todayAt(9) - DAY_MILLIS, meters = 900.0, seconds = 1_200)

        val totals = history.todayTotals()

        assertEquals(2, totals.count)
        assertEquals(1_500_000L, totals.activeDurationMillis)
        // 거리는 좌표에서 다시 계산한 값이라 미터 단위로만 비교한다.
        assertEquals(1_000.0, totals.distanceMeters, 5.0)
    }

    /** 끝나지 않은 산책은 걷는 중이라 아직 오늘의 기록이 아니다. */
    @Test
    fun `미종료 세션은 홈 요약에 안 들어간다`() = runBlocking {
        walked("walking", startedAt = todayAt(9), meters = 400.0, seconds = 600, close = false)

        assertEquals(0, history.todayTotals().count)
    }

    @Test
    fun `기록이 없으면 0 이다`() = runBlocking {
        val totals = history.todayTotals()

        assertEquals(0, totals.count)
        assertEquals(0L, totals.activeDurationMillis)
        assertEquals(0.0, totals.distanceMeters, 0.001)
    }

    /**
     * 산책으로 안 치는 옛 기록은 목록에도 안 나온다.
     *
     * 규칙([countsAsWalk])이 생기기 전에 쌓인 0m 짜리가 목록에 남아 있으면,
     * "너무 짧아서 기록하지 않았어요" 라고 말해 놓고 목록에는 0m 이 보이는 앞뒤 안
     * 맞는 화면이 된다. **지우지는 않는다** — 원본은 그대로 있다.
     */
    @Test
    fun `산책으로 안 치는 옛 기록은 목록에서 빠진다`() = runBlocking {
        walked("real", startedAt = 1_000L, meters = 400.0, seconds = 600)
        walked("stub", startedAt = 5_000L, meters = 2.0, seconds = 5)

        assertEquals(listOf("real"), history.finished().map { it.sessionId })
        // 지운 것이 아니다. 원본은 남아 있다.
        assertNotNull(log.session("stub"))
    }

    /**
     * 좌표 둘을 [meters] 만큼 떼어 [seconds] 초에 걸쳐 남긴 산책 하나.
     *
     * 위도 0.001° 가 약 111m 다. [TrailRecorder] 의 문턱값(최소 3m · 최대 점프 200m)
     * 안에 들도록 중간 점을 끼워 넣는다.
     */
    private suspend fun walked(
        id: String,
        startedAt: Long,
        meters: Double,
        seconds: Int,
        close: Boolean = true,
    ) {
        log.openSession(RecordedSession(id, dogId = null, startedAtMillis = startedAt))
        val steps = ((meters / 100.0).toInt() + 1).coerceAtLeast(1)
        for (i in 0..steps) {
            log.append(
                id,
                RecordedFix(
                    clientSeq = i,
                    chainIndex = 0,
                    atMillis = startedAt + seconds * 1_000L * i / steps,
                    lat = 37.5 + (meters / 111_195.0) * i / steps,
                    lng = 127.0,
                    accuracyM = 5f,
                    isMock = false,
                ),
            )
        }
        if (close) log.closeSession(id, startedAt + seconds * 1_000L)
    }

    private fun todayAt(hour: Int): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).plusHours(hour.toLong())
            .toInstant().toEpochMilli()

    private companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
