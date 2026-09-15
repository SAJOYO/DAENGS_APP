package com.daengs.app.walk.diary

import org.json.JSONObject
import java.time.Instant

/** Scene-bound historical grid observation. No session/current-weather fallback. */
data class DiarySceneTemperature(
    val celsius: Double,
    val observedAtMillis: Long,
    val sceneAtMillis: Long,
    val gridX: Int,
    val gridY: Int,
    val evidenceId: String,
)

internal fun sceneTemperature(scene: JSONObject, atMillis: Long): DiarySceneTemperature? {
    val raw = scene.optJSONObject("temperature") ?: return null
    return runCatching {
        require(raw.getString("provider") == "kma-vilage-fcst:ncst")
        require(raw.getString("unit") == "celsius")
        val value = raw.get("temperature_c") as? Number ?: error("invalid temperature")
        val celsius = value.toDouble()
        require(celsius.isFinite() && celsius in -90.0..60.0)
        val observed = Instant.parse(raw.getString("observed_at"))
        val at = Instant.parse(raw.getString("scene_at"))
        require(at.toEpochMilli() == atMillis)
        val age = java.time.Duration.between(observed, at)
        require(!age.isNegative && age <= java.time.Duration.ofHours(2))
        val grid = raw.getJSONArray("grid")
        require(grid.length() == 2)
        fun coordinate(index: Int): Int {
            val n = (grid.get(index) as? Number)?.toDouble() ?: error("invalid grid")
            require(n.isFinite() && n > 0 && n <= Int.MAX_VALUE && n % 1.0 == 0.0)
            return n.toInt()
        }
        val id = raw.getString("evidence_id").trim()
        require(id.isNotEmpty() && id != "null")
        DiarySceneTemperature(celsius, observed.toEpochMilli(), atMillis, coordinate(0), coordinate(1), id)
    }.getOrNull()
}
