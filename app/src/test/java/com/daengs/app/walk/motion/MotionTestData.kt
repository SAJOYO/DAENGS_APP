package com.daengs.app.walk.motion

import com.daengs.app.walk.RecordedFix

internal fun nanos(seconds: Double) = (seconds * 1_000_000_000L).toLong()
internal fun fix(seq: Int, x: Double, seconds: Double, speed: Float? = 1f, accuracy: Float? = 1f,
    speedAccuracy: Float? = 0.2f, source: String = "e", clock: String = "c", chain: Int = 0): RecordedFix =
    RecordedFix(seq, chain, (seconds * 1000).toLong(), 0.0, Math.toDegrees(x / 6_371_000.0), accuracy, false,
        ingressSeq = seq.toLong(), sourceEpoch = source, clockEpochId = clock,
        elapsedRealtimeNanos = nanos(seconds), receivedElapsedNanos = nanos(seconds) + 10_000_000,
        receivedAtMillis = (seconds * 1000).toLong() + 10, speedMps = speed,
        speedAccuracyMps = speedAccuracy, recordingEligible = true, provider = "fused")

internal class EngineHarness(config: MotionConfig = MotionConfig()) {
    val engine = MotionPolicyEngine(MotionPolicies.freeze("s", config))
    val entries = mutableListOf<MotionJournalEntry>()
    val steps = mutableListOf<MotionStep>()
    init { send(MotionEvent.Begin(MotionEpoch("e", "c", 0, 0, 0))) }
    fun send(event: MotionEvent): MotionStep {
        val entry = MotionJournalEntry(entries.size.toLong(), event)
        val step = engine.step(entry)
        entries += entry
        steps += step
        return step
    }
    fun observe(fix: RecordedFix) = send(MotionEvent.Observation("s", fix))
}
