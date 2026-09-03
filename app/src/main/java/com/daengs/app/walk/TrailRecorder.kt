package com.daengs.app.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class TrackingState { OFF, RECORDING, PAUSED }

/**
 * 지도에 그릴 산책 동선의 현재 상태.
 *
 * 한 산책을 평평한 점 목록 대신 여러 세그먼트로 보관한다. 일시정지 전후나 GPS가 비현실적으로
 * 튄 구간은 서로 다른 세그먼트가 되므로, 화면에서 두 지점을 직선으로 잇거나 그 간격을 걸은
 * 거리로 세지 않는다.
 */
data class TrailSnapshot(
    val state: TrackingState = TrackingState.OFF,
    val segments: List<List<LocationSample>> = emptyList(),
    val distanceMeters: Double = 0.0,
    /** 정확도가 낮아 연속으로 버린 fix 수. 0이 아니면 기록이 멈춘 것처럼 보일 수 있다. */
    val skippedLowAccuracy: Int = 0,
) {
    val sampleCount: Int get() = segments.sumOf { it.size }

    val lastSample: LocationSample? get() = segments.lastOrNull()?.lastOrNull()
}

/**
 * 위치 표본을 화면용 산책 동선으로 바꾸는 순수 상태 계산기.
 *
 * Android 생명주기, 위치 구독, 로컬 저장과는 의도적으로 분리한다. 여기서 버린 원본 fix를
 * 영구 저장할지는 후속 저장소 계층이 결정하고, 이 계산기는 지도에 그릴 깨끗한 경로만 소유한다.
 */
class TrailRecorder(
    private val minDistanceMeters: Double = 3.0,
    private val maxAccuracyMeters: Float = 50f,
    private val maxJumpMeters: Double = 200.0,
    private val maxSamples: Int = 5_000,
) {
    private var state = TrackingState.OFF
    private val segments = mutableListOf<MutableList<LocationSample>>()
    private var storedSampleCount = 0
    private var distanceMeters = 0.0
    private var skippedLowAccuracy = 0
    private var breakBeforeNext = false
    private var publishedSnapshot = TrailSnapshot()

    init {
        require(minDistanceMeters >= 0.0) { "minDistanceMeters must not be negative" }
        require(maxAccuracyMeters >= 0f) { "maxAccuracyMeters must not be negative" }
        require(maxJumpMeters >= 0.0) { "maxJumpMeters must not be negative" }
        require(maxSamples > 0) { "maxSamples must be positive" }
    }

    fun snapshot(): TrailSnapshot = publishedSnapshot

    /** 새 산책을 시작하며 이전 화면용 동선을 비운다. */
    fun start(): TrailSnapshot {
        state = TrackingState.RECORDING
        segments.clear()
        storedSampleCount = 0
        distanceMeters = 0.0
        skippedLowAccuracy = 0
        breakBeforeNext = false
        return publishSnapshot()
    }

    fun pause(): TrailSnapshot {
        if (state == TrackingState.RECORDING) {
            state = TrackingState.PAUSED
            return publishSnapshot()
        }
        return publishedSnapshot
    }

    fun resume(): TrailSnapshot {
        if (state == TrackingState.PAUSED) {
            breakBeforeNext = true
            state = TrackingState.RECORDING
            return publishSnapshot()
        }
        return publishedSnapshot
    }

    /** 기록을 끝내되 종료 화면에서 쓸 수 있도록 계산된 동선과 거리는 유지한다. */
    fun stop(): TrailSnapshot {
        state = TrackingState.OFF
        return publishSnapshot()
    }

    fun add(sample: LocationSample): TrailSnapshot {
        if (!accept(sample, trimAfter = true)) return publishedSnapshot
        return publishSnapshot()
    }

    /**
     * 저장된 원본처럼 중간 화면을 만들 필요가 없는 묶음을 한 번에 재생한다.
     *
     * [add]를 반복하면 매 fix마다 불변 화면 목록을 복사한다. 과거 산책을 다시 읽을 때는
     * 마지막 결과만 필요하므로 내부 목록에 먼저 쌓고 한 번만 복사한다.
     */
    fun addAll(samples: Iterable<LocationSample>): TrailSnapshot {
        var changed = false
        for (sample in samples) changed = accept(sample, trimAfter = false) || changed
        if (!changed) return publishedSnapshot
        trim()
        return publishSnapshot()
    }

    private fun accept(sample: LocationSample, trimAfter: Boolean): Boolean {
        if (state != TrackingState.RECORDING) return false
        if (sample.accuracyMeters != null && sample.accuracyMeters > maxAccuracyMeters) {
            skippedLowAccuracy += 1
            return true
        }

        val previous = segments.lastOrNull()?.lastOrNull()
        val delta = previous?.point?.distanceTo(sample.point) ?: 0.0
        if (previous != null && !breakBeforeNext && delta < minDistanceMeters) {
            val changed = skippedLowAccuracy != 0
            skippedLowAccuracy = 0
            return changed
        }

        val startsSegment = previous == null || breakBeforeNext || delta > maxJumpMeters
        breakBeforeNext = false
        if (startsSegment) {
            segments += mutableListOf(sample)
        } else {
            segments.last() += sample
        }
        storedSampleCount += 1
        distanceMeters += if (startsSegment) 0.0 else delta
        skippedLowAccuracy = 0
        if (trimAfter) trim()
        return true
    }

    private fun trim() {
        var excess = storedSampleCount - maxSamples
        while (excess > 0 && segments.isNotEmpty()) {
            val first = segments.first()
            if (excess >= first.size) {
                excess -= first.size
                storedSampleCount -= first.size
                segments.removeAt(0)
            } else {
                first.subList(0, excess).clear()
                storedSampleCount -= excess
                excess = 0
            }
        }
    }

    private fun publishSnapshot(): TrailSnapshot {
        publishedSnapshot = TrailSnapshot(
            state = state,
            segments = segments.map { it.toList() },
            distanceMeters = distanceMeters,
            skippedLowAccuracy = skippedLowAccuracy,
        )
        return publishedSnapshot
    }
}

internal fun GeoPoint.distanceTo(other: GeoPoint): Double {
    val earthRadiusMeters = 6_371_000.0
    val lat1 = Math.toRadians(latitude)
    val lat2 = Math.toRadians(other.latitude)
    val latDelta = lat2 - lat1
    val lngDelta = Math.toRadians(other.longitude - longitude)
    val a = sin(latDelta / 2) * sin(latDelta / 2) +
        cos(lat1) * cos(lat2) * sin(lngDelta / 2) * sin(lngDelta / 2)
    return 2 * earthRadiusMeters * atan2(sqrt(a), sqrt(1 - a))
}
