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

/**
 * 방에 세울 아이를 고르는 셈.
 *
 * **차례가 어긋나는 것이 제일 무섭다** — 명부와 배웅 자리가 첨자로 이어져 있어서,
 * 거르는 곳이 둘이 되면 **배웅한 아이의 하트가 남의 아이 곁에** 뜬다. 화면에서는
 * "왜 얘한테 하트가 있지" 로만 보인다.
 */
class RoomPetsTest {

    private fun pet(id: String, breed: String, primary: Boolean = false, farewell: LocalDate? = null) = Pet(
        id = id, name = "네옹", breed = breed, sex = null, neutered = null,
        weightKg = null, birthDate = null, birthDateKind = null, isPrimary = primary,
        farewellOn = farewell,
    )

    private val a = pet("1", "dog_beagle", primary = true)
    private val b = pet("2", "dog_welsh_corgi")
    private val c = pet("3", "dog_maltese")

    @Test
    fun `아무도 안 뺐으면 다 선다`() {
        // 기본이 "다 들어감" 이라야 새로 등록한 아이가 저절로 방에 선다.
        assertEquals(listOf(a, b, c), roomPets(listOf(a, b, c), emptySet()))
    }

    @Test
    fun `뺀 아이만 빠진다`() {
        assertEquals(listOf(a, c), roomPets(listOf(a, b, c), setOf("2")))
    }

    @Test
    fun `모르는 id 는 아무 일도 안 한다`() {
        // 지운 아이의 id 가 남아 있을 수 있다.
        assertEquals(listOf(a, b, c), roomPets(listOf(a, b, c), setOf("없는아이")))
    }

    @Test
    fun `다 빼면 대표 한 마리는 남는다`() {
        // 화면이 마지막 한 마리를 못 빼게 막지만, 아이를 지우거나 다른 기기에서
        // 고치면 이 상태로 흘러들 수 있다. **빈 방은 고장 난 것으로 읽힌다.**
        assertEquals(listOf(a), roomPets(listOf(a, b, c), setOf("1", "2", "3")))
    }

    @Test
    fun `아직 못 받았으면 그대로 모른다`() {
        assertEquals(null, roomPets(null, setOf("1")))
    }

    @Test
    fun `배웅한 아이도 방에 둘 수 있다`() {
        // 배웅은 지우는 일이 아니라는 것이 그 화면의 전제다.
        val gone = pet("4", "dog_beagle", farewell = LocalDate.of(2026, 1, 1))
        val inRoom = roomPets(listOf(a, gone), emptySet())

        assertEquals(listOf(a, gone), inRoom)
        // **명부와 배웅 자리는 같은 목록에서 나와야 한다.** 이 번호로 하트를 그린다.
        assertEquals(setOf(1), departedInRoom(inRoom))
    }

    @Test
    fun `아이를 빼면 하트 자리도 같이 밀린다`() {
        val gone = pet("4", "dog_beagle", farewell = LocalDate.of(2026, 1, 1))
        // b 를 빼면 배웅한 아이는 둘째가 아니라 첫째 다음이다. 하트도 그리로 밀린다.
        val inRoom = roomPets(listOf(a, b, gone), setOf("2"))

        assertEquals(listOf(a, gone), inRoom)
        assertEquals(setOf(1), departedInRoom(inRoom))
    }

    @Test
    fun `마지막 한 마리는 못 뺀다`() {
        assertTrue(canHideFromRoom(listOf(a, b), emptySet(), "1"))
        assertFalse("하나만 남았다", canHideFromRoom(listOf(a, b), setOf("2"), "1"))
    }

    @Test
    fun `이미 뺀 아이는 언제나 되돌릴 수 있다`() {
        // 되돌리는 쪽은 막을 이유가 없다.
        assertTrue(canHideFromRoom(listOf(a, b), setOf("2"), "2"))
    }
}
