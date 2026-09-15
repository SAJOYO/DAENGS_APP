package com.daengs.app.walk.records

import com.daengs.app.location.LocationSample
import com.daengs.app.walk.RecordedWeather
import com.daengs.app.walk.WalkSummary
import com.daengs.app.walk.shared.SharedWalk
import com.daengs.app.walk.shared.SharedWalkCarer

/**
 * 「산책별」 목록의 한 줄 — 기기에 저장된 **내 산책**이거나 서버에서 읽은 **공동 보호자 산책**.
 *
 * 둘은 출처가 달라 한 타입으로 합치지 않는다. 내 산책만 상세·일기·행동 핀이 있고, 공동 보호자 산책은
 * 읽기 전용 상세(#413)로만 연다.
 */
sealed interface WalkRecordRow {
    val id: String
    val startedAtMillis: Long

    data class Mine(val record: WalkRecord) : WalkRecordRow {
        override val id get() = record.summary.sessionId
        override val startedAtMillis get() = record.summary.startedAtMillis
    }

    data class Shared(val walk: SharedWalk) : WalkRecordRow {
        override val id get() = walk.id
        override val startedAtMillis get() = walk.startedAtMs
    }
}

/**
 * 내 산책(기기)과 공동 보호자 산책(서버)을 **최근 순 한 목록**으로. 같은 산책은 한 번만 둔다.
 *
 * 중복 기준은 서버 산책 id 다 — 기기 기록의 [WalkRecord.serverWalkId] 와 같거나, 서버가 내 것이라고
 * 한(`is_mine`) 산책은 기기 기록을 쓴다. 같은 시각이면 id 로 한 번 더 갈라 서버 커서와 같은 순서를 지킨다.
 */
fun mergeWalkRecordRows(mine: List<WalkRecord>, shared: List<SharedWalk>): List<WalkRecordRow> {
    val mineServerIds = mine.mapNotNullTo(HashSet()) { it.serverWalkId }
    val mineSessionIds = mine.mapTo(HashSet()) { it.summary.sessionId }
    val seen = HashSet<String>()
    val others = shared.filter { walk ->
        !walk.isMine && walk.id !in mineServerIds && walk.id !in mineSessionIds && seen.add(walk.id)
    }
    return (mine.map { WalkRecordRow.Mine(it) } + others.map { WalkRecordRow.Shared(it) })
        .sortedWith(compareByDescending<WalkRecordRow> { it.startedAtMillis }.thenByDescending { it.id })
}

/**
 * 한 쪽. [complete] 가 거짓이면 공동 보호자 산책을 덜 받아 이 쪽의 순서가 아직 확정되지 않았다
 * (받는 대로 끼어들 수 있다). 내 산책은 늘 다 있으므로 비워 두지 않고 그대로 보여 준다.
 */
data class UnifiedWalkPage(
    val rows: List<WalkRecordRow>,
    val pageIndex: Int,
    val pageCount: Int,
    val total: Int,
    val complete: Boolean,
)

/** [pageIndex] 쪽을 정확히 그리려면 공동 보호자 산책을 몇 건까지 받아 둬야 하나. */
fun sharedWalksNeededFor(pageIndex: Int, pageSize: Int): Int = (pageIndex + 1) * pageSize

/**
 * 합친 목록의 한 쪽.
 *
 * 공동 보호자 산책은 서버에서 최근 순으로 받으므로, 앞에서부터 **k 건**을 받아 두면 받지 않은 산책은
 * 모두 그보다 오래됐다. 그래서 k ≥ (쪽 번호 + 1) × 쪽 크기면 그 쪽까지는 내 산책과 섞어도 순서가 맞다
 * — 끝까지 받지 않는다. 전체 쪽 수는 내 산책 수 + 서버 합계로 센다.
 *
 * @param sharedTotal 서버가 준 조건 전체의 공동 보호자 산책 수. 아직 모르면 받은 수.
 * @param sharedExhausted 더 받을 것이 없거나(마지막 쪽·실패·묻지 않음) 받지 않기로 했으면 true.
 */
