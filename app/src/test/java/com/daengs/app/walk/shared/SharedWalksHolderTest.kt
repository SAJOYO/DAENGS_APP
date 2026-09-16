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

    private fun page(vararg ids: String, cursor: String? = null, count: Int = ids.size) =
        SharedWalkResult.Ready(SharedWalkFeedPage(ids.map { walk(it) }, SharedWalkTotals(count, 100L * count, 60L * count),
            listOf(SharedWalkCarer("me", "나", true, listOf("p1")), SharedWalkCarer("u2", "키키", false, listOf("p1"))), cursor))

    private class FakeReader : SharedWalkReader {
        val calls = mutableListOf<Triple<SharedWalkFeedQuery, String?, Int>>()
        var feeds: (SharedWalkFeedQuery, String?) -> SharedWalkResult<SharedWalkFeedPage> = { _, _ -> SharedWalkResult.Unsupported }
        var details: (String, String) -> SharedWalkResult<SharedWalkDetail> = { _, _ -> SharedWalkResult.Unsupported }
        val detailCalls = mutableListOf<Pair<String, String>>()
        var beforeAnswer: () -> Unit = {}

        override suspend fun feed(accessToken: String, query: SharedWalkFeedQuery, cursor: String?, limit: Int): SharedWalkResult<SharedWalkFeedPage> {
            calls += Triple(query, cursor, limit)
            beforeAnswer()
            return feeds(query, cursor)
        }

        override suspend fun detail(accessToken: String, petId: String, walkId: String): SharedWalkResult<SharedWalkDetail> {
            detailCalls += petId to walkId
            beforeAnswer()
            return details(petId, walkId)
        }
    }

    private val all = SharedWalkFeedQuery(zoneId = "Asia/Seoul")

    @Test fun `필요한 만큼만 이어 읽고 내가 올린 산책과 이미 받은 산책은 다시 담지 않는다`() = runBlocking {
        val reader = FakeReader().apply {
            feeds = { _, cursor ->
                when (cursor) {
                    null -> SharedWalkResult.Ready(SharedWalkFeedPage(listOf(walk("a"), walk("mine", mine = true), walk("b")),
                        SharedWalkTotals(5, 0, 0), emptyList(), "c1"))
                    "c1" -> page("b", "c", cursor = "c2", count = 5)
                    else -> page("d", "e", count = 5)
                }
            }
        }
        val holder = SharedWalksHolder(reader, { "sample-token" }, { true })

        holder.ensure(all, atLeast = 3)

        assertEquals(listOf(null, "c1"), reader.calls.map { it.second })
        assertEquals(listOf("a", "b", "c"), holder.walks.map { it.id })
        assertEquals(5, holder.totals?.count)
        assertEquals("c2", holder.nextCursor)

        holder.ensure(all, atLeast = 3)
        assertEquals("이미 충분하면 부르지 않는다", 2, reader.calls.size)

        holder.ensure(all, atLeast = 10)
        assertEquals(listOf("a", "b", "c", "d", "e"), holder.walks.map { it.id })
        assertNull("마지막 페이지다", holder.nextCursor)
        holder.ensure(all, atLeast = 10)
        assertEquals("다음이 없으면 부르지 않는다", 3, reader.calls.size)
    }

    @Test fun `조건이 바뀌면 받아 둔 목록 커서 합계를 버리고 처음부터 읽는다`() = runBlocking {
        val kiki = all.copy(actorIds = setOf("u2"))
        val reader = FakeReader().apply {
            feeds = { query, _ -> if (query == kiki) page("kiki-walk") else page("a", "b", cursor = "c1", count = 9) }
        }
        val holder = SharedWalksHolder(reader, { "sample-token" }, { true })
        holder.ensure(all, atLeast = 2)

        holder.ensure(kiki, atLeast = 5)

        assertEquals(kiki, holder.feedQuery)
        assertEquals(listOf("kiki-walk"), holder.walks.map { it.id })
        assertEquals(1, holder.totals?.count)
        assertEquals(listOf(null, null), reader.calls.map { it.second })
    }

    /** 이전 계정의 산책이 다음 계정 화면에 남으면 안 된다. */
    @Test fun `기다리는 사이 계정이 바뀌면 늦게 온 결과를 버린다`() = runBlocking {
        var current = true
        val reader = FakeReader().apply {
            feeds = { _, _ -> page("theirs") }
            beforeAnswer = { current = false }
        }
        val holder = SharedWalksHolder(reader, { "sample-token" }, { current })

        holder.ensure(all, atLeast = 5)

        assertTrue(holder.walks.isEmpty())
        assertNull(holder.feedQuery)
        assertNull(holder.totals)
        assertEquals(SharedWalksStatus.Idle, holder.status)
    }

    @Test fun `통합 목록이 없는 서버면 미지원으로 두고 다시 부르지 않는다`() = runBlocking {
        val reader = FakeReader()
        val holder = SharedWalksHolder(reader, { "sample-token" }, { true })

        holder.ensure(all, atLeast = 5)
        holder.ensure(all, atLeast = 5)

        assertEquals(SharedWalksStatus.Unsupported, holder.status)
        assertTrue(holder.walks.isEmpty())
        assertEquals(1, reader.calls.size)
    }

    /** 이어 읽기에서 망이 흔들렸으면 받아 둔 것은 남기고, 다시 시도는 그 커서부터 읽는다. */
    @Test fun `실패하면 받아 둔 목록을 지키고 다시 시도는 그 커서부터 읽는다`() = runBlocking {
        var failNext = true
        val reader = FakeReader().apply {
            feeds = { _, cursor ->
                when {
                    cursor == null -> page("a", cursor = "c1", count = 2)
                    failNext -> SharedWalkResult.Failed("서버에 닿지 못했어요.")
                    else -> page("b", count = 2)
                }
            }
        }
        val holder = SharedWalksHolder(reader, { "sample-token" }, { true })
        holder.ensure(all, atLeast = 5)
        assertEquals(SharedWalksStatus.Failed("서버에 닿지 못했어요."), holder.status)
        assertEquals(listOf("a"), holder.walks.map { it.id })
        assertEquals(2, holder.totals?.count)

        holder.ensure(all, atLeast = 5)
        assertEquals("실패한 채로는 저절로 다시 부르지 않는다", 2, reader.calls.size)

        failNext = false
        holder.retry(all, atLeast = 5)

        assertEquals(listOf(null, "c1", "c1"), reader.calls.map { it.second })
        assertEquals(listOf("a", "b"), holder.walks.map { it.id })
        assertEquals(SharedWalksStatus.Ready, holder.status)
    }

    @Test fun `로그인 정보가 없으면 서버를 부르지 않는다`() = runBlocking {
        val reader = FakeReader()
        val holder = SharedWalksHolder(reader, { null }, { true })

        holder.ensure(all, atLeast = 5)

        assertTrue(reader.calls.isEmpty())
        assertEquals(SharedWalksStatus.Failed("로그인 정보를 확인해 주세요."), holder.status)
    }

    @Test fun `보호자 후보는 목록을 받을 때 같이 오고 따로 받을 때는 한 건만 부른다`() = runBlocking {
        val reader = FakeReader().apply { feeds = { _, _ -> page() } }
        val holder = SharedWalksHolder(reader, { "sample-token" }, { true })

        holder.loadCarers()
        holder.loadCarers()

        assertEquals(listOf("나", "키키"), holder.carers.map { it.displayName })
        assertEquals("한 번만 부른다", 1, reader.calls.size)
        assertEquals(1, reader.calls.single().third)
        assertNull("후보만 받을 때는 목록 상태를 건드리지 않는다", holder.feedQuery)
    }

    @Test fun `상세는 고른 강아지와 산책으로 열고 닫으면 비운다`() = runBlocking {
        val detail = SharedWalkDetail(walk("a"), listOf(SharedWalkPoint(1L, 37.5, 127.0)))
        val reader = FakeReader().apply { details = { _, _ -> SharedWalkResult.Ready(detail) } }
        val holder = SharedWalksHolder(reader, { "sample-token" }, { true })

        holder.openDetail("p1", "a")
        assertEquals(listOf("p1" to "a"), reader.detailCalls)
        assertEquals(SharedWalkDetailStatus.Ready(detail), holder.detail)

        holder.closeDetail()
        assertEquals(SharedWalkDetailStatus.Closed, holder.detail)
    }

    @Test fun `그룹 밖 산책의 상세는 다시 시도 없이 서버 문장만 남긴다`() = runBlocking {
        val reader = FakeReader().apply { details = { _, _ -> SharedWalkResult.NotFound("산책 기록을 찾을 수 없습니다.") } }
        val holder = SharedWalksHolder(reader, { "sample-token" }, { true })

        holder.openDetail("p1", "other")

        assertEquals(
            SharedWalkDetailStatus.Unavailable("other", "산책 기록을 찾을 수 없습니다.", retryable = false),
            holder.detail,
        )
    }

    @Test fun `상세 다시 시도는 같은 강아지와 산책으로 부른다`() = runBlocking {
        var fail = true
        val detail = SharedWalkDetail(walk("a"), emptyList())
        val reader = FakeReader().apply {
            details = { _, _ -> if (fail) SharedWalkResult.Failed("서버에 닿지 못했어요.") else SharedWalkResult.Ready(detail) }
        }
        val holder = SharedWalksHolder(reader, { "sample-token" }, { true })
        holder.openDetail("p1", "a")
        assertEquals(SharedWalkDetailStatus.Unavailable("a", "서버에 닿지 못했어요.", retryable = true), holder.detail)

        fail = false
        holder.retryDetail()

        assertEquals(listOf("p1" to "a", "p1" to "a"), reader.detailCalls)
        assertEquals(SharedWalkDetailStatus.Ready(detail), holder.detail)
    }
}
