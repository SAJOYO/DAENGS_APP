package com.daengs.app.walk.diary

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.time.LocalDate

class SpatialDiaryApiTest {
    @Test
    fun `posts an authenticated query to the walk-owned endpoint`() {
        val fixture = javaClass.getResource("/spatial_diary_response.json")!!.readText()
        val stub = Stub(status = 200, response = fixture)
        try {
            val result = runBlocking {
                SpatialDiaryApi { stub.base }.query(
                    accessToken = "access-token",
                    query = SpatialDiaryQuery(
                        petId = "11111111-1111-1111-1111-111111111111",
                        since = LocalDate.parse("2026-09-01"),
                        until = LocalDate.parse("2026-09-30"),
                        contextFilters = listOf(
                            PrecipitationFilter(setOf(SpatialDiaryPrecipitation.RAIN)),
                            DaylightFilter(setOf(SpatialDiaryDaylight.NIGHT)),
                        ),
                    ),
                )
            }.getOrThrow()

            assertEquals("/app/walks/spatial-diary/views/query", stub.path)
            assertEquals("Bearer access-token", stub.authorization)
            assertEquals("11111111-1111-1111-1111-111111111111", stub.body
                .getJSONObject("walk_selector").getString("pet_id"))
            assertEquals(2, result.field.cells.size)
        } finally {
            stub.stop()
        }
    }

    @Test
    fun `preserves status and code when the selected field is too large`() {
        val stub = Stub(
            status = 413,
            response = """{"detail":{"code":"spatial_diary_result_cell_limit","message":"필터를 좁혀 주세요."}}""",
        )
        try {
            val failure = runBlocking {
                SpatialDiaryApi { stub.base }.query(
                    accessToken = "access-token",
                    query = SpatialDiaryQuery(petId = "pet"),
                )
            }.exceptionOrNull() as SpatialDiaryHttpException

            assertEquals(413, failure.statusCode)
            assertEquals("spatial_diary_result_cell_limit", failure.code)
            assertTrue(failure.message!!.contains("필터를 좁혀"))
        } finally {
            stub.stop()
        }
    }

    private class Stub(status: Int, response: String) {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val base: String get() = "http://127.0.0.1:${server.address.port}"
        var path: String? = null
        var authorization: String? = null
        lateinit var body: JSONObject

        init {
            server.createContext("/app/walks/spatial-diary/views/query") { exchange ->
                path = exchange.requestURI.path
                authorization = exchange.requestHeaders.getFirst("Authorization")
                body = JSONObject(exchange.requestBody.bufferedReader().use { it.readText() })
                val bytes = response.toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
        }

        fun stop() = server.stop(0)
    }
}
