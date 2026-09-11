package com.daengs.app.pet

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress

/**
 * 수락 API 를 내장 HTTP 서버에 붙여 본다 (`PetInviteHolderTest` 와 같은 방식).
 *
 * 저쪽 계약은 `routers/pet_member.py` 의 `POST /app/pet-invites/accept` 다 —
 * 200 / 404 / 410 / 409(세 종류).
 */
class InviteAcceptApiTest {

    private val inviteToken = "abc_DEF-123"

    @Test
    fun `성공하면 pet_id 와 이름을 읽는다`() = runTest {
        val stub = Stub(200, """{"pet_id":"p1","name":"네옹"}""")
        try {
            val outcome = InviteAcceptApi { stub.base }.accept("sample-token", inviteToken)

            val joined = outcome as AcceptOutcome.Joined
            assertEquals("p1", joined.pet.petId)
            assertEquals("네옹", joined.pet.name)
        } finally {
            stub.stop()
        }
    }

    /** 토큰은 본문에만 실린다. 경로에 `pet_id` 가 없는 것이 계약이다. */
    @Test
    fun `토큰을 본문에 담아 보내고 경로에는 아이 정보를 싣지 않는다`() = runTest {
        val stub = Stub(200, """{"pet_id":"p1","name":"네옹"}""")
        try {
            InviteAcceptApi { stub.base }.accept("sample-token", inviteToken)

            assertEquals("POST", stub.method)
            assertEquals("/app/pet-invites/accept", stub.path)
            assertEquals("Bearer sample-token", stub.authorization)
            assertEquals(setOf("token"), stub.body.keys().asSequence().toSet())
            assertEquals(inviteToken, stub.body.getString("token"))
        } finally {
            stub.stop()
        }
    }

    @Test
    fun `404 는 없는 링크로 가른다`() = runTest {
        val stub = Stub(404, """{"detail":"초대를 찾을 수 없습니다."}""")
        try {
            assertEquals(AcceptOutcome.NotFound, InviteAcceptApi { stub.base }.accept("t", inviteToken))
        } finally {
            stub.stop()
        }
    }

    @Test
    fun `410 은 만료로 가른다`() = runTest {
        val stub = Stub(410, """{"detail":"만료된 초대입니다. 새 초대를 요청하세요."}""")
        try {
            assertEquals(AcceptOutcome.Expired, InviteAcceptApi { stub.base }.accept("t", inviteToken))
        } finally {
            stub.stop()
        }
    }

    /** 409 는 세 종류인데 앱이 다시 가르지 않는다 — 서버 문장을 그대로 쓴다. */
    @Test
    fun `409 세 가지 문장을 그대로 전달한다`() = runTest {
        val messages = listOf(
            "이미 이 아이의 대표입니다.",
            "한 아이의 보호자는 5명까지입니다.",
            "돌보는 아이가 너무 많습니다.",
        )
        for (message in messages) {
            val stub = Stub(409, JSONObject().put("detail", message).toString())
            try {
                val outcome = InviteAcceptApi { stub.base }.accept("t", inviteToken)

                assertEquals(message, (outcome as AcceptOutcome.Conflict).message)
            } finally {
                stub.stop()
            }
        }
    }

    @Test
    fun `서버에 닿지 못하면 다시 시도할 수 있는 실패다`() = runTest {
        val outcome = InviteAcceptApi { "http://127.0.0.1:1" }.accept("t", inviteToken)

        val failed = outcome as AcceptOutcome.Failed
        assertEquals("서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.", failed.message)
    }

    @Test
    fun `서버 주소가 없으면 요청하지 않는다`() = runTest {
        val outcome = InviteAcceptApi { "" }.accept("t", inviteToken)

        assertTrue(outcome is AcceptOutcome.Failed)
    }

    /** 실패 문구에 토큰이 섞이면 화면·로그에 그대로 남는다. */
    @Test
    fun `어떤 응답에서도 결과에 토큰이 들어가지 않는다`() = runTest {
        val cases = listOf(
            Stub(404, """{"detail":"초대를 찾을 수 없습니다."}"""),
            Stub(410, """{"detail":"만료된 초대입니다."}"""),
            Stub(409, """{"detail":"이미 이 아이의 대표입니다."}"""),
            Stub(500, """{"detail":"서버 오류"}"""),
        )
        for (stub in cases) {
            try {
                val outcome = InviteAcceptApi { stub.base }.accept("t", inviteToken)

                assertFalse("결과 문자열에 토큰이 있으면 안 된다", outcome.toString().contains(inviteToken))
            } finally {
                stub.stop()
            }
        }
    }

    private class Stub(status: Int, response: String) {
        private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val base get() = "http://127.0.0.1:${server.address.port}"
        var method: String? = null
        var path: String? = null
        var authorization: String? = null
        lateinit var body: JSONObject

        init {
            server.createContext("/app/pet-invites/accept") { exchange ->
                method = exchange.requestMethod
                path = exchange.requestURI.path
                authorization = exchange.requestHeaders.getFirst("Authorization")
                body = JSONObject(exchange.requestBody.bufferedReader().use { it.readText() })
                val bytes = response.toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
        }

        fun stop() = server.stop(0)
    }
}
