package com.daengs.app.walk.pin

import com.daengs.app.location.GeoPoint
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Earth-centred metres avoid longitude discontinuities and division by cos(latitude) at poles. */
internal data class PinVector(val x: Double, val y: Double, val z: Double) {
    operator fun plus(other: PinVector) = PinVector(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: PinVector) = PinVector(x - other.x, y - other.y, z - other.z)
    operator fun times(scale: Double) = PinVector(x * scale, y * scale, z * scale)
    fun length() = sqrt(x * x + y * y + z * z)

    fun point(): GeoPoint? {
        val norm = length()
        if (!norm.isFinite() || norm < EARTH_RADIUS / 2) return null
        return GeoPoint(
            Math.toDegrees(asin((z / norm).coerceIn(-1.0, 1.0))),
            Math.toDegrees(atan2(y, x)),
        )
    }

    companion object {
        const val EARTH_RADIUS = 6_371_008.8
        val ZERO = PinVector(0.0, 0.0, 0.0)
        fun from(point: GeoPoint): PinVector {
            val lat = Math.toRadians(point.latitude)
            val lng = Math.toRadians(point.longitude)
            return PinVector(cos(lat) * cos(lng), cos(lat) * sin(lng), sin(lat)) * EARTH_RADIUS
        }
    }
}

internal fun GeoPoint.isUsablePinPoint() =
    latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0

internal fun pinDistance(a: GeoPoint, b: GeoPoint): Double =
    2 * PinVector.EARTH_RADIUS * asin(
        ((PinVector.from(a) - PinVector.from(b)).length() / (2 * PinVector.EARTH_RADIUS)).coerceIn(0.0, 1.0),
    )

internal data class PinFit(val atTarget: PinVector, val velocity: PinVector, val residual: Double)

/** Weighted least squares only on the short, screened motion segment. t=0 is always the tap. */
internal fun fitPinMotion(samples: List<ActionPinObservation>, target: Long): PinFit? {
    if (samples.size < 3 || samples.last().atMillis - samples.first().atMillis < PinPolicyV1.MIN_FIT_SPAN_MILLIS) {
        return null
    }
    val weights = samples.map { 1.0 / it.accuracyMeters!!.toDouble().coerceAtLeast(3.0).let { a -> a * a } }
    val sum = weights.sum()
    val times = samples.map { (it.atMillis - target) / 1_000.0 }
    val vectors = samples.map { PinVector.from(it.point) }
    val meanTime = times.indices.sumOf { times[it] * weights[it] } / sum
    val meanPoint = vectors.indices.fold(PinVector.ZERO) { acc, i -> acc + vectors[i] * (weights[i] / sum) }
    val denominator = times.indices.sumOf { weights[it] * (times[it] - meanTime).let { t -> t * t } }
    if (denominator < 1e-9) return null
    val velocity = times.indices.fold(PinVector.ZERO) { acc, i ->
        acc + (vectors[i] - meanPoint) * (weights[i] * (times[i] - meanTime) / denominator)
    }
    val atTarget = meanPoint - velocity * meanTime
    val residual = vectors.indices.maxOf { (vectors[it] - (atTarget + velocity * times[it])).length() }
    return PinFit(atTarget, velocity, residual)
}
