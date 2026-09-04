package com.daengs.app.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample

/**
 * 저장된 산책 하나를 목록·상세에 보여줄 모양으로.
 *
 * **요약을 따로 저장하지 않고 원본에서 다시 낸다.** 거리를 세션 행에 적어 두면
 * 필터 규칙([TrailRecorder] 의 문턱값)을 고칠 때 이미 저장된 숫자와 새로 계산한 숫자가
 * 갈라진다 — 그때 어느 쪽이 맞는지 아무도 모른다. 원본 fix 는 그대로 남아 있으니
 * 규칙이 바뀌면 지난 기록도 같이 바뀌는 것이 맞다.
 */
data class WalkSummary(
    val sessionId: String,
    /**
     * 그날 데리고 나간 아이들. **이름이 아니라 id 다.**
     *
     * 이름은 바뀌는 값이라 기록에 박아 두면 개명한 뒤에도 옛 이름이 뜬다. 화면은
     * 지금의 강아지 목록에서 이 id 로 찾아 이름을 붙인다.
     */
    val dogIds: List<String>,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val weather: RecordedWeather?,
    val distanceMeters: Double,
    /** 걸은 시간. 일시정지와 끊긴 구간은 빠진다 — 나갔다 온 시간이 아니라 걸은 시간이다. */
    val activeDurationMillis: Long,
    /** 지도에 그릴 경로. 세그먼트마다 따로다 — 이어 붙이면 안 걸은 길이 생긴다. */
    val segments: List<List<LocationSample>>,
    /**
     * 이 산책이 있었던 자리.
     *
     * 경로를 그릴 수 없을 때(좌표가 한 점뿐이거나 정확도가 나빠 다 버려졌을 때)
     * **지도를 어디로 보낼지**가 이 값이다. 없으면 지도가 네이버 기본 카메라
     * (서울시청)에 앉아서, 강남에서 한 산책이 시청에서 한 것처럼 보인다.
     */
    val anchor: GeoPoint?,
    /**
     * 원본 fix의 시각을 세션 활동 시간축에 대응한 값.
     *
     * 지도용 필터가 점을 버리거나 GPS 점프 때문에 선을 끊어도 시간축은 원본 fix와
     * [RecordedFix.chainIndex]에서 한 번만 계산한다. 완료 경로가 별도로 시간을 다시
     * 계산해 결과 요약과 다른 숫자를 보여 주지 않게 하는 읽기용 파생값이다.
     */
    val activeElapsedAtMillis: Map<Long, Long> = emptyMap(),
) {
    val hasRoute: Boolean get() = segments.any { it.size >= 2 }
}

/**
 * 원본 fix 로 요약을 만든다.
 *
 * **거리는 [TrailRecorder] 를 다시 돌려 얻는다.** 산책 중 화면에 보이던 숫자와 목록의
 * 숫자가 다르면 안 되는데, 여기서 거리 공식을 새로 쓰면 규칙이 두 벌이 되고 언젠가
 * 갈라진다. 같은 계산기에 같은 좌표를 넣으면 같은 값이 나온다.
 *
 * [RecordedFix.chainIndex] 가 바뀌는 자리에서 [TrailRecorder.pause] · [TrailRecorder.resume]
 * 를 넣어 **저장할 때 끊겼던 자리를 그대로 재현한다.** 안 그러면 일시정지 전후가 한 선으로
 * 이어져 걷지 않은 거리가 더해진다.
 */
/**
 * 하루치 합산. 홈의 "오늘의 산책 요약" 이 쓴다.
 *
 * **저장하지 않고 그때그때 센다.** 요약을 어딘가 적어 두면 산책을 지우거나 기준을
 * 고쳤을 때 그 숫자가 남아 거짓말을 한다.
 */
data class WalkDayTotals(
    val count: Int,
    val activeDurationMillis: Long,
    val distanceMeters: Double,
) {
    companion object {
        val EMPTY = WalkDayTotals(count = 0, activeDurationMillis = 0L, distanceMeters = 0.0)
    }
}

/**
 * 그날 걸은 것을 합친다.
 *
 * **산책으로 치는 것만** 센다 — 문을 눌렀다 닫은 0m 짜리가 "1회" 로 잡히면 숫자가
 * 거짓이 된다.
 */
fun List<WalkSummary>.totalsFor(
    dayStartMillis: Long,
    dayEndMillis: Long,
): WalkDayTotals {
    // **시작한 시각**으로 가른다. 자정을 넘겨 걸은 산책은 나선 날의 것이다 —
    // 밤 11시에 나가 새벽 1시에 들어오면 그건 어제의 산책이다.
    val today = filter { it.startedAtMillis in dayStartMillis until dayEndMillis && it.countsAsWalk }
    if (today.isEmpty()) return WalkDayTotals.EMPTY
    return WalkDayTotals(
        count = today.size,
        activeDurationMillis = today.sumOf { it.activeDurationMillis },
        distanceMeters = today.sumOf { it.distanceMeters },
    )
}

