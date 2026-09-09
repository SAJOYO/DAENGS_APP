package com.daengs.app.territory

/** Physical proximity only. IN_RANGE grants neither a claim nor permission to take a photo. */
enum class TerritoryProximityRange { UNAVAILABLE, APPROACHING, IN_RANGE }

data class TerritoryProximity(
    val range: TerritoryProximityRange = TerritoryProximityRange.UNAVAILABLE,
    val distanceMeters: Double? = null,
    val accuracyMeters: Double? = null,
)

/** Shared distance/error boundary; has no walk, account, pet or occupancy input. */
fun evaluateTerritoryProximity(
    trustedLocation: Boolean,
    distanceMeters: Double,
    accuracyMeters: Double,
    radiusMeters: Double,
): TerritoryProximity {
    require(radiusMeters.isFinite() && radiusMeters > 0)
    val distance = distanceMeters.takeIf { it.isFinite() && it >= 0 }
    val accuracy = accuracyMeters.takeIf { it.isFinite() && it >= 0 }
    val range = when {
        !trustedLocation || distance == null || accuracy == null -> TerritoryProximityRange.UNAVAILABLE
        distance > radiusMeters -> TerritoryProximityRange.APPROACHING
        distance + accuracy > radiusMeters -> TerritoryProximityRange.UNAVAILABLE
        else -> TerritoryProximityRange.IN_RANGE
    }
    return TerritoryProximity(range, distance, accuracy)
}
