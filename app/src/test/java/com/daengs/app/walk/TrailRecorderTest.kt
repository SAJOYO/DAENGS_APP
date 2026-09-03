package com.daengs.app.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailRecorderTest {
    @Test
    fun `pausing splits the trail and the gap is not walked distance`() {
        val recorder = TrailRecorder(minDistanceMeters = 1.0)
        val first = sample(37.0, 127.0, 1L)
        val duringPause = sample(37.0001, 127.0, 2L)
        val afterResume = sample(37.0002, 127.0, 3L)

        recorder.start()
        recorder.add(first)
        recorder.pause()
        recorder.add(duringPause)
        assertEquals(listOf(listOf(first)), recorder.snapshot().segments)

        recorder.resume()
        val snapshot = recorder.add(afterResume)

        assertEquals(listOf(listOf(first), listOf(afterResume)), snapshot.segments)
        assertEquals(0.0, snapshot.distanceMeters, 0.001)
    }

    @Test
    fun `walking within a segment accumulates distance`() {
        val recorder = TrailRecorder(minDistanceMeters = 1.0)
        recorder.start()
        recorder.add(sample(37.0, 127.0, 1L))

        val snapshot = recorder.add(sample(37.0002, 127.0, 2L))

        assertEquals(1, snapshot.segments.size)
        assertTrue(snapshot.distanceMeters > 20.0)
    }

    @Test
    fun `an implausible jump starts a new segment without counting the gap`() {
        val recorder = TrailRecorder(minDistanceMeters = 1.0, maxJumpMeters = 200.0)
        recorder.start()
        recorder.add(sample(37.5665, 126.9780, 1L))

        val snapshot = recorder.add(sample(35.1796, 129.0756, 2L))

        assertEquals(2, snapshot.segments.size)
        assertEquals(0.0, snapshot.distanceMeters, 0.001)
    }

    @Test
    fun `starting a new walk resets the previous trail`() {
        val recorder = TrailRecorder(minDistanceMeters = 1.0)
        recorder.start()
        recorder.add(sample(37.0, 127.0, 1L))
        recorder.add(sample(37.0002, 127.0, 2L))
        assertTrue(recorder.snapshot().distanceMeters > 0.0)

        val next = recorder.start()

        assertEquals(TrackingState.RECORDING, next.state)
        assertEquals(0, next.sampleCount)
        assertEquals(0.0, next.distanceMeters, 0.001)
    }

    @Test
    fun `bad accuracy and jitter are ignored and low accuracy is counted`() {
        val recorder = TrailRecorder(minDistanceMeters = 3.0, maxAccuracyMeters = 20f)
        recorder.start()
        recorder.add(sample(37.0, 127.0, 1L, accuracy = 5f))
        recorder.add(sample(37.000001, 127.0, 2L, accuracy = 5f))
        val snapshot = recorder.add(sample(37.001, 127.0, 3L, accuracy = 100f))

        assertEquals(1, snapshot.sampleCount)
        assertEquals(1, snapshot.skippedLowAccuracy)
    }

    @Test
    fun `an accepted fix clears the low accuracy streak`() {
        val recorder = TrailRecorder(minDistanceMeters = 1.0, maxAccuracyMeters = 20f)
        recorder.start()
        recorder.add(sample(37.0, 127.0, 1L, accuracy = 500f))
        assertEquals(1, recorder.snapshot().skippedLowAccuracy)

        val snapshot = recorder.add(sample(37.0, 127.0, 2L, accuracy = 5f))

        assertEquals(0, snapshot.skippedLowAccuracy)
    }

    @Test
    fun `samples outside recording state do not mutate the trail`() {
        val recorder = TrailRecorder()
        val beforeStart = recorder.snapshot()
        assertSame(beforeStart, recorder.add(sample(37.0, 127.0, 1L)))

        recorder.start()
        recorder.add(sample(37.0, 127.0, 2L))
        val stopped = recorder.stop()

        assertSame(stopped, recorder.add(sample(37.001, 127.0, 3L)))
        assertEquals(1, recorder.snapshot().sampleCount)
    }

    @Test
    fun `stopping keeps the completed trail for a result surface`() {
        val recorder = TrailRecorder(minDistanceMeters = 1.0)
        recorder.start()
        recorder.add(sample(37.0, 127.0, 1L))
        recorder.add(sample(37.0002, 127.0, 2L))

        val stopped = recorder.stop()

        assertEquals(TrackingState.OFF, stopped.state)
        assertEquals(2, stopped.sampleCount)
        assertTrue(stopped.distanceMeters > 20.0)
    }

    @Test
    fun `old display samples are trimmed without changing accumulated distance`() {
        val recorder = TrailRecorder(minDistanceMeters = 0.0, maxSamples = 2)
        recorder.start()
        recorder.add(sample(37.0, 127.0, 1L))
        recorder.add(sample(37.0001, 127.0, 2L))
        val snapshot = recorder.add(sample(37.0002, 127.0, 3L))

        assertEquals(2, snapshot.sampleCount)
        assertEquals(2L, snapshot.segments.single().first().capturedAtMillis)
        assertTrue(snapshot.distanceMeters > 20.0)
    }

    @Test
    fun `저장 원본 묶음 재생은 한 점씩 넣은 것과 같은 결과를 낸다`() {
        val samples = listOf(
            sample(37.0, 127.0, 1L),
            sample(37.000001, 127.0, 2L),
            sample(37.001, 127.0, 3L, accuracy = 100f),
            sample(37.0002, 127.0, 4L),
            sample(35.1796, 129.0756, 5L),
        )
        val oneByOne = TrailRecorder(maxSamples = 3).also { recorder ->
            recorder.start()
            samples.forEach(recorder::add)
        }
        val batched = TrailRecorder(maxSamples = 3).also { recorder ->
            recorder.start()
            recorder.addAll(samples)
        }

        assertEquals(oneByOne.snapshot(), batched.snapshot())
    }

    @Test
    fun `invalid thresholds fail at construction instead of corrupting a walk`() {
        assertThrows(IllegalArgumentException::class.java) {
            TrailRecorder(minDistanceMeters = -1.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TrailRecorder(maxAccuracyMeters = -1f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TrailRecorder(maxJumpMeters = -1.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TrailRecorder(maxSamples = 0)
        }
    }

    private fun sample(
        latitude: Double,
        longitude: Double,
        time: Long,
        accuracy: Float = 5f,
    ) = LocationSample(
        point = GeoPoint(latitude, longitude),
        capturedAtMillis = time,
        accuracyMeters = accuracy,
    )
}
