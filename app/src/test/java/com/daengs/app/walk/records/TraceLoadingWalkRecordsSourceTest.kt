package com.daengs.app.walk.records

import com.daengs.app.auth.Session
import com.daengs.app.map.layers.traces.WalkTraceSheet
import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.WalkSummary
import com.daengs.app.walk.diary.SpatialDiaryCellId
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TraceLoadingWalkRecordsSourceTest {
    @Test fun `diary observation stays local and rejects stale account emissions`() = runBlocking {
        val record = record("local", null)
        var current = true
        val diary = com.daengs.app.walk.diary.DiaryWalk(record.summary, emptyList(), "")
        val local = object : WalkRecordsSource {
            override suspend fun select(query: WalkRecordsQuery) = selection(record)
            override fun observeDiary(record: WalkRecord) = kotlinx.coroutines.flow.flow {
                emit(diary)
                current = false
                emit(diary)
            }
        }
        val source = TraceLoadingWalkRecordsSource(local, "owner", { current },
            { error("diary must not refresh tokens") }, { _, _ -> error("diary must not fetch") })
        val received = mutableListOf<com.daengs.app.walk.diary.DiaryWalk?>()
        assertTrue(runCatching { source.observeDiary(record).collect { received += it } }.isFailure)
        assertEquals(listOf(diary), received)
    }

    @Test fun `route loading delegates locally and rejects an account change during the read`() = runBlocking {
        val record = record("local", null)
        var current = true
        var changeDuringRead = false
        var reads = 0
        val full = record.summary.copy(distanceMeters = 123.0)
        val local = object : WalkRecordsSource {
            override suspend fun select(query: WalkRecordsQuery) = selection(record)
            override suspend fun loadRoute(record: WalkRecord): com.daengs.app.walk.WalkSummary {
                reads++
                if (changeDuringRead) current = false
                return full
            }
        }
        val source = TraceLoadingWalkRecordsSource(local, "owner", { current },
            { error("route reads must not refresh a token") }, { _, _ -> error("route reads must stay local") })
        assertSame(full, source.loadRoute(record))
        changeDuringRead = true
        assertTrue(runCatching { source.loadRoute(record) }.isFailure)
        assertTrue(runCatching { source.loadRoute(record) }.isFailure)
        assertEquals(2, reads)
    }

    @Test fun `local list never calls network and an unuploaded walk needs no session`() = runBlocking {
        val stored = selection(record("local", null))
        val source = source({ stored }, fresh = { error("unexpected login request") }) { _, _ -> error("unexpected HTTP") }
        assertSame(stored, source.select(stored.query))
        assertSame(stored, source.loadTraces(stored))
    }

    @Test fun `one batch enriches fixed records and retains their action evidence`() = runBlocking {
        val stored = selection(record("a"), record("b"), record("local", null))
        var calls = 0
        val source = source({ stored }) { token, ids ->
            calls++
            assertEquals("token", token)
            assertEquals(linkedMapOf("a" to "server-a", "b" to "server-b"), ids)
            Result.success(mapOf("a" to ready("a"), "b" to WalkRecordSheetResult(null, WalkTraceState.ANALYSIS_PENDING)))
        }
        source.select(stored.query)
        assertEquals(0, calls)
        val result = source.loadTraces(stored)
        assertEquals(1, calls)
        assertEquals(stored.sessionIds, result.sessionIds)
        assertEquals(stored.records.map { it.entries }, result.records.map { it.entries })
        assertEquals(stored.records.map { it.summary }, result.records.map { it.summary })
        assertEquals(listOf(WalkTraceState.READY, WalkTraceState.ANALYSIS_PENDING, WalkTraceState.NOT_UPLOADED),
            result.records.map { it.effectiveTraceState })
        assertNull(stored.records.first().trace)
    }

    @Test fun `unavailable network clears previous sheets but preserves the list for retry`() = runBlocking {
        val stored = selection(record("a"), record("local", null))
        var attempt = 0
        val source = source({ stored }) { _, _ ->
            if (attempt++ == 0) Result.failure(IllegalStateException("404"))
            else Result.success(mapOf("a" to ready("a")))
        }
        val stale = selection(stored.records[0].copy(trace = ready("a").trace, traceState = WalkTraceState.READY), stored.records[1])
        val failed = source.loadTraces(stale)
        assertEquals(stored.sessionIds, failed.sessionIds)
        assertEquals(WalkTraceState.FAILED, failed.records[0].effectiveTraceState)
        assertNull(failed.records[0].trace)
        assertEquals(WalkTraceState.READY, source.loadTraces(failed).records[0].effectiveTraceState)
    }

    @Test fun `a new login lifetime cannot accept a late success or failure`() = runBlocking {
        for (httpFailure in listOf(false, true)) {
            val stored = selection(record("a"))
            var sameScope = true
            val source = source({ stored }, current = { sameScope }) { _, _ ->
                sameScope = false
                if (httpFailure) Result.failure(IllegalStateException("offline")) else Result.success(mapOf("a" to ready("a")))
            }
            assertNotNull(runCatching { source.loadTraces(stored) }.exceptionOrNull())
        }
    }

    @Test fun `deleted edited or remapped records reject the older response`() = runBlocking {
        val original = record("a")
        for (replacement in listOf(emptyList(), listOf(original.copy(title = "edited")), listOf(original.copy(serverWalkId = "new-server")))) {
            val stored = selection(original)
            var current = stored
            val source = source({ current }) { _, _ ->
                current = WalkRecordsSelection(stored.query, replacement)
                Result.success(mapOf("a" to ready("a")))
            }
            assertNotNull(runCatching { source.loadTraces(stored) }.exceptionOrNull())
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun `cancelled fetch cannot publish even if the transport returns late`() = runTest {
        val stored = selection(record("a"))
        val entered = CompletableDeferred<Unit>()
        var published = false
        val source = source({ stored }) { _, _ ->
            entered.complete(Unit)
            try { awaitCancellation() } catch (_: CancellationException) { Result.success(mapOf("a" to ready("a"))) }
        }
        val job = async { source.loadTraces(stored); published = true }
        try {
            runCurrent()
            assertTrue("The fetch must start before cancellation is tested", entered.isCompleted)
        } finally { job.cancelAndJoin() }
        assertFalse(published)
    }

    @Test fun `oversized selection stays whole and never silently fetches a prefix`() = runBlocking {
        val stored = WalkRecordsSelection(WalkRecordsQuery(), (1..401).map { record("$it") })
        val source = source({ stored }) { _, _ -> error("must not query a partial selection") }
        val result = source.loadTraces(stored)
        assertEquals(stored.sessionIds, result.sessionIds)
        assertTrue(result.records.all { it.effectiveTraceState == WalkTraceState.UNSUPPORTED })
        val mostlyLocal = WalkRecordsSelection(stored.query, stored.records.mapIndexed { i, walk ->
            if (i == 0) walk else walk.copy(serverWalkId = null, traceState = WalkTraceState.NOT_UPLOADED)
        })
        val smallBatch = source({ mostlyLocal }) { _, ids ->
            assertEquals(1, ids.size)
            Result.success(mapOf("1" to ready("1")))
        }.loadTraces(mostlyLocal)
        assertEquals(401, smallBatch.records.size)
        assertEquals(WalkTraceState.READY, smallBatch.records.first().effectiveTraceState)
    }

    private fun source(stored: () -> WalkRecordsSelection, current: () -> Boolean = { true },
        fresh: suspend () -> Session? = { Session("owner", "token", "refresh", Long.MAX_VALUE, Long.MAX_VALUE) },
        fetch: suspend (String, Map<String, String>) -> Result<Map<String, WalkRecordSheetResult>>,
    ) = TraceLoadingWalkRecordsSource(WalkRecordsSource { stored() }, "owner", current, fresh, fetch)

    private fun record(id: String, server: String? = "server-$id") = WalkRecord(
        WalkSummary(id, listOf("dog"), 0, 10_000, null, 0.0, 0, emptyList(), null),
        entries = listOf(WalkEntry("entry-$id", id, WalkMomentType.SNIFFING, 5000)),
        serverWalkId = server, traceState = if (server == null) WalkTraceState.NOT_UPLOADED else WalkTraceState.NOT_REQUESTED)
    private fun selection(vararg records: WalkRecord) = WalkRecordsSelection(WalkRecordsQuery(), records.toList())
    private fun ready(id: String) = WalkRecordSheetResult(WalkTraceSheet(id, cells = setOf(SpatialDiaryCellId(0, 0))), WalkTraceState.READY)
}
