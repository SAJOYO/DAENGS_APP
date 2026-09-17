package com.daengs.app.care

import java.time.LocalDate

/**
 * 목록에 보낼 조회 창 하나. 저쪽 `GET /app/vet-visits` 의 `from`/`to` 다.
 *
 * ⚠️ **[to] 가 [from] 보다 앞서면 저쪽이 422 다.** [VetRange.window] 가 그 일이 없게
 *    당겨 두므로, 이 값을 손으로 만들지 말고 거기서 받아 쓴다.
 */
data class VetWindow(val from: LocalDate, val to: LocalDate)

/**
 * 유저가 고른 기간. **프리셋이 먼저고 달력은 [Custom] 뒤에 숨는다** — 유저가 찾는 것은
 * "특정 날짜" 보다 **"그때쯤"** 이다. 영수증은 보험 청구·연말정산 때문에 연 단위로
 * 기억된다 (PR #416 본문).
 */
sealed interface VetRange {
    /** 서버 기본값과 같은 창. 이 화면이 처음 열릴 때의 값이다. */
    data object RecentYear : VetRange

    data class Year(val year: Int) : VetRange

    /**
     * 전부. **상한이 없다** — 저쪽이 #528 에서 5년 상한을 없앴다. 상한이 있으면 앱이
     * 창을 쪼개 여러 번 불러야 하고, **쪼개는 코드는 경계에서 한 건씩 흘린다.**
     */
    data object All : VetRange

    data class Custom(val from: LocalDate, val to: LocalDate) : VetRange
}

/**
 * 이 기간이 뜻하는 창. **[today] 는 `Asia/Seoul` 의 오늘이어야 한다** — 서버가 그것으로
 * 오늘을 정하므로 기기 시간대로 재면 날짜가 하루 어긋난다 (PR #416 「알아 둘 것」).
 *
 * ⚠️ **양 끝을 [today] 로 당긴다.** 목록은 미래를 보여 줄 수 없고(확정도 미래 날짜를
 *    422 로 막는다), 무엇보다 **끝만 당기면 시작이 미래로 남아 `to < from` 이 되어 422**
 *    가 된다 — 올해를 고르거나 휠로 올해의 남은 날을 고르면 바로 그 모양이다.
 */
fun VetRange.window(today: LocalDate): VetWindow {
    val raw = when (this) {
        VetRange.RecentYear -> today.minusYears(1) to today
        is VetRange.Year -> LocalDate.of(year, 1, 1) to LocalDate.of(year, 12, 31)
        VetRange.All -> EARLIEST to today
        is VetRange.Custom -> from to to
    }
    return VetWindow(raw.first.coerceAtMost(today), raw.second.coerceAtMost(today))
}

/** 칩에 적는 이름. [VetRange.Custom] 은 고른 날짜를 화면이 직접 그리므로 여기 없다. */
fun VetRange.label(): String = when (this) {
    VetRange.RecentYear -> "최근 1년"
    is VetRange.Year -> "${year}년"
    VetRange.All -> "전체"
    is VetRange.Custom -> "${from}~${to}"
}

/**
 * 칩에 늘어놓을 프리셋. **연도를 적어 두지 않고 [today] 에서 센다.**
 *
 * PR #416 본문은 `[2025년] [2024년]` 이라고 적었지만 그 숫자를 그대로 두면 2027년
 * 1월에 재작년과 그 전 해가 뜬다. 오늘 보이는 칩은 본문과 같고, 해가 바뀌면 같이 바뀐다.
 */
fun vetRangePresets(today: LocalDate): List<VetRange> = listOf(
    VetRange.RecentYear,
    VetRange.Year(today.year - 1),
    VetRange.Year(today.year - 2),
    VetRange.All,
)

/** `0001-01-01`. 저쪽이 "전체" 로 알아듣는 값이다 (#528). */
private val EARLIEST: LocalDate = LocalDate.of(1, 1, 1)
