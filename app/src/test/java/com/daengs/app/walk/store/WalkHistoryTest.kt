package com.daengs.app.walk.store

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.RecordedSession
import com.daengs.app.walk.RecordedWalkAction
import com.daengs.app.walk.WalkHistory
import com.daengs.app.walk.WalkMomentType
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

    @Test
    fun `완료 상세의 경로는 저장된 좌표 원본에서 함께 만들어진다`() = runBlocking {
        walked("real", startedAt = 1_000L, meters = 400.0, seconds = 600)

        val detail = history.sessionDetail("real")

        assertNotNull(detail)
        assertEquals(detail?.summary?.segments?.flatten()?.size, detail?.route?.points?.size)
        assertEquals(detail?.summary?.segments?.first()?.first()?.capturedAtMillis, detail?.route?.start?.capturedAtMillis)
        assertEquals(detail?.summary?.segments?.last()?.last()?.capturedAtMillis, detail?.route?.end?.capturedAtMillis)
    }

    @Test
    fun `저장된 상세는 행동 원본을 현재 반경으로 다시 묶는다`() = runBlocking {
        walked("real", startedAt = 1_000L, meters = 400.0, seconds = 600)
        log.appendAction(action("a1", WalkMomentType.EXPLORE, 2_000L, 37.50000))
        log.appendAction(action("a2", WalkMomentType.SOCIAL, 3_000L, 37.50001))
        log.appendAction(action("a3", WalkMomentType.SPECIAL, 4_000L, 37.50100))

        val detail = history.sessionDetail("real")

        assertEquals(2, detail?.moments?.size)
        assertEquals(2, detail?.moments?.first()?.actions?.size)
        assertEquals(
            setOf(WalkMomentType.EXPLORE, WalkMomentType.SOCIAL),
            detail?.moments?.first()?.types,
        )
    }

    /**
     * 좌표 둘을 [meters] 만큼 떼어 [seconds] 초에 걸쳐 남긴 산책 하나.
     *
     * 위도 0.001° 가 약 111m 다. [TrailRecorder] 의 문턱값(최소 3m · 최대 점프 200m)
     * 안에 들도록 중간 점을 끼워 넣는다.
     */
    /**
     * 탈퇴하면 **이 기기의 좌표와 행동도 남으면 안 된다.**
     *
     * 서버는 탈퇴에서 개인정보를 파기하고 산책도 `ON DELETE CASCADE` 로 지운다.
     * 그런데 폰의 Room 에는 원본 좌표가 그대로 남아 있었다 — 산책 경로는 집과
     * 생활권을 그대로 드러내는 값이라, 그걸 두고 "계정을 지우면 데이터도 지운다"
     * 고 하면 거짓말이 된다. 다음에 이 폰으로 로그인한 사람이 남의 동선을
     * 물려받기도 한다.
     */
    @Test
    fun `전부 잊으면 좌표와 행동까지 사라진다`() = runBlocking {
        walked("a", startedAt = todayAt(9), meters = 400.0, seconds = 600)
        walked("b", startedAt = todayAt(19), meters = 600.0, seconds = 900)
        log.appendAction(action("a1", WalkMomentType.EXPLORE, todayAt(9), 37.5, sessionId = "a"))
        log.appendAction(action("b1", WalkMomentType.SOCIAL, todayAt(19), 37.6, sessionId = "b"))
        assertEquals(2, history.finished().size)

        history.forgetEverything()

        assertTrue("세션이 남았다", history.finished().isEmpty())
        assertNull(log.session("a"))
        assertNull(log.session("b"))
        assertTrue("좌표가 남았다", log.fixes("a").isEmpty())
        assertTrue("좌표가 남았다", log.fixes("b").isEmpty())
        assertTrue("행동이 남았다", log.actions("a").isEmpty())
        assertTrue("행동이 남았다", log.actions("b").isEmpty())
    }

    /** 비어 있을 때 불러도 터지지 않는다 — 강아지를 한 번도 안 걸은 사람도 탈퇴한다. */
    @Test
    fun `지울 게 없어도 조용히 끝난다`() = runBlocking {
        history.forgetEverything()
        assertTrue(history.finished().isEmpty())
    }

    /** 아직 안 끝난 산책도 지운다. 탈퇴는 "이 기기에서 나를 지우는 것" 이다. */
    @Test
    fun `진행 중인 산책도 지운다`() = runBlocking {
        walked("open", startedAt = todayAt(9), meters = 300.0, seconds = 400, close = false)
        assertNotNull(log.session("open"))

        history.forgetEverything()

        assertNull(log.session("open"))
        assertTrue(log.fixes("open").isEmpty())
    }

    private suspend fun walked(
        id: String,
        startedAt: Long,
        meters: Double,
        seconds: Int,
        close: Boolean = true,
    ) {
        log.openSession(RecordedSession(id, dogIds = emptyList(), startedAtMillis = startedAt))
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

    private fun action(
        id: String,
        type: WalkMomentType,
        recordedAtMillis: Long,
        latitude: Double,
        sessionId: String = "real",
    ) = RecordedWalkAction(
        id = id,
        sessionId = sessionId,
        type = type,
        recordedAtMillis = recordedAtMillis,
        locationCapturedAtMillis = recordedAtMillis - 100L,
        point = GeoPoint(latitude, 127.0),
        accuracyMeters = 5f,
    )

    private companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
