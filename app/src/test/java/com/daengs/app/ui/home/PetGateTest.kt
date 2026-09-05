package com.daengs.app.ui.home

import com.daengs.app.pet.Pet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 강아지가 있어야 하는 기능 앞의 문을 **언제 세우는가.**
 *
 * 셋을 갈라야 한다 — 못 받아 왔다 / 없다 / 있다. 앞의 둘을 같이 다루면 목록이 오는
 * 사이에 문이 떴다가 사라지고, 사용자는 자기가 뭘 잘못 눌렀다고 읽는다.
 * [roomRoster] 가 방에서 하는 것과 같은 구분이다.
 */
class PetGateTest {

    private fun pet(id: String) = Pet(
        id = id, name = "네옹", breed = "dog_beagle", sex = null, neutered = null,
        weightKg = null, birthDate = null, birthDateKind = null, isPrimary = true,
        farewellOn = null,
    )

    @Test
    fun `로그인했는데 한 마리도 없으면 청한다`() {
        assertTrue(needsPet(signedIn = true, pets = emptyList()))
    }

    @Test
    fun `강아지가 있으면 안 청한다`() {
        assertFalse(needsPet(signedIn = true, pets = listOf(pet("a"))))
    }

    /**
     * **아직 못 받아 온 것에는 안 청한다.**
     *
     * 여기서 청하면 목록이 오는 사이에 문이 떴다가 저절로 사라진다. 방이 데모를
     * 세웠다가 뺏던 것과 같은 종류의 거짓말이다 ([roomRoster] ①).
     */
    @Test
    fun `아직 못 받아 왔으면 안 청한다`() {
        assertFalse(needsPet(signedIn = true, pets = null))
    }

    /**
     * **둘러보기에는 안 청한다.** 계정이 없어 등록할 곳이 없고, 거기서는 이름 없이도
     * 카드가 뽑히게 되어 있다(`CardDrawScreen`). 문을 세우면 둘러보기가 아무것도
     * 못 하는 화면이 된다. 디버그 전용이지만 이 저장소에서 실제로 쓰는 길이다.
     */
    @Test
    fun `로그인 전에는 안 청한다`() {
        assertFalse(needsPet(signedIn = false, pets = emptyList()))
        assertFalse(needsPet(signedIn = false, pets = null))
    }

    /**
     * **방과 문이 같은 값을 본다.** [needsPet] 이 낸 답을 [roomRoster] 가 그대로
     * 받으므로, 문이 뜨는 상황은 곧 방이 비는 상황이다. 갈라지면 "방은 비었는데
     * 산책은 그냥 되는" 화면이 생긴다.
     */
    @Test
    fun `문이 뜨는 상황이면 방도 빈다`() {
        listOf(null, emptyList(), listOf(pet("a"))).forEach { pets ->
            listOf(true, false).forEach { signedIn ->
                val waits = needsPet(signedIn, pets)
                if (waits) {
                    assertTrue("pets=$pets", roomRoster(pets, waits).isEmpty())
                }
            }
        }
    }

    /** 반대로 **문이 안 뜨면 방에 누군가 선다** — 못 받아 온 사이만 빼고. */
    @Test
    fun `문이 안 뜨면 방이 안 빈다`() {
        assertFalse(roomRoster(emptyList(), needsPet(false, emptyList())).isEmpty())
        assertFalse(roomRoster(listOf(pet("a")), needsPet(true, listOf(pet("a")))).isEmpty())
        // 못 받아 온 사이는 예외다. 문도 안 뜨고 방도 빈다 — 그때는 "불러오는 중" 이다.
        assertTrue(roomRoster(null, needsPet(true, null)).isEmpty())
    }

    // -- 문구 ----------------------------------------------------------------
    //
    // 자리마다 다른 말을 하되 **같은 것을 청해야** 한다. 하나는 등록하라 하고 하나는
    // 로그인하라 하면 사용자는 무엇을 해야 할지 모른다.

    @Test
    fun `세 자리가 다 있고 할 말이 다르다`() {
        assertEquals(3, PetNeed.entries.size)
        assertEquals(3, PetNeed.entries.map { it.title }.toSet().size)
    }

    @Test
    fun `어느 자리든 강아지 등록을 청한다`() {
        PetNeed.entries.forEach {
            assertTrue(it.name, it.body.contains("강아지를 등록"))
            assertTrue(it.name, it.title.isNotBlank())
        }
    }

    /** 두 줄까지다. 더 길면 좁은 폰에서 버튼이 밀린다 (`PetNeededNarrowPreview`). */
    @Test
    fun `본문이 두 줄을 넘지 않는다`() {
        PetNeed.entries.forEach {
            assertEquals(it.name, 2, it.body.lines().size)
        }
    }
}
