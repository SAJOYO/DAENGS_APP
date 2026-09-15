package com.daengs.app.dogcard.photo

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PhotoCardHolderTest {

    @get:Rule val tmp = TemporaryFolder()

    private class FakeRemote : PhotoCardRemote {
        val server = mutableListOf<PhotoCard>()
        var failList = false
        var failDelete = false
        var throwOnCreate = false
        var createError: String? = null
        var gets = 0
        val urls = mutableMapOf<String, String>()
        var onDownload: (suspend () -> Unit)? = null
        var onCreate: (suspend () -> Unit)? = null
        var onList: (suspend () -> Unit)? = null

        override suspend fun create(token: String, month: Int, dogName: String, dogId: String?, jpeg: ByteArray): Result<PhotoCard> {
            onCreate?.invoke()
            if (throwOnCreate) throw IllegalStateException("만드는 중 예외")
            createError?.let { return Result.failure(IllegalStateException(it)) }
            val made = card("new-$month", month, PhotoCardStatus.Generating, at = 1_000L)
            server.add(0, made)
            return Result.success(made)
        }
        override suspend fun list(token: String): Result<List<PhotoCard>> {
            onList?.invoke()
            return if (failList) Result.failure(IllegalStateException("서버에 닿지 못했어요.")) else Result.success(server.toList())
        }
        override suspend fun get(token: String, id: String): Result<PhotoCardDetail> {
            gets++
            val c = server.first { it.id == id }
            return Result.success(PhotoCardDetail(c, urls[id]))
        }
        override suspend fun delete(token: String, id: String): Result<Unit> {
            if (failDelete) return Result.failure(IllegalStateException("카드 보관은 아직 준비 중이에요."))
            server.removeAll { it.id == id }
            return Result.success(Unit)
        }
        override suspend fun download(url: String): Result<ByteArray> {
            onDownload?.invoke()
            return Result.success(byteArrayOf(1, 2, 3))
        }
    }

    companion object {
        fun card(id: String, month: Int, status: PhotoCardStatus, at: Long = 0L) = PhotoCard(
            id = id, dogId = null, month = month, dogName = "콩이", title = "BLOSSOM 콩이",
            status = status, errorCode = if (status == PhotoCardStatus.Failed) "upstream" else null,
            likeness = if (status == PhotoCardStatus.Ready) 5 else null, createdAtMillis = at,
        )
    }

    private fun holder(remote: FakeRemote, token: String? = "t", now: Long = 2_000L) =
        PhotoCardHolder(remote, PhotoCardFiles(tmp.newFolder()), { token }, { now })

    @Test
    fun `완성 카드는 그림을 받아 둔다`() = runTest {
        val remote = FakeRemote().apply {
            server += card("a", 4, PhotoCardStatus.Ready)
            urls["a"] = "https://x/a.png"
        }
        val h = holder(remote)
        h.load()
        assertEquals(listOf("a"), h.cards.map { it.id })
        assertTrue(h.images.getValue("a").exists())
    }

    @Test
    fun `이미 받은 그림은 다시 안 받는다`() = runTest {
        val remote = FakeRemote().apply {
            server += card("a", 4, PhotoCardStatus.Ready)
            urls["a"] = "https://x/a.png"
        }
        val h = holder(remote)
        h.load()
        h.load()
        assertEquals(1, remote.gets)
    }

    /**
     * 목록이 통째로 사라지면 사용자는 카드가 지워진 줄 안다.
     *
     * **`error` 는 안 남는다.** 도감을 열 때마다 도는 자리라, 여기서 `error` 를 남기면
     * 오프라인이거나 서버 점검 중인 사람이 도감을 열 때마다 토스트를 본다 — 실패는
     * 조용하다(docs/photo-cards.md §3). `error` 는 `remove()` 실패에만 쓴다.
     */
    @Test
    fun `못 불러와도 들고 있던 것을 유지한다`() = runTest {
        val remote = FakeRemote().apply { server += card("a", 4, PhotoCardStatus.Generating) }
        val h = holder(remote)
        h.load()
        remote.failList = true
        h.load()
        assertEquals(1, h.cards.size)
        assertNull(h.error)
    }

    /** 목록을 받는 사이 로그아웃하면 그 응답이 늦게 와도 이전 사람 목록이 안 돌아온다. */
    @Test
    fun `목록을 받는 사이 비우면 이전 사람의 목록이 안 돌아온다`() = runTest {
        val remote = FakeRemote().apply {
            server += card("a", 4, PhotoCardStatus.Ready)
            urls["a"] = "https://x/a.png"
        }
        val dir = tmp.newFolder()
        val h = PhotoCardHolder(remote, PhotoCardFiles(dir), { "t" }, { 0L })
        remote.onList = { h.forget() }
        h.load()
        assertTrue(h.cards.isEmpty())
        assertTrue(h.images.isEmpty())
        assertTrue(dir.listFiles { f -> f.name.endsWith(".png") }.orEmpty().isEmpty())
    }

    @Test
    fun `로그인 전이면 아무것도 안 한다`() = runTest {
        val remote = FakeRemote().apply { server += card("a", 4, PhotoCardStatus.Ready) }
        val h = holder(remote, token = null)
        h.load()
        assertTrue(h.cards.isEmpty())
        assertNull(h.error)
    }

    @Test
    fun `만들면 맨 앞에 만드는 중으로 온다`() = runTest {
        val h = holder(FakeRemote())
        assertTrue(h.create(4, "콩이", null, byteArrayOf(9)))
        assertEquals(PhotoCardStatus.Generating, h.cards.first().status)
        assertTrue(h.generating)
        assertFalse(h.creating)
    }

    /** 서버 문장(한도·중복)은 만들기 화면에 그대로 뜬다. */
    @Test
    fun `만들기가 거절되면 문장을 남기고 목록은 그대로다`() = runTest {
        val remote = FakeRemote().apply { createError = "오늘은 카드를 더 만들 수 없어요. 내일 다시 시도해 주세요." }
        val h = holder(remote)
        assertFalse(h.create(4, "콩이", null, byteArrayOf(9)))
        assertEquals("오늘은 카드를 더 만들 수 없어요. 내일 다시 시도해 주세요.", h.createError)
        assertTrue(h.cards.isEmpty())
        h.clearCreateError()
        assertNull(h.createError)
    }

    /** 로그아웃하는 사이 만들기가 성공으로 끝나면 다음 사람 목록에 이전 사람 카드가 얹힌다. */
    @Test
    fun `만들기 요청 사이 비우면 새 카드가 목록에 안 들어온다`() = runTest {
        val remote = FakeRemote()
        val h = holder(remote)
        remote.onCreate = { h.forget() }
        assertFalse(h.create(4, "콩이", null, byteArrayOf(9)))
        assertTrue(h.cards.isEmpty())
        assertFalse(h.creating)
    }

    /** 로그아웃하는 사이 만들기가 거절로 끝나면 이전 사람의 오류 문장이 다음 사람 화면에 뜬다. */
    @Test
    fun `만들기가 거절된 사이 비우면 이전 사람의 문장이 안 남는다`() = runTest {
        val remote = FakeRemote().apply { createError = "오늘은 카드를 더 만들 수 없어요. 내일 다시 시도해 주세요." }
        val h = holder(remote)
        remote.onCreate = { h.forget() }
        assertFalse(h.create(4, "콩이", null, byteArrayOf(9)))
        assertNull(h.createError)
    }

    @Test
    fun `조회해서 완성되면 바꿔 끼우고 그림을 받는다`() = runTest {
        val remote = FakeRemote()
        val h = holder(remote)
        h.create(4, "콩이", null, byteArrayOf(9))
        remote.server[0] = remote.server[0].copy(status = PhotoCardStatus.Ready, likeness = 4)
        remote.urls["new-4"] = "https://x/n.png"
        h.pollOnce()
        assertEquals(PhotoCardStatus.Ready, h.cards.first().status)
        assertTrue(h.images.containsKey("new-4"))
        assertFalse(h.generating)
    }

    /** 서버는 9분 지나면 조회 때 interrupted 로 바꾼다. 앱은 10분 뒤 목록을 다시 받는다. */
    @Test
    fun `너무 오래 만드는 중이면 목록을 다시 받는다`() = runTest {
        val remote = FakeRemote().apply { server += card("old", 9, PhotoCardStatus.Generating, at = 0L) }
        val h = holder(remote, now = PHOTO_STALE_MS + 1)
        h.load()
        remote.server[0] = remote.server[0].copy(status = PhotoCardStatus.Failed, errorCode = "interrupted")
        h.pollOnce()
        assertEquals(0, remote.gets)
        assertEquals("old", h.latestFailure?.id)
    }

    @Test
    fun `만드는 중이 없으면 조회를 안 돈다`() = runTest {
        val remote = FakeRemote().apply { server += card("a", 4, PhotoCardStatus.Failed) }
        val h = holder(remote)
        h.load()
        h.pollWhileGenerating(intervalMs = 1)
        assertEquals(0, remote.gets)
    }

    @Test
    fun `지우면 서버와 파일에서 다 빠진다`() = runTest {
        val remote = FakeRemote().apply {
            server += card("a", 4, PhotoCardStatus.Ready)
            urls["a"] = "https://x/a.png"
        }
        val h = holder(remote)
        h.load()
        val file = h.images.getValue("a")
        assertTrue(h.remove("a"))
        assertTrue(h.cards.isEmpty())
        assertFalse(file.exists())
    }

    /** 정본이 서버라 기기에서만 지우면 다음 목록에서 되살아난다 — 누끼 카드와 반대다. */
    @Test
    fun `서버가 못 지우면 기기에서도 안 지운다`() = runTest {
        val remote = FakeRemote().apply { server += card("a", 4, PhotoCardStatus.Ready); failDelete = true }
        val h = holder(remote)
        h.load()
        assertFalse(h.remove("a"))
        assertEquals(1, h.cards.size)
        assertEquals("카드 보관은 아직 준비 중이에요.", h.error)
    }

    @Test
    fun `서버 목록에 없는 그림 파일은 치운다`() = runTest {
        val dir = tmp.newFolder()
        val files = PhotoCardFiles(dir)
        files.write("gone", byteArrayOf(1))
        val h = PhotoCardHolder(FakeRemote(), files, { "t" }, { 0L })
        h.load()
        assertFalse(files.existing().containsKey("gone"))
    }

    @Test
    fun `비우면 목록과 파일이 다 사라진다`() = runTest {
        val remote = FakeRemote().apply {
            server += card("a", 4, PhotoCardStatus.Ready)
            urls["a"] = "https://x/a.png"
        }
        val h = holder(remote)
        h.load()
        h.forget()
        assertTrue(h.cards.isEmpty())
        assertTrue(h.images.isEmpty())
    }

    @Test
    fun `만들기 도중 예외가 나도 만드는 중 표시가 풀린다`() = runTest {
        val remote = FakeRemote().apply { throwOnCreate = true }
        val h = holder(remote)
        runCatching { h.create(4, "콩이", null, byteArrayOf(9)) }
        assertFalse(h.creating)
    }

    @Test
    fun `반쯤 받은 파일은 목록을 맞출 때 치운다`() = runTest {
        val dir = tmp.newFolder()
        val files = PhotoCardFiles(dir)
        java.io.File(dir, "partial.part").writeBytes(byteArrayOf(1))
        val h = PhotoCardHolder(FakeRemote(), files, { "t" }, { 0L })
        h.load()
        assertFalse(java.io.File(dir, "partial.part").exists())
    }

    @Test
    fun `그림을 받는 사이 비우면 그림이 되살아나지 않는다`() = runTest {
        val remote = FakeRemote()
        val dir = tmp.newFolder()
        val files = PhotoCardFiles(dir)
        val h = PhotoCardHolder(remote, files, { "t" }, { 0L })
        remote.server += card("a", 4, PhotoCardStatus.Ready)
        remote.urls["a"] = "https://x/a.png"
        remote.onDownload = { h.forget() }
        h.load()
        assertTrue(h.images.isEmpty())
        assertFalse(java.io.File(dir, "a.png").exists())
    }

    @Test
    fun `그림을 받는 사이 그 카드를 지우면 파일이 남지 않는다`() = runTest {
        val remote = FakeRemote()
        val dir = tmp.newFolder()
        val files = PhotoCardFiles(dir)
        val h = PhotoCardHolder(remote, files, { "t" }, { 0L })
        remote.server += card("a", 4, PhotoCardStatus.Ready)
        remote.urls["a"] = "https://x/a.png"
        remote.onDownload = { h.remove("a") }
        h.load()
        assertFalse(h.images.containsKey("a"))
        assertFalse(java.io.File(dir, "a.png").exists())
    }

    /** 콜드스타트·오프라인에서도 방 액자·칸이 발바닥이 아니라 기기에 있던 그림을 보여준다. */
    @Test
    fun `켜자마자 기기에 받아 둔 그림을 쓴다`() = runTest {
        val dir = tmp.newFolder()
        val files = PhotoCardFiles(dir)
        files.write("a", byteArrayOf(1, 2, 3))
        val h = PhotoCardHolder(FakeRemote(), files, { "t" }, { 0L })
        assertTrue(h.images.containsKey("a"))
    }
}
