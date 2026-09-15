package com.daengs.app.walk.records

import com.daengs.app.care.CareActor
import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.WalkSummary
import com.daengs.app.walk.shared.SharedWalk
import com.daengs.app.walk.shared.SharedWalkCarer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 내 산책(기기)과 공동 보호자 산책(서버)을 한 목록·쪽·합계로 합치는 규칙. */
class WalkRecordRowsTest {

    private fun mine(id: String, at: Long, serverId: String? = null) = WalkRecord(
        WalkSummary(id, listOf("p1"), at, at + 600_000, null, 500.0, 600_000, emptyList(), null), serverWalkId = serverId,
    )

    private fun shared(id: String, at: Long, isMine: Boolean = false) =
        SharedWalk(id, at, at + 600_000, 600, 700, 500, CareActor("u2", "키키"), isMine, listOf("p1"))

    private fun ids(rows: List<WalkRecordRow>) = rows.map { (if (it is WalkRecordRow.Shared) "s:" else "m:") + it.id }

    @Test fun `내 산책과 공동 보호자 산책을 최근 순으로 합치고 같은 시각은 id 로 가른다`() {
        val rows = mergeWalkRecordRows(
            mine = listOf(mine("m1", 1_000), mine("m2", 3_000)),
            shared = listOf(shared("s-b", 4_000), shared("s-a", 2_000), shared("s-c", 3_000)),
        )

        assertEquals(listOf("s:s-b", "s:s-c", "m:m2", "s:s-a", "m:m1"), ids(rows))
    }

    @Test fun `같은 산책은 한 번만 둔다 — 기기 기록의 서버 id 내 산책 표시 중복 응답`() {
        val rows = mergeWalkRecordRows(
            mine = listOf(mine("session-1", 5_000, serverId = "server-1")),
            shared = listOf(
                shared("server-1", 5_000),
                shared("server-2", 4_000, isMine = true),
                shared("server-3", 3_000),
                shared("server-3", 3_000),
            ),
        )

        assertEquals(listOf("m:session-1", "s:server-3"), ids(rows))
    }

    @Test fun `받아 둔 공동 보호자 산책이 그 쪽을 채울 만큼이면 순서가 확정된다`() {
        val myWalks = (1..6).map { mine("m$it", it * 10L) }
        val theirs = (1..5).map { shared("s$it", it * 10L + 5) }.reversed()

        val first = unifiedWalkPage(myWalks, theirs, sharedTotal = 12, sharedExhausted = false, pageIndex = 0, pageSize = 5)
        assertTrue(first.complete)
        assertEquals(listOf("m:m6", "s:s5", "m:m5", "s:s4", "m:m4"), ids(first.rows))
        assertEquals("내 산책 6 + 서버 합계 12 = 18 → 4쪽", 4, first.pageCount)
        assertEquals(18, first.total)

        val second = unifiedWalkPage(myWalks, theirs, sharedTotal = 12, sharedExhausted = false, pageIndex = 1, pageSize = 5)
        assertFalse("5건만 받았는데 2쪽은 10건이 필요하다", second.complete)
        assertEquals(10, sharedWalksNeededFor(1, 5))
        assertEquals("덜 받았어도 내 산책은 비우지 않는다", 5, second.rows.size)
    }

    @Test fun `끝까지 받았거나 실패했으면 더 기다리지 않고 쪽 번호는 범위 안으로 맞춘다`() {
        val page = unifiedWalkPage(listOf(mine("m1", 10)), listOf(shared("s1", 20)), sharedTotal = 1,
            sharedExhausted = true, pageIndex = 9, pageSize = 5)

        assertTrue(page.complete)
        assertEquals(0, page.pageIndex)
        assertEquals(1, page.pageCount)
        assertEquals(listOf("s:s1", "m:m1"), ids(page.rows))
    }

