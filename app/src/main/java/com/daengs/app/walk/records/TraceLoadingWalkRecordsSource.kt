package com.daengs.app.walk.records

import com.daengs.app.auth.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Network enrichment belongs to the captured login and to one unchanged local selection. */
internal class TraceLoadingWalkRecordsSource(
    private val local: WalkRecordsSource,
    private val ownerId: String,
    private val isCurrentAccount: () -> Boolean,
    private val freshSession: suspend () -> Session?,
    private val fetch: suspend (String, Map<String, String>) -> Result<Map<String, WalkRecordSheetResult>>,
) : WalkRecordsSource {
    override val changes get() = local.changes
    override suspend fun select(query: WalkRecordsQuery): WalkRecordsSelection {
        checkAccount()
        return local.select(query).also { checkAccount() }
    }

    override suspend fun loadTraces(selection: WalkRecordsSelection): WalkRecordsSelection {
        checkAccount()
        val expected = selection.records.mapNotNull { record ->
            record.serverWalkId?.let { record.summary.sessionId to it }
        }.toMap()
        if (expected.isEmpty()) return selection
        if (expected.size > 400) return selection.withResults(expected.mapValues {
            WalkRecordSheetResult(null, WalkTraceState.UNSUPPORTED)
        })

        val results = try {
            val session = freshSession() ?: error("로그인 정보를 확인해 주세요.")
            checkAccount()
            check(session.appUserId == ownerId)
            fetch(session.accessToken, expected).getOrThrow().also {
                require(it.keys == expected.keys) { "요청한 산책과 지도 흔적이 달라요." }
            }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            expected.mapValues { WalkRecordSheetResult(null, WalkTraceState.FAILED) }
        }
        currentCoroutineContext().ensureActive()
        checkAccount()
        // Edits/deletions or a new upload acknowledgement must not receive an older batch result.
        val current = local.select(selection.query)
        check(current.withoutTraces() == selection.withoutTraces()) {
            "산책 기록이 변경됐어요. 현재 기록으로 다시 불러와 주세요."
        }
        currentCoroutineContext().ensureActive()
        checkAccount()
        return selection.withResults(results)
    }

    private fun checkAccount() = check(isCurrentAccount()) { "산책을 읽는 동안 계정이 변경됐어요." }

    private fun WalkRecordsSelection.withoutTraces() = records.map { it.copy(trace = null, traceState = null) }

    private fun WalkRecordsSelection.withResults(results: Map<String, WalkRecordSheetResult>) =
        WalkRecordsSelection(query, records.map { record ->
            results[record.summary.sessionId]?.let { record.copy(trace = it.trace, traceState = it.state) } ?: record
        })
}
