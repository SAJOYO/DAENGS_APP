package com.daengs.app.ui.walk.review

import com.daengs.app.walk.diary.*

internal fun recordContextMapFixture(): ReviewRecord {
    val record = observedRouteMapFixture()
    val summary = record.detail.summary
    fun event(id: String, at: Long, title: String) = DiaryScene("context/$id", summary.sessionId, at, title,
        "위치가 없는 사건도 시간과 전후 기록을 확인할 수 있어요.", null, "",
        source = StoryboardScene(id, at, title, "", "", "fixture-v1"))
    val extra = listOf(event("start", summary.startedAtMillis, "위치 없는 기록 시작"),
        event("note", summary.startedAtMillis + 110_000, "공백 속 위치 없는 메모"),
        event("end", checkNotNull(summary.endedAtMillis), "마지막 확인 후 기록 종료"))
    return record.copy(scenes = (record.scenes + extra).sortedBy { it.atMillis })
}
