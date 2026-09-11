package com.daengs.app.walk.motion

import org.junit.Assert.*
import org.junit.Test

class MotionPolicyTest {
    @Test fun `frozen config survives storage and does not follow later defaults or edits`() {
        val config = MotionConfig(maxWalkingSpeedMps = 5.0, windowSize = 4)
        val original = MotionPolicies.freeze("s", config)
        val edited = config.copy(maxWalkingSpeedMps = 9.0)
        val restored = MotionPolicies.resolve("s", original.stored) as MotionPolicySelection.Supported
        assertEquals(original.config, restored.policy.config)
        assertEquals(5.0, restored.policy.config.maxWalkingSpeedMps, 0.0)
        assertEquals(original.stored, MotionPolicies.freeze("s", config).stored)
        assertNotEquals(original.stored.configHash, MotionPolicies.freeze("s", edited).stored.configHash)
    }

    @Test fun `legacy unknown version schema and corrupt hash never fall back to v1`() {
        assertEquals(MotionPolicySelection.Legacy, MotionPolicies.resolve("s", null))
        val stored = MotionPolicies.freeze("s").stored
        for (bad in listOf(stored.copy(version = "future-v2"), stored.copy(observationSchemaVersion = 14),
            stored.copy(configHash = "broken"), stored.copy(configJson = stored.configJson.replace("7.0", "8.0")))) {
            assertTrue(MotionPolicies.resolve("s", bad) is MotionPolicySelection.Unsupported)
        }
    }

    @Test fun `unknown malformed and missing config fields are explicit unsupported input`() {
        val original = MotionPolicies.freeze("s").stored
        val jsons = listOf("{}", "[]", "null", "broken", original.configJson.replace("\"windowSize\":8", "\"windowSize\":1"),
            original.configJson.replace("\"windowSize\":8", "\"windowSize\":\"8\""),
            original.configJson.dropLast(1) + ",\"futureSetting\":true}")
        for (json in jsons) {
            val stored = original.copy(configJson = json, configHash = MotionPolicies.hash(json))
            assertTrue(json, MotionPolicies.resolve("s", stored) is MotionPolicySelection.Unsupported)
        }
    }

    @Test fun `nonsensical thresholds are rejected rather than clamped`() {
        val invalid = listOf<() -> MotionConfig>({ MotionConfig(windowSize = 1) }, { MotionConfig(windowSize = 1000) },
            { MotionConfig(maxGapSeconds = Double.NaN) }, { MotionConfig(maxWalkingSpeedMps = Double.POSITIVE_INFINITY) },
            { MotionConfig(minDistanceM = -1.0) }, { MotionConfig(minCoordinateSeconds = 0.0) },
            { MotionConfig(windowSeconds = 30.0, maxGapSeconds = 20.0) }, { MotionConfig(stationarySpeedMps = 8.0) })
        invalid.forEach { make -> assertThrows(IllegalArgumentException::class.java) { make() } }
    }
}
