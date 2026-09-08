package com.daengs.app.map.style

import org.json.JSONObject
import kotlin.math.roundToInt

data class WalkColorTheme(val id: String, val label: String, val colors: List<Int>)

data class WalkStylePolicy(
    val themes: List<WalkColorTheme>,
    val boundaries: List<Double>,
    val blendHalfWidth: Double,
    val speedMax: Double,
    val unknownColor: Int,
    val defaultTheme: String,
) {
    fun theme(id: String) = themes.firstOrNull { it.id == id } ?: themes.first { it.id == defaultTheme }

    fun color(speed: Double?, themeId: String): Int {
        if (speed == null || !speed.isFinite() || speed < 0) return unknownColor
        val value = speed.coerceAtMost(speedMax)
        val colors = theme(themeId).colors
        boundaries.forEachIndexed { i, boundary ->
            if (kotlin.math.abs(value - boundary) <= blendHalfWidth) {
                return mix(colors[i], colors[i + 1], (value - boundary + blendHalfWidth) / (2 * blendHalfWidth))
            }
        }
        return colors[boundaries.count { value >= it }]
    }

    companion object {
        /** Reject unsupported/malformed remote policies; callers retain the bundled policy. */
        fun parse(text: String): WalkStylePolicy {
            val json = JSONObject(text)
            require(json.getInt("version") == 1)
            val max = json.getDouble("speed_max_mps")
            require(max.isFinite() && max in 0.5..7.0)
            val bounds = json.getJSONArray("boundaries_mps")
            require(bounds.length() == 4)
            val boundaries = List(4) { bounds.getDouble(it) }
            require(boundaries.all { it.isFinite() && it > 0 && it < max })
            require(boundaries.zipWithNext().all { (a, b) -> a < b })
            val half = json.getDouble("blend_half_width_mps")
            require(half.isFinite() && half > 0)
            require((listOf(0.0) + boundaries + max).zipWithNext().all { (a, b) -> b - a > half * 2 })
            val array = json.getJSONArray("themes")
            require(array.length() in 1..8)
            val themes = List(array.length()) { index ->
                val row = array.getJSONObject(index)
                val colors = row.getJSONArray("colors")
                require(colors.length() == 5)
                val id = row.getString("id")
                val label = row.getString("label")
                require(id.matches(Regex("[a-z]{1,20}")) && label.length in 1..20)
                WalkColorTheme(id, label, List(5) { parseColor(colors.getString(it)) })
            }
            require(themes.map { it.id }.distinct().size == themes.size)
            val default = json.getString("default_theme")
            require(themes.any { it.id == default })
            return WalkStylePolicy(themes, boundaries, half, max, parseColor(json.getString("unknown_color")), default)
        }

        private fun parseColor(value: String): Int {
            require(value.matches(Regex("#[0-9a-fA-F]{6}")))
            return 0xff000000.toInt() or value.drop(1).toInt(16)
        }

        private fun mix(a: Int, b: Int, ratio: Double): Int {
            var result = 0xff000000.toInt()
            for (shift in listOf(16, 8, 0)) {
                val from = (a shr shift) and 255
                val to = (b shr shift) and 255
                result = result or ((from + (to - from) * ratio).roundToInt().coerceIn(0, 255) shl shift)
            }
            return result
        }
    }
}
