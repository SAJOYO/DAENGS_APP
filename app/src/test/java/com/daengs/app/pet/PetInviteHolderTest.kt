package com.daengs.app.pet

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress

/**
 * 홀더와 API 를 내장 HTTP 서버에 붙여 본다 (`PetMemberApiTest` 와 같은 방식).
 *
 * 시각은 주입한 고정값이라 실제 현재 시각에 안 기댄다.
 */
class PetInviteHolderTest {

    private val now = 1_757_000_000_000L
    private val past = "2020-01-01T00:00:00Z"
    private val future = "2099-01-01T00:00:00Z"

    private fun holder(stub: Stub) = PetInviteHolder(PetInviteApi { stub.base }, now = { now })

    @Test
    fun `목록을 서버 순서 그대로 들고 상태를 가른다`() = runTest {
        val stub = Stub()
        stub.listBody = """{"pet_id":"p1","invites":[
            {"id":"live","created_at":"$past","expires_at":"$future","accepted_at":null},
            {"id":"used","created_at":"$past","expires_at":"$future","accepted_at":"$past"},
            {"id":"old","created_at":"$past","expires_at":"$past","accepted_at":null}
        ]}"""
        try {
            val holder = holder(stub)

            assertTrue(holder.load("t", "p1"))

            assertEquals(listOf("live", "used", "old"), holder.invites?.map { it.id })
            assertEquals(InviteStatus.ACTIVE, holder.statusOf(holder.invites!![0]))
            assertEquals(InviteStatus.ACCEPTED, holder.statusOf(holder.invites!![1]))
            assertEquals(InviteStatus.EXPIRED, holder.statusOf(holder.invites!![2]))
            assertEquals(1, holder.activeCount)
        } finally {
            stub.stop()
        }
    }

    /**
     * **생성 직후 목록을 다시 받아도 토큰이 남아야 한다.** 목록에는 토큰이 없어서, 같은
     * 자리에 담으면 사용자가 공유하기 전에 링크가 사라진다.
     */
    @Test
    fun `방금 만든 토큰은 목록 갱신에 지워지지 않는다`() = runTest {
        val stub = Stub()
        stub.createBody = """{"id":"new","pet_id":"p1","token":"tok_abc","expires_at":"$future"}"""
        stub.listBody = """{"pet_id":"p1","invites":[{"id":"new","created_at":"$past","expires_at":"$future","accepted_at":null}]}"""
        try {
            val holder = holder(stub)

            assertTrue(holder.create("t", "p1"))

            assertEquals("tok_abc", holder.justCreated?.token)
            assertEquals(listOf("new"), holder.invites?.map { it.id })
            assertNull(holder.error)
        } finally {
            stub.stop()
        }
    }

    @Test
    fun `상한을 넘으면 서버가 준 문장을 그대로 쓴다`() = runTest {
        val stub = Stub()
        stub.createStatus = 409
        stub.createBody = """{"detail":"살아 있는 초대는 3개까지입니다."}"""
        try {
            val holder = holder(stub)

            assertFalse(holder.create("t", "p1"))

            assertEquals("살아 있는 초대는 3개까지입니다.", holder.error)
            assertNull("실패했으면 토큰이 없어야 한다", holder.justCreated)
        } finally {
            stub.stop()
        }
    }

    @Test
    fun `대표가 아니면 404 문장을 그대로 쓴다`() = runTest {
        val stub = Stub()
        stub.listStatus = 404
        stub.listBody = """{"detail":"강아지를 찾을 수 없습니다."}"""
        try {
            val holder = holder(stub)

            assertFalse(holder.load("t", "p1"))

            assertEquals("강아지를 찾을 수 없습니다.", holder.error)
            assertNull("실패를 빈 목록으로 바꾸지 않는다", holder.invites)
        } finally {
            stub.stop()
        }
    }

