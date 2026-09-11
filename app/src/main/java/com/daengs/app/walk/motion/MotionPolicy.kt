package com.daengs.app.walk.motion

import java.security.MessageDigest
import kotlinx.serialization.json.*

/** Experimental v1 settings, frozen per session; these are not calibrated field thresholds. */
data class MotionConfig(
    val windowSize: Int = 8,
    val windowSeconds: Double = 15.0,
    val minCoordinateSeconds: Double = 2.0,
    val maxPositionAccuracyM: Double = 50.0,
    val maxSpeedAccuracyMps: Double = 2.0,
    val speedAgreementMps: Double = 1.5,
    val maxWalkingSpeedMps: Double = 7.0,
    val stationarySpeedMps: Double = 0.25,
    val minDistanceM: Double = 3.0,
    val noiseRadiusFactor: Double = 0.5,
    val maxJumpM: Double = 200.0,
    val maxGapSeconds: Double = 20.0,
    val reentrySamples: Int = 2,
    val reentrySeconds: Double = 1.5,
) {
    init {
        require(windowSize in 2..128 && reentrySamples in 1..128)
        require(listOf(windowSeconds, minCoordinateSeconds, maxPositionAccuracyM,
            maxSpeedAccuracyMps, speedAgreementMps, maxWalkingSpeedMps, stationarySpeedMps,
            minDistanceM, noiseRadiusFactor, maxJumpM, maxGapSeconds, reentrySeconds)
            .all { it.isFinite() && it >= 0.0 })
        require(minCoordinateSeconds > 0 && minCoordinateSeconds <= windowSeconds)
        require(windowSeconds <= maxGapSeconds && maxGapSeconds <= 3600)
        require(maxWalkingSpeedMps > stationarySpeedMps && maxJumpM > minDistanceM)
        require(reentrySeconds <= maxGapSeconds)
    }

    internal fun json(): String = buildJsonObject {
        put("windowSize", windowSize); put("windowSeconds", windowSeconds)
        put("minCoordinateSeconds", minCoordinateSeconds)
        put("maxPositionAccuracyM", maxPositionAccuracyM); put("maxSpeedAccuracyMps", maxSpeedAccuracyMps)
        put("speedAgreementMps", speedAgreementMps); put("maxWalkingSpeedMps", maxWalkingSpeedMps)
        put("stationarySpeedMps", stationarySpeedMps); put("minDistanceM", minDistanceM)
        put("noiseRadiusFactor", noiseRadiusFactor); put("maxJumpM", maxJumpM)
        put("maxGapSeconds", maxGapSeconds); put("reentrySamples", reentrySamples)
        put("reentrySeconds", reentrySeconds)
    }.toString()

    companion object {
        internal fun fromJson(text: String): MotionConfig {
            val json = Json.parseToJsonElement(text).jsonObject
            require(json.keys == Json.parseToJsonElement(MotionConfig().json()).jsonObject.keys)
            fun number(key: String) = json.getValue(key).jsonPrimitive.also { require(!it.isString) }.double
            fun integer(key: String) = json.getValue(key).jsonPrimitive.also { require(!it.isString) }.int
            return MotionConfig(integer("windowSize"), number("windowSeconds"), number("minCoordinateSeconds"),
                number("maxPositionAccuracyM"), number("maxSpeedAccuracyMps"), number("speedAgreementMps"),
                number("maxWalkingSpeedMps"), number("stationarySpeedMps"), number("minDistanceM"),
                number("noiseRadiusFactor"), number("maxJumpM"), number("maxGapSeconds"),
                integer("reentrySamples"), number("reentrySeconds"))
        }
    }
}

/** Frozen envelope stored verbatim on the session; observation schema is independent of Room's version. */
data class StoredMotionPolicy(val version: String, val observationSchemaVersion: Int,
    val configJson: String, val configHash: String, val measurementVersion: String? = null)

class SessionMotionPolicy internal constructor(val sessionId: String, val config: MotionConfig,
    val stored: StoredMotionPolicy)

sealed interface MotionPolicySelection {
    data object Legacy : MotionPolicySelection
    data class Supported(val policy: SessionMotionPolicy) : MotionPolicySelection
    data class Unsupported(val reason: String) : MotionPolicySelection
}

object MotionPolicies {
    const val VERSION = "motion-v1"
    const val OBSERVATION_SCHEMA = 15
    const val MEASUREMENT_VERSION = "motion-measurement-v1"

    fun encode(policy: SessionMotionPolicy): String = buildJsonObject {
        put("version", policy.stored.version)
        put("observationSchemaVersion", policy.stored.observationSchemaVersion)
        put("configJson", policy.stored.configJson)
        put("configHash", policy.stored.configHash)
        policy.stored.measurementVersion?.let { put("measurementVersion", it) }
    }.toString()

    /** Preserve missing, corrupt and future records as distinct from today's defaults. */
    fun resolveJson(sessionId: String, json: String?): MotionPolicySelection {
        require(sessionId.isNotBlank())
        if (json == null) return MotionPolicySelection.Legacy
        val stored = try {
            val envelope = Json.parseToJsonElement(json).jsonObject
            val required = setOf("version", "observationSchemaVersion", "configJson", "configHash")
            require(envelope.keys == required || envelope.keys == required + "measurementVersion")
            fun string(key: String) = envelope.getValue(key).jsonPrimitive.also { require(it.isString) }.content
            val schema = envelope.getValue("observationSchemaVersion").jsonPrimitive.also { require(!it.isString) }.int
            StoredMotionPolicy(string("version"), schema, string("configJson"), string("configHash"),
                if ("measurementVersion" in envelope) string("measurementVersion") else null)
        }
        catch (_: IllegalArgumentException) { return MotionPolicySelection.Unsupported("POLICY_ENVELOPE") }
        return resolve(sessionId, stored)
    }

    fun freeze(sessionId: String, config: MotionConfig = MotionConfig(), measure: Boolean = false): SessionMotionPolicy {
        require(sessionId.isNotBlank())
        val json = config.json()
        return SessionMotionPolicy(sessionId, config,
            StoredMotionPolicy(VERSION, OBSERVATION_SCHEMA, json, hash(json), MEASUREMENT_VERSION.takeIf { measure }))
    }

    /** Missing is legacy; unknown/corrupt is never silently evaluated with current settings. */
    fun resolve(sessionId: String, stored: StoredMotionPolicy?): MotionPolicySelection {
        require(sessionId.isNotBlank())
        if (stored == null) return MotionPolicySelection.Legacy
        if (stored.version != VERSION) return MotionPolicySelection.Unsupported("POLICY_VERSION")
        if (stored.observationSchemaVersion != OBSERVATION_SCHEMA) return MotionPolicySelection.Unsupported("OBSERVATION_SCHEMA")
        if (stored.measurementVersion != null && stored.measurementVersion != MEASUREMENT_VERSION)
            return MotionPolicySelection.Unsupported("MEASUREMENT_VERSION")
        if (stored.configHash != hash(stored.configJson)) return MotionPolicySelection.Unsupported("CONFIG_HASH")
        val config = try { MotionConfig.fromJson(stored.configJson) }
        catch (_: IllegalArgumentException) { return MotionPolicySelection.Unsupported("CONFIG_INVALID") }
        catch (_: NoSuchElementException) { return MotionPolicySelection.Unsupported("CONFIG_INVALID") }
        return MotionPolicySelection.Supported(SessionMotionPolicy(sessionId, config, stored))
    }

    internal fun hash(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
}
