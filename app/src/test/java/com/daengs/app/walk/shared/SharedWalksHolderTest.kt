package com.daengs.app.walk.shared

import com.daengs.app.care.CareActor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedWalksHolderTest {

    private fun walk(id: String, mine: Boolean = false) =
        SharedWalk(id, 1L, 2L, 1L, null, null, CareActor(if (mine) "me" else "u2", if (mine) "나" else "키키"), mine, listOf("p1"))

    private class FakeReader : SharedWalkReader {
        val lists = mutableListOf<Pair<String, String?>>()
        var pages: (String, String?) -> SharedWalkResult<SharedWalkPage> = { _, _ -> SharedWalkResult.Unsupported }
        var details: (String) -> SharedWalkResult<SharedWalkDetail> = { SharedWalkResult.Unsupported }
        var beforeAnswer: () -> Unit = {}

        override suspend fun list(accessToken: String, petId: String, cursor: String?, limit: Int): SharedWalkResult<SharedWalkPage> {
            lists += petId to cursor
            beforeAnswer()
            return pages(petId, cursor)
        }

        override suspend fun detail(accessToken: String, petId: String, walkId: String): SharedWalkResult<SharedWalkDetail> {
            beforeAnswer()
            return details(walkId)
        }
    }

    @Test fun `내가 올린 산책은 빼고 다른 보호자의 산책만 담는다`() = runBlocking {
        val reader = FakeReader().apply {
            pages = { pet, _ -> SharedWalkResult.Ready(SharedWalkPage(pet, listOf(walk("theirs"), walk("mine", mine = true)), "c1")) }
        }
        val holder = SharedWalksHolder(reader, { "sample-token" }, { true })

        holder.open("p1")

        assertEquals(listOf("theirs"), holder.walks.map { it.id })
        assertEquals("c1", holder.nextCursor)
        assertEquals(SharedWalksStatus.Ready, holder.status)
    }

    @Test fun `더 보기는 받은 커서로 이어 읽고 이미 받은 산책은 다시 넣지 않는다`() = runBlocking {
        val reader = FakeReader().apply {
            pages = { pet, cursor ->
                if (cursor == null) SharedWalkResult.Ready(SharedWalkPage(pet, listOf(walk("a"), walk("b")), "c1"))
                else SharedWalkResult.Ready(SharedWalkPage(pet, listOf(walk("b"), walk("c")), null))
            }
        }
        val holder = SharedWalksHolder(reader, { "sample-token" }, { true })

        holder.open("p1")
        holder.loadMore()

        assertEquals(listOf("p1" to null, "p1" to "c1"), reader.lists)
        assertEquals(listOf("a", "b", "c"), holder.walks.map { it.id })
        assertNull("마지막 페이지다", holder.nextCursor)
        holder.loadMore()
        assertEquals("다음이 없으면 부르지 않는다", 2, reader.lists.size)
    }

    /** 이전 계정의 산책이 다음 계정 화면에 남으면 안 된다. */
    @Test fun `기다리는 사이 계정이 바뀌면 늦게 온 결과를 버린다`() = runBlocking {
        var current = true
        val reader = FakeReader().apply {
            pages = { pet, _ -> SharedWalkResult.Ready(SharedWalkPage(pet, listOf(walk("theirs")), null)) }
            beforeAnswer = { current = false }
        }
        val holder = SharedWalksHolder(reader, { "sample-token" }, { current })

        holder.open("p1")

        assertTrue(holder.walks.isEmpty())
        assertNull(holder.petId)
        assertEquals(SharedWalksStatus.Idle, holder.status)
    }

    @Test fun `공동 조회가 없는 서버면 미지원으로 둔다`() = runBlocking {
        val holder = SharedWalksHolder(FakeReader(), { "sample-token" }, { true })

        holder.open("p1")

        assertEquals(SharedWalksStatus.Unsupported, holder.status)
        assertTrue(holder.walks.isEmpty())
    }

    /** 나갔거나 내보내진 뒤에는 서버가 404 를 준다 — 받아 둔 목록도 비운다. */
    @Test fun `볼 수 없는 강아지면 서버 문장을 남기고 목록을 비운다`() = runBlocking {
        var answer: SharedWalkResult<SharedWalkPage> = SharedWalkResult.Ready(SharedWalkPage("p1", listOf(walk("a")), null))
        val reader = FakeReader().apply { pages = { _, _ -> answer } }
        val holder = SharedWalksHolder(reader, { "sample-token" }, { true })
        holder.open("p1")

        answer = SharedWalkResult.NotFound("강아지를 찾을 수 없습니다.")
        holder.retry()

        assertEquals(SharedWalksStatus.NotFound("강아지를 찾을 수 없습니다."), holder.status)
        assertTrue(holder.walks.isEmpty())
    }

    /** 이어 읽기에서 망이 흔들렸으면 받아 둔 것은 남기고 그 커서부터 다시 읽는다. */
    @Test fun `이어 읽기 실패 뒤 다시 시도는 받아 둔 목록을 지키고 그 커서부터 읽는다`() = runBlocking {
        var failNext = true
        val reader = FakeReader().apply {
            pages = { pet, cursor ->
                when {
                    cursor == null -> SharedWalkResult.Ready(SharedWalkPage(pet, listOf(walk("a")), "c1"))
                    failNext -> SharedWalkResult.Failed("서버에 닿지 못했어요.")
                    else -> SharedWalkResult.Ready(SharedWalkPage(pet, listOf(walk("b")), null))
                }
            }
        }
        val holder = SharedWalksHolder(reader, { "sample-token" }, { true })
        holder.open("p1")
        holder.loadMore()
        assertEquals(SharedWalksStatus.Failed("서버에 닿지 못했어요."), holder.status)
        assertEquals(listOf("a"), holder.walks.map { it.id })

        failNext = false
        holder.retry()

        assertEquals(listOf("p1" to null, "p1" to "c1", "p1" to "c1"), reader.lists)
        assertEquals(listOf("a", "b"), holder.walks.map { it.id })
    }

    @Test fun `로그인 정보가 없으면 서버를 부르지 않는다`() = runBlocking {
        val reader = FakeReader()
        val holder = SharedWalksHolder(reader, { null }, { true })

        holder.open("p1")

        assertTrue(reader.lists.isEmpty())
        assertEquals(SharedWalksStatus.Failed("로그인 정보를 확인해 주세요."), holder.status)
    }

    @Test fun `다른 강아지를 고르면 앞 목록을 버린다`() = runBlocking {
        val reader = FakeReader().apply {
            pages = { pet, _ -> SharedWalkResult.Ready(SharedWalkPage(pet, listOf(walk("walk-of-$pet")), null)) }
        }
        val holder = SharedWalksHolder(reader, { "sample-token" }, { true })

        holder.open("p1")
        holder.open("p2")

        assertEquals("p2", holder.petId)
        assertEquals(listOf("walk-of-p2"), holder.walks.map { it.id })
    }

    @Test fun `상세를 열면 경로까지 받고 닫으면 비운다`() = runBlocking {
        val detail = SharedWalkDetail(walk("a"), listOf(SharedWalkPoint(1L, 37.5, 127.0)))
        val reader = FakeReader().apply {
            pages = { pet, _ -> SharedWalkResult.Ready(SharedWalkPage(pet, listOf(walk("a")), null)) }
            details = { SharedWalkResult.Ready(detail) }
        }
        val holder = SharedWalksHolder(reader, { "sample-token" }, { true })
        holder.open("p1")

        holder.openDetail("a")
        assertEquals(SharedWalkDetailStatus.Ready(detail), holder.detail)

        holder.closeDetail()
        assertEquals(SharedWalkDetailStatus.Closed, holder.detail)
    }

    @Test fun `그룹 밖 산책의 상세는 다시 시도 없이 서버 문장만 남긴다`() = runBlocking {
        val reader = FakeReader().apply {
            pages = { pet, _ -> SharedWalkResult.Ready(SharedWalkPage(pet, emptyList(), null)) }
            details = { SharedWalkResult.NotFound("산책 기록을 찾을 수 없습니다.") }
        }
        val holder = SharedWalksHolder(reader, { "sample-token" }, { true })
        holder.open("p1")

        holder.openDetail("other")

        assertEquals(
            SharedWalkDetailStatus.Unavailable("other", "산책 기록을 찾을 수 없습니다.", retryable = false),
            holder.detail,
        )
    }
}
