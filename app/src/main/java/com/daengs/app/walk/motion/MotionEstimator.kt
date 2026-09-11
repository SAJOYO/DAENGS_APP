package com.daengs.app.walk.motion

import com.daengs.app.walk.RecordedFix
import kotlin.math.max
import kotlin.math.*

internal data class MotionPoint(val ref: MotionRef, val fix: RecordedFix) {
    val time: Long get() = requireNotNull(fix.elapsedRealtimeNanos)
    val accuracy: Double get() = requireNotNull(fix.accuracyM).toDouble()
    fun distanceTo(other: MotionPoint): Double {
        val lat1 = Math.toRadians(fix.lat)
        val lat2 = Math.toRadians(other.fix.lat)
        val dlat = lat2 - lat1
        val dlng = Math.toRadians(other.fix.lng - fix.lng)
        val haversine = (sin(dlat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dlng / 2).pow(2)).coerceIn(0.0, 1.0)
        return 6_371_000.0 * 2 * atan2(sqrt(haversine), sqrt(1 - haversine))
    }
}

/** Measurement history only: it never reads the distance anchor or runtime wall clock. */
internal class MotionEstimator(private val config: MotionConfig) {
    private val window = ArrayDeque<MotionPoint>()
    val size get() = window.size
    fun reset() = window.clear()

    fun estimate(point: MotionPoint): MotionEstimate {
        val fix = point.fix
        val reasons = linkedSetOf<MotionReason>()
        val position = positionQuality(fix, config)
        when (position) {
            PositionQuality.UNCERTAIN -> reasons += MotionReason.POSITION_UNCERTAIN
            PositionQuality.INVALID -> reasons += MotionReason.INVALID_POSITION
            PositionQuality.MOCK -> reasons += MotionReason.MOCK
            PositionQuality.USABLE -> Unit
        }
        while (window.isNotEmpty() && seconds(window.first().time, point.time) > config.windowSeconds) window.removeFirst()
        val previous = window.firstOrNull()?.takeIf { position == PositionQuality.USABLE }
        val dt = previous?.let { seconds(it.time, point.time) } ?: 0.0
        val displacement = previous?.distanceTo(point) ?: 0.0
        val uncertainty = previous?.let { (it.accuracy + point.accuracy) / max(dt, 0.000001) } ?: 0.0
        val coordinateSpeed = if (previous != null && dt >= config.minCoordinateSeconds &&
            displacement > (previous.accuracy + point.accuracy) * config.noiseRadiusFactor) displacement / dt else null

        var speed: Double? = null
        var source = SpeedSource.UNKNOWN
        var quality = SpeedQuality.UNKNOWN
        val device = fix.speedMps?.toDouble()
        val accuracy = fix.speedAccuracyMps?.toDouble()
        when {
            fix.isMock -> Unit
            device == null -> reasons += MotionReason.SPEED_MISSING
            !device.isFinite() || device < 0 -> reasons += MotionReason.SPEED_INVALID
            accuracy != null && (!accuracy.isFinite() || accuracy < 0 || accuracy > config.maxSpeedAccuracyMps) ->
                reasons += MotionReason.SPEED_UNCERTAIN
            accuracy == null -> {
                reasons += MotionReason.SPEED_UNCERTAIN
                if (coordinateSpeed != null && kotlin.math.abs(device - coordinateSpeed) > max(config.speedAgreementMps, uncertainty)) {
                    reasons += MotionReason.SPEED_CONFLICT
                } else {
                    speed = device; source = SpeedSource.DEVICE
                    quality = if (coordinateSpeed != null) SpeedQuality.ESTIMATED else SpeedQuality.UNVERIFIED
                }
            }
            else -> { speed = device; source = SpeedSource.DEVICE; quality = SpeedQuality.TRUSTED }
        }
        if (speed == null && coordinateSpeed != null && !fix.isMock) {
            speed = coordinateSpeed; source = SpeedSource.COORDINATE_WINDOW; quality = SpeedQuality.ESTIMATED
        }
        if (position == PositionQuality.USABLE) {
            window.addLast(point)
            while (window.size > config.windowSize) window.removeFirst()
        }
        val movement = when {
            speed == null -> Movement.UNKNOWN
            speed > config.maxWalkingSpeedMps -> Movement.HIGH_SPEED_SUSPECTED
            quality == SpeedQuality.UNVERIFIED -> Movement.UNKNOWN
            speed <= config.stationarySpeedMps -> Movement.STILL
            else -> Movement.MOVING
        }
        return MotionEstimate(point.ref, position, speed, source, quality, point.time, movement, reasons.toSet())
    }
}

internal fun seconds(from: Long, to: Long): Double = (to - from) / 1_000_000_000.0

internal fun positionQuality(fix: RecordedFix, config: MotionConfig): PositionQuality = when {
    fix.isMock -> PositionQuality.MOCK
    !fix.lat.isFinite() || !fix.lng.isFinite() || fix.lat !in -90.0..90.0 || fix.lng !in -180.0..180.0 -> PositionQuality.INVALID
    fix.accuracyM == null -> PositionQuality.UNCERTAIN
    !fix.accuracyM.isFinite() || fix.accuracyM < 0f -> PositionQuality.INVALID
    fix.accuracyM > config.maxPositionAccuracyM -> PositionQuality.UNCERTAIN
    else -> PositionQuality.USABLE
}
