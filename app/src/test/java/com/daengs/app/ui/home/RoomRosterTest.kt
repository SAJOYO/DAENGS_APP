package com.daengs.app.ui.home

import com.daengs.app.miniroom.RoomDefaults
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.pet.Pet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 등록한 강아지가 방에 서는가. `miniroom` 과 `pet` 이 만나는 유일한 자리다. */
class RoomRosterTest {

    private fun pet(id: String, breed: String) = Pet(
        id = id, name = "네옹", breed = breed, sex = null, neutered = null,
        weightKg = null, birthDate = null, birthDateKind = null, isPrimary = false,
    )

    @Test
    fun `등록한 견종이 그대로 방에 선다`() {
        val pets = listOf(pet("1", "dog_beagle"), pet("2", "dog_welsh_corgi"))
        assertEquals(listOf(DogBreed.BEAGLE, DogBreed.WELSH_CORGI), roomRoster(pets))
    }

    /** null 은 **아직 못 받아 온 것**이다. 빈 방을 깜빡이지 않는다. */
    @Test
    fun `목록을 못 받았으면 데모로 채운다`() {
        assertEquals(RoomDefaults.DOG_COUNT, roomRoster(null).size)
        assertTrue(roomRoster(null).all { it in DogBreed.ROOM_BREEDS })
    }

    @Test
    fun `한 마리도 없어도 방은 안 비운다`() {
        assertEquals(RoomDefaults.DOG_COUNT, roomRoster(emptyList()).size)
    }

    /** 믹스는 얼굴이 없지만 **방에서는 대역이 선다** — 안 세우면 내 개가 사라진다. */
    @Test
    fun `그림이 없는 견종도 방에는 선다`() {
        val roster = roomRoster(listOf(pet("mix-1", "mix")))
        assertEquals(1, roster.size)
        assertTrue(roster.single() in DogBreed.ROOM_BREEDS)
    }
}
