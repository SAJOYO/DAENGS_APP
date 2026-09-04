package com.daengs.app.walk

import com.daengs.app.location.LocationSample

/**
 * 이 fix 가 **걷는 속도인가.**
 *
 * 차·버스·기차로 이동한 것이 산책으로 기록되면 "오늘 얼마나 걸었나" 가 뜻을 잃는다.
 * 지하철로 두 정거장 간 것이 3km 산책이 된다.
 *
 * **버리되 말은 해 준다.** 조용히 버리면 사용자는 앱이 멈춘 줄 안다 — 지도에 점이
 * 안 찍히는데 이유를 모른다. 반대로 자동으로 일시정지하면 오판했을 때 진짜 산책이
 * 끊긴다. 그래서 기록은 계속 돌아가고, 빠른 구간만 안 담고, 담지 않고 있다는 것을
 * 화면이 말한다 ([TrailSnapshot.skippedTooFast]).
 *
 * **원본은 안 지운다.** Room 에 남는 원본은 화면용 필터보다 먼저 저장하는 것이 이
 * 저장소의 규칙이다(PR #47). 여기서 거르는 것은 지도에 그리고 거리를 세는 쪽이지,
 * 기록 자체가 아니다 — 나중에 문턱을 바꾸면 원본에서 다시 셀 수 있어야 한다.
 */
object WalkPace {

    /**
     * 산책으로 볼 수 있는 가장 빠른 속도 (m/s). 7.0 m/s = 25.2 km/h.
     *
     * **사람이 개와 함께 낼 수 있는 속도 위, 탈것 아래**로 잡았다.
     * 개와 조깅하면 2~3 m/s 이고, 잘 뛰는 사람이 개와 함께 달려도 5 m/s(18km/h)를
     * 오래 못 넘긴다. 자전거는 4~8, 시내 주행은 8~17 m/s 다.
     *
     * **넉넉하게 잡은 쪽이다.** 진짜 산책을 잘라 먹는 것이 탈것을 놓치는 것보다
     * 나쁘다 — 걸었는데 기록이 안 되면 사용자는 앱을 못 믿는다. 천천히 가는 차는
     * 이 그물을 빠져나가지만, 그 속도면 걷는 것과 구분할 근거도 약하다.
     */
    const val MAX_METERS_PER_SECOND = 7.0

    /**
     * 좌표로 속도를 낼 때 필요한 최소 시간 간격 (ms).
     *
     * 짧은 간격에서는 GPS 흔들림이 그대로 속도가 된다 — 제자리에서도 몇 미터씩
     * 튀므로 0.2초 간격이면 순간 속도가 10 m/s 로 나온다. 위치 구독이
     * `intervalMillis = 1_500` · `minIntervalMillis = 750` 이라 보통은 이 아래로 안
     * 내려가지만, 내려가면 **판단하지 않는다.**
     */
    private const val MIN_DERIVE_MILLIS = 500L

    /**
     * [next] 가 걷는 속도 밖인가.
     *
     * 기기가 준 속도를 먼저 본다. 도플러로 재는 값이라 좌표로 낸 것보다 믿을 만하다.
     * 없으면([LocationSample.speedMetersPerSecond] 는 null 일 수 있다) 직전 점과의
     * 좌표·시각으로 낸다 — `WalkSessionRoute` 가 화면에 속도를 그릴 때 쓰는 것과
     * 같은 방식이다.
     *
     * @param previous 직전에 **받아들인** 점. 없거나(첫 점) 일시정지를 건너온 자리면
     *   null 을 준다 — 멈춰 있던 시간이 간격에 섞이면 속도가 뜻을 잃는다
     */
    fun tooFast(
        previous: LocationSample?,
        next: LocationSample,
        limit: Double = MAX_METERS_PER_SECOND,
    ): Boolean {
        next.speedMetersPerSecond?.let { return it > limit }
        if (previous == null) return false
        val millis = next.capturedAtMillis - previous.capturedAtMillis
        if (millis < MIN_DERIVE_MILLIS) return false
        val meters = previous.point.distanceTo(next.point)
        return meters / (millis / 1000.0) > limit
    }
}
