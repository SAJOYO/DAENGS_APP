package com.daengs.app.pet

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress

class InviteAcceptHolderTest {

    private val token = "abc_DEF-123"
    private val link = "https://daengapi.weareithero.cloud/invite#$token"

    private fun holder(stub: Stub) = InviteAcceptHolder(InviteAcceptApi { stub.base })

    /** 링크를 찾아도 미리보기를 못 받았으면(로그인·망 문제) 눌러도 할 수 있는 일이 없다. */
    @Test
    fun `유효한 링크를 붙여넣어도 미리보기 전에는 수락할 수 없다`() {
        val holder = InviteAcceptHolder(InviteAcceptApi { "http://127.0.0.1:1" })

        assertFalse("아무것도 안 붙여넣었을 때", holder.canAccept)

        holder.paste("그냥 인사말")
        assertFalse("우리 링크가 아닐 때", holder.canAccept)

        holder.paste(link)
        assertEquals(InvitePaste.Result.Found(token), holder.parsed)
        assertFalse("미리보기 전", holder.canAccept)
    }

    /** 확인을 누르기 전에는 서버에 아무것도 안 보낸다. */
    @Test
    fun `붙여넣기만으로는 요청하지 않는다`() = runTest {
        val stub = Stub(200, """{"pet_id":"p1","name":"네옹"}""")
        try {
            val holder = holder(stub)

            holder.paste(link)

            assertEquals(0, stub.calls)
        } finally {
            stub.stop()
        }
    }

    @Test
    fun `수락에 성공하면 참여한 아이를 돌려주고 입력을 비운다`() = runTest {
        val stub = Stub(200, """{"pet_id":"p1","name":"네옹"}""")
        try {
            val holder = holder(stub)
            holder.paste(link)

            val pet = holder.accept("access")

            assertEquals("p1", pet?.petId)
            assertEquals("네옹", pet?.name)
            assertEquals("성공했으면 입력을 비운다", "", holder.pasted)
            assertEquals(InvitePaste.Result.Empty, holder.parsed)
            assertTrue(holder.outcome is AcceptOutcome.Joined)
        } finally {
            stub.stop()
        }
    }

    /** 연타로 같은 초대를 두 번 보내면 화면이 흔들린다. */
    @Test
    fun `처리 중에는 다시 보내지 않는다`() = runTest {
        val stub = Stub(200, """{"pet_id":"p1","name":"네옹"}""")
        try {
            val holder = holder(stub)
            holder.paste(link)
            holder.accept("access")

            // 성공한 뒤에는 입력이 비어 토큰이 없다 — 다시 눌러도 요청이 안 나간다.
            assertNull(holder.accept("access"))
            assertEquals(1, stub.calls)
            assertFalse(holder.canAccept)
        } finally {
            stub.stop()
        }
    }

    /** 망이 끊긴 것이면 그대로 다시 누를 수 있어야 한다. */
    @Test
    fun `실패하면 입력을 남겨 다시 시도할 수 있다`() = runTest {
        val stub = Stub(500, """{"detail":"서버 오류"}""")
        try {
            val holder = holder(stub)
            holder.paste(link)

            val pet = holder.accept("access")

            assertNull(pet)
            assertEquals("입력이 남아 있어야 한다", link, holder.pasted)
            assertEquals(InvitePaste.Result.Found(token), holder.parsed)
            assertTrue(holder.outcome is AcceptOutcome.Failed)
        } finally {
            stub.stop()
        }
    }

    @Test
    fun `404 410 409 를 구분해 들고 있는다`() = runTest {
        for ((status, expected) in listOf(
            404 to AcceptOutcome.NotFound,
            410 to AcceptOutcome.Expired,
        )) {
            val stub = Stub(status, """{"detail":"…"}""")
            try {
                val holder = holder(stub)
                holder.paste(link)
                holder.accept("access")

                assertEquals(expected, holder.outcome)
            } finally {
                stub.stop()
            }
        }

        val stub = Stub(409, """{"detail":"돌보는 아이가 너무 많습니다."}""")
        try {
            val holder = holder(stub)
            holder.paste(link)
            holder.accept("access")

            assertEquals("돌보는 아이가 너무 많습니다.", (holder.outcome as AcceptOutcome.Conflict).message)
        } finally {
            stub.stop()
        }
    }

