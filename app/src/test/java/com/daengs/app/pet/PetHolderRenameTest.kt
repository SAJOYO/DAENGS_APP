package com.daengs.app.pet

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 연결된 보호자의 **개인 이름 바꾸기** (`PATCH /app/pets/{id}/display`).
 *
 * 공통 정보 수정(`edit` → 전체 PUT)과 다른 길이다. 연결된 아이에서 전체 PUT 은 서버가 409
 * (`not_group_owner`) 로 막고, 뚫리더라도 그룹 주보호자의 견종·건강정보를 덮어쓴다. 그래서
 * 이 홀더는 이름 요청 **하나만** 보내고, 실패해도 PUT 으로 대신하지 않는다.
 */
class PetHolderRenameTest {

    private fun pet(id: String, name: String, isOwner: Boolean, isGroupOwner: Boolean) = Pet(
        id = id, name = name, breed = "dog_beagle",
        sex = null, neutered = null, weightKg = null, birthDate = null, birthDateKind = null,
        isPrimary = true, isOwner = isOwner, isGroupOwner = isGroupOwner,
    )

    /** 이름 요청을 받아 적는 가짜. 성공하면 서버 목록의 이름도 바꿔 준다. */
    private class FakeServer(var pets: List<Pet>) {
        val renames = mutableListOf<Triple<String, String, String>>()
        var lists = 0
        var failWith: String? = null

        suspend fun rename(token: String, id: String, name: String): Result<Pet> {
            renames += Triple(token, id, name)
            failWith?.let { return Result.failure(IllegalStateException(it)) }
            pets = pets.map { if (it.id == id) it.copy(name = name) else it }
            return Result.success(pets.first { it.id == id })
        }

        suspend fun list(token: String): Result<PetList> {
            lists++
            return Result.success(PetList(pets, 5))
        }
    }

    private suspend fun holderWith(server: FakeServer): PetHolder =
        PetHolder(renameDisplay = server::rename, listPets = server::list).also { assertTrue(it.refresh("token")) }

    // -- 권한 ------------------------------------------------------------------

    @Test
    fun `연결된 보호자는 자기 표시 행 id 로 이름만 보낸다`() = runTest {
        // B 의 목록에서 연결된 아이의 id 는 B 자기 행(display_pet_id)이다.
        val linked = pet("b-row", "테스트연결", isOwner = true, isGroupOwner = false)
        val server = FakeServer(listOf(linked))
        val holder = holderWith(server)

        assertTrue(holder.rename("token", "b-row", "새이름"))

        assertEquals(listOf(Triple("token", "b-row", "새이름")), server.renames)
    }

    @Test
    fun `성공하면 목록을 다시 받아 새 이름이 보인다`() = runTest {
        val server = FakeServer(listOf(pet("b-row", "테스트연결", isOwner = true, isGroupOwner = false)))
        val holder = holderWith(server)
        val listsBefore = server.lists

        assertTrue(holder.rename("token", "b-row", "새이름"))

        assertEquals("다시 받아 온다", listsBefore + 1, server.lists)
        assertEquals("새이름", holder.pets!!.single().name)
        assertEquals("대표 표시도 같은 목록에서 나온다", "새이름", holder.primary!!.name)
        assertNull(holder.renameError)
        assertFalse(holder.renameBusy)
    }

    /** 돌보미(행의 대표가 아님)는 서버가 404 다 — 보내기 전에 막는다. */
    @Test
    fun `행의 대표가 아니면 요청을 보내지 않는다`() = runTest {
        val server = FakeServer(listOf(pet("cared", "롱롱씨", isOwner = false, isGroupOwner = false)))
        val holder = holderWith(server)

        assertFalse(holder.rename("token", "cared", "새이름"))

        assertTrue(server.renames.isEmpty())
        assertEquals("내가 등록한 아이의 이름만 바꿀 수 있어요.", holder.renameError)
    }

