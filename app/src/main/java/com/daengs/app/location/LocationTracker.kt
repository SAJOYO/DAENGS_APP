package com.daengs.app.location

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 지금 구독이 어떤 상태인가.
 *
 * 피드가 끝나는 길은 둘이고 **화면은 그 둘을 구분해야 한다** — 끝이 있는 소스(리플레이)가
 * 다 떨어진 것과, 소스가 실패한 것. 예전에는 둘 다 조용했다. 실패는 프로세스를 죽였고,
 * 완료는 죽은 피드를 붙잡은 채 앱을 얼렸다.
 */
sealed interface FeedStatus {
    data object Idle : FeedStatus

    data object Running : FeedStatus

    data object Completed : FeedStatus

    data class Failed(val cause: Throwable) : FeedStatus
}

/** 앱에 하나뿐인 연속 위치 구독을 맡는다. */
class LocationTracker(
    private val scope: CoroutineScope,
) {
    private val _updates = MutableSharedFlow<LocationSample>(extraBufferCapacity = 16)
    val updates = _updates.asSharedFlow()

    private val _status = MutableStateFlow<FeedStatus>(FeedStatus.Idle)
    val status: StateFlow<FeedStatus> = _status.asStateFlow()

    private var collectionJob: Job? = null
    private var generation = 0

    fun start(
        source: LocationSource,
        config: LocationUpdateConfig = LocationUpdateConfig(),
    ) {
        collectionJob?.cancel()
        val token = ++generation
        _status.value = FeedStatus.Running
        collectionJob = scope.launch {
            try {
                source.locationUpdates(config).collect(_updates::emit)
                if (generation == token) _status.value = FeedStatus.Completed
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                // 이 실패를 받아 줄 곳은 여기뿐이다 — collect() 위쪽에서는 아무도 못 잡고,
                // 여기서 안 잡고 던지면 프로세스가 내려간다.
                if (generation == token) _status.value = FeedStatus.Failed(error)
            }
        }
    }

    fun stop() {
        collectionJob?.cancel()
        collectionJob = null
        generation++
        _status.value = FeedStatus.Idle
    }
}