    @Test fun `서버 합계에 기기 기록과 겹친 산책이 있으면 전체 수에서 덜어 낸다`() {
        val page = unifiedWalkPage(listOf(mine("m1", 10, serverId = "dup")), listOf(shared("dup", 10), shared("s1", 20)),
            sharedTotal = 2, sharedExhausted = true, pageIndex = 0, pageSize = 5)

        assertEquals(2, page.total)
    }

    @Test fun `아무것도 없으면 한 쪽짜리 빈 목록이다`() {
        val page = unifiedWalkPage(emptyList(), emptyList(), 0, sharedExhausted = true, pageIndex = 0, pageSize = 5)

        assertEquals(0, page.total)
        assertEquals(1, page.pageCount)
        assertTrue(page.rows.isEmpty())
    }

    @Test fun `공동 보호자 산책은 제목 없이 축약 경로 날씨 수행 시간으로 카드 기록이 된다`() {
        val walk = shared("s1", 1_000).copy(weatherCode = 61, isDay = null, temperatureC = 18f, distanceM = null,
            routePreview = listOf(listOf(GeoPoint(37.5, 127.0), GeoPoint(37.6, 127.1)), listOf(GeoPoint(37.7, 127.2))))

        val record = sharedWalkRecord(walk)

        assertNull(record.title)
        assertEquals("s1", record.serverWalkId)
        assertEquals(listOf("p1"), record.summary.dogIds)
        assertEquals(61, record.summary.weather?.weatherCode)
        assertEquals(600_000L, record.summary.activeDurationMillis)
        assertEquals(0.0, record.summary.distanceMeters, 0.0)
        assertEquals(listOf(2, 1), record.summary.segments.map { it.size })
        assertTrue(record.summary.hasRoute)
        assertEquals(GeoPoint(37.5, 127.0), record.summary.anchor)
    }

    // -- 보호자 조건 ---------------------------------------------------------------------------

    @Test fun `개별 보호자는 여러 명 고를 수 있고 모두 풀면 모든 보호자로 돌아간다`() {
        var selected: Set<String>? = null
        selected = toggleCarer(selected, "u2")
        assertEquals(setOf("u2"), selected)
        selected = toggleCarer(selected, "me")
        assertEquals(setOf("u2", "me"), selected)
        selected = toggleCarer(selected, "u2")
        assertEquals(setOf("me"), selected)
        assertNull(toggleCarer(selected, "me"))
    }

    private val carers = listOf(
        SharedWalkCarer("u2", "키키", isMe = false, petIds = listOf("p1")),
        SharedWalkCarer("me", "롱롱씨 메인", isMe = true, petIds = listOf("p1", "p2")),
        SharedWalkCarer("u3", "보리아빠", isMe = false, petIds = listOf("p2")),
    )

    @Test fun `후보는 나를 맨 앞에 두고 고른 강아지와 함께 돌보는 사람만이다`() {
        assertEquals(listOf("나", "키키", "보리아빠"), carerCandidates(carers, dogIds = null, myId = "me").map { it.displayName })
        assertEquals(listOf("나", "키키"), carerCandidates(carers, dogIds = setOf("p1"), myId = "me").map { it.displayName })
        assertEquals(listOf("나", "보리아빠"), carerCandidates(carers, dogIds = setOf("p2"), myId = "me").map { it.displayName })
        assertEquals("후보를 못 받았어도 나는 고를 수 있다", listOf("나"),
            carerCandidates(emptyList(), dogIds = null, myId = "me").map { it.displayName })
    }

    @Test fun `조건 요약은 모든 보호자 나 닉네임 외 몇 명으로 줄인다`() {
        assertEquals("모든 보호자", carerSummary(null, carers, "me"))
        assertEquals("나", carerSummary(setOf("me"), carers, "me"))
        assertEquals("키키", carerSummary(setOf("u2"), carers, "me"))
        assertEquals("나 외 1명", carerSummary(setOf("u2", "me"), carers, "me"))
        assertEquals("키키 외 1명", carerSummary(setOf("u2", "u3"), carers, "me"))
        assertEquals("선택한 보호자", carerSummary(setOf("gone"), carers, "me"))
    }
}
