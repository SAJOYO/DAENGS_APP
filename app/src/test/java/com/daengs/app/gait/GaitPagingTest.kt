package com.daengs.app.gait

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 목록을 커서 끝까지 받아 오는 규칙.
 *
 * **여기가 한 번 뚫린 자리다.** 저쪽은 기록을 **오래된 것부터** 주는데(서버 설명:
 * "한 강아지의 기록, 오래된 것부터") 앱은 첫 장만 받고 `next_cursor` 를 버렸다. 그래서
 * 기록이 한 장을 넘기는 순간 **방금 분석한 것이 목록에서 사라졌다** — 분석 직후에는
 * `analyze` 가 앞에 끼워 넣어 보이다가, 챗을 나갔다 들어오면 `load` 가 첫 장(가장 오래된
 * 20개)으로 덮었다. 빌드도 테스트도 그대로 통과했다.
 *
 * 정렬을 바꿔 달라고 할 수는 없다 — `GET /app/gait/records` 에는 정렬 옵션이 없다.
 */
class GaitPagingTest {

    private fun summary(id: String) = GaitSummary(
        recordId = id,
        status = "DONE",
        date = LocalDate.of(2026, 9, 12),
        comparable = true,
        hasOverlay = false,
        filterVersion = null,
        tier = null,
        note = null,
    )

    /** `ids` 를 장마다 끊어 주는 가짜 서버. 마지막 장에서 `next_cursor` 를 null 로 준다. */
    private fun pagesOf(vararg pages: List<String>): suspend (String?) -> Result<GaitPage> {
        val byCursor = pages.mapIndexed { index, ids ->
            val next = if (index + 1 < pages.size) "cursor-${index + 1}" else null
            val key = if (index == 0) null else "cursor-$index"
            key to GaitPage(ids.map(::summary), next)
        }.toMap()
        return { cursor -> Result.success(byCursor.getValue(cursor)) }
    }

    // -- 끝까지 받는다 --------------------------------------------------------

    @Test
    fun `두 장이면 둘 다 받는다 — 새 기록은 뒷장에 있다`() = runTest {
        val got = gatherGaitPages(fetch = pagesOf(listOf("old1", "old2"), listOf("new1")))

        assertEquals(listOf("old1", "old2", "new1"), got.summaries.map { it.recordId })
        assertNull(got.error)
        assertFalse(got.hitPageLimit)
    }

    @Test
    fun `한 장뿐이면 한 번만 부른다`() = runTest {
        var calls = 0
        val got = gatherGaitPages { cursor ->
            calls++
            assertNull("첫 장은 커서가 없다", cursor)
            Result.success(GaitPage(listOf(summary("only")), null))
        }

        assertEquals(1, calls)
        assertEquals(listOf("only"), got.summaries.map { it.recordId })
    }

    // -- 멈추는 조건 ----------------------------------------------------------

    @Test
    fun `같은 커서가 다시 오면 멈춘다`() = runTest {
        // 서버가 늘 같은 커서를 주는 경우. 안 막으면 상한까지 같은 장을 반복해 받는다.
        var calls = 0
        val got = gatherGaitPages(maxPages = 50) {
            calls++
            Result.success(GaitPage(listOf(summary("loop$calls")), "same"))
        }

        assertEquals("두 번째에서 같은 커서를 보고 멈춰야 한다", 2, calls)
        assertEquals(2, got.summaries.size)
    }

    @Test
    fun `커서가 돌고 돌아도 멈춘다`() = runTest {
        // a -> b -> a 로 도는 경우. 직전 것만 비교하면 안 걸린다.
        val ring = mapOf(null to "a", "a" to "b", "b" to "a")
        var calls = 0
        val got = gatherGaitPages(maxPages = 50) { cursor ->
            calls++
            Result.success(GaitPage(listOf(summary("r$calls")), ring[cursor]))
        }

        assertTrue("무한히 돌면 안 된다 (부른 횟수 $calls)", calls < 10)
        assertFalse(got.hitPageLimit)
    }

    @Test
    fun `상한을 넘겨 부르지 않는다`() = runTest {
        var calls = 0
        val got = gatherGaitPages(maxPages = 3) {
            calls++
            Result.success(GaitPage(listOf(summary("p$calls")), "next-$calls"))
        }

        assertEquals(3, calls)
        assertTrue("상한에 걸린 것을 알려야 한다", got.hitPageLimit)
        assertEquals(3, got.summaries.size)
    }

    // -- 실패 --------------------------------------------------------------

    @Test
    fun `첫 장부터 실패하면 아무것도 못 받았다고 말한다`() = runTest {
        val got = gatherGaitPages { Result.failure(IllegalStateException("서버에 닿지 못했어요.")) }

        assertTrue("화면이 목록을 비우지 않도록 비어 있어야 한다", got.summaries.isEmpty())
        assertEquals("서버에 닿지 못했어요.", got.error)
    }

    @Test
    fun `뒷장에서 끊기면 받은 데까지는 남긴다`() = runTest {
        var calls = 0
        val got = gatherGaitPages {
            calls++
            if (calls == 1) Result.success(GaitPage(listOf(summary("got")), "next"))
            else Result.failure(IllegalStateException("끊겼어요."))
        }

        assertEquals(listOf("got"), got.summaries.map { it.recordId })
        assertNotNull("이유는 남겨야 한다", got.error)
    }

    @Test
    fun `실패하면 같은 커서로 되풀이하지 않는다`() = runTest {
        var calls = 0
        gatherGaitPages(maxPages = 20) {
            calls++
            Result.failure(IllegalStateException("늘 실패"))
        }

        assertEquals("한 번 실패하면 멈춘다", 1, calls)
    }
}
