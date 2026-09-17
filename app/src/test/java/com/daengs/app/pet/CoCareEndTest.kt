package com.daengs.app.pet

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 공동 돌봄이 끝나 내 강아지 정보로 돌아온 아이를 한 번만 알리는지 (#440).
 *
 * 카드가 "비글 · 남아 · 5.0kg" 에서 "믹스" 로 바뀌어도 서버는 아무것도 안 지웠다 — 연결된 동안
 * 주보호자 정보를 보여 줬을 뿐이다. 알림이 안 뜨면 사용자는 지워졌다고 읽고, 두 번 뜨면 또
 * 뭔가 바뀐 줄 안다.
 */
class CoCareEndTest {

    /** 연결 없이 혼자 돌보는 내 아이. */
    private fun solo(id: String) = pet(id, isOwner = true, isGroupOwner = true)

    /** 남의 아이와 연결된 내 아이 — 공통 정보는 그룹 주보호자 것이다. */
    private fun linked(id: String) = pet(id, isOwner = true, isGroupOwner = false)

    /** 연결 없이 참여한 돌보미 — 내 행이 없다. */
    private fun joined(id: String) = pet(id, isOwner = false, isGroupOwner = false)

    private fun pet(id: String, isOwner: Boolean, isGroupOwner: Boolean, hasOtherCarers: Boolean = false) = Pet(
        id = id,
        name = "테스트연결",
        breed = "mix",
        sex = null,
        neutered = null,
        weightKg = null,
        birthDate = null,
        birthDateKind = null,
        isPrimary = false,
        isOwner = isOwner,
        isGroupOwner = isGroupOwner,
        hasOtherCarers = hasOtherCarers,
    )

    // ── 판정 ─────────────────────────────────────────────────────────────

    @Test
    fun `연결된 내 아이가 혼자 돌보는 아이로 돌아오면 끝난 것이다`() {
        val previous = CoCareEnd.linkedOwnIds(listOf(linked("p1")))
        assertEquals(setOf("p1"), CoCareEnd.ended(previous, listOf(solo("p1"))))
    }

    @Test
    fun `아직 연결돼 있으면 끝난 것이 아니다`() {
        val previous = CoCareEnd.linkedOwnIds(listOf(linked("p1")))
        assertEquals(emptySet<String>(), CoCareEnd.ended(previous, listOf(linked("p1"))))
    }

    @Test
    fun `연결 없이 참여한 돌보미의 아이가 사라지면 알리지 않는다`() {
        val before = listOf(solo("mine"), joined("shared"))
        val previous = CoCareEnd.linkedOwnIds(before)
        assertEquals("돌보미 아이는 기준에 안 들어간다", emptySet<String>(), previous)
        assertEquals(emptySet<String>(), CoCareEnd.ended(previous, listOf(solo("mine"))))
    }

    @Test
    fun `연결된 적 없는 아이를 새로 등록해도 알리지 않는다`() {
        val previous = CoCareEnd.linkedOwnIds(listOf(solo("old")))
        assertEquals(emptySet<String>(), CoCareEnd.ended(previous, listOf(solo("old"), solo("new"))))
    }

    @Test
    fun `연결됐던 아이가 목록에서 사라지면 알리지 않는다`() {
        val previous = CoCareEnd.linkedOwnIds(listOf(linked("p1")))
        assertEquals(emptySet<String>(), CoCareEnd.ended(previous, emptyList()))
    }

    @Test
    fun `승계로 그룹 주보호자가 됐어도 다른 보호자가 남아 있으면 끝난 것이 아니다`() {
        val previous = CoCareEnd.linkedOwnIds(listOf(linked("p1")))
        val succeeded = pet("p1", isOwner = true, isGroupOwner = true, hasOtherCarers = true)
        assertEquals(emptySet<String>(), CoCareEnd.ended(previous, listOf(succeeded)))
    }

    @Test
    fun `여러 마리 중 끝난 아이만 고른다`() {
        val previous = CoCareEnd.linkedOwnIds(listOf(linked("a"), linked("b"), linked("c"), solo("d")))
        val now = listOf(solo("a"), linked("b"), solo("d"), joined("e"))
        assertEquals(setOf("a"), CoCareEnd.ended(previous, now))

        val watch = CoCareEndWatch(MemoryCoCareLinkLog())
        watch.observe("u1", listOf(linked("a"), linked("b"), linked("c")))
        watch.observe("u1", listOf(solo("a"), solo("b"), linked("c")))
        assertEquals(setOf("a", "b"), watch.pending?.petIds)
    }

    @Test
    fun `알림 문장에 기술 용어가 없다`() {
        assertEquals("공동 돌봄이 종료되어 내 강아지 정보로 돌아왔어요. 비어 있는 정보를 확인해 주세요.", CoCareEnd.MESSAGE)
    }

    // ── 한 번만 ──────────────────────────────────────────────────────────

    @Test
    fun `처음 받은 목록으로는 알리지 않는다`() {
        val watch = CoCareEndWatch(MemoryCoCareLinkLog())
        watch.observe("u1", listOf(solo("p1")))
        assertNull(watch.pending)
    }

    @Test
    fun `띄운 알림은 다음 목록에서 다시 뜨지 않는다`() {
        val watch = CoCareEndWatch(MemoryCoCareLinkLog())
        watch.observe("u1", listOf(linked("p1")))
        watch.observe("u1", listOf(solo("p1")))
        assertEquals(CoCareEndWatch.Pending("u1", setOf("p1")), watch.pending)

        watch.shown()
        watch.observe("u1", listOf(solo("p1")))
        assertNull(watch.pending)
    }

    // ── 계정 ─────────────────────────────────────────────────────────────

    @Test
    fun `다른 계정의 연결 기록으로 알리지 않는다`() {
        val log = MemoryCoCareLinkLog()
        CoCareEndWatch(log).observe("a", listOf(linked("p1")))

        val watch = CoCareEndWatch(log)
        watch.observe("b", listOf(solo("p1")))
        assertNull(watch.pending)
        assertEquals("A 의 기록은 그대로다", setOf("p1"), log.load("a"))
    }

    @Test
    fun `로그아웃하면 안 띄운 알림을 버리고 연결 기록은 남긴다`() {
        val log = MemoryCoCareLinkLog()
        val watch = CoCareEndWatch(log)
        watch.observe("a", listOf(linked("p1"), linked("p2")))
        watch.observe("a", listOf(solo("p1"), linked("p2")))

        watch.signOut()
        assertNull(watch.pending)
        assertEquals(setOf("p2"), log.load("a"))
    }

    @Test
    fun `탈퇴하면 그 계정의 연결 기록을 지운다`() {
        val log = MemoryCoCareLinkLog()
        val watch = CoCareEndWatch(log)
        watch.observe("a", listOf(linked("p1")))
        watch.forgetAccount("a")
        assertEquals(emptySet<String>(), log.load("a"))
    }

    // ── 목록 홀더와 이어서 ───────────────────────────────────────────────

    @Test
    fun `목록을 받는 사이 계정이 바뀌면 기록하지 않는다`() = runTest {
        val log = MemoryCoCareLinkLog()
        val watch = CoCareEndWatch(log)
        var account = "a"
        val holder = PetHolder(
            listPets = {
                account = "b"
                Result.success(PetList(listOf(linked("p1")), 5))
            },
            currentAccount = { account },
            onListed = watch::observe,
        )
        assertTrue(holder.refresh("token"))
        assertEquals(emptySet<String>(), log.load("a"))
        assertEquals(emptySet<String>(), log.load("b"))
    }

    @Test
    fun `목록을 못 받으면 연결 기록을 건드리지 않는다`() = runTest {
        val log = MemoryCoCareLinkLog()
        log.save("a", setOf("p1"))
        val watch = CoCareEndWatch(log)
        val holder = PetHolder(
            listPets = { Result.failure(IllegalStateException("잠시 뒤 다시 시도해 주세요.")) },
            currentAccount = { "a" },
            onListed = watch::observe,
        )
        holder.refresh("token")
        assertEquals(setOf("p1"), log.load("a"))
        assertNull(watch.pending)
    }

    @Test
    fun `직접 나가고 목록을 다시 받으면 알림이 한 번만 쌓인다`() = runTest {
        val watch = CoCareEndWatch(MemoryCoCareLinkLog())
        var left = false
        val holder = PetHolder(
            listPets = { Result.success(PetList(listOf(if (left) solo("p1") else linked("p1")), 5)) },
            currentAccount = { "me" },
            onListed = watch::observe,
        )
        val members = PetMemberHolder(
            removeMember = { _, _, _ -> left = true; Result.success(Unit) },
            listMembers = { _, petId -> Result.success(PetMemberList(petId, emptyList())) },
        )
        assertTrue(holder.refresh("token"))
        assertNull(watch.pending)

        // MainActivity 의 onLeave 와 같은 순서: 나가기 성공 → 목록 다시 받기.
        if (members.leave("token", "p1", "me")) holder.refresh("token")
        assertEquals(CoCareEndWatch.Pending("me", setOf("p1")), watch.pending)

        // 화면이 한 번 띄운 뒤, 다른 자리에서 목록을 또 받아도 다시 쌓이지 않는다.
        watch.shown()
        holder.refresh("token")
        holder.refresh("token")
        assertNull(watch.pending)
    }
}
