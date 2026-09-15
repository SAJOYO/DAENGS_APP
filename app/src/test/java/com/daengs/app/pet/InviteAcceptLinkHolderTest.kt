package com.daengs.app.pet

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress

/**
 * 미리보기 → 아이마다 고르기 → 한 번에 수락.
 *
 * 단일 초대 시절의 [InviteAcceptHolderTest] 는 그대로 두고, 여기서 다중 초대에서만
 * 생기는 규칙을 본다 — 선택을 다 해야 수락되고, 같은 아이를 두 번 못 걸고, **미리보기를
 * 못 받으면 선택을 아예 안 보낸다.**
 */
class InviteAcceptLinkHolderTest {

    private val token = "abc_DEF-123"
    private val link = "https://daengapi.weareithero.cloud/invite#$token"

    private fun holder(stub: Stub) =
        InviteAcceptHolder(InviteAcceptApi { stub.base }, PetInviteBundleApi { stub.base })

    private suspend fun ready(stub: Stub, body: String = TWO_PETS): InviteAcceptHolder {
        stub.preview(200, body)
        val holder = holder(stub)
        holder.paste(link)
        holder.loadPreview("t")
        return holder
    }

    // -- 미리보기 ---------------------------------------------------------------

    @Test
    fun `미리보기로 초대된 아이와 후보를 받는다`() = runTest {
        val stub = Stub()
        try {
            val holder = ready(stub)

            assertEquals(listOf("롱이", "몽이"), holder.invite?.pets?.map { it.name })
            assertEquals(listOf("롱롱씨"), holder.invite?.linkCandidates?.map { it.name })
            assertTrue(holder.previewed)
        } finally {
            stub.stop()
        }
    }

    /** 새로 붙여넣으면 앞 초대의 미리보기와 선택이 남아 있으면 안 된다. */
    @Test
    fun `다시 붙여넣으면 미리보기와 선택을 버린다`() = runTest {
        val stub = Stub()
        try {
            val holder = ready(stub)
            holder.choose("p1", PetChoice.Join)

            holder.paste("https://daengapi.weareithero.cloud/invite#zzz_YYY-999")

            assertNull(holder.preview)
            assertTrue(holder.choices.isEmpty())
            assertFalse(holder.previewed)
        } finally {
            stub.stop()
        }
    }

    /** 이미 구성원인 아이는 고를 것이 없다 — 서버가 그냥 지나간다. */
    @Test
    fun `이미 구성원인 아이는 미리 골라 둔다`() = runTest {
        val stub = Stub()
        try {
            val holder = ready(stub, ONE_ALREADY)

            assertEquals(PetChoice.Join, holder.choices["p2"])
            assertNull("아직 안 고른 아이는 비어 있다", holder.choices["p1"])
        } finally {
            stub.stop()
        }
    }

    // -- 고르기 ----------------------------------------------------------------

    /** 묶음에서 선택이 빠지면 서버가 409 로 막는다 — 화면에서 먼저 고르게 한다. */
    @Test
    fun `다 고르기 전에는 수락할 수 없다`() = runTest {
        val stub = Stub()
        try {
            val holder = ready(stub)

            assertFalse(holder.allChosen)
            assertFalse(holder.canAccept)

            holder.choose("p1", PetChoice.Join)
            assertFalse("아직 한 마리 남았다", holder.canAccept)

            holder.choose("p2", PetChoice.Link("m1"))
            assertTrue(holder.allChosen)
            assertTrue(holder.canAccept)
        } finally {
            stub.stop()
        }
    }

    /** 서버가 422 `duplicate_link_target` 으로 막는데, 그때는 어느 줄을 고칠지 알 수 없다. */
    @Test
    fun `같은 기존 아이를 두 항목에 걸 수 없다`() = runTest {
        val stub = Stub()
        try {
            val holder = ready(stub)
            holder.choose("p1", PetChoice.Link("m1"))

            assertFalse(holder.choose("p2", PetChoice.Link("m1")))

            assertNull(holder.choices["p2"])
            assertEquals(setOf("m1"), holder.takenBy("p2"))
            assertTrue("내가 고른 것은 내 줄에서 안 잠긴다", holder.takenBy("p1").isEmpty())
        } finally {
            stub.stop()
        }
    }

