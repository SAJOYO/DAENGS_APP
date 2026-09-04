package com.daengs.app.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 걷는 속도인지 가르는 판정.
 *
 * **진짜 산책을 잘라 먹는 것이 탈것을 놓치는 것보다 나쁘다.** 걸었는데 기록이 안 되면
 * 사용자는 앱을 못 믿는다. 그래서 "걷기·조깅은 절대 안 걸린다" 를 먼저 잡고, 그다음에
 * "차 속도는 걸린다" 를 잡는다.
 */
class WalkPaceTest {

    /** 서울 어딘가. 값 자체는 뜻이 없고 두 점 사이 거리만 쓴다. */
    private val origin = GeoPoint(37.5665, 126.9780)

    private fun at(meters: Double, afterMillis: Long, speed: Float? = null): LocationSample {
        // 위도 1도 ≈ 111km. 북쪽으로 [meters] 만큼 민다.
        return LocationSample(
            point = GeoPoint(origin.latitude + meters / 111_320.0, origin.longitude),
            capturedAtMillis = afterMillis,
            speedMetersPerSecond = speed,
        )
    }

    private val start = LocationSample(point = origin, capturedAtMillis = 0L)

    @Test
    fun `걷기와 조깅은 안 걸린다`() {
        // 1.4 m/s(걷기) · 3.0 m/s(조깅) · 5.0 m/s(개와 함께 달리기)
        listOf(1.4, 3.0, 5.0).forEach { speed ->
            val next = at(meters = speed * 2, afterMillis = 2_000)
            assertFalse("$speed m/s 는 산책이다", WalkPace.tooFast(start, next))
        }
    }

    @Test
    fun `차 속도는 걸린다`() {
        // 12 m/s = 43 km/h. 시내 주행이다.
        val next = at(meters = 24.0, afterMillis = 2_000)
        assertTrue(WalkPace.tooFast(start, next))
    }

    @Test
    fun `기기가 준 속도를 먼저 본다`() {
        // 좌표는 제자리인데 기기가 빠르다고 한다 — 도플러 값을 믿는다.
        val standing = at(meters = 0.0, afterMillis = 2_000, speed = 20f)
        assertTrue(WalkPace.tooFast(start, standing))

        // 반대로 좌표가 튀었어도 기기가 걷는 속도라고 하면 믿는다.
        val jumped = at(meters = 40.0, afterMillis = 2_000, speed = 1.2f)
        assertFalse(WalkPace.tooFast(start, jumped))
    }

    @Test
    fun `첫 점은 판단하지 않는다`() {
        // 비교할 직전 점이 없다. 여기서 막으면 산책이 시작을 못 한다.
        assertFalse(WalkPace.tooFast(null, at(meters = 100.0, afterMillis = 1_000)))
    }

    @Test
    fun `일시정지를 건너온 자리는 판단하지 않는다`() {
        // 부르는 쪽이 previous 에 null 을 준다. 멈춰 있던 시간이 간격에 섞이면
        // 속도가 뜻을 잃기 때문이다 — 여기서는 그 계약만 확인한다.
        assertFalse(WalkPace.tooFast(null, at(meters = 500.0, afterMillis = 600_000)))
    }

    @Test
    fun `간격이 너무 짧으면 판단하지 않는다`() {
        // 0.2초에 3m 는 60 m/s 지만, 그 간격에서는 GPS 흔들림이 그대로 속도가 된다.
        val next = at(meters = 3.0, afterMillis = 200)
        assertFalse(WalkPace.tooFast(start, next))
    }

    @Test
    fun `시각이 거꾸로 와도 안 죽는다`() {
        val next = at(meters = 10.0, afterMillis = -5_000)
        assertFalse(WalkPace.tooFast(start, next))
    }

    @Test
    fun `문턱은 걷기와 탈것 사이에 있다`() {
        // 이 값을 옮기면 위 테스트들의 전제가 같이 움직인다.
        assertTrue("개와 달리는 속도보다 위여야 한다", WalkPace.MAX_METERS_PER_SECOND > 5.0)
        assertTrue("시내 주행보다 아래여야 한다", WalkPace.MAX_METERS_PER_SECOND < 8.0)
    }
}
