package com.daengs.app.ui.walk

import com.daengs.app.R
import com.daengs.app.pet.Pet
import org.junit.Assert.*
import org.junit.Test

class ReplayParticipantTest {
    private fun pet(id: String, primary: Boolean = false) = Pet(
        id = id, name = id, breed = "dog_beagle", sex = null, neutered = null,
        weightKg = null, birthDate = null, birthDateKind = null,
        isPrimary = primary, farewellOn = null,
    )

    @Test fun `참여하지 않은 대표 대신 실제 산책한 아이를 고른다`() {
        val home = pet("home", primary = true)
        val walked = pet("walked")
        assertEquals(walked, replayParticipant(listOf(home, walked), listOf("walked")))
    }

    @Test fun `함께 걸은 대표가 우선이고 대표가 없으면 기록의 참여 순서를 따른다`() {
        val a = pet("a")
        val b = pet("b", primary = true)
        assertEquals(b, replayParticipant(listOf(a, b), listOf("a", "b")))
        val other = b.copy(isPrimary = false)
        assertEquals(other, replayParticipant(listOf(a, other), listOf("b", "a")))
    }

    @Test fun `참여 정보를 잃었거나 견종을 모르면 다른 아이가 아닌 발바닥을 쓴다`() {
        val missing = replayParticipant(listOf(pet("home", true)), listOf("deleted"))
        assertNull(missing)
        assertEquals(R.drawable.ic_location_paw, walkFacePortraitRes(missing, null))
        assertEquals(R.drawable.ic_location_paw, walkFacePortraitRes(pet("a").copy(breed = "unknown"), null))
    }
}
