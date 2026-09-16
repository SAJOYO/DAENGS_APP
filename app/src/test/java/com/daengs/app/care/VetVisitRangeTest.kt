package com.daengs.app.care

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * 기간 프리셋이 **어떤 창으로 번역되는가.** 저쪽 `routers/vet_visit.py` 의
 * `from`/`to` 는 둘 다 보내는 것이 권장이고(한쪽만 보내면 나머지를 서버가 채운다),
 * `to < from` 이면 422 다 (SAJOYO/DAENGS_dev#528).
 *
 * **"오늘" 이 인자인 이유가 여기 있다.** 서버는 `Asia/Seoul` 로 오늘을 정하는데, 기기
 * 시간대는 다를 수 있다. 오늘을 밖에서 받으면 그 규칙을 테스트가 붙잡을 수 있다 —
 * `LocalDate.now()` 를 안에서 부르면 테스트는 자기가 도는 기기의 시간대를 볼 뿐이다.
 */
class VetVisitRangeTest {

    /** 2026-09-16 (목). 이 파일의 모든 "오늘". */
    private val today = LocalDate.of(2026, 9, 16)

    @Test
    fun `최근 1년은 한 해 전부터 오늘까지다`() {
        assertEquals(
            VetWindow(LocalDate.of(2025, 9, 16), today),
            VetRange.RecentYear.window(today),
        )
    }

    @Test
    fun `지난 해는 1월 1일부터 12월 31일까지다`() {
        assertEquals(
            VetWindow(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)),
            VetRange.Year(2025).window(today),
        )
    }

    @Test
    fun `올해는 끝이 오늘로 당겨진다 — 목록은 미래를 못 보여 준다`() {
        assertEquals(
            VetWindow(LocalDate.of(2026, 1, 1), today),
            VetRange.Year(2026).window(today),
        )
    }

    @Test
    fun `전체는 0001-01-01 부터다`() {
        assertEquals(
            VetWindow(LocalDate.of(1, 1, 1), today),
            VetRange.All.window(today),
        )
    }

    @Test
    fun `직접 고른 기간은 그대로 나간다`() {
        val from = LocalDate.of(2024, 3, 2)
        val to = LocalDate.of(2024, 11, 30)
        assertEquals(VetWindow(from, to), VetRange.Custom(from, to).window(today))
    }

    /**
     * 휠이 올해까지만 열려 있어도 **올해의 남은 날**은 고를 수 있다. 그대로 보내면
     * 빈 목록이 오고, 끝이 시작보다 앞서면 422 다. 양쪽을 다 당겨 둔다.
     */
    @Test
    fun `직접 고른 기간이 미래면 양쪽 다 오늘로 당긴다`() {
        val window = VetRange.Custom(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31))
            .window(today)

        assertEquals(VetWindow(today, today), window)
        assert(!window.to.isBefore(window.from)) { "to < from 이면 저쪽이 422 를 낸다" }
    }

    @Test
    fun `프리셋은 최근 1년·작년·재작년·전체 순이다`() {
        assertEquals(
            listOf(VetRange.RecentYear, VetRange.Year(2025), VetRange.Year(2024), VetRange.All),
            vetRangePresets(today),
        )
    }

    /**
     * PR #416 본문은 `[2025년] [2024년]` 이라고 적었지만 **그 숫자를 적어 두지 않는다.**
     * 적어 두면 2027년 1월에 재작년·그 전 해가 뜬다 — 오늘 보이는 칩은 똑같고 해가
     * 바뀌면 같이 바뀌는 쪽이 맞다.
     */
    @Test
    fun `해가 바뀌면 프리셋의 연도도 같이 바뀐다`() {
        assertEquals(
            listOf(VetRange.RecentYear, VetRange.Year(2026), VetRange.Year(2025), VetRange.All),
            vetRangePresets(LocalDate.of(2027, 1, 1)),
        )
    }

    @Test
    fun `프리셋의 이름은 최근 1년·연도·전체다`() {
        assertEquals(
            listOf("최근 1년", "2025년", "2024년", "전체"),
            vetRangePresets(today).map { it.label() },
        )
    }
}
