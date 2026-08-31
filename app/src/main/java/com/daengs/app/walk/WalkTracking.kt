package com.daengs.app.walk

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

    fun start()

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
)
