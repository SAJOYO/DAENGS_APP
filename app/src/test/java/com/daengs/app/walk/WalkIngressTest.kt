package com.daengs.app.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalkIngressTest {
    private fun epoch(first: Long = 0, chain: Int = 0) = RecordingEpoch("epoch-$chain", "walk", "clock",
        chain, 100, 100_000_000, first)
    private fun sample(at: Long = 150_000_000) = LocationSample(GeoPoint(37.5, 127.0), at / 1_000_000,
        elapsedRealtimeNanos = at, accuracyMeters = 4f, speedMetersPerSecond = 1.4f,
        isMock = true, speedAccuracyMetersPerSecond = .3f, provider = "gps")

    @Test fun `seal drains all accepted samples and late callbacks get no sequence`() = runTest {
        val saved = mutableListOf<RecordedFix>()
        val seq = AtomicLong()
        val stream = WalkIngress(epoch(), seq, this, { 200_000_000 }, { 200 },
            { saved += it }, {})
        repeat(20) { assertTrue(stream.offer(sample())) }
        val boundary = stream.seal("STOP")
        assertFalse(stream.offer(sample()))
        assertEquals(boundary, stream.seal("PAUSE"))
        val receipt = stream.drain()
        assertEquals(20L, seq.get())
        assertEquals((0L..19L).toList(), saved.map { it.ingressSeq })
        assertEquals(20L, receipt.persistedCount)
        checkRecordingComplete(listOf(receipt))
    }

    @Test fun `full queue reports its rejected sequence and retains its accepted prefix`() = runTest {
        val saved = mutableListOf<RecordedFix>()
        val stream = WalkIngress(epoch(), AtomicLong(), this, { 200_000_000 }, { 200 },
            { saved += it }, {}, capacity = 2)
        assertTrue(stream.offer(sample()))
        assertTrue(stream.offer(sample()))
        assertFalse(stream.offer(sample()))
        assertFalse(stream.offer(sample()))
        val result = stream.drain()
        assertEquals(listOf(0L, 1L), saved.map { it.ingressSeq })
        assertEquals(2L, result.firstFailedSeq)
        assertEquals(2L, result.targetIngressSeq)
        assertEquals("INGRESS_OVERFLOW", result.failureReason)
        assertTrue(runCatching { checkRecordingComplete(listOf(result)) }.isFailure)
    }

    @Test fun `slow persistence delays drain but does not block callbacks or need a UI subscriber`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val saved = mutableListOf<RecordedFix>()
        val stream = WalkIngress(epoch(), AtomicLong(), this, { 200_000_000 }, { 200 },
            { gate.await(); saved += it }, {})
        stream.offer(sample()); runCurrent()
        repeat(15) { assertTrue(stream.offer(sample())) }
        stream.seal("STOP")
        assertTrue(saved.isEmpty())
        assertEquals(16, stream.progress.value.pendingCount)
        gate.complete(Unit)
        assertEquals(16L, stream.drain().persistedCount)
        assertEquals(0, stream.progress.value.pendingCount)
    }

    @Test fun `later writes cannot advance the contiguous watermark across a failed write`() = runTest {
        val saved = mutableListOf<RecordedFix>()
        val stream = WalkIngress(epoch(), AtomicLong(), this, { 200_000_000 }, { 200 },
            { if (it.ingressSeq == 1L) error("disk"); saved += it }, {})
        repeat(3) { stream.offer(sample()) }
        stream.seal("STOP")
        val result = stream.drain()
        assertEquals(listOf(0L, 2L), saved.map { it.ingressSeq })
        assertEquals(0L, stream.progress.value.persistedThrough)
        assertEquals(2L, result.persistedCount)
        assertEquals(1L, result.firstFailedSeq)
        assertEquals("STORAGE_FAILURE", result.failureReason)
    }

    @Test fun `raw fields survive independently of the eligibility of cached fixes`() = runTest {
        val saved = mutableListOf<RecordedFix>()
        val stream = WalkIngress(epoch(), AtomicLong(), this, { 200_000_000 }, { 200 },
            { saved += it }, {})
        stream.offer(sample(90_000_000)); stream.offer(sample())
        stream.seal("STOP"); stream.drain()
        assertEquals(false, saved[0].recordingEligible)
        assertEquals(true, saved[1].recordingEligible)
        assertEquals(1.4f, saved[1].speedMps)
        assertEquals(.3f, saved[1].speedAccuracyMps)
        assertEquals(150_000_000L, saved[1].elapsedRealtimeNanos)
        assertEquals(200_000_000L, saved[1].receivedElapsedNanos)
        assertEquals("epoch-0", saved[1].sourceEpoch)
        assertEquals(true, saved[1].isMock)
        assertEquals("gps", saved[1].provider)
    }

    @Test fun `resume has a new epoch but continues the session sequence`() = runTest {
        val seq = AtomicLong()
        val saved = mutableListOf<RecordedFix>()
        val first = WalkIngress(epoch(), seq, this, { 200_000_000 }, { 200 }, { saved += it }, {})
        first.offer(sample()); first.seal("PAUSE")
        val paused = first.drain()
        val second = WalkIngress(epoch(1, 1), seq, this, { 200_000_000 }, { 200 }, { saved += it }, {})
        assertFalse(first.offer(sample()))
        second.offer(sample()); second.seal("STOP")
        val stopped = second.drain()
        checkRecordingComplete(listOf(paused, stopped))
        assertEquals(listOf(0, 1), saved.map { it.clientSeq })
        assertEquals(listOf("epoch-0", "epoch-1"), saved.map { it.sourceEpoch })
    }

    @Test fun `subscription close failure after raw drain updates the durable receipt`() = runTest {
        val receipts = mutableListOf<RecordingEpoch>()
        val stream = WalkIngress(epoch(), AtomicLong(), this, { 200_000_000 }, { 200 }, {}, { receipts += it })
        stream.seal("STOP"); stream.drain()
        stream.fail("SOURCE_CLOSE_FAILURE")
        assertEquals("SOURCE_CLOSE_FAILURE", stream.drain().failureReason)
        assertEquals("SOURCE_CLOSE_FAILURE", receipts.last().failureReason)
    }

    @Test fun `empty stop is valid but missing tail failure and pause alone cannot complete`() {
        val empty = epoch().copy(endedAtMillis = 200, endedElapsedNanos = 200_000_000,
            endKind = "STOP", targetIngressSeq = -1, drained = true)
        checkRecordingComplete(listOf(empty))
        for (bad in listOf(empty.copy(drained = false), empty.copy(endKind = "PAUSE"),
            empty.copy(targetIngressSeq = 1), empty.copy(failureReason = "disk"), empty.copy(firstIngressSeq = 1))) {
            assertTrue(runCatching { checkRecordingComplete(listOf(bad)) }.isFailure)
        }
        assertTrue(runCatching { checkRecordingComplete(emptyList()) }.isFailure)
    }
    @Test fun `concurrent callbacks and stop retain exactly the accepted prefix`() = kotlinx.coroutines.runBlocking {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
        val executor = java.util.concurrent.Executors.newFixedThreadPool(5)
        try {
            val saved = java.util.Collections.synchronizedList(mutableListOf<RecordedFix>())
            val accepted = AtomicLong()
            val stream = WalkIngress(epoch(), AtomicLong(), scope, { 200_000_000 }, { 200 },
                { saved += it }, {}, capacity = 4096)
            repeat(10) { if (stream.offer(sample())) accepted.incrementAndGet() }
            val gate = java.util.concurrent.CountDownLatch(1)
            val producers = (1..4).map {
                executor.submit { gate.await(); repeat(500) { if (stream.offer(sample())) accepted.incrementAndGet() } }
            }
            val stopping = executor.submit { gate.await(); stream.seal("STOP") }
            gate.countDown()
            (producers + stopping).forEach { it.get(5, java.util.concurrent.TimeUnit.SECONDS) }
            val result = kotlinx.coroutines.withTimeout(5000) { stream.drain() }
            assertEquals(accepted.get(), result.persistedCount)
            assertEquals((0L until accepted.get()).toList(), saved.map { it.ingressSeq })
            assertEquals(0, stream.progress.value.pendingCount)
            checkRecordingComplete(listOf(result))
        } finally { executor.shutdownNow(); scope.coroutineContext[kotlinx.coroutines.Job]?.cancel() }
    }

    @Test fun `cached rows remain raw but do not extend the local walk time or anchor`() {
        val old = RecordedFix(0, 0, 1, 10.0, 10.0, 4f, false, recordingEligible = false)
        val current = old.copy(clientSeq = 1, atMillis = 200, lat = 37.5, lng = 127.0, recordingEligible = true)
        val session = RecordedSession("walk", startedAtMillis = 100)
        val summary = summarize(session, listOf(old, current))
        assertEquals(0L, summary.activeDurationMillis)
        assertEquals(GeoPoint(37.5, 127.0), summary.anchor)
        assertNull(summarize(session, listOf(old)).anchor)
    }

}
