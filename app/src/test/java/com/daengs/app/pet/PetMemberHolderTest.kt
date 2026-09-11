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
}