    @Test
    fun `목록에 없는 아이는 요청을 보내지 않는다`() = runTest {
        val server = FakeServer(listOf(pet("b-row", "테스트연결", isOwner = true, isGroupOwner = false)))
        val holder = holderWith(server)

        assertFalse(holder.rename("token", "someone-else", "새이름"))

        assertTrue(server.renames.isEmpty())
    }

    // -- 이름 검증 (서버 `PetDisplayUpdate.name`: 1~40자) ------------------------

    @Test
    fun `앞뒤 공백은 떼고 보낸다`() = runTest {
        val server = FakeServer(listOf(pet("b-row", "테스트연결", isOwner = true, isGroupOwner = false)))
        val holder = holderWith(server)

        assertTrue(holder.rename("token", "b-row", "  새이름  "))

        assertEquals("새이름", server.renames.single().third)
    }

    @Test
    fun `빈 이름은 보내지 않는다`() = runTest {
        val server = FakeServer(listOf(pet("b-row", "테스트연결", isOwner = true, isGroupOwner = false)))
        val holder = holderWith(server)

        assertFalse(holder.rename("token", "b-row", "   "))

        assertTrue(server.renames.isEmpty())
        assertEquals("이름을 입력해 주세요.", holder.renameError)
    }

    @Test
    fun `40자를 넘기면 보내지 않고 40자는 보낸다`() = runTest {
        val server = FakeServer(listOf(pet("b-row", "테스트연결", isOwner = true, isGroupOwner = false)))
        val holder = holderWith(server)

        assertFalse(holder.rename("token", "b-row", "가".repeat(PET_NAME_MAX + 1)))
        assertTrue(server.renames.isEmpty())
        assertEquals("이름은 ${PET_NAME_MAX}자까지예요.", holder.renameError)

        assertTrue(holder.rename("token", "b-row", "가".repeat(PET_NAME_MAX)))
        assertEquals(1, server.renames.size)
    }

    // -- 실패·중복 ---------------------------------------------------------------

    @Test
    fun `실패하면 서버 문장을 남기고 목록을 다시 받지 않는다`() = runTest {
        val server = FakeServer(listOf(pet("b-row", "테스트연결", isOwner = true, isGroupOwner = false)))
        val holder = holderWith(server)
        val listsBefore = server.lists
        server.failWith = "서버 오류 (502)"

        assertFalse(holder.rename("token", "b-row", "새이름"))

        assertEquals("서버 오류 (502)", holder.renameError)
        assertEquals("실패했는데 목록을 받으면 안 된다", listsBefore, server.lists)
        assertEquals("이름이 그대로다", "테스트연결", holder.pets!!.single().name)
        assertEquals("다른 길(PUT)로 다시 보내지 않는다", 1, server.renames.size)
        assertFalse(holder.renameBusy)
    }

    @Test
    fun `진행 중에 한 번 더 누르면 두 번째는 보내지 않는다`() = runTest {
        val linked = pet("b-row", "테스트연결", isOwner = true, isGroupOwner = false)
        val gate = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()
        val holder = PetHolder(
            renameDisplay = { _, id, name ->
                calls += name
                gate.await()
                Result.success(linked.copy(id = id, name = name))
            },
            listPets = { Result.success(PetList(listOf(linked), 5)) },
        )
        assertTrue(holder.refresh("token"))

        val first = async { holder.rename("token", "b-row", "첫번째") }
        yield()
        assertTrue("첫 요청이 진행 중이다", holder.renameBusy)

        assertFalse(holder.rename("token", "b-row", "두번째"))

        gate.complete(Unit)
        assertTrue(first.await())
        assertEquals(listOf("첫번째"), calls)
        assertFalse(holder.renameBusy)
    }

    @Test
    fun `오류를 지우면 다음 창이 깨끗하게 열린다`() = runTest {
        val server = FakeServer(listOf(pet("b-row", "테스트연결", isOwner = true, isGroupOwner = false)))
        val holder = holderWith(server)
        server.failWith = "서버 오류 (500)"
        assertFalse(holder.rename("token", "b-row", "새이름"))

        holder.clearRenameError()

        assertNull(holder.renameError)
    }
}
