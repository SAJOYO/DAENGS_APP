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
    private var snapshot = TrailSnapshot()
    private var breakBeforeNext = false

    init {
        require(minDistanceMeters >= 0.0) { "minDistanceMeters must not be negative" }
        require(maxAccuracyMeters >= 0f) { "maxAccuracyMeters must not be negative" }
        require(maxJumpMeters >= 0.0) { "maxJumpMeters must not be negative" }
        require(maxSamples > 0) { "maxSamples must be positive" }
    }

    fun snapshot(): TrailSnapshot = snapshot

    /** 새 산책을 시작하며 이전 화면용 동선을 비운다. */
    fun start(): TrailSnapshot {
        breakBeforeNext = false
        snapshot = TrailSnapshot(state = TrackingState.RECORDING)
        return snapshot
    }

    fun pause(): TrailSnapshot {
        if (snapshot.state == TrackingState.RECORDING) {
            snapshot = snapshot.copy(state = TrackingState.PAUSED)
        }
        return snapshot
    }

    fun resume(): TrailSnapshot {
        if (snapshot.state == TrackingState.PAUSED) {
            breakBeforeNext = true
            snapshot = snapshot.copy(state = TrackingState.RECORDING)
        }
        return snapshot
    }

    /** 기록을 끝내되 종료 화면에서 쓸 수 있도록 계산된 동선과 거리는 유지한다. */
    fun stop(): TrailSnapshot {
        snapshot = snapshot.copy(state = TrackingState.OFF)
        return snapshot
    }

    fun add(sample: LocationSample): TrailSnapshot {
        if (snapshot.state != TrackingState.RECORDING) return snapshot
        if (sample.accuracyMeters != null && sample.accuracyMeters > maxAccuracyMeters) {
            snapshot = snapshot.copy(skippedLowAccuracy = snapshot.skippedLowAccuracy + 1)
            return snapshot
        }

        val previous = snapshot.lastSample
        val delta = previous?.point?.distanceTo(sample.point) ?: 0.0
        if (previous != null && !breakBeforeNext && delta < minDistanceMeters) {
            snapshot = snapshot.copy(skippedLowAccuracy = 0)
            return snapshot
        }

        val startsSegment = previous == null || breakBeforeNext || delta > maxJumpMeters
        breakBeforeNext = false
        val segments = if (startsSegment) {
            snapshot.segments + listOf(listOf(sample))
        } else {
            snapshot.segments.dropLast(1) + listOf(snapshot.segments.last() + sample)
        }
        snapshot = snapshot.copy(
            segments = trim(segments),
            distanceMeters = snapshot.distanceMeters + if (startsSegment) 0.0 else delta,
            skippedLowAccuracy = 0,
        )
        return snapshot
    }

    private fun trim(segments: List<List<LocationSample>>): List<List<LocationSample>> {
        var excess = segments.sumOf { it.size } - maxSamples
        if (excess <= 0) return segments
        val kept = mutableListOf<List<LocationSample>>()
        for (segment in segments) {
            when {
                excess <= 0 -> kept += segment
                excess >= segment.size -> excess -= segment.size
                else -> {
                    kept += segment.drop(excess)
                    excess = 0
                }
            }
        }
        return kept
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
