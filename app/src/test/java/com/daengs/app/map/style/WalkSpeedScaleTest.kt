package com.daengs.app.map.style

import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

class WalkSpeedScaleTest {
    private val json = File("src/main/assets/walk-style-v1.json").readText()

    private fun checkScale(policy: WalkStylePolicy) {
        for (theme in policy.themes) {
            val stops = policy.speedScaleStops(theme.id)
            assertEquals(0f, stops.first().first, 0f)
            assertEquals(1f, stops.last().first, 0f)
            assertTrue(stops.zipWithNext().all { (a, b) -> a.first < b.first })
            // Sample the resulting gradient as a renderer would, and compare its
            // meaning to the actual route policy, including every narrow blend.
            for (i in 0..2000) {
                val fraction = i / 2000.0
                val (a, b) = stops.zipWithNext().first { fraction <= it.second.first }
                val ratio = (fraction - a.first) / (b.first - a.first)
                val expected = policy.color(fraction * policy.speedMax, theme.id)
                for (shift in listOf(0, 8, 16)) {
                    val from = (a.second shr shift) and 255
                    val to = (b.second shr shift) and 255
                    val rendered = (from + (to - from) * ratio).roundToInt()
                    assertTrue(abs(rendered - ((expected shr shift) and 255)) <= 1)
                }
            }
        }
    }

    @Test fun `legend matches route colors across every theme and speed`() {
        checkScale(WalkStylePolicy.parse(json))
    }

    @Test fun `uneven remote bands and narrow blends keep their true positions`() {
        val changed = JSONObject(json).put("speed_max_mps", 3.0)
            .put("boundaries_mps", org.json.JSONArray(listOf(.2, .9, 1.2, 2.4)))
            .put("blend_half_width_mps", .01)
        checkScale(WalkStylePolicy.parse(changed.toString()))
    }
}
