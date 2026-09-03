package com.daengs.app.ui.places

import com.daengs.app.location.FeedStatus
import com.daengs.app.location.LocationSample
import com.daengs.app.location.LocationSource
import com.daengs.app.location.LocationTracker
import com.daengs.app.map.features.places.PlaceSearchArea
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
 * 시설 화면이 사용하는 위치 권한·단발 측위·연속 구독의 생애를 맡는다.
 *
 * 검색 종류나 Journey를 알지 못하며, 유효한 위치 관측만 [updates]로 내보낸다. 단발 측위
 * callback도 현재 활성 세대일 때만 호출하므로 취소를 무시하는 위치원에서 늦은 값이 와도
 * 화면 밖 작업을 다시 시작하지 않는다.
 */
internal class PlaceLocationCoordinator(
    private val source: LocationSource,
    private val scope: CoroutineScope,
) {
    private val tracker = LocationTracker(scope)
    private val mutableState = MutableStateFlow<PlaceLocationState>(
        PlaceLocationState.PermissionRequired,
    )
    val state: StateFlow<PlaceLocationState> = mutableState.asStateFlow()

    private val mutableUpdates = MutableSharedFlow<LocationSample>(extraBufferCapacity = 16)
    val updates = mutableUpdates.asSharedFlow()

    private var active = false
    private var permissionGranted = false
    private var locateJob: Job? = null
    private var locateGeneration = 0L

    val isActive: Boolean get() = active

    init {
        scope.launch {
            tracker.updates.collect { sample ->
                if (accept(sample)) mutableUpdates.emit(sample)
            }
        }
        scope.launch {
            tracker.status.collect { status ->
                if (status is FeedStatus.Failed && active && permissionGranted) {
                    mutableState.value = PlaceLocationState.Failed(
                        failure = PlaceLocationFailure.UPDATE_FAILED,
                        lastKnown = mutableState.value.devicePosition,
                    )
                }
            }
        }
    }

    fun activate(granted: Boolean) {
        active = true
        updatePermission(granted, permanentlyDenied = false)
    }

    fun deactivate() {
        active = false
        cancelLocate()
        tracker.stop()
    }

    fun updatePermission(granted: Boolean, permanentlyDenied: Boolean) {
        permissionGranted = granted
        if (!granted) {
            cancelLocate()
            tracker.stop()
            mutableState.value = if (permanentlyDenied) {
                PlaceLocationState.PermissionPermanentlyDenied
            } else {
                PlaceLocationState.PermissionRequired
            }
            return
        }

        if (mutableState.value is PlaceLocationState.PermissionRequired ||
            mutableState.value is PlaceLocationState.PermissionPermanentlyDenied
        ) {
            mutableState.value = PlaceLocationState.Locating(lastKnown = null)
        }
        if (active) tracker.start(source)
    }

    fun locate(onLocated: (LocationSample) -> Unit) {
        if (!active || !permissionGranted) return
        cancelLocate()
        val generation = ++locateGeneration
        val lastKnown = mutableState.value.devicePosition
        mutableState.value = PlaceLocationState.Locating(lastKnown)
        locateJob = scope.launch {
            val sample = try {
                source.currentLocation()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                if (isCurrent(generation)) {
                    mutableState.value = PlaceLocationState.Failed(
                        PlaceLocationFailure.UNAVAILABLE,
                        lastKnown,
                    )
                }
                return@launch
            }

            if (!isCurrent(generation)) return@launch
            if (accept(sample)) onLocated(sample)
        }.also { job ->
            job.invokeOnCompletion {
                if (generation == locateGeneration && locateJob === job) locateJob = null
            }
        }
    }

    fun cancelLocate() {
        locateGeneration++
        locateJob?.cancel()
        locateJob = null
    }

    private fun isCurrent(generation: Long): Boolean =
        generation == locateGeneration && active && permissionGranted

    private fun accept(sample: LocationSample): Boolean {
        if (!active || !permissionGranted) return false
        if (sample.isMock) {
            mutableState.value = PlaceLocationState.Failed(
                PlaceLocationFailure.MOCK_LOCATION,
                mutableState.value.devicePosition,
            )
            return false
        }
        mutableState.value = if (PlaceSearchArea.contains(sample.point)) {
            PlaceLocationState.Ready(sample.point)
        } else {
            PlaceLocationState.Unsupported(sample.point)
        }
        return true
    }
}
