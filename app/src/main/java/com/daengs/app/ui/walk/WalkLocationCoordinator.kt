package com.daengs.app.ui.walk

import com.daengs.app.location.FeedStatus
import com.daengs.app.location.LocationSample
import com.daengs.app.location.LocationSource
import com.daengs.app.location.LocationTracker
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class WalkLocationOwnership(
    val screenActive: Boolean,
    val permissionGranted: Boolean,
    val trackingActive: Boolean,
)

internal fun WalkLocationOwnership.owner(): WalkLocationOwner = when {
    trackingActive -> WalkLocationOwner.TRACKING_SERVICE
    screenActive && permissionGranted -> WalkLocationOwner.SCREEN
    else -> WalkLocationOwner.NONE
}

/**
 * 산책 화면과 Foreground Service 사이의 위치 구독 소유권을 한곳에서 조정한다.
 * 기록 중에는 서비스 fix만 받아 화면에 투영하고, 별도 화면 구독은 반드시 멈춘다.
 */
internal class WalkLocationCoordinator(
    private val source: LocationSource,
    private val scope: CoroutineScope,
) {
    private val tracker = LocationTracker(scope)
    private val mutableState = MutableStateFlow(WalkLocationUiState())
    val state: StateFlow<WalkLocationUiState> = mutableState.asStateFlow()

    private var screenActive = false
    private var trackingActive = false
    private var locateJob: Job? = null
    private var currentOwner = WalkLocationOwner.NONE
    private var latestSample: LocationSample? = null

    init {
        scope.launch { tracker.updates.collect(::accept) }
        scope.launch {
            tracker.status.collect { status ->
                if (status is FeedStatus.Failed) {
                    mutableState.update {
                        it.copy(
                            errorMessage = status.cause.message ?: "위치 업데이트를 이어가지 못했습니다.",
                        )
                    }
                }
            }
        }
    }

    fun activate(permissionGranted: Boolean, precisePermission: Boolean) {
        screenActive = true
        updatePermission(permissionGranted, precisePermission)
        if (permissionGranted && !trackingActive && mutableState.value.currentPosition == null) {
            locate(recenter = true)
        }
    }

    fun deactivate() {
        screenActive = false
        locateJob?.cancel()
        locateJob = null
        reconcileOwner()
    }

    fun updatePermission(granted: Boolean, precise: Boolean) {
        mutableState.update {
            it.copy(
                permissionGranted = granted,
                precisePermission = granted && precise,
                errorMessage = if (granted) it.errorMessage else null,
                locating = if (granted) it.locating else false,
            )
        }
        if (!granted) {
            locateJob?.cancel()
            locateJob = null
        }
        reconcileOwner()
    }

    fun acceptTrackingState(active: Boolean, sample: LocationSample?) {
        trackingActive = active
        sample?.let(::accept)
        reconcileOwner()
    }

    fun setFollowDevice(follow: Boolean) {
        mutableState.update { it.copy(followDevice = follow) }
    }

    fun locate(recenter: Boolean) {
        if (!mutableState.value.permissionGranted || mutableState.value.locating) return
        if (trackingActive) {
            latestSample?.let { sample ->
                mutableState.update {
                    it.accept(sample).copy(
                        followDevice = if (recenter) true else it.followDevice,
                        centerOn = if (recenter) sample.point else it.centerOn,
                        centerZoom = if (recenter) {
                            zoomForAccuracy(sample.accuracyMeters)
                        } else {
                            it.centerZoom
                        },
                    )
                }
            }
            return
        }
        locateJob?.cancel()
        locateJob = scope.launch {
            mutableState.update { it.copy(locating = true, errorMessage = null) }
            try {
                val sample = source.currentLocation()
                latestSample = sample
                mutableState.update {
                    it.accept(sample).copy(
                        followDevice = if (recenter) true else it.followDevice,
                        centerOn = if (recenter) sample.point else it.centerOn,
                        centerZoom = if (recenter) {
                            zoomForAccuracy(sample.accuracyMeters)
                        } else {
                            it.centerZoom
                        },
                        locating = false,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                mutableState.update {
                    it.copy(
                        locating = false,
                        errorMessage = error.message ?: "현재 위치를 확인하지 못했습니다.",
                    )
                }
            }
        }
    }

    private fun accept(sample: LocationSample) {
        latestSample = sample
        mutableState.update { it.accept(sample) }
    }

    private fun reconcileOwner() {
        val nextOwner = WalkLocationOwnership(
            screenActive = screenActive,
            permissionGranted = mutableState.value.permissionGranted,
            trackingActive = trackingActive,
        ).owner()
        if (nextOwner == currentOwner) return
        currentOwner = nextOwner
        when (nextOwner) {
            WalkLocationOwner.SCREEN -> tracker.start(source)
            WalkLocationOwner.NONE,
            WalkLocationOwner.TRACKING_SERVICE,
            -> tracker.stop()
        }
        mutableState.update { it.copy(owner = nextOwner) }
    }
}

internal fun zoomForAccuracy(accuracyMeters: Float?): Double = when {
    accuracyMeters == null -> 15.0
    accuracyMeters <= 50f -> 16.5
    accuracyMeters <= 200f -> 15.0
    accuracyMeters <= 1_000f -> 13.5
    else -> 12.0
}
