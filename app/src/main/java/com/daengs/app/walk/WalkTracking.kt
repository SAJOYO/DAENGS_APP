package com.daengs.app.walk

import com.daengs.app.walk.sync.WalkSync
import com.daengs.app.location.LocationSample
import com.daengs.app.location.LocationSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WalkTrackingState(
    val trail: TrailSnapshot = TrailSnapshot(),
    val lastSample: LocationSample? = null,
    val errorMessage: String? = null,
    /** 이미 끝난 기록 구간의 실제 활동 시간. 일시정지 시간은 포함하지 않는다. */
    val activeDurationMillis: Long = 0L,
    /** 기록 중인 현재 구간의 monotonic 시작 시각. 일시정지·종료 상태에서는 null이다. */
    val activeSinceRealtimeMillis: Long? = null,
) {
    fun elapsedMillisAt(realtimeMillis: Long): Long =
        activeDurationMillis + activeSinceRealtimeMillis
            ?.let { (realtimeMillis - it).coerceAtLeast(0L) }
            .orZero()
}

private fun Long?.orZero(): Long = this ?: 0L

/** 화면은 이 계약으로 명령을 보내고 상태만 관찰한다. 연속 위치 구독은 서비스가 소유한다. */
interface WalkTrackingController {
    val state: StateFlow<WalkTrackingState>

    /**
     * @param dogIds 이 산책에 데리고 나가는 아이들. **여러 마리다** — 두 마리를
     *   데리고 나갔는데 한 아이만 남으면 나머지 아이의 운동량이 통째로 빈다.
     *   **비어 있어도 된다** — 로그인 전이거나 등록한 강아지가 없는 상태에서도
     *   산책은 되어야 하고, 그때 아무 강아지나 갖다 붙이면 남의 기록이 된다.
     *
     * 산책 중에는 못 바꾼다. 중간에 바꾸면 "언제부터 누가"를 따져야 하는데 그 값을
     * 좌표마다 두지 않기로 했다.
     */
    fun start(dogIds: List<String> = emptyList())

    fun pause()

    fun resume()

    fun stop()
}

/** 시작된 서비스와 화면 사이의 프로세스 로컬 상태 다리. */
class WalkTrackingStore {
    private val _state = MutableStateFlow(WalkTrackingState())
    val state: StateFlow<WalkTrackingState> = _state.asStateFlow()

    internal fun publish(state: WalkTrackingState) {
        _state.value = state
    }
}

/** Application과 Service가 공유하는 산책 기록 의존성. 화면에는 제어 계약만 공개한다. */
class WalkRuntime internal constructor(
    internal val locationSource: LocationSource,
    internal val store: WalkTrackingStore,
    val controller: WalkTrackingController,
    internal val writer: WalkFixWriter,
    /**
     * 방금 쓴 것을 **되읽는** 자리. `writer` 는 쓰기 전용 큐라 읽을 수가 없는데,
     * 산책이 끝나고 "너무 짧은가" 를 재려면 좌표를 다시 봐야 한다.
     */
    internal val log: WalkFixLog,
    /** 지난 산책을 읽는 자리. 쓰기와 같은 DB 를 보되 성질이 달라 갈라 뒀다. */
    val history: WalkHistory,
    /** 기기와 서버를 맞추는 자리. 로그인했을 때만 일한다. */
    val sync: WalkSync,
)