    @Test
    fun `취소하면 목록을 다시 받는다`() = runTest {
        val stub = Stub()
        stub.listBody = """{"pet_id":"p1","invites":[{"id":"live","created_at":"$past","expires_at":"$future","accepted_at":null}]}"""
        try {
            val holder = holder(stub)
            assertTrue(holder.load("t", "p1"))
            stub.listBody = """{"pet_id":"p1","invites":[]}"""

            assertTrue(holder.cancel("t", "p1", "live"))

            // 취소 뒤에 목록을 다시 받으므로 마지막 요청은 GET 이다 — 전체 기록에서 찾는다.
            assertTrue("DELETE /app/pets/p1/invites/live 가 있어야 한다", stub.requests.contains("DELETE /app/pets/p1/invites/live"))
            assertEquals("GET", stub.requests.last().substringBefore(' '))
            assertEquals(emptyList<PetInvite>(), holder.invites)
        } finally {
            stub.stop()
        }
    }

    /** 링크를 잃었을 때의 길 — 취소하면 들고 있던 토큰도 같이 버린다. */
    @Test
    fun `방금 만든 초대를 취소하면 토큰도 버린다`() = runTest {
        val stub = Stub()
        stub.createBody = """{"id":"new","pet_id":"p1","token":"tok_abc","expires_at":"$future"}"""
        stub.listBody = """{"pet_id":"p1","invites":[{"id":"new","created_at":"$past","expires_at":"$future","accepted_at":null}]}"""
        try {
            val holder = holder(stub)
            assertTrue(holder.create("t", "p1"))
            assertNotNull(holder.justCreated)
            stub.listBody = """{"pet_id":"p1","invites":[]}"""

            assertTrue(holder.cancel("t", "p1", "new"))

            assertNull("죽은 링크를 공유할 수 있으면 안 된다", holder.justCreated)
        } finally {
            stub.stop()
        }
    }

    @Test
    fun `다른 아이로 넘어가면 앞 아이의 토큰과 목록을 비운다`() = runTest {
        val stub = Stub()
        stub.createBody = """{"id":"new","pet_id":"p1","token":"tok_abc","expires_at":"$future"}"""
        stub.listBody = """{"pet_id":"p1","invites":[{"id":"new","created_at":"$past","expires_at":"$future","accepted_at":null}]}"""
        try {
            val holder = holder(stub)
            assertTrue(holder.create("t", "p1"))
            stub.listBody = """{"pet_id":"p2","invites":[]}"""

            assertTrue(holder.load("t", "p2"))

            assertNull(holder.justCreated)
            assertEquals("p2", holder.petId)
        } finally {
            stub.stop()
        }
    }

    @Test
    fun `잊으면 토큰도 같이 사라진다`() = runTest {
        val stub = Stub()
        stub.createBody = """{"id":"new","pet_id":"p1","token":"tok_abc","expires_at":"$future"}"""
        stub.listBody = """{"pet_id":"p1","invites":[]}"""
        try {
            val holder = holder(stub)
            assertTrue(holder.create("t", "p1"))

            holder.forget()

            assertNull(holder.justCreated)
            assertNull(holder.invites)
            assertNull(holder.petId)
        } finally {
            stub.stop()
        }
    }

    /** 오류 문구에 토큰이 섞여 나가면 로그·화면에 그대로 남는다. */
    @Test
    fun `오류 문구에 토큰이 들어가지 않는다`() = runTest {
        val stub = Stub()
        stub.createStatus = 500
        stub.createBody = """{"detail":"서버 오류"}"""
        try {
            val holder = holder(stub)
            holder.create("t", "p1")

            assertFalse(holder.error.orEmpty().contains("tok"))
        } finally {
            stub.stop()
        }
    }

    private class Stub {
        private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val base get() = "http://127.0.0.1:${server.address.port}"
        var listBody = """{"pet_id":"p1","invites":[]}"""
        var createBody = """{"id":"i","pet_id":"p1","token":"tok","expires_at":"2099-01-01T00:00:00Z"}"""
        var listStatus = 200
        var createStatus = 201
        val requests = mutableListOf<String>()

        init {
            server.createContext("/app/pets") { exchange ->
                requests += "${exchange.requestMethod} ${exchange.requestURI.path}"
                val (status, body) = when (exchange.requestMethod) {
                    "POST" -> createStatus to createBody
                    "DELETE" -> 204 to ""
                    else -> listStatus to listBody
                }
                val bytes = body.toByteArray(Charsets.UTF_8)
                if (bytes.isEmpty()) {
                    exchange.sendResponseHeaders(status, -1)
                } else {
                    exchange.sendResponseHeaders(status, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
            }
            server.start()
        }

        fun stop() = server.stop(0)
    }
}
