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
    fun `start stays at first accepted fix through trimming pauses and stop`() {
        val recorder = TrailRecorder(maxSamples = 2)
        recorder.start()
        recorder.add(sample(37.0, 127.0, 1L, accuracy = 100f))
        assertEquals(null, recorder.snapshot().startSample)
        val first = sample(37.0, 127.0, 2L)
        recorder.add(first)
        recorder.add(sample(37.0001, 127.0, 3L))
        recorder.pause(); recorder.resume()
        recorder.add(sample(37.0002, 127.0, 4L))
        assertEquals(2, recorder.snapshot().sampleCount)
        assertEquals(first, recorder.snapshot().startSample)
        assertEquals(first, recorder.stop().startSample)
        assertEquals(null, recorder.start().startSample)
    }

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

    // -- 속도 문턱 ----------------------------------------------------------
    //
    // 차·버스·기차로 이동한 것이 산책 거리로 합산되면 "오늘 얼마나 걸었나" 가 뜻을
    // 잃는다. 판정 자체는 [WalkPaceTest] 가 잡고, 여기서는 **기록기가 그 판정으로
    // 무엇을 하는지**를 잡는다 — 버리고, 세고, 다음 점에서 그 수를 되돌린다.

    @Test
    fun `걷는 속도를 넘으면 그 점을 안 담고 센다`() {
        val recorder = TrailRecorder(minDistanceMeters = 1.0)
        recorder.start()
        recorder.add(sample(37.0, 127.0, 0L))

        // 2초에 약 111m = 55 m/s. 지하철이다.
        val snapshot = recorder.add(sample(37.001, 127.0, 2_000L))

        assertEquals("담기지 않아야 한다", 1, snapshot.sampleCount)
        assertEquals(0.0, snapshot.distanceMeters, 0.001)
        assertEquals(1, snapshot.skippedTooFast)
    }

    @Test
    fun `연속으로 빠르면 계속 센다`() {
        val recorder = TrailRecorder(minDistanceMeters = 1.0)
        recorder.start()
        recorder.add(sample(37.0, 127.0, 0L))

        var snapshot = recorder.snapshot()
        repeat(3) { step ->
            snapshot = recorder.add(sample(37.0 + 0.001 * (step + 1), 127.0, 2_000L * (step + 1)))
        }

        // 화면은 이 수가 이어질 때만 말한다 — 한 번 튄 것으로는 안 띄운다.
        assertEquals(3, snapshot.skippedTooFast)
        assertEquals(1, snapshot.sampleCount)
    }

    @Test
    fun `다시 걷기 시작하면 센 수가 돌아온다`() {
        val recorder = TrailRecorder(minDistanceMeters = 1.0)
        recorder.start()
        recorder.add(sample(37.0, 127.0, 0L))
        recorder.add(sample(37.001, 127.0, 2_000L))
        assertEquals(1, recorder.snapshot().skippedTooFast)

        // 차에서 내렸다. 앞 점에서 111m 를 80초에 걸었다 = 1.4 m/s.
        val snapshot = recorder.add(sample(37.001, 127.0, 82_000L))

        assertEquals(0, snapshot.skippedTooFast)
        assertEquals(2, snapshot.sampleCount)
    }

    @Test
    fun `일시정지를 건너온 자리는 빠르다고 보지 않는다`() {
        val recorder = TrailRecorder(minDistanceMeters = 1.0)
        recorder.start()
        recorder.add(sample(37.0, 127.0, 0L))
        recorder.pause()
        recorder.resume()

        // 멈춰 있던 30분이 간격에 섞이면 속도가 뜻을 잃는다. 여기서 막으면 쉬었다
        // 이어 걷는 산책이 다시 시작을 못 한다.
        val snapshot = recorder.add(sample(37.02, 127.0, 1_800_000L))

        assertEquals(0, snapshot.skippedTooFast)
        assertEquals(2, snapshot.sampleCount)
    }

    @Test
    fun `새 산책은 센 수를 비우고 시작한다`() {
        val recorder = TrailRecorder(minDistanceMeters = 1.0)
        recorder.start()
        recorder.add(sample(37.0, 127.0, 0L))
        recorder.add(sample(37.001, 127.0, 2_000L))
        assertEquals(1, recorder.snapshot().skippedTooFast)

        assertEquals(0, recorder.start().skippedTooFast)
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
