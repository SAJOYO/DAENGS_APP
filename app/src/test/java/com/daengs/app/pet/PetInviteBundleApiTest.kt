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
 * 묶음 초대 API 를 내장 HTTP 서버에 붙여 본다.
 *
 * 저쪽 계약은 `routers/pet_member.py` 의 `/app/pet-invites` 넷이다 — 생성·목록·취소·미리보기.
 * **경로에 `pet_id` 가 없다** — 묶음은 강아지 하나에 매이지 않는다.
 */
class PetInviteBundleApiTest {

    private val inviteToken = "abc_DEF-123"

    @Test
    fun `생성은 pet_ids 배열을 담아 보낸다`() = runTest {
        val stub = Stub(200, """{"id":"i1","pet_ids":["p1","p2"],"token":"$inviteToken","expires_at":"2026-09-13T00:00:00Z"}""")
        try {
            val created = PetInviteBundleApi { stub.base }.create("sample", listOf("p1", "p2")).getOrThrow()

            assertEquals("POST", stub.method)
            assertEquals("/app/pet-invites", stub.path)
            assertEquals("Bearer sample", stub.authorization)
            assertEquals(listOf("p1", "p2"), stub.body!!.getJSONArray("pet_ids").let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            })
            assertEquals(listOf("p1", "p2"), created.petIds)
        } finally {
            stub.stop()
        }
    }

    @Test
    fun `목록은 담긴 아이들과 함께 온다`() = runTest {
        val stub = Stub(
            200,
            """
            {"invites":[{"id":"i1","pets":[{"pet_id":"p1","name":"롱이"},{"pet_id":"p2","name":"몽이"}],
             "created_at":"2026-09-12T00:00:00Z","expires_at":"2026-09-13T00:00:00Z","accepted_at":null}]}
            """.trimIndent(),
        )
        try {
            val list = PetInviteBundleApi { stub.base }.list("sample").getOrThrow()

            assertEquals("GET", stub.method)
            assertEquals(listOf("롱이", "몽이"), list.invites[0].pets.map { it.name })
        } finally {
            stub.stop()
        }
    }

    /** 취소는 묶음 단위다 — 담긴 아이를 골라 뺄 수 없다. */
    @Test
    fun `취소는 묶음 id 로 보내고 204 를 받는다`() = runTest {
        val stub = Stub(204, "")
        try {
            assertTrue(PetInviteBundleApi { stub.base }.cancel("sample", "i1").isSuccess)

            assertEquals("DELETE", stub.method)
            assertEquals("/app/pet-invites/i1", stub.path)
        } finally {
            stub.stop()
        }
    }

    /** 토큰을 URL 에 실으면 액세스 로그·Referer 에 평문이 남는다. */
    @Test
    fun `미리보기는 토큰을 본문으로 보낸다`() = runTest {
        val stub = Stub(200, PREVIEW)
        try {
            val outcome = PetInviteBundleApi { stub.base }.preview("sample", inviteToken)

            assertEquals("/app/pet-invites/preview", stub.path)
            assertFalse("경로에 토큰이 있으면 안 된다", stub.rawQuery.orEmpty().contains(inviteToken))
            assertEquals(inviteToken, stub.body!!.getString("token"))
            assertTrue(outcome is PreviewOutcome.Ready)
        } finally {
            stub.stop()
        }
    }

    /**
     * **구 서버와 없는 토큰을 갈라야 한다.** 구 서버면 새 계약을 쓰면 안 되고(선택이 조용히
     * 무시된다), 없는 토큰이면 사용자에게 링크가 잘못됐다고 말해야 한다.
     */
    @Test
    fun `404 를 경로 없음과 없는 토큰으로 가른다`() = runTest {
        val missingRoute = Stub(404, """{"detail":"Not Found"}""")
        try {
            assertEquals(
                PreviewOutcome.Unsupported,
                PetInviteBundleApi { missingRoute.base }.preview("sample", inviteToken),
            )
        } finally {
            missingRoute.stop()
        }

        val noToken = Stub(404, """{"detail":"초대를 찾을 수 없습니다."}""")
        try {
            assertEquals(
                PreviewOutcome.NotFound,
                PetInviteBundleApi { noToken.base }.preview("sample", inviteToken),
            )
        } finally {
            noToken.stop()
        }
    }

    /** 못 가르면 안전한 쪽으로 — 새 계약을 안 쓰는 편이 선택이 사라지는 것보다 낫다. */
    @Test
    fun `본문이 비어 있는 404 는 경로 없음으로 본다`() = runTest {
        val stub = Stub(404, "")
        try {
            assertEquals(
                PreviewOutcome.Unsupported,
                PetInviteBundleApi { stub.base }.preview("sample", inviteToken),
            )
        } finally {
            stub.stop()
        }
    }

    @Test
    fun `410 은 만료로 가른다`() = runTest {
        val stub = Stub(410, """{"detail":"만료된 초대입니다."}""")
        try {
            assertEquals(PreviewOutcome.Expired, PetInviteBundleApi { stub.base }.preview("sample", inviteToken))
        } finally {
            stub.stop()
        }
    }

    @Test
    fun `상한을 넘으면 서버가 준 문장을 그대로 쓴다`() = runTest {
        val stub = Stub(409, """{"detail":"살아 있는 초대는 3개까지입니다."}""")
        try {
            val failure = PetInviteBundleApi { stub.base }.create("sample", listOf("p1"))

            assertTrue(failure.isFailure)
            assertEquals("살아 있는 초대는 3개까지입니다.", failure.exceptionOrNull()?.message)
        } finally {
            stub.stop()
        }
    }

    /** 어떤 실패에서도 토큰이 오류 문구에 실리면 안 된다. */
    @Test
    fun `실패 문구에 토큰이 들어가지 않는다`() = runTest {
        val stub = Stub(500, """{"detail":"서버 오류"}""")
        try {
            val outcome = PetInviteBundleApi { stub.base }.preview("sample", inviteToken)

            assertFalse(outcome.toString().contains(inviteToken))
        } finally {
            stub.stop()
        }
    }

    private class Stub(status: Int, response: String) {
        private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val base get() = "http://127.0.0.1:${server.address.port}"
        var method: String? = null
        var path: String? = null
        var rawQuery: String? = null
        var authorization: String? = null
        var body: JSONObject? = null

        init {
            server.createContext("/app/pet-invites") { exchange ->
                method = exchange.requestMethod
                path = exchange.requestURI.path
                rawQuery = exchange.requestURI.rawQuery
                authorization = exchange.requestHeaders.getFirst("Authorization")
                val raw = exchange.requestBody.bufferedReader().use { it.readText() }
                body = raw.takeIf(String::isNotBlank)?.let { JSONObject(it) }
                val bytes = response.toByteArray(Charsets.UTF_8)
                // 204 는 본문을 실으면 안 된다 — -1 이 "본문 없음" 이다.
                exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1L else bytes.size.toLong())
                if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
        }

        fun stop() = server.stop(0)
    }

    private companion object {
        const val PREVIEW = """
        {"invited_by_nickname":"네옹집사","expires_at":"2026-09-13T00:00:00Z",
         "pets":[{"pet_id":"p1","name":"롱이","already_member":false}],
         "link_candidates":[]}
        """
    }
}