    @Test
    fun `새로 붙여넣으면 앞 시도의 결과를 지운다`() = runTest {
        val stub = Stub(404, """{"detail":"초대를 찾을 수 없습니다."}""")
        try {
            val holder = holder(stub)
            holder.paste(link)
            holder.accept("access")
            assertEquals(AcceptOutcome.NotFound, holder.outcome)

            holder.paste("https://daengapi.weareithero.cloud/invite#zzz_YYY-999")

            assertNull(holder.outcome)
        } finally {
            stub.stop()
        }
    }

    /** 화면을 닫거나 로그아웃하면 입력과 토큰이 사라져야 한다. */
    @Test
    fun `잊으면 입력과 토큰을 버린다`() = runTest {
        val stub = Stub(200, """{"pet_id":"p1","name":"네옹"}""")
        try {
            val holder = holder(stub)
            holder.paste(link)

            holder.forget()

            assertEquals("", holder.pasted)
            assertEquals(InvitePaste.Result.Empty, holder.parsed)
            assertNull(holder.outcome)
            assertFalse(holder.canAccept)
        } finally {
            stub.stop()
        }
    }

    // -- App Links 자동 진입 -----------------------------------------------------

    /** 딥링크로 이미 검증된 토큰이면 붙여넣지 않아도 미리보기로 이어질 준비가 된다. */
    @Test
    fun `링크에서 받은 토큰도 붙여넣은 것과 같이 취급한다`() {
        val holder = InviteAcceptHolder(InviteAcceptApi { "http://127.0.0.1:1" })

        holder.acceptFromLink(token)

        assertEquals(InvitePaste.Result.Found(token), holder.parsed)
        assertFalse("미리보기 전에는 누를 수 없다", holder.canAccept)
    }

    /** 같은 링크가 두 번(연타·재실행) 전달돼도 이미 보고 있는 미리보기·선택을 지우지 않는다. */
    @Test
    fun `같은 토큰이 다시 오면 아무것도 지우지 않는다`() = runTest {
        val stub = Stub(200, """{"pet_id":"p1","name":"네옹"}""")
        try {
            val holder = holder(stub)
            holder.acceptFromLink(token)
            holder.choose("pet-1", PetChoice.Join)

            holder.acceptFromLink(token)

            assertEquals(mapOf("pet-1" to PetChoice.Join), holder.choices)
        } finally {
            stub.stop()
        }
    }

    /** 다른 초대를 받았으면 앞서 고르던 것이 섞이면 안 된다. */
    @Test
    fun `다른 토큰이 오면 앞선 선택을 지운다`() {
        val holder = InviteAcceptHolder(InviteAcceptApi { "http://127.0.0.1:1" })
        holder.acceptFromLink(token)
        holder.choose("pet-1", PetChoice.Join)

        holder.acceptFromLink("zzz_YYY-999")

        assertEquals(InvitePaste.Result.Found("zzz_YYY-999"), holder.parsed)
        assertEquals(emptyMap<String, PetChoice>(), holder.choices)
    }

    @Test
    fun `잘못된 입력으로는 요청하지 않는다`() = runTest {
        val stub = Stub(200, """{"pet_id":"p1","name":"네옹"}""")
        try {
            val holder = holder(stub)
            holder.paste("https://evil.example.com/invite#$token")

            assertNull(holder.accept("access"))
            assertEquals(0, stub.calls)
        } finally {
            stub.stop()
        }
    }

    private class Stub(status: Int, response: String) {
        private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val base get() = "http://127.0.0.1:${server.address.port}"
        var calls = 0

        init {
            server.createContext("/app/pet-invites/accept") { exchange ->
                calls++
                exchange.requestBody.bufferedReader().use { it.readText() }
                val bytes = response.toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
        }

        fun stop() = server.stop(0)
    }
}
