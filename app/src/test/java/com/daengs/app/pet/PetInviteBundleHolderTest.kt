package com.daengs.app.pet

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress

/**
 * 묶음 초대 홀더. [PetInviteHolderTest] 와 같은 방식이지만 **스코프가 계정 전체**다.
 */
class PetInviteBundleHolderTest {

    private val token = "abc_DEF-123"

    private fun holder(stub: Stub, now: Long = 200L) =
        PetInviteBundleHolder(PetInviteBundleApi { stub.base }) { now }

    // -- 고르기 ----------------------------------------------------------------

    @Test
    fun `고른 순서를 지킨다`() {
        val holder = PetInviteBundleHolder(PetInviteBundleApi { "http://127.0.0.1:1" })

        holder.toggle("p2")
        holder.toggle("p1")

        assertEquals(listOf("p2", "p1"), holder.selected)
    }

    @Test
    fun `다시 누르면 뺀다`() {
        val holder = PetInviteBundleHolder(PetInviteBundleApi { "http://127.0.0.1:1" })

        holder.toggle("p1")
        holder.toggle("p1")

        assertTrue(holder.selected.isEmpty())
        assertFalse(holder.canCreate)
    }

    /** 담아 봐야 서버가 422 로 막고, 그때 사용자는 무엇을 빼야 하는지 모른다. */
    @Test
    fun `상한을 넘겨 담지 않고 이유를 말한다`() {
        val holder = PetInviteBundleHolder(PetInviteBundleApi { "http://127.0.0.1:1" })
        repeat(MAX_PETS_PER_INVITE) { holder.toggle("p$it") }

        assertFalse(holder.toggle("one-more"))

        assertEquals(MAX_PETS_PER_INVITE, holder.selected.size)
        assertTrue(holder.error!!.contains("${MAX_PETS_PER_INVITE}마리"))
    }

    /** 상한에 닿아도 이미 고른 것은 뺄 수 있어야 한다 — 아니면 다시 고를 방법이 없다. */
    @Test
    fun `상한에 닿아도 고른 것은 뺄 수 있다`() {
        val holder = PetInviteBundleHolder(PetInviteBundleApi { "http://127.0.0.1:1" })
        repeat(MAX_PETS_PER_INVITE) { holder.toggle("p$it") }

        assertTrue(holder.toggle("p0"))

        assertEquals(MAX_PETS_PER_INVITE - 1, holder.selected.size)
    }

    @Test
    fun `아무것도 안 골랐으면 만들 수 없다`() = runTest {
        val stub = Stub(200, CREATED)
        try {
            val holder = holder(stub)

            assertFalse(holder.canCreate)
            assertFalse(holder.create("t"))
            assertEquals(0, stub.calls)
        } finally {
            stub.stop()
        }
    }

    // -- 만들기 ----------------------------------------------------------------

    @Test
    fun `고른 아이들로 만들고 토큰을 들고 있는다`() = runTest {
        val stub = Stub(200, CREATED)
        try {
            val holder = holder(stub)
            holder.toggle("p1")
            holder.toggle("p2")

            assertTrue(holder.create("t"))

            assertEquals(listOf("p1", "p2"), holder.justCreated?.petIds)
            assertEquals(token, holder.justCreated?.token)
        } finally {
            stub.stop()
        }
    }

    /** 안 비우면 같은 아이들로 두 번째 초대를 만들기 쉽고, 상한 세 자리가 같은 초대로 찬다. */
    @Test
    fun `성공하면 고르기를 비운다`() = runTest {
        val stub = Stub(200, CREATED)
        try {
            val holder = holder(stub)
            holder.toggle("p1")

            holder.create("t")

            assertTrue(holder.selected.isEmpty())
        } finally {
            stub.stop()
        }
    }

    @Test
    fun `실패하면 고른 것을 남겨 다시 시도할 수 있다`() = runTest {
        val stub = Stub(409, """{"detail":"살아 있는 초대는 3개까지입니다."}""")
        try {
            val holder = holder(stub)
            holder.toggle("p1")

            assertFalse(holder.create("t"))

            assertEquals(listOf("p1"), holder.selected)
            assertEquals("살아 있는 초대는 3개까지입니다.", holder.error)
            assertNull(holder.justCreated)
        } finally {
            stub.stop()
        }
    }

    // -- 목록과 상한 ------------------------------------------------------------

    /** **묶음 수를 센다.** 세 마리를 한 링크로 부른 것은 한 자리다. */
    @Test
    fun `활성 묶음 수를 세고 상한에서 만들기를 막는다`() = runTest {
        val stub = Stub(200, threeActive())
        try {
            val holder = holder(stub)
            holder.load("t")
            holder.toggle("p1")

            assertEquals(3, holder.activeCount)
            assertFalse("상한이면 버튼을 미리 가린다", holder.canCreate)
        } finally {
            stub.stop()
        }
    }

