package com.daengs.app.ui.home

import com.daengs.app.miniroom.RoomDefaults
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.pet.Pet
import org.junit.Assert.assertFalse
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 등록한 강아지가 방에 서는가. `miniroom` 과 `pet` 이 만나는 유일한 자리다. */
class RoomRosterTest {

    private fun pet(id: String, breed: String, farewell: LocalDate? = null) = Pet(
        id = id, name = "네옹", breed = breed, sex = null, neutered = null,
        weightKg = null, birthDate = null, birthDateKind = null, isPrimary = false,
        farewellOn = farewell,
    )

    @Test
    fun `등록한 견종이 그대로 방에 선다`() {
        val pets = listOf(pet("1", "dog_beagle"), pet("2", "dog_welsh_corgi"))
        assertEquals(listOf(DogBreed.BEAGLE, DogBreed.WELSH_CORGI), roomRoster(pets))
    }

    /**
     * null 은 **아직 못 받아 온 것**이다. 그때는 아무도 안 세운다.
     *
     * 예전에는 데모 네 마리로 채웠는데, 목록이 도착하면 그 넷이 사라지고 내 아이만
     * 남았다 — 실기기에서 "강아지 4마리 있다가 샥 사라진다" 로 걸렸다. **모르는
     * 동안 남의 개를 세우는 것보다 비워 두고 불러오는 중이라고 말하는 편이 낫다.**
     */
    @Test
    fun `목록을 못 받았으면 아무도 안 세운다`() {
        assertTrue(roomRoster(null).isEmpty())
    }

    /**
     * 빈 목록은 **받아 왔는데 없는 것**이라 null 과 다르다.
     *
     * 둘러보기 중이거나 온보딩으로 넘어가기 직전이다. 그 짧은 사이에 방이 비면
     * 앱이 고장 난 것처럼 보여서 데모를 세운다 — 이건 거짓이 아니라 견본이다.
     */
    @Test
    fun `받아 왔는데 한 마리도 없으면 견본을 세운다`() {
        assertEquals(RoomDefaults.DOG_COUNT, roomRoster(emptyList()).size)
        assertTrue(roomRoster(emptyList()).all { it in DogBreed.ROOM_BREEDS })
    }

    /** 믹스는 얼굴이 없지만 **방에서는 대역이 선다** — 안 세우면 내 개가 사라진다. */
    @Test
    fun `그림이 없는 견종도 방에는 선다`() {
        val roster = roomRoster(listOf(pet("mix-1", "mix")))
        assertEquals(1, roster.size)
        assertTrue(roster.single() in DogBreed.ROOM_BREEDS)
    }

    // -- 배웅한 아이 ---------------------------------------------------------
    //
    // 방은 매일 보는 자리다. 목록에는 무지개가 붙는데 방에서만 다른 아이들과 똑같이
    // 돌아다니면 화면 두 곳이 다른 말을 한다.

    @Test
    fun `배웅한 아이의 자리를 명부와 같은 차례로 준다`() {
        val gone = LocalDate.of(2026, 3, 14)
        val pets = listOf(
            pet("1", "dog_beagle"),
            pet("2", "dog_welsh_corgi", gone),
            pet("3", "dog_poodle"),
        )
        assertEquals(setOf(1), departedInRoom(pets))
        // 명부와 첨자가 맞물려야 한다 — 어긋나면 멀쩡한 아이에게 무지개가 붙는다.
        assertEquals(3, roomRoster(pets).size)
    }

    @Test
    fun `배웅한 아이가 없으면 비어 있다`() {
        assertTrue(departedInRoom(listOf(pet("1", "dog_beagle"))).isEmpty())
    }

    /**
     * **견본에는 안 붙인다.** 있지도 않은 아이를 배웅한 것으로 만든다.
     */
    @Test
    fun `못 받아 왔거나 견본일 때는 아무도 아니다`() {
        assertTrue(departedInRoom(null).isEmpty())
        assertTrue(departedInRoom(emptyList()).isEmpty())
        assertFalse(roomRoster(emptyList()).isEmpty())
    }

    @Test
    fun `여러 마리를 배웅했으면 다 나온다`() {
        val gone = LocalDate.of(2026, 1, 1)
        val pets = listOf(
            pet("1", "dog_beagle", gone),
            pet("2", "dog_welsh_corgi"),
            pet("3", "dog_poodle", gone),
        )
        assertEquals(setOf(0, 2), departedInRoom(pets))
    }
}
