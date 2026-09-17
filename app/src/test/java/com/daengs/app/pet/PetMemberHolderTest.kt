package com.daengs.app.pet

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PetMemberHolderTest {

    private fun list(petId: String, vararg members: PetMember) = PetMemberList(petId, members.toList())

    @Test
    fun `서버가 준 순서를 그대로 들고 있는다`() = runTest {
        val owner = PetMember("u1", "아빠", isOwner = true)
        val carer = PetMember("u2", "엄마", isOwner = false)
        val holder = PetMemberHolder { _, petId -> Result.success(list(petId, owner, carer)) }

        assertTrue(holder.load("token", "p1"))

        assertEquals(listOf(owner, carer), holder.members)
        assertNull(holder.error)
    }

    /**
     * **실패를 빈 목록으로 바꾸지 않는다.** 빈 목록으로 두면 화면이 "보호자가 없어요" 라고
     * 단언하게 되는데, 사실은 못 불러온 것이다.
     */
    @Test
    fun `실패는 빈 목록이 아니라 오류로 남는다`() = runTest {
        val holder = PetMemberHolder { _, _ -> Result.failure(IllegalStateException("서버에 닿지 못했어요.")) }

        assertFalse(holder.load("token", "p1"))

        assertNull("빈 목록으로 바뀌면 안 된다", holder.members)
        assertEquals("서버에 닿지 못했어요.", holder.error)
    }

    /**
     * 늦게 온 응답이 지금 화면을 덮으면, 다른 아이의 보호자가 이 아이 것처럼 앉는다.
     * 두 번째 조회가 먼저 끝난 뒤 첫 조회가 도착하는 순서를 만든다.
     */
    @Test
    fun `다른 아이로 넘어간 뒤 도착한 응답은 버린다`() = runTest {
        val slow = CompletableDeferred<Result<PetMemberList>>()
        val first = PetMember("u1", "아빠", isOwner = true)
        val second = PetMember("u9", "이모", isOwner = false)
        val holder = PetMemberHolder { _, petId ->
            if (petId == "p1") slow.await() else Result.success(list(petId, second))
        }

        val pending = async { holder.load("token", "p1") }
        // **먼저 시작시켜 둔다.** `runTest` 는 async 를 바로 실행하지 않아서, 그냥 두면
        // 두 조회의 순서가 뒤집혀 "늦게 온 응답" 이라는 상황 자체가 안 만들어진다.
        runCurrent()
        assertTrue(holder.load("token", "p2"))
        slow.complete(Result.success(list("p1", first)))

        assertFalse("늦게 온 응답은 false 로 끝난다", pending.await())
        assertEquals(listOf(second), holder.members)
        assertEquals("p2", holder.petId)
    }

    /** 계정을 바꾼 뒤 앞 계정의 응답이 도착해도 마찬가지다. */
    @Test
    fun `계정을 잊은 뒤 도착한 응답은 버린다`() = runTest {
        val slow = CompletableDeferred<Result<PetMemberList>>()
        val holder = PetMemberHolder { _, _ -> slow.await() }

        val pending = async { holder.load("token", "p1") }
        runCurrent()
        holder.forget()
        slow.complete(Result.success(list("p1", PetMember("u1", "아빠", isOwner = true))))

        assertFalse(pending.await())
        assertNull(holder.members)
        assertNull(holder.petId)
    }

    /** 다른 아이를 열면 앞 아이의 목록이 잠깐이라도 그 아이 것처럼 보이면 안 된다. */
    @Test
    fun `아이를 바꾸면 들고 있던 목록을 먼저 비운다`() = runTest {
        val slow = CompletableDeferred<Result<PetMemberList>>()
        var calls = 0
        val holder = PetMemberHolder { _, petId ->
            calls++
            if (calls == 1) Result.success(list(petId, PetMember("u1", "아빠", isOwner = true))) else slow.await()
        }
        assertTrue(holder.load("token", "p1"))
        assertEquals(1, holder.members?.size)

        val pending = async { holder.load("token", "p2") }
        runCurrent()

        assertNull("새 목록이 오기 전에는 비어 있어야 한다", holder.members)
        assertEquals("p2", holder.petId)
        slow.complete(Result.success(list("p2")))
        pending.await()
    }

    // -- 내보내기 / 나가기 -----------------------------------------------------------

    /**
     * **줄을 먼저 지우지 않는다.** 서버가 막으면 되살려야 하는데, 그 사이 다른 기기에서
     * 명단이 바뀌었을 수 있어 되살린 것이 진짜인지 알 수 없다 — 성공한 뒤 다시 받는다.
     */
    @Test
    fun `내보내면 목록을 서버에서 다시 받는다`() = runTest {
        val owner = PetMember("u1", "아빠", isOwner = true)
        val carer = PetMember("u2", "가연", isOwner = false)
        var loads = 0
        var asked: Triple<String, String, String>? = null
        val holder = PetMemberHolder(
            removeMember = { token, petId, target ->
                asked = Triple(token, petId, target)
                Result.success(Unit)
            },
            listMembers = { _, petId ->
                loads++
                Result.success(if (loads == 1) list(petId, owner, carer) else list(petId, owner))
            },
        )
        assertTrue(holder.load("token", "display-1"))

        assertTrue(holder.remove("token", "display-1", "u2"))

        assertEquals(Triple("token", "display-1", "u2"), asked)
        assertEquals(listOf(owner), holder.members)
        assertNull(holder.actionError)
        assertFalse(holder.actionBusy)
    }

    @Test
    fun `내보내기에 실패하면 목록이 그대로 남고 이유만 남는다`() = runTest {
        val owner = PetMember("u1", "아빠", isOwner = true)
        val carer = PetMember("u2", "가연", isOwner = false)
        val holder = PetMemberHolder(
            removeMember = { _, _, _ -> Result.failure(IllegalStateException("주보호자만 내보낼 수 있습니다.")) },
            listMembers = { _, petId -> Result.success(list(petId, owner, carer)) },
        )
        holder.load("token", "p1")

        assertFalse(holder.remove("token", "p1", "u2"))

        assertEquals("목록을 건드리면 안 된다", listOf(owner, carer), holder.members)
        assertEquals("주보호자만 내보낼 수 있습니다.", holder.actionError)
        assertNull("목록 오류 자리를 쓰지 않는다", holder.error)
    }

    /**
     * 나가고 나면 그 아이에 권한이 없다. 목록을 다시 받으면 404 라, 방금 성공한 일이
     * 화면에서 실패로 보인다.
     */
    @Test
    fun `나가면 목록을 다시 받지 않고 들고 있던 것을 버린다`() = runTest {
        var loads = 0
        val holder = PetMemberHolder(
            removeMember = { _, _, _ -> Result.success(Unit) },
            listMembers = { _, petId ->
                loads++
                Result.success(list(petId, PetMember("me", "나", isOwner = false)))
            },
        )
        holder.load("token", "p1")
        assertEquals(1, loads)

        assertTrue(holder.leave("token", "p1", "me"))

        assertEquals("나간 뒤에 다시 읽으면 404 다", 1, loads)
        assertNull(holder.members)
        assertNull(holder.petId)
    }

    @Test
    fun `나가기에 실패하면 목록이 그대로 있다`() = runTest {
        val me = PetMember("me", "나", isOwner = false)
        val holder = PetMemberHolder(
            removeMember = { _, _, _ -> Result.failure(IllegalStateException("서버에 닿지 못했어요.")) },
            listMembers = { _, petId -> Result.success(list(petId, me)) },
        )
        holder.load("token", "p1")

        assertFalse(holder.leave("token", "p1", "me"))

        assertEquals(listOf(me), holder.members)
        assertEquals("서버에 닿지 못했어요.", holder.actionError)
    }

    /** 확인 창을 닫고 버튼을 다시 눌러도 같은 사람을 두 번 빼는 요청이 나가면 안 된다. */
    @Test
    fun `진행 중에는 두 번째 요청을 안 보낸다`() = runTest {
        val slow = CompletableDeferred<Result<Unit>>()
        var calls = 0
        val holder = PetMemberHolder(
            removeMember = { _, _, _ ->
                calls++
                slow.await()
            },
            listMembers = { _, petId -> Result.success(list(petId)) },
        )

        val pending = async { holder.remove("token", "p1", "u2") }
        runCurrent()
        assertFalse("도는 중에는 거절한다", holder.remove("token", "p1", "u2"))
        slow.complete(Result.success(Unit))
        pending.await()

        assertEquals(1, calls)
    }

    /** 화면을 닫았다 다시 들어오면 지난 실패 문구가 새 목록 밑에 남아 있으면 안 된다. */
    @Test
    fun `다시 조회하면 지난 실패 문구가 지워진다`() {
        val me = PetMember("me", "나", isOwner = false)
        val holder = PetMemberHolder(
            removeMember = { _, _, _ -> Result.failure(IllegalStateException("안 돼요")) },
            listMembers = { _, petId -> Result.success(list(petId, me)) },
        )
        runTest {
            holder.load("token", "p1")
            assertFalse(holder.remove("token", "p1", "u2"))
            assertEquals("안 돼요", holder.actionError)

            holder.load("token", "p1")

            assertNull(holder.actionError)
        }
    }

    /** 로그아웃하면 실패 자국도 같이 지운다 — 다음 사람이 남의 오류를 본다. */
    @Test
    fun `잊으면 동작 오류도 지운다`() = runTest {
        val holder = PetMemberHolder(
            removeMember = { _, _, _ -> Result.failure(IllegalStateException("안 돼요")) },
            listMembers = { _, petId -> Result.success(list(petId)) },
        )
        holder.remove("token", "p1", "u2")
        assertEquals("안 돼요", holder.actionError)

        holder.forget()

        assertNull(holder.actionError)
    }
}
