package com.daengs.app.pet

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
            assertEquals("아빠", result.members[0].identity.displayName)
            assertEquals("이전 보호자", result.members[1].identity.displayName)
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
