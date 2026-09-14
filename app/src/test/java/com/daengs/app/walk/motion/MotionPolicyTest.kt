package com.daengs.app.walk.motion

import org.junit.Assert.*
import org.junit.Test

class MotionPolicyTest {
    @Test fun `measurement adoption is frozen separately from historical speed only policies`() {
        val old = MotionPolicies.freeze("s")
        assertNull((MotionPolicies.resolveJson("s", MotionPolicies.encode(old)) as MotionPolicySelection.Supported)
            .policy.stored.measurementVersion)
        val measuring = MotionPolicies.freeze("s", measure = true)
        val json = MotionPolicies.encode(measuring)
        assertEquals(measuring.stored, (MotionPolicies.resolveJson("s", json) as MotionPolicySelection.Supported).policy.stored)
        assertEquals(MotionPolicySelection.Unsupported("MEASUREMENT_VERSION"),
            MotionPolicies.resolveJson("s", json.replace(MotionPolicies.MEASUREMENT_VERSION, "future")))
        assertTrue(MotionPolicies.resolveJson("s", json.replace("\"motion-measurement-v1\"", "null")) is MotionPolicySelection.Unsupported)
    }

    @Test fun `stored envelope round trips and malformed envelopes cannot become default policies`() {
        val policy = MotionPolicies.freeze("s", MotionConfig(maxWalkingSpeedMps = 4.5))
        val text = MotionPolicies.encode(policy)
        assertEquals(policy.stored, (MotionPolicies.resolveJson("s", text) as MotionPolicySelection.Supported).policy.stored)
        assertEquals(MotionPolicySelection.Legacy, MotionPolicies.resolveJson("s", null))
        for (bad in listOf("", "null", "{}", "[]", "broken", text.replace("motion-v1", "future-v2"),
            text.replace(policy.stored.configHash, "invalid"), text.dropLast(1) + ",\"future\":true}")) {
            assertTrue(bad, MotionPolicies.resolveJson("s", bad) is MotionPolicySelection.Unsupported)
        }
    }

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