    @Test
    fun `같은 줄에서는 골랐던 것을 바꿀 수 있다`() = runTest {
        val stub = Stub()
        try {
            val holder = ready(stub)
            holder.choose("p1", PetChoice.Link("m1"))

            assertTrue(holder.choose("p1", PetChoice.Join))

            assertEquals(PetChoice.Join, holder.choices["p1"])
        } finally {
            stub.stop()
        }
    }

    // -- 수락 ------------------------------------------------------------------

    @Test
    fun `고른 것을 항목마다 실어 보낸다`() = runTest {
        val stub = Stub()
        try {
            val holder = ready(stub)
            holder.choose("p1", PetChoice.Link("m1"))
            holder.choose("p2", PetChoice.Join)
            stub.accept(200, ACCEPTED)

            holder.accept("t")

            val links = stub.acceptBody!!.getJSONArray("links")
            val sent = (0 until links.length()).associate {
                links.getJSONObject(it).getString("pet_id") to
                    links.getJSONObject(it).let { o -> if (o.isNull("link_to_pet_id")) null else o.getString("link_to_pet_id") }
            }
            assertEquals(mapOf("p1" to "m1", "p2" to null), sent)
        } finally {
            stub.stop()
        }
    }

    @Test
    fun `성공하면 항목별 결과를 돌려주고 입력을 비운다`() = runTest {
        val stub = Stub()
        try {
            val holder = ready(stub)
            holder.choose("p1", PetChoice.Join)
            holder.choose("p2", PetChoice.Join)
            stub.accept(200, ACCEPTED)

            val accepted = holder.accept("t")

            assertEquals(listOf("m1", "p2"), accepted?.pets?.map { it.displayPetId })
            assertEquals("", holder.pasted)
            assertNull("성공하면 미리보기도 버린다", holder.preview)
            assertTrue(holder.choices.isEmpty())
        } finally {
            stub.stop()
        }
    }

    /** 링크로 들어와 미리보기를 받고 다 골라도, 수락 요청은 버튼([accept])에서만 나간다. */
    @Test
    fun `링크 진입과 미리보기와 선택만으로는 수락 요청이 안 나간다`() = runTest {
        val stub = Stub()
        try {
            stub.preview(200, TWO_PETS)
            stub.accept(200, ACCEPTED)
            val holder = holder(stub)

            holder.acceptFromLink(token)
            holder.loadPreview("t")
            holder.choose("p1", PetChoice.Join)
            holder.choose("p2", PetChoice.Link("m1"))

            assertTrue(holder.canAccept)
            assertEquals(0, stub.acceptCalls)
        } finally {
            stub.stop()
        }
    }

    // -- 다른 공동 보호자 때문에 막힌 연결 (has_other_carers) ---------------------------

    private val blockedLinkBody = """{"detail":{"code":"link_not_allowed","message":"선택한 아이는 연결할 수 없어요.",""" +
        """"pet_id":"p2","link_to_pet_id":"m1","reason":"has_other_carers"}}"""

    /** 「연결 없이 참여」는 막힌 줄만 새 참여로 바꾸고 안내를 닫는다. **수락은 다시 부르지 않는다.** */
    @Test
    fun `연결 없이 참여는 막힌 줄만 새 참여로 바꾸고 다시 수락하지 않는다`() = runTest {
        val stub = Stub()
        try {
            val holder = ready(stub)
            holder.choose("p1", PetChoice.Join)
            holder.choose("p2", PetChoice.Link("m1"))
            stub.accept(409, blockedLinkBody)
            assertNull(holder.accept("t"))
            assertTrue((holder.outcome as AcceptOutcome.Conflict).linkBlockedByOtherCarers)

            holder.joinInsteadOfBlockedLink()

            assertEquals(mapOf("p1" to PetChoice.Join, "p2" to PetChoice.Join), holder.choices)
            assertNull(holder.outcome)
            assertEquals("처음 누른 한 번뿐이다", 1, stub.acceptCalls)
            assertTrue("사용자가 다시 누를 수 있다", holder.canAccept)
        } finally {
            stub.stop()
        }
    }

