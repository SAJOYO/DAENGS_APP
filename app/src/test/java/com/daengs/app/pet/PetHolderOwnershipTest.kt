package com.daengs.app.pet

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetHolderOwnershipTest {

    private fun pet(id: String, isOwner: Boolean) = Pet(
        id = id,
        name = "네옹",
        breed = "dog_beagle",
        sex = null,
        neutered = null,
        weightKg = null,
        birthDate = null,
        birthDateKind = null,
        isPrimary = true,
        isOwner = isOwner,
    )

    @Test
    fun `공동 돌봄 아이도 서버 마릿수 상한에 포함한다`() = runTest {
        val holder = PetHolder { Result.success(PetList(listOf(pet("mine", true), pet("shared", false)), 2)) }
        assertTrue(holder.refresh("token"))
        assertFalse(holder.canAddMore)
    }

    @Test
    fun `공동 돌봄 아이의 프로필 변경 요청을 보내기 전에 막는다`() = runTest {
        val shared = pet("shared", false)
        val holder = PetHolder { Result.success(PetList(listOf(shared), 5)) }
        assertTrue(holder.refresh("token"))

        assertFalse(holder.edit("token", shared.id, shared.toDraft()))
        assertEquals("대표 보호자만 강아지 정보를 바꿀 수 있어요.", holder.error)
    }
}
