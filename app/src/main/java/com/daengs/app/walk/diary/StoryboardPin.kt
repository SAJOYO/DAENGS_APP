package com.daengs.app.walk.diary

import org.json.JSONObject
import java.time.Instant

/** Display provenance only; never an observation anchor or an attestation. */
internal data class StoryboardPin(val revision: Long, val targetAtMillis: Long, val label: String) {
    companion object {
        fun parse(j: JSONObject): StoryboardPin {
            j.exactKeys("revision", "resolution_id", "state", "method", "target_at", "point", "uncertainty_m", "uncertainty_basis")
            val revision = j.strictLong("revision", 0, Long.MAX_VALUE)
            j.requiredText("resolution_id", 100)
            val state = j.getString("state")
            val method = j.getString("method")
            require(state in setOf("resolved", "unlocated"))
            require(method in setOf("observed", "estimated", "last_known", "none"))
            val point = j.optJSONObject("point")
            require(j.isNull("point") || point != null)
            require((point == null) == (state == "unlocated") && (point == null) == (method == "none"))
            point?.let {
                it.exactKeys("lat", "lng")
                require(it.getDouble("lat") in -90.0..90.0 && it.getDouble("lng") in -180.0..180.0)
            }
            val basis = j.getString("uncertainty_basis")
            require(basis in setOf("unknown", "provider_accuracy", "model_bound"))
            require(j.isNull("uncertainty_m") == (basis == "unknown"))
            if (!j.isNull("uncertainty_m")) require(j.getDouble("uncertainty_m").let { it.isFinite() && it > 0 })
            require(basis != "provider_accuracy" || method == "observed")
            return StoryboardPin(revision, Instant.parse(j.getString("target_at")).toEpochMilli(), when (method) {
                "observed" -> "GPS 위치"
                "estimated" -> "추정 위치"
                "last_known" -> "마지막 확인 위치"
                else -> "위치 없이 남긴 행동"
            })
        }
    }
}
