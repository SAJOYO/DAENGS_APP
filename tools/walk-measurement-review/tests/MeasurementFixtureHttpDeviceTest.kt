package com.daengs.app.ui.walk

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class MeasurementFixtureHttpDeviceTest {
    @Test fun rejectsWrongMethodIdentityVersionAndChunkWithoutStoppingTheServer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cases = context.assets.open("walk/walk-measurement-v1.json").bufferedReader().use {
            JSONObject(it.readText()).getJSONArray("cases")
        }
        val wire = (0 until cases.length()).map(cases::getJSONObject).single { it.getString("name") == "high-speed-reentry" }
        val summary = JSONObject(wire.getString("summary"))
        val base = "/app/walks/${summary.getString("walk_id")}/measurements"
        val id = summary.getJSONObject("measurement").getString("measurement_id")
        val version = "?version=${summary.getString("version")}"
        val chunk = "$base/$id/chunks/0$version"
        MeasurementFixtureHttp(wire).use { http ->
            fun request(method: String, path: String): Pair<Int, String> {
                val conn = URL(http.origin + path).openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = method; conn.connectTimeout = 5_000; conn.readTimeout = 5_000
                    val code = conn.responseCode
                    val stream = if (code == 200) conn.inputStream else conn.errorStream
                    return code to stream.bufferedReader().use { it.readText() }
                } finally { conn.disconnect() }
            }
            val invalid = listOf("GET" to "$base$version", "POST" to chunk,
                "GET" to chunk.replace(summary.getString("walk_id"), "wrong-walk"),
                "GET" to chunk.replace(id, "wrong-measurement"),
                "GET" to chunk.replace(version, "?version=unknown"), "GET" to chunk.substringBefore('?'),
                "GET" to chunk.replace("/chunks/0", "/chunks/999"), "GET" to "/unknown")
            invalid.forEach { (method, path) -> assertEquals("$method $path", 404, request(method, path).first) }
            assertEquals(200 to wire.getString("summary"), request("POST", "$base$version"))
            assertEquals(200 to wire.getJSONArray("pages").getString(0), request("GET", chunk))
        }
    }
}