/**
 * 산책으로 치는 최소 거리(m).
 *
 * 문을 눌렀다가 그냥 닫은 것, 시작을 실수로 누른 것을 거른다. GPS 흔들림은 이미
 * [TrailRecorder] 가 걸러 내므로 이 50m 는 **실제로 걸은 거리**다.
 */
const val MIN_WALK_METERS = 50.0

/** 산책으로 치는 최소 활동 시간(ms). 거리와 **둘 다** 넘어야 한다. */
const val MIN_WALK_MILLIS = 60_000L

/**
 * 산책으로 칠 만한가.
 *
 * **둘 다 넘어야 한다.** 50m 를 걸었어도 10초 만에 끝났으면 걸은 것이 아니고,
 * 1분을 서 있었어도 제자리면 산책이 아니다.
 *
 * 경계값은 **인정한다** — 딱 50m 를 걷고 "왜 기록이 없지" 가 되면 안 된다.
 */
val WalkSummary.countsAsWalk: Boolean
    get() = countsAsWalk(distanceMeters, activeDurationMillis)

/**
 * 저장하기 **전에도** 같은 규칙으로 잴 수 있게 꺼내 둔다.
 *
 * 산책 화면이 멈춤 카드에서 이 값을 미리 본다 — 종료를 누른 뒤에 "너무 짧아서 안
 * 남겼어요" 라고 하면, 걷고 온 사람이 **기록이 사라진 것을 보고 나서** 이유를 읽는
 * 순서가 된다. 두 자리가 같은 규칙을 봐야 화면과 저장이 다른 말을 하지 않는다.
 */
fun countsAsWalk(distanceMeters: Double, activeDurationMillis: Long): Boolean =
    distanceMeters >= MIN_WALK_METERS && activeDurationMillis >= MIN_WALK_MILLIS

fun summarize(
    session: RecordedSession,
    fixes: List<RecordedFix>,
    maxRouteSamples: Int = WALK_SUMMARY_ROUTE_SAMPLE_LIMIT,
): WalkSummary {
    // 목록·오늘 합계는 경로 전체를 소비하지 않으므로 기본 상한을 지킨다. 한 세션 상세만
    // Int.MAX_VALUE를 넘겨 실제 출발점을 보존한다. addAll은 중간 화면을 만들지 않아
    // 원본마다 경로 목록을 다시 복사하던 O(n²) 비용을 피한다.
    val recorder = TrailRecorder(maxSamples = maxRouteSamples)
    recorder.start()
    var previousChain: Int? = null
    var activeMillis = 0L
    var previousAtMillis: Long? = null
    val activeElapsedAtMillis = linkedMapOf<Long, Long>()
    val pendingChainSamples = mutableListOf<LocationSample>()

    for (fix in fixes.sortedBy { it.clientSeq }) {
        if (previousChain != null && fix.chainIndex != previousChain) {
            recorder.addAll(pendingChainSamples)
            pendingChainSamples.clear()
            // 끊긴 자리. 저장할 때 그랬던 것처럼 여기서도 선을 끊는다.
            recorder.pause()
            recorder.resume()
            previousAtMillis = null
        }
        // 같은 구간 안에서 흐른 시간만 더한다. 끊긴 구간을 건너뛴 시간은 안 걸은 시간이다.
        previousAtMillis?.let { activeMillis += (fix.atMillis - it).coerceAtLeast(0L) }
        activeElapsedAtMillis[fix.atMillis] = activeMillis
        pendingChainSamples += fix.toSample()
        previousAtMillis = fix.atMillis
        previousChain = fix.chainIndex
    }
    recorder.addAll(pendingChainSamples)

    val snapshot = recorder.snapshot()
    // 필터가 다 버렸어도 **원본에는 남아 있다.** 그 첫 점이 산책이 있었던 자리다.
    val anchor = snapshot.segments.firstOrNull()?.firstOrNull()?.point
        ?: fixes.minByOrNull { it.clientSeq }?.let { GeoPoint(it.lat, it.lng) }
    return WalkSummary(
        sessionId = session.id,
        dogIds = session.dogIds,
        startedAtMillis = session.startedAtMillis,
        endedAtMillis = session.endedAtMillis,
        weather = session.weather,
        distanceMeters = snapshot.distanceMeters,
        activeDurationMillis = activeMillis,
        segments = snapshot.segments,
        anchor = anchor,
        activeElapsedAtMillis = activeElapsedAtMillis,
    )
}

/** 목록·홈 합계가 한 산책의 화면용 점을 무제한으로 들고 있지 않게 하는 상한. */
const val WALK_SUMMARY_ROUTE_SAMPLE_LIMIT = 5_000

private fun RecordedFix.toSample(): LocationSample = LocationSample(
    point = GeoPoint(latitude = lat, longitude = lng),
    capturedAtMillis = atMillis,
    accuracyMeters = accuracyM,
    isMock = isMock,
)
