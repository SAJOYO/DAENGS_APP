package com.daengs.app.map.style

import com.daengs.app.location.GeoPoint
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WalkStylePolicyTest {
    private val json = File("src/main/assets/walk-style-v1.json").readText()
    private val policy = WalkStylePolicy.parse(json)
    private fun point(meters: Double, time: Long) = WalkSpeedPoint(GeoPoint(37.5 + meters / 111195.0, 127.0), time)

    @Test fun fiveBandsBlendAtBoundariesInEveryTheme() {
        for (theme in policy.themes) {
            assertEquals(theme.colors, (0..4).map { policy.color(it * .4 + .2, theme.id) })
            for (boundary in policy.boundaries) {
                val a = policy.color(boundary - 1e-8, theme.id)
                val b = policy.color(boundary + 1e-8, theme.id)
                for (shift in listOf(16, 8, 0)) assertTrue(kotlin.math.abs(((a shr shift) and 255) - ((b shr shift) and 255)) <= 1)
            }
            assertEquals(theme.colors.last(), policy.color(5.0, theme.id))
        }
    }
    @Test fun unknownSpeedAndUnsupportedPolicyAreNotSilentlyAccepted() {
        for (speed in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, -1.0)) assertEquals(policy.unknownColor, policy.color(speed, "pink"))
        assertEquals("pink", policy.theme("gone").id)
        for (bad in listOf(JSONObject(json).put("version", 2), JSONObject(json).put("blend_half_width_mps", .3))) {
            assertTrue(runCatching { WalkStylePolicy.parse(bad.toString()) }.isFailure)
        }
    }
    @Test fun renderPartsKeepTheExactRouteAndNeverMutateSamples() {
        val path = listOf(point(0.0, 0), point(2.0, 2000), point(6.0, 4000))
        val before = path.toList()
        val parts = paintWalkSpeedPath(path, policy, "pink")
        assertEquals(before, path)
        assertEquals(path.first().point, parts.first().points.first())
        assertEquals(path.last().point, parts.last().points.last())
        assertTrue(parts.zipWithNext().all { (a, b) -> a.points.last() == b.points.first() })
        assertTrue(parts.flatMap { it.points }.all { it.longitude == 127.0 })
        assertTrue(parts.any { it.points.last() == path[1].point })
        assertNotEquals(parts.map { it.color }, paintWalkSpeedPath(path, policy, "blue").map { it.color })
    }
    @Test fun zeroLengthAndInvalidTimeDoNotInventSpeed() {
        assertTrue(paintWalkSpeedPath(listOf(point(0.0, 0)), policy, "pink").isEmpty())
        assertTrue(paintWalkSpeedPath(listOf(point(0.0, 0), point(0.0, 2000)), policy, "pink").isEmpty())
        for (time in listOf(0L, 100L, 15000L)) {
            assertEquals(policy.unknownColor, paintWalkSpeedPath(listOf(point(0.0, 0), point(2.0, time)), policy, "pink").single().color)
        }
    }
}