    @Test
    fun `자리가 남으면 만들 수 있다`() = runTest {
        val stub = Stub(200, """{"invites":[${bundle("i1", listOf("롱이", "몽이"), expires = 300L)}]}""")
        try {
            val holder = holder(stub)
            holder.load("t")
            holder.toggle("p1")

            assertEquals(1, holder.activeCount)
            assertTrue(holder.canCreate)
        } finally {
            stub.stop()
        }
    }

    /** "못 불러왔다" 와 "아직 없다" 는 다르다. */
    @Test
    fun `못 불러오면 빈 목록으로 바꾸지 않는다`() = runTest {
        val stub = Stub(500, """{"detail":"서버 오류"}""")
        try {
            val holder = holder(stub)

            assertFalse(holder.load("t"))

            assertNull(holder.invites)
            assertEquals("서버 오류", holder.error)
        } finally {
            stub.stop()
        }
    }

    // -- 취소와 잊기 ------------------------------------------------------------

    /** 안 버리면 이미 죽은 링크를 공유할 수 있다. */
    @Test
    fun `방금 만든 묶음을 취소하면 토큰도 버린다`() = runTest {
        val stub = Stub(200, CREATED)
        try {
            val holder = holder(stub)
            holder.toggle("p1")
            holder.create("t")
            assertEquals("i1", holder.justCreated?.id)

            stub.next(204, "")
            holder.cancel("t", "i1")

            assertNull(holder.justCreated)
        } finally {
            stub.stop()
        }
    }

    @Test
    fun `다른 묶음을 취소하면 토큰은 남는다`() = runTest {
        val stub = Stub(200, CREATED)
        try {
            val holder = holder(stub)
            holder.toggle("p1")
            holder.create("t")

            stub.next(204, "")
            holder.cancel("t", "other")

            assertEquals("i1", holder.justCreated?.id)
        } finally {
            stub.stop()
        }
    }

    /** 화면을 닫거나 로그아웃하면 자격증명이 남으면 안 된다. */
    @Test
    fun `잊으면 토큰과 고르기가 같이 사라진다`() = runTest {
        val stub = Stub(200, CREATED)
        try {
            val holder = holder(stub)
            holder.toggle("p1")
            holder.create("t")

            holder.forget()

            assertNull(holder.justCreated)
            assertNull(holder.invites)
            assertTrue(holder.selected.isEmpty())
            assertNull(holder.error)
        } finally {
            stub.stop()
        }
    }

    @Test
    fun `오류 문구에 토큰이 들어가지 않는다`() = runTest {
        val stub = Stub(500, """{"detail":"서버 오류"}""")
        try {
            val holder = holder(stub)
            holder.toggle("p1")
            holder.create("t")

            assertFalse(holder.error!!.contains(token))
        } finally {
            stub.stop()
        }
    }

    private class Stub(status: Int, response: String) {
        private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val base get() = "http://127.0.0.1:${server.address.port}"
        var calls = 0
        private var status = status
        private var response = response

        /** 다음 응답을 바꾼다. 만들고 나서 취소를 보는 흐름에 쓴다. */
        fun next(status: Int, response: String) {
            this.status = status
            this.response = response
        }

        init {
            server.createContext("/app/pet-invites") { exchange ->
                calls++
                exchange.requestBody.bufferedReader().use { it.readText() }
                val bytes = response.toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1L else bytes.size.toLong())
                if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
        }

        fun stop() = server.stop(0)
    }

    private companion object {
        const val CREATED = """{"id":"i1","pet_ids":["p1","p2"],"token":"abc_DEF-123","expires_at":"2026-09-13T00:00:00Z"}"""

        fun bundle(id: String, names: List<String>, expires: Long) = """
            {"id":"$id","pets":[${names.mapIndexed { i, n -> """{"pet_id":"x$i","name":"$n"}""" }.joinToString(",")}],
             "created_at":"1970-01-01T00:00:00Z","expires_at":"${java.time.Instant.ofEpochMilli(expires)}","accepted_at":null}
        """.trimIndent()

        /** 세 묶음이 살아 있다 — 담긴 마릿수는 6마리지만 상한은 3자리를 다 쓴 것이다. */
        fun threeActive() = """
            {"invites":[
              ${bundle("i1", listOf("롱이", "몽이"), 300L)},
              ${bundle("i2", listOf("콩이", "봉이"), 300L)},
              ${bundle("i3", listOf("맹이", "탱이"), 300L)}
            ]}
        """.trimIndent()
    }
}
