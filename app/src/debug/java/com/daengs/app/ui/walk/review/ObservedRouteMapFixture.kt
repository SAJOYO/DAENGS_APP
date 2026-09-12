package com.daengs.app.ui.walk.review

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.*

/** In-memory only: no account, history database, upload, scene generation or captured-record export. */
internal fun observedRouteMapFixture(): ReviewRecord {
    val start = java.time.Instant.parse("2026-09-12T08:00:00Z").toEpochMilli()
    fun point(x: Double) = GeoPoint(37.5445, 127.0377 + x / 88_170)
    val xs = (0..20).map { it * 4.0 } + (19 downTo 0).map { it * 4.0 }
    val raw = xs.mapIndexed { i, x ->
        RecordedFix(i, 0, start + 10_000 + i * 2_000, point(x).latitude, point(x).longitude, 1f, false)
    } + (0..20).map { i ->
        RecordedFix(41 + i, 1, start + 120_000 + i * 1_000, point(300 + i * 12.0).latitude,
            point(300 + i * 12.0).longitude, 10f, false)
    } + (0..12).map { i ->
        RecordedFix(62 + i, 2, start + 160_000 + i * 1_000, point(620.0).latitude,
            point(620.0 + if (i % 2 == 0) 0.0 else 0.5).longitude, 1f, false)
    }
    val detail = readCompletedRoute(RecordedSession("observed-display-fixture", startedAtMillis = start,
        endedAtMillis = start + 180_000), raw)
    fun scene(seq: Int, title: String): DiaryScene {
        val fix = raw.single { it.clientSeq == seq }; val p = GeoPoint(fix.lat, fix.lng)
        return DiaryScene("fixture/$seq", detail.summary.sessionId, fix.atMillis, title,
            "실제 산책이 아닌 합성 검증 기록이에요.", p, "",
            source = StoryboardScene("fixture-$seq", fix.atMillis, title, "", "", "fixture-v1",
                observation = StoryboardObservation(seq, fix.chainIndex, fix.atMillis, p)))
    }
    val gap = DiaryScene("fixture/gap", detail.summary.sessionId, start + 105_000, "공백 중 위치 있는 기록",
        "위치 핀만 남고 앞뒤 관측을 잇는 선이나 방향은 만들지 않아요.", point(180.0), "")
    return ReviewRecord(detail, listOf(scene(10, "같은 길 · 갈 때"), scene(30, "같은 길 · 돌아올 때"),
        gap, scene(51, "보행 제외 이동"), scene(68, "보행 미확정 · 작은 흔들림")), "")
}
