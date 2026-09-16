package com.daengs.app.walk.diary

import com.daengs.app.walk.support.diaryBoardFixture
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class DiarySceneTemperatureTest {
    private val at = Instant.parse("2026-09-14T10:01:00Z").toEpochMilli()
    private fun temperature() = JSONObject("""{"temperature_c":0.0,"unit":"celsius",
        "provider":"kma-vilage-fcst:ncst","observed_at":"2026-09-14T10:00:00Z",
        "scene_at":"2026-09-14T10:01:00Z","grid":[61,125],"evidence_id":"slot:example"}""")

    @Test fun `zero temperature retains observation provenance`() {
        val value = sceneTemperature(JSONObject().put("temperature", temperature()), at)!!
        assertEquals(0.0, value.celsius, 0.0)
        assertEquals(61, value.gridX)
        assertEquals("slot:example", value.evidenceId)
    }

    @Test fun `missing malformed future stale or another scene never uses session weather`() {
        assertNull(sceneTemperature(JSONObject(), at))
        val cases = listOf(
            "temperature_c" to JSONObject.NULL, "temperature_c" to "20",
            "provider" to "forecast", "unit" to "fahrenheit",
            "observed_at" to "2026-09-14T11:00:00Z",
            "observed_at" to "2026-09-14T07:00:00Z",
            "scene_at" to "2026-09-14T10:02:00Z",
        )
        cases.forEach { (key, value) ->
            assertNull(sceneTemperature(JSONObject().put("temperature", temperature().put(key, value)), at))
        }
    }

    @Test fun `published board connects only the scene carrying temperature`() {
        val response = diaryBoardFixture()
        val scenes = response.getJSONObject("bundle").getJSONArray("scenes")
        val stamp = scenes.getJSONObject(0).getJSONObject("anchor").getString("event_at")
        val observation = Instant.parse(stamp).truncatedTo(java.time.temporal.ChronoUnit.HOURS)
        scenes.getJSONObject(0).put("temperature", temperature().put("scene_at", stamp)
            .put("observed_at", observation.toString()).put("temperature_c", -2.3))
        val parsed = GeoStoryboardBundle.parse(response.toString())
        assertEquals(-2.3, parsed.scenes.first().diary!!.temperatureC!!, 0.0)
        assertNotNull(parsed.scenes.first().diary!!.temperatureObservation)
        assertTrue(parsed.scenes.drop(1).all { it.diary!!.temperatureC == null })
    }
}
