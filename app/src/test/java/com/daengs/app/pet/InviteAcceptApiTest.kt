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

    // -- 다중 초대 -------------------------------------------------------------

    /**
     * **선택이 없으면 `links` 키를 넣지 않는다.** 옛 계약과 바이트가 같아야 구 서버에서도
     * 뜻이 안 흔들린다 — 빈 배열은 서버가 같게 읽지만 굳이 다르게 보낼 이유가 없다.
     */
    @Test
    fun `선택이 없으면 옛 계약 그대로 토큰만 보낸다`() = runTest {
        val stub = Stub(200, """{"pet_id":"p1","name":"네옹"}""")
        try {
            InviteAcceptApi { stub.base }.accept("t", inviteToken)

            assertEquals(setOf("token"), stub.body.keys().asSequence().toSet())
        } finally {
            stub.stop()
        }
    }

    /** `null` 은 "연결 없이 참여" 라는 **선택**이다. 키를 빼면 뜻이 달라진다. */
    @Test
    fun `연결 선택을 항목마다 싣고 null 도 값으로 보낸다`() = runTest {
        val stub = Stub(200, """{"pet_id":"m1","name":"롱롱씨","pets":[]}""")
        try {
            InviteAcceptApi { stub.base }.accept(
                "t",
                inviteToken,
                listOf(InviteLinkChoice("p1", "m1"), InviteLinkChoice("p2", null)),
            )

            val links = stub.body.getJSONArray("links")
            assertEquals(2, links.length())
            assertEquals("p1", links.getJSONObject(0).getString("pet_id"))
            assertEquals("m1", links.getJSONObject(0).getString("link_to_pet_id"))
            assertEquals("p2", links.getJSONObject(1).getString("pet_id"))
            assertTrue("키가 있어야 한다", links.getJSONObject(1).has("link_to_pet_id"))
            assertTrue("값은 JSON null 이다", links.getJSONObject(1).isNull("link_to_pet_id"))
        } finally {
            stub.stop()
        }
    }

    @Test
    fun `성공 응답의 항목별 결과를 읽는다`() = runTest {
        val stub = Stub(
            200,
            """
            {"pet_id":"m1","name":"롱롱씨",
             "pets":[{"invited_pet_id":"p1","display_pet_id":"m1","name":"롱롱씨","result":"linked"},
                     {"invited_pet_id":"p2","display_pet_id":"p2","name":"몽이","result":"joined"}]}
            """.trimIndent(),
        )
        try {
            val joined = InviteAcceptApi { stub.base }.accept("t", inviteToken) as AcceptOutcome.Joined

            assertEquals(2, joined.pet.pets.size)
            assertEquals("m1", joined.pet.pets[0].displayPetId)
            assertEquals(AcceptResult.JOINED, joined.pet.pets[1].result)
        } finally {
            stub.stop()
        }
    }

    /** 구 앱이 묶음을 조용히 수락하는 것을 막는 자리. 새 앱에서는 나오면 안 되는 오류다. */
    @Test
    fun `선택 누락 409 를 code 로 가른다`() = runTest {
        val stub = Stub(
            409,
            """
            {"detail":{"code":"link_selection_required","message":"이 초대에는 아이가 여러 마리예요.",
                       "missing_pet_ids":["p1","p2"]}}
            """.trimIndent(),
        )
        try {
            val conflict = InviteAcceptApi { stub.base }.accept("t", inviteToken) as AcceptOutcome.Conflict

            assertEquals(InviteErrorCode.LINK_SELECTION_REQUIRED, conflict.code)
            assertEquals(listOf("p1", "p2"), conflict.missingPetIds)
        } finally {
            stub.stop()
        }
    }

    @Test
    fun `부적격 연결 409 는 사유를 함께 읽는다`() = runTest {
        val stub = Stub(
            409,
            """
            {"detail":{"code":"link_not_allowed","message":"선택한 아이는 연결할 수 없어요.",
                       "pet_id":"p1","link_to_pet_id":"m1","reason":"has_other_members"}}
            """.trimIndent(),
        )
        try {
            val conflict = InviteAcceptApi { stub.base }.accept("t", inviteToken) as AcceptOutcome.Conflict

            assertEquals(InviteErrorCode.LINK_NOT_ALLOWED, conflict.code)
            assertEquals("has_other_members", conflict.reason)
        } finally {
            stub.stop()
        }
    }

    /** 고른 내 강아지에 다른 공동 보호자가 있으면 서버가 `has_other_carers` 로 막는다 — 어느 줄인지도 읽는다. */
    @Test
    fun `다른 공동 보호자 때문에 막힌 연결은 거절된 초대 강아지 id 와 함께 읽는다`() = runTest {
        val stub = Stub(
            409,
            """
            {"detail":{"code":"link_not_allowed","message":"선택한 아이는 연결할 수 없어요.",
                       "pet_id":"p2","link_to_pet_id":"m1","reason":"has_other_carers"}}
            """.trimIndent(),
        )
        try {
            val conflict = InviteAcceptApi { stub.base }.accept("t", inviteToken) as AcceptOutcome.Conflict

            assertEquals("p2", conflict.petId)
            assertTrue(conflict.linkBlockedByOtherCarers)
        } finally {
            stub.stop()
        }
    }

    @Test
    fun `다른 사유의 연결 거절은 공동 보호자 안내로 보지 않는다`() {
        val conflict = AcceptOutcome.Conflict("x", InviteErrorCode.LINK_NOT_ALLOWED, reason = "farewelled", petId = "p1")

        assertFalse(conflict.linkBlockedByOtherCarers)
    }

    /** 422 는 상한 409 와 다른 뜻이다 — 다시 눌러도 같고, 불러오기부터 다시 해야 한다. */
    @Test
    fun `422 는 Conflict 가 아니라 Invalid 로 가른다`() = runTest {
        val stub = Stub(422, """{"detail":{"code":"duplicate_link_target","message":"같은 아이를 두 번 골랐어요."}}""")
        try {
            val invalid = InviteAcceptApi { stub.base }.accept("t", inviteToken) as AcceptOutcome.Invalid

            assertEquals(InviteErrorCode.DUPLICATE_LINK_TARGET, invalid.code)
            assertEquals("같은 아이를 두 번 골랐어요.", invalid.message)
        } finally {
            stub.stop()
        }
    }

    /** 상한 409 는 `code` 가 없는 문장 한 줄이다. 그때도 문구는 서버 것을 쓴다. */
    @Test
    fun `code 없는 409 도 문장을 그대로 쓴다`() = runTest {
        val stub = Stub(409, """{"detail":"돌보는 아이가 너무 많습니다."}""")
        try {
            val conflict = InviteAcceptApi { stub.base }.accept("t", inviteToken) as AcceptOutcome.Conflict

            assertEquals(null, conflict.code)
            assertEquals("돌보는 아이가 너무 많습니다.", conflict.message)
        } finally {
            stub.stop()
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
