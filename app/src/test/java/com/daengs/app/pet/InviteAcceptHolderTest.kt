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

    @Test
    fun `유효한 링크를 붙여넣어야 수락할 수 있다`() {
        val holder = InviteAcceptHolder(InviteAcceptApi { "http://127.0.0.1:1" })

        assertFalse("아무것도 안 붙여넣었을 때", holder.canAccept)

        holder.paste("그냥 인사말")
        assertFalse("우리 링크가 아닐 때", holder.canAccept)

        holder.paste(link)
        assertTrue(holder.canAccept)
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
            assertTrue(holder.canAccept)
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
