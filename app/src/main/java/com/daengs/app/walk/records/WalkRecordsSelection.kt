package com.daengs.app.walk.records

import com.daengs.app.map.layers.traces.WalkTraceSheet
import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.WalkHistoryFilter
import com.daengs.app.walk.WalkSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.ZoneId

data class WalkRecordsQuery(
    val dogIds: Set<String>? = null,
    val filter: WalkHistoryFilter = WalkHistoryFilter(),
) {
    // null means all dogs, including records without an assigned dog. An empty subset is invalid.
    init { require(dogIds == null || (dogIds.isNotEmpty() && dogIds.all { it.isNotBlank() })) }
    constructor(dogId: String, filter: WalkHistoryFilter = WalkHistoryFilter()) : this(setOf(dogId), filter)

    fun includesDogs(ids: Collection<String>): Boolean = dogIds == null || ids.any { it in dogIds }
    fun includesEntryDog(id: String?): Boolean = dogIds == null || id in dogIds
}

/** A saved walk record, distinct from a diary assembled from selected scenes. */
data class WalkRecord(
    val summary: WalkSummary,
    val title: String? = null,
    val notes: List<String> = emptyList(),
    val trace: WalkTraceSheet? = null,
    val entries: List<WalkEntry> = emptyList(),
    val serverWalkId: String? = null,
    val traceState: WalkTraceState? = null,
) {
    val effectiveTraceState: WalkTraceState
        get() = traceState ?: if (trace?.cells.isNullOrEmpty()) WalkTraceState.EMPTY else WalkTraceState.READY

    init {
        require(summary.sessionId.isNotBlank())
        require(trace == null || trace.walkId == summary.sessionId) {
            "산책 기록과 지도 흔적의 ID가 달라요."
        }
        require(entries.all { it.sessionId == summary.sessionId && it.id.isNotBlank() }) {
            "산책 기록과 행동 기록의 ID가 달라요."
        }
        require(entries.map { it.id }.distinct().size == entries.size) {
            "같은 행동 기록이 두 번 들어왔어요."
        }
    }
}

enum class WalkTraceState {
    NOT_REQUESTED, LOADING, READY, EMPTY, NOT_UPLOADED, ANALYSIS_PENDING, UNSUPPORTED, FAILED,
}

/** The full selected set is fixed before pagination; the map consumes [records], not [page]. */
class WalkRecordsSelection(query: WalkRecordsQuery, records: List<WalkRecord>) {
    val query: WalkRecordsQuery = query.snapshot()
    val records: List<WalkRecord> = records.map { record ->
        record.copy(
            summary = record.summary.copy(
                dogIds = record.summary.dogIds.toList(),
                segments = record.summary.segments.map { it.toList() },
                activeElapsedAtMillis = record.summary.activeElapsedAtMillis.toMap(),
            ),
            notes = record.notes.toList(),
            trace = record.trace?.let { it.copy(cells = it.cells.toSet()) },
            entries = record.entries.toList(),
        )
    }
    val sessionIds: List<String> = this.records.map { it.summary.sessionId }

    init {
        require(sessionIds.distinct().size == sessionIds.size) { "같은 산책 기록이 두 번 들어왔어요." }
        require(this.records.all { it.summary.endedAtMillis != null }) { "종료된 산책 기록만 선택할 수 있어요." }
    }

    fun page(pageIndex: Int, size: Int = 5): List<WalkRecord> {
        require(pageIndex >= 0 && size in 1..30)
        val offset = pageIndex.toLong() * size
        if (offset >= records.size) return emptyList()
        return records.subList(offset.toInt(), minOf(offset + size, records.size.toLong()).toInt()).toList()
    }
}

fun interface WalkRecordsSource {
    /** Emit once on subscription and again when the saved inputs or account scope change. */
    val changes: Flow<Unit> get() = flowOf(Unit)
    suspend fun select(query: WalkRecordsQuery): WalkRecordsSelection
    /** Original route timestamps for one highlighted session; thumbnail samples must not drive speed colour. */
    suspend fun loadRoute(record: WalkRecord): WalkSummary = record.summary
    /** Observe only the selected walk's saved diary. No generation, network calls or route preparation. */
    fun observeDiary(record: WalkRecord): Flow<com.daengs.app.walk.diary.DiaryWalk?> =
        flowOf(com.daengs.app.walk.diary.DiaryWalk(record.summary, emptyList(), "", sourceEntries = record.entries))
    /** Enrich the fixed local selection only when its map is requested. */
    suspend fun loadTraces(selection: WalkRecordsSelection): WalkRecordsSelection = selection
}

/**
 * The caller supplies walks already accepted as records. Do not introduce another countsAsWalk
 * threshold here: a short walk retained for its action entries must remain searchable.
 * Titles and notes must likewise be the current visible text supplied by the source.
 * Entries are the source's currently valid records, with deleted or superseded versions removed.
 */
fun selectWalkRecords(
    candidates: List<WalkRecord>,
    query: WalkRecordsQuery,
    zone: ZoneId = ZoneId.systemDefault(),
): WalkRecordsSelection {
    require(candidates.map { it.summary.sessionId }.distinct().size == candidates.size) {
        "같은 산책 기록이 두 번 들어왔어요."
    }
    val selectedQuery = query.snapshot()
    val records = candidates.asSequence().filter { record ->
        val summary = record.summary
        summary.endedAtMillis != null &&
            selectedQuery.includesDogs(summary.dogIds) &&
            selectedQuery.filter.matches(summary, zone) &&
            selectedQuery.filter.matchesText(listOfNotNull(record.title) + record.notes)
    }.sortedWith(compareByDescending<WalkRecord> { it.summary.startedAtMillis }
        .thenByDescending { it.summary.sessionId }).toList()
    return WalkRecordsSelection(selectedQuery, records)
}

private fun WalkRecordsQuery.snapshot() = copy(dogIds = dogIds?.toSet(), filter = filter.copy(
    seasons = filter.seasons.toSet(), weather = filter.weather.toSet(),
))