fun unifiedWalkPage(
    mine: List<WalkRecord>,
    shared: List<SharedWalk>,
    sharedTotal: Int,
    sharedExhausted: Boolean,
    pageIndex: Int,
    pageSize: Int,
): UnifiedWalkPage {
    require(pageSize > 0)
    val merged = mergeWalkRecordRows(mine, shared)
    val sharedKept = merged.count { it is WalkRecordRow.Shared }
    // 서버 합계에는 기기 기록과 겹친 산책이 있을 수 있다 — 겹쳐서 뺀 만큼 덜어 낸다.
    val total = mine.size + (sharedTotal - (shared.size - sharedKept)).coerceAtLeast(sharedKept)
    val pageCount = maxOf(1, (total + pageSize - 1) / pageSize)
    val index = pageIndex.coerceIn(0, pageCount - 1)
    val complete = sharedExhausted || shared.size >= sharedWalksNeededFor(index, pageSize)
    return UnifiedWalkPage(merged.drop(index * pageSize).take(pageSize), index, pageCount, total, complete)
}

/** 공동 보호자 산책을 카드가 그리는 기록 모양으로. 제목·행동·일기는 없고 썸네일은 축약 경로다. */
fun sharedWalkRecord(walk: SharedWalk): WalkRecord = WalkRecord(
    summary = WalkSummary(
        sessionId = walk.id,
        dogIds = walk.petIds,
        startedAtMillis = walk.startedAtMs,
        endedAtMillis = walk.endedAtMs,
        weather = walk.weatherCode?.let { RecordedWeather(it, walk.isDay ?: true, walk.temperatureC) },
        distanceMeters = walk.distanceM?.toDouble() ?: 0.0,
        activeDurationMillis = walk.durationS * 1_000L,
        segments = walk.routePreview.map { segment -> segment.map { LocationSample(it, walk.startedAtMs) } },
        anchor = walk.routePreview.firstOrNull()?.firstOrNull(),
    ),
    serverWalkId = walk.id,
)

// -- 보호자 조건 ---------------------------------------------------------------------------------

/**
 * 보호자 조건에서 [id] 를 누른 뒤의 선택. null 은 "모든 보호자" 다.
 * 개별 선택을 모두 풀면 "모든 보호자" 로 돌아간다.
 */
fun toggleCarer(selected: Set<String>?, id: String): Set<String>? {
    val current = selected.orEmpty()
    return (if (id in current) current - id else current + id).ifEmpty { null }
}

/**
 * 보호자 조건 후보 — 나는 늘 맨 앞("나"), 나머지는 **고른 강아지와 함께 돌보는 사람만**.
 * 모든 강아지면 볼 수 있는 강아지 전부의 보호자다(서버가 사람 단위로 중복을 없앴다).
 *
 * @param dogIds 고른 강아지. null 은 모든 강아지.
 */
fun carerCandidates(carers: List<SharedWalkCarer>, dogIds: Set<String>?, myId: String): List<SharedWalkCarer> {
    val me = carers.firstOrNull { it.isMe } ?: SharedWalkCarer(myId, null, isMe = true, petIds = emptyList())
    val others = carers.filter { !it.isMe && (dogIds == null || it.petIds.any { pet -> pet in dogIds }) }
    return listOf(me) + others
}

/** 조건 요약의 보호자 칸. 여러 명이면 화면 폭을 생각해 "키키 외 1명" 으로 줄인다. */
fun carerSummary(selected: Set<String>?, carers: List<SharedWalkCarer>, myId: String): String {
    if (selected == null) return "모든 보호자"
    val names = buildList {
        if (myId in selected) add("나")
        carers.filter { !it.isMe && it.appUserId in selected }.forEach { add(it.displayName) }
    }
    return when (names.size) {
        0 -> "선택한 보호자"
        1 -> names.single()
        else -> "${names.first()} 외 ${names.size - 1}명"
    }
}
