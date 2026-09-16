package com.daengs.app.pet

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress

/** `WalkRecordSheetsApiTest` 와 같은 방식으로 내장 HTTP 서버를 향해 실제 요청을 보내 본다. */
class PetMemberApiTest {

    @Test fun `대표를 인증 토큰과 함께 조회한다`() = runBlocking {
        val body = """{
            "pet_id": "p1",
            "members": [
                {"app_user_id": "u1", "nickname": "아빠", "is_owner": true},
                {"app_user_id": "u2", "nickname": null, "is_owner": false}
            ]
        }"""
        val stub = Stub("p1", 200, body)
        try {
            val result = PetMemberApi { stub.base }.listMembers("sample-token", "p1").getOrThrow()
            assertEquals("GET", stub.method)
            assertEquals("Bearer sample-token", stub.authorization)
            assertEquals("p1", result.petId)
            assertEquals(2, result.members.size)
            assertEquals("아빠", result.members[0].nickname)
            assertTrue(result.members[0].isOwner)
            assertNull(result.members[1].nickname)
        } finally {
            stub.stop()
        }
    }

    @Test fun `구성원이 아니면 404를 실패로 돌려준다`() = runBlocking {
        val stub = Stub("p1", 404, """{"detail": "강아지를 찾을 수 없습니다."}""")
        try {
            val result = PetMemberApi { stub.base }.listMembers("sample-token", "p1")
            assertTrue(result.isFailure)
            assertEquals("강아지를 찾을 수 없습니다.", result.exceptionOrNull()?.message)
        } finally {
            stub.stop()
        }
    }

    // -- 내보내기 / 나가기 (`DELETE .../members/{uid}`) ---------------------------

    /**
     * **요청이 카드의 표시 행 id 로 나가야 한다.** 목록을 받아 온 것과 다른 id 로 보내면
     * 방금 본 명단에서 뺀 사람이 그 명단에서 안 빠진다.
     */
    @Test fun `내보내기는 표시 행 id 와 대상 id 로 DELETE 한다`() = runBlocking {
        val stub = DeleteStub("display-1", "u2", 204, "")
        try {
            val result = PetMemberApi { stub.base }.remove("sample-token", "display-1", "u2")
            assertTrue(result.isSuccess)
            assertEquals("DELETE", stub.method)
            assertEquals("/app/pets/display-1/members/u2", stub.path)
            assertEquals("Bearer sample-token", stub.authorization)
        } finally {
            stub.stop()
        }
    }

    /** 나가기도 **같은 요청**이다 — 대상이 나일 뿐이다. */
    @Test fun `나가기는 내 id 로 같은 경로에 DELETE 한다`() = runBlocking {
        val stub = DeleteStub("display-1", "me", 204, "")
        try {
            assertTrue(PetMemberApi { stub.base }.remove("sample-token", "display-1", "me").isSuccess)
            assertEquals("/app/pets/display-1/members/me", stub.path)
        } finally {
            stub.stop()
        }
    }

    /** 저쪽이 사용자에게 보여 줄 문장으로 `detail` 을 써 놨다 — 앱이 다시 짓지 않는다. */
    @Test fun `403 404 409 는 서버 문장을 그대로 돌려준다`() = runBlocking {
        val cases = listOf(
            403 to "주보호자만 다른 보호자를 내보낼 수 있습니다.",
            404 to "강아지를 찾을 수 없습니다.",
            409 to "주보호자는 나갈 수 없습니다. 먼저 대표를 넘겨 주세요.",
        )
        for ((status, detail) in cases) {
            val stub = DeleteStub("p1", "u2", status, """{"detail": "$detail"}""")
            try {
                val result = PetMemberApi { stub.base }.remove("sample-token", "p1", "u2")
                assertTrue("$status 는 실패여야 한다", result.isFailure)
                assertEquals(detail, result.exceptionOrNull()?.message)
            } finally {
                stub.stop()
            }
        }
    }

    /** 연결이 안 되면 영어 한 줄이 화면에 뜨면 안 된다 — 목록 조회와 같은 규칙이다. */
    @Test fun `서버에 못 닿으면 우리 문장으로 바꾼다`() = runBlocking {
        val result = PetMemberApi { "http://127.0.0.1:1" }.remove("sample-token", "p1", "u2")

        assertTrue(result.isFailure)
        assertEquals("서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.", result.exceptionOrNull()?.message)
    }

    private class DeleteStub(petId: String, targetId: String, status: Int, response: String) {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val base get() = "http://127.0.0.1:${server.address.port}"
        var method: String? = null
        var path: String? = null
        var authorization: String? = null

        init {
            server.createContext("/app/pets/$petId/members/$targetId") { exchange ->
                method = exchange.requestMethod
                path = exchange.requestURI.path
                authorization = exchange.requestHeaders.getFirst("Authorization")
                val bytes = response.toByteArray(Charsets.UTF_8)
                // 204 는 본문이 없다 — 길이를 실으면 저쪽 구현과 달라진다.
                if (bytes.isEmpty()) {
                    exchange.sendResponseHeaders(status, -1)
                    exchange.close()
                } else {
                    exchange.sendResponseHeaders(status, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
            }
            server.start()
        }

        fun stop() = server.stop(0)
    }

    private class Stub(petId: String, status: Int, response: String) {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val base get() = "http://127.0.0.1:${server.address.port}"
        var method: String? = null
        var authorization: String? = null

        init {
            server.createContext("/app/pets/$petId/members") { exchange ->
                method = exchange.requestMethod
                authorization = exchange.requestHeaders.getFirst("Authorization")
                val bytes = response.toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
        }

        fun stop() = server.stop(0)
    }
}
