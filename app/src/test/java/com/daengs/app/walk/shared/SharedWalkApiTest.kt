package com.daengs.app.walk.shared

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetSocketAddress

/** `PetMemberApiTest` 와 같은 방식으로 내장 HTTP 서버를 향해 실제 요청을 보내 본다. 토큰은 가짜다. */
class SharedWalkApiTest {

    @Test fun `목록은 개수와 커서를 싣고 인증 토큰으로 부른다`() = runBlocking {
        val stub = Stub(200, PAGE)
        try {
            val result = SharedWalkApi { stub.base }.list("sample-token", "p1", cursor = "abc_-1", limit = 20)

            assertEquals("GET", stub.method)
            assertEquals("Bearer sample-token", stub.authorization)
            assertEquals("/app/pets/p1/walks", stub.path)
            assertEquals("limit=20&cursor=abc_-1", stub.query)
            val page = (result as SharedWalkResult.Ready).value
            assertEquals(listOf("w1"), page.walks.map { it.id })
        } finally {
            stub.stop()
        }
    }

    @Test fun `첫 페이지는 커서 없이 부른다`() = runBlocking {
        val stub = Stub(200, PAGE)
        try {
            SharedWalkApi { stub.base }.list("sample-token", "p1", cursor = null)

            assertEquals("limit=$SHARED_WALK_PAGE_LIMIT", stub.query)
        } finally {
            stub.stop()
        }
    }

    /** 공동 조회 전 서버는 경로 자체가 없다 — FastAPI 라우팅 404 는 `"Not Found"` 한 줄이다. */
    @Test fun `경로가 없는 옛 서버의 404 는 미지원이다`() = runBlocking {
        val stub = Stub(404, """{"detail": "Not Found"}""")
        try {
            assertEquals(SharedWalkResult.Unsupported, SharedWalkApi { stub.base }.list("sample-token", "p1", null))
        } finally {
            stub.stop()
        }
    }

    /** 구성원이 아니거나 나갔으면 서버가 한국어 문장으로 404 를 준다 — 그대로 들고 온다. */
    @Test fun `볼 수 없는 강아지의 404 는 서버 문장 그대로다`() = runBlocking {
        val stub = Stub(404, """{"detail": "강아지를 찾을 수 없습니다."}""")
        try {
            assertEquals(
                SharedWalkResult.NotFound("강아지를 찾을 수 없습니다."),
                SharedWalkApi { stub.base }.list("sample-token", "p1", null),
            )
        } finally {
            stub.stop()
        }
    }

    @Test fun `서버 오류는 빈 목록이 아니라 실패다`() = runBlocking {
        val stub = Stub(500, "{}")
        try {
            assertEquals(SharedWalkResult.Failed("서버 오류 (500)"), SharedWalkApi { stub.base }.list("sample-token", "p1", null))
        } finally {
            stub.stop()
        }
    }

    @Test fun `상세는 산책 id 경로로 부르고 좌표를 읽는다`() = runBlocking {
        val stub = Stub(200, DETAIL)
        try {
            val result = SharedWalkApi { stub.base }.detail("sample-token", "p1", "w1")

            assertEquals("/app/pets/p1/walks/w1", stub.path)
            val detail = (result as SharedWalkResult.Ready).value
            assertEquals("w1", detail.walk.id)
            assertEquals(1, detail.points.size)
        } finally {
            stub.stop()
        }
    }

    @Test fun `그룹 밖 산책의 상세는 서버 문장 그대로 없다고 한다`() = runBlocking {
        val stub = Stub(404, """{"detail": "산책 기록을 찾을 수 없습니다."}""")
        try {
            assertEquals(
                SharedWalkResult.NotFound("산책 기록을 찾을 수 없습니다."),
                SharedWalkApi { stub.base }.detail("sample-token", "p1", "other-walk"),
            )
        } finally {
            stub.stop()
        }
    }

    private class Stub(status: Int, response: String) {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val base get() = "http://127.0.0.1:${server.address.port}"
        var method: String? = null
        var authorization: String? = null
        var path: String? = null
        var query: String? = null

        init {
            server.createContext("/app/pets/") { exchange ->
                method = exchange.requestMethod
                authorization = exchange.requestHeaders.getFirst("Authorization")
                path = exchange.requestURI.path
                query = exchange.requestURI.rawQuery
                val bytes = response.toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
        }

        fun stop() = server.stop(0)
    }

    private companion object {
        const val WALK = """
            {"id": "w1", "started_at": "2025-09-01T08:00:00+09:00", "ended_at": "2025-09-01T08:40:00+09:00",
             "duration_s": 2400, "distance_m": 1234, "moving_s": 2100,
             "actor": {"app_user_id": "u2", "nickname": "키키"}, "is_mine": false, "pet_ids": ["p1"]"""
        const val PAGE = """{"pet_id": "p1", "walks": [$WALK}], "next_cursor": null}"""
        const val DETAIL = """$WALK, "points": [{"at": "2025-09-01T08:00:00+09:00", "lat": "37.5665", "lng": "126.978"}]}"""
    }
}
