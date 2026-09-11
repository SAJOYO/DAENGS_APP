package com.daengs.app.walk

import com.daengs.app.location.LocationSample
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One subscription's durable boundary. A missing end is an interrupted recording, not success. */
data class RecordingEpoch(
    val id: String,
    val sessionId: String,
    val clockEpochId: String,
    val chainIndex: Int,
    val startedAtMillis: Long,
    val startedElapsedNanos: Long,
    val firstIngressSeq: Long,
    val endedAtMillis: Long? = null,
    val endedElapsedNanos: Long? = null,
    val endKind: String? = null,
    val targetIngressSeq: Long? = null,
    val persistedCount: Long = 0,
    val failureReason: String? = null,
    val firstFailedSeq: Long? = null,
    val drained: Boolean = false,
)

data class IngressProgress(
    val issuedThrough: Long,
    val persistedThrough: Long,
    val persistedCount: Long = 0,
    val pendingCount: Int = 0,
    val maxPendingCount: Int = 0,
    val lastStorageLatencyNanos: Long = 0,
    val maxStorageLatencyNanos: Long = 0,
    val failureReason: String? = null,
    val firstFailedSeq: Long? = null,
)

/** Callback ingress and IO consumption have no dependency on a screen or policy collector. */
class WalkIngress(
    val epoch: RecordingEpoch,
    private val sequence: AtomicLong,
    scope: CoroutineScope,
    private val nowNanos: () -> Long,
    private val nowMillis: () -> Long,
    private val persist: suspend (RecordedFix) -> Unit,
    private val saveEpoch: suspend (RecordingEpoch) -> Unit,
    capacity: Int = 256,
) {
    private val lock = Any()
    private val queue = Channel<RecordedFix>(capacity)
    private var boundary: RecordingEpoch? = null
    private val mutableProgress = MutableStateFlow(IngressProgress(sequence.get() - 1, sequence.get() - 1))
    val progress = mutableProgress.asStateFlow()

    init { require(capacity > 0) }

    private val consumer: Deferred<RecordingEpoch> = scope.async {
        try {
            saveEpoch(epoch)
            for (fix in queue) {
                try {
                    persist(fix)
                    synchronized(lock) {
                        val p = mutableProgress.value
                        val latency = (nowNanos() - requireNotNull(fix.receivedElapsedNanos)).coerceAtLeast(0)
                        mutableProgress.value = p.copy(
                            // A later success must never hide an earlier failed sequence.
                            persistedThrough = if (p.failureReason == null) requireNotNull(fix.ingressSeq) else p.persistedThrough,
                            persistedCount = p.persistedCount + 1,
                            pendingCount = p.pendingCount - 1,
                            lastStorageLatencyNanos = latency,
                            maxStorageLatencyNanos = maxOf(p.maxStorageLatencyNanos, latency),
                        )
                    }
                } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    synchronized(lock) {
                        mutableProgress.value = mutableProgress.value.let { it.copy(pendingCount = it.pendingCount - 1) }
                        failLocked("STORAGE_FAILURE", fix.ingressSeq)
                    }
                }
            }
            val result = synchronized(lock) {
                val p = mutableProgress.value
                checkNotNull(boundary).copy(persistedCount = p.persistedCount,
                    failureReason = p.failureReason, firstFailedSeq = p.firstFailedSeq, drained = true)
            }
            saveEpoch(result)
            result
        } catch (error: Exception) {
            synchronized(lock) { failLocked("JOURNAL_FAILURE", null) }
            throw error
        }
    }

    /** Numbering, enqueue and sealing share this lock. No DB or suspension in the callback. */
    fun offer(sample: LocationSample): Boolean = synchronized(lock) {
        if (boundary != null) return false
        val seq = sequence.getAndIncrement()
        val received = nowNanos()
        val receivedWall = nowMillis()
        val p = mutableProgress.value
        mutableProgress.value = p.copy(issuedThrough = seq)
        if (seq > Int.MAX_VALUE) { failLocked("SEQUENCE_EXHAUSTED", seq); return false }
        val eligible = sample.elapsedRealtimeNanos?.let {
            it >= epoch.startedElapsedNanos && it <= received
        } ?: (sample.capturedAtMillis in epoch.startedAtMillis..receivedWall)
        val fix = RecordedFix(seq.toInt(), epoch.chainIndex, sample.capturedAtMillis,
            sample.point.latitude, sample.point.longitude, sample.accuracyMeters, sample.isMock,
            ingressSeq = seq, sourceEpoch = epoch.id, clockEpochId = epoch.clockEpochId,
            elapsedRealtimeNanos = sample.elapsedRealtimeNanos, receivedElapsedNanos = received,
            receivedAtMillis = receivedWall, speedMps = sample.speedMetersPerSecond,
            speedAccuracyMps = sample.speedAccuracyMetersPerSecond,
            bearingDegrees = sample.bearingDegrees, bearingAccuracyDegrees = sample.bearingAccuracyDegrees,
            provider = sample.provider, recordingEligible = eligible)
        if (!queue.trySend(fix).isSuccess) { failLocked("INGRESS_OVERFLOW", seq); return false }
        mutableProgress.value = mutableProgress.value.let {
            it.copy(pendingCount = it.pendingCount + 1, maxPendingCount = maxOf(it.maxPendingCount, it.pendingCount + 1))
        }
        true
    }

    fun fail(reason: String) = synchronized(lock) { failLocked(reason, null) }

    private fun failLocked(reason: String, seq: Long?) {
        val p = mutableProgress.value
        if (p.failureReason == null) mutableProgress.value = p.copy(failureReason = reason, firstFailedSeq = seq)
        sealLocked("INTERRUPTED")
    }

    fun seal(kind: String): RecordingEpoch = synchronized(lock) { sealLocked(kind) }

    private fun sealLocked(kind: String): RecordingEpoch {
        boundary?.let { return it }
        val result = epoch.copy(endedAtMillis = nowMillis(), endedElapsedNanos = nowNanos(),
            endKind = kind, targetIngressSeq = mutableProgress.value.issuedThrough)
        boundary = result
        queue.close() // close drains buffered values; cancel would discard them.
        return result
    }

    suspend fun drain(): RecordingEpoch {
        val saved = consumer.await()
        val current = synchronized(lock) {
            saved.copy(failureReason = mutableProgress.value.failureReason,
                firstFailedSeq = mutableProgress.value.firstFailedSeq)
        }
        if (current != saved) saveEpoch(current)
        return current
    }
}