    @Test
    fun `확인은 안내만 닫고 선택을 그대로 둔다`() = runTest {
        val stub = Stub()
        try {
            val holder = ready(stub)
            holder.choose("p1", PetChoice.Join)
            holder.choose("p2", PetChoice.Link("m1"))
            stub.accept(409, blockedLinkBody)
            holder.accept("t")

            holder.dismissBlockedLink()

            assertEquals(PetChoice.Link("m1"), holder.choices["p2"])
            assertNull(holder.outcome)
            assertEquals(1, stub.acceptCalls)
        } finally {
            stub.stop()
        }
    }

    /** 서버가 어느 줄인지 안 주면 연결로 고른 줄을 전부 새 참여로 바꾼다 — 막힌 연결이 남으면 또 409 다. */
    @Test
    fun `막힌 줄을 모르면 연결로 고른 줄을 전부 새 참여로 바꾼다`() = runTest {
        val stub = Stub()
        try {
            val holder = ready(stub)
            holder.choose("p1", PetChoice.Join)
            holder.choose("p2", PetChoice.Link("m1"))
            stub.accept(409, """{"detail":{"code":"link_not_allowed","message":"x","reason":"has_other_carers"}}""")
            holder.accept("t")

            holder.joinInsteadOfBlockedLink()

            assertEquals(mapOf("p1" to PetChoice.Join, "p2" to PetChoice.Join), holder.choices)
        } finally {
            stub.stop()
        }
    }

    /** 다른 409(상한 등)에는 「연결 없이 참여」가 아무것도 바꾸지 않는다. */
    @Test
    fun `다른 409 에는 연결 없이 참여가 선택을 바꾸지 않는다`() = runTest {
        val stub = Stub()
        try {
            val holder = ready(stub)
            holder.choose("p1", PetChoice.Join)
            holder.choose("p2", PetChoice.Link("m1"))
            stub.accept(409, """{"detail":"돌보는 아이가 너무 많습니다."}""")
            holder.accept("t")

            holder.joinInsteadOfBlockedLink()

            assertEquals(PetChoice.Link("m1"), holder.choices["p2"])
            assertTrue(holder.outcome is AcceptOutcome.Conflict)
        } finally {
            stub.stop()
        }
    }

    /** 다 고르기 전에 눌러도 요청이 안 나가야 한다 — 나가면 서버가 409 를 낸다. */
    @Test
    fun `선택이 빠진 채로는 요청하지 않는다`() = runTest {
        val stub = Stub()
        try {
            val holder = ready(stub)
            holder.choose("p1", PetChoice.Join)
            stub.accept(200, ACCEPTED)

            assertNull(holder.accept("t"))
            assertEquals(0, stub.acceptCalls)
        } finally {
            stub.stop()
        }
    }

    /**
     * ⚠️ **가장 중요한 규칙.** 구 서버는 `links` 를 조용히 무시하고 200 을 내므로,
     * 미리보기를 못 받았으면 선택을 아예 싣지 않고 옛 흐름 그대로 간다.
     */
    @Test
    fun `미리보기를 못 받으면 선택을 보내지 않는다`() = runTest {
        val stub = Stub()
        try {
            stub.preview(404, """{"detail":"Not Found"}""")
            val holder = holder(stub)
            holder.paste(link)
            holder.loadPreview("t")
            assertEquals(PreviewOutcome.Unsupported, holder.preview)
            assertFalse(holder.previewed)

            stub.accept(200, """{"pet_id":"p1","name":"롱이"}""")
            holder.accept("t")

            assertFalse("옛 계약 그대로 토큰만 간다", stub.acceptBody!!.has("links"))
        } finally {
            stub.stop()
        }
    }

    @Test
    fun `미리보기를 못 받아도 옛 흐름으로 수락할 수 있다`() = runTest {
        val stub = Stub()
        try {
            stub.preview(404, """{"detail":"Not Found"}""")
            val holder = holder(stub)
            holder.paste(link)
            holder.loadPreview("t")

            assertTrue("고를 것이 없으니 바로 수락할 수 있다", holder.canAccept)
        } finally {
            stub.stop()
        }
    }

    /** 미리보기가 실패·만료·없는 초대면 눌러도 할 수 있는 일이 없다 — 버튼을 살려 두지 않는다. */
    @Test
    fun `미리보기가 실패하거나 만료되거나 없는 초대면 수락할 수 없다`() = runTest {
        for ((status, body) in listOf(
            500 to """{"detail":"서버 오류"}""",
            410 to """{"detail":"만료된 초대입니다."}""",
            404 to """{"detail":"초대를 찾을 수 없습니다."}""",
        )) {
            val stub = Stub()
            try {
                stub.preview(status, body)
                stub.accept(200, ACCEPTED)
                val holder = holder(stub)
                holder.acceptFromLink(token)

                holder.loadPreview("t")

                assertFalse("미리보기 $status 뒤", holder.canAccept)
                assertEquals(0, stub.acceptCalls)
            } finally {
                stub.stop()
            }
        }
    }

    /** 망이 흔들린 뒤 다시 물어봐서 받으면 그때부터 고르고 누를 수 있다. 다시 묻기만으로 수락은 안 나간다. */
    @Test
    fun `미리보기 실패 뒤 다시 받으면 수락할 수 있게 된다`() = runTest {
        val stub = Stub()
        try {
            stub.preview(500, """{"detail":"서버 오류"}""")
            val holder = holder(stub)
            holder.acceptFromLink(token)
            holder.loadPreview("t")
            assertFalse(holder.canAccept)

            stub.preview(200, TWO_PETS)
            holder.loadPreview("t")
            holder.choose("p1", PetChoice.Join)
            holder.choose("p2", PetChoice.Join)

            assertTrue(holder.canAccept)
            assertEquals(0, stub.acceptCalls)
        } finally {
            stub.stop()
        }
    }

    /** 화면을 닫거나 로그아웃하면 자격증명도 고르던 것도 남으면 안 된다. */
    @Test
    fun `잊으면 미리보기와 선택도 사라진다`() = runTest {
        val stub = Stub()
        try {
            val holder = ready(stub)
            holder.choose("p1", PetChoice.Join)

            holder.forget()

            assertNull(holder.preview)
            assertTrue(holder.choices.isEmpty())
            assertEquals("", holder.pasted)
        } finally {
            stub.stop()
        }
    }

    private class Stub {
        private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val base get() = "http://127.0.0.1:${server.address.port}"
        private var previewStatus = 200
        private var previewBody = ""
        private var acceptStatus = 200
        private var acceptBodyText = ""
        var acceptBody: JSONObject? = null
        var acceptCalls = 0

        fun preview(status: Int, body: String) {
            previewStatus = status
            previewBody = body
        }

        fun accept(status: Int, body: String) {
            acceptStatus = status
            acceptBodyText = body
        }

        init {
            server.createContext("/app/pet-invites/preview") { exchange ->
                exchange.requestBody.bufferedReader().use { it.readText() }
                val bytes = previewBody.toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(previewStatus, if (bytes.isEmpty()) -1L else bytes.size.toLong())
                if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
            }
            server.createContext("/app/pet-invites/accept") { exchange ->
                acceptCalls++
                acceptBody = JSONObject(exchange.requestBody.bufferedReader().use { it.readText() })
                val bytes = acceptBodyText.toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(acceptStatus, if (bytes.isEmpty()) -1L else bytes.size.toLong())
                if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
        }

        fun stop() = server.stop(0)
    }

    private companion object {
        const val TWO_PETS = """
        {"invited_by_nickname":"네옹집사","expires_at":"2026-09-13T00:00:00Z",
         "pets":[{"pet_id":"p1","name":"롱이","already_member":false},
                 {"pet_id":"p2","name":"몽이","already_member":false}],
         "link_candidates":[{"pet_id":"m1","name":"롱롱씨"}]}
        """

        const val ONE_ALREADY = """
        {"invited_by_nickname":"네옹집사","expires_at":"2026-09-13T00:00:00Z",
         "pets":[{"pet_id":"p1","name":"롱이","already_member":false},
                 {"pet_id":"p2","name":"몽이","already_member":true}],
         "link_candidates":[]}
        """

        const val ACCEPTED = """
        {"pet_id":"m1","name":"롱롱씨",
         "pets":[{"invited_pet_id":"p1","display_pet_id":"m1","name":"롱롱씨","result":"linked"},
                 {"invited_pet_id":"p2","display_pet_id":"p2","name":"몽이","result":"joined"}]}
        """
    }
}
