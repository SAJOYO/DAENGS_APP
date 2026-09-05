package com.daengs.app.ui.walk

import com.daengs.app.pet.Pet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 산책에 데리고 나갈 아이 고르기의 셈.
 *
 * **비공개 테스트에서 난 사고를 고정한다** — 전부 골라진 채로 "누구와 나갈까요?" 라고
 * 물으니, 데려갈 아이를 고르려고 누른 것이 빼는 동작이 되어 나머지 아이들과 다녀온
 * 것으로 기록됐다. 걷는 내내 아무 표시도 없었고 끝나고서야 알았다.
 */
class WalkDogPickTest {

    private fun pet(id: String, name: String = "네옹") = Pet(
        id = id, name = name, breed = "dog_beagle", sex = null, neutered = null,
        weightKg = null, birthDate = null, birthDateKind = null, isPrimary = false,
        farewellOn = null,
    )

    // -- 기본 선택 -------------------------------------------------------------

    /** 고를 것이 없다. 매번 누르게 하면 탭만 늘고 실수할 여지도 없다. */
    @Test
    fun `한 마리면 그 아이가 골라져 있다`() {
        val only = pet("a")
        assertEquals(setOf("a"), defaultWalkDogs(listOf(only)))
    }

    /**
     * **사고가 나는 곳이 정확히 여기다.** 두 마리 이상일 때만 "눌러서 뺐다" 가
     * 성립한다. 그래서 여기서만 아무도 안 고른 채로 시작한다.
     */
    @Test
    fun `두 마리 이상이면 아무도 안 골라져 있다`() {
        assertTrue(defaultWalkDogs(listOf(pet("a"), pet("b"))).isEmpty())
        assertTrue(defaultWalkDogs(listOf(pet("a"), pet("b"), pet("c"))).isEmpty())
    }

    @Test
    fun `한 마리도 없으면 빈 값이다`() {
        assertTrue(defaultWalkDogs(emptyList()).isEmpty())
    }

    // -- 시작해도 되나 ----------------------------------------------------------

    /**
     * **강아지 앱이라 아이 없이는 안 나간다.**
     *
     * 예전에는 "사람이 걸은 것은 걸은 것이다" 라며 열어 두었는데, 그 근거였던
     * *등록한 아이가 없는 사람* 은 이제 `ui/home/PetGate.kt` 의 문이 먼저 막아서
     * 산책 화면에 못 온다. 남은 것은 "아이가 있는데 다 뺀 경우" 뿐이고 그건 실수다.
     */
    @Test
    fun `아이가 있는데 아무도 안 골랐으면 못 나간다`() {
        assertFalse(canStartWalk(listOf(pet("a"), pet("b")), emptySet()))
    }

    @Test
    fun `한 마리라도 골랐으면 나간다`() {
        assertTrue(canStartWalk(listOf(pet("a"), pet("b")), setOf("a")))
    }

    /**
     * ⚠️ **둘러보기는 예외다.** 로그인 전에는 문이 안 서고 등록한 아이도 없다.
     * 여기까지 막으면 둘러보기가 아무것도 못 하는 화면이 된다.
     */
    @Test
    fun `등록한 아이가 없으면 막지 않는다`() {
        assertTrue(canStartWalk(emptyList(), emptySet()))
    }

    // -- 막았으면 이유를 말한다 --------------------------------------------------

    @Test
    fun `막았을 때만 이유가 있다`() {
        assertNull(walkStartBlockedReason(listOf(pet("a")), setOf("a")))
        assertNull(walkStartBlockedReason(emptyList(), emptySet()))
        val reason = walkStartBlockedReason(listOf(pet("a"), pet("b")), emptySet())
        assertTrue(reason != null && reason.isNotBlank())
    }

    /** 이유와 막는 판단이 갈라지면 "안 눌리는데 아무 말도 없는" 버튼이 생긴다. */
    @Test
    fun `이유가 있는 것과 막힌 것이 늘 같다`() {
        val cases = listOf(
            emptyList<Pet>() to emptySet<String>(),
            listOf(pet("a")) to emptySet(),
            listOf(pet("a")) to setOf("a"),
            listOf(pet("a"), pet("b")) to emptySet(),
            listOf(pet("a"), pet("b")) to setOf("b"),
        )
        cases.forEach { (pets, selected) ->
            assertEquals(
                "pets=${pets.size} selected=$selected",
                canStartWalk(pets, selected),
                walkStartBlockedReason(pets, selected) == null,
            )
        }
    }

    // -- 문구 ------------------------------------------------------------------
    //
    // 예전에는 늘 "누구와 나갈까요?" 였다. 전부 골라진 채로 그렇게 물으니 아직
    // 아무도 안 골라진 줄로 읽혔다 — 그게 사고의 시작이었다.

    @Test
    fun `아무도 안 골랐으면 묻는다`() {
        assertEquals("누구와 나갈까요?", walkDogPickLabel(listOf(pet("a"), pet("b")), emptySet()))
    }

    @Test
    fun `고른 만큼 말해 준다`() {
        val pets = listOf(pet("a"), pet("b"), pet("c"))
        assertEquals("1마리와 나가요", walkDogPickLabel(pets, setOf("a")))
        assertEquals("2마리와 나가요", walkDogPickLabel(pets, setOf("a", "b")))
    }

    @Test
    fun `다 골랐으면 모두라고 한다`() {
        val pets = listOf(pet("a"), pet("b"))
        assertEquals("모두 함께 나가요", walkDogPickLabel(pets, setOf("a", "b")))
    }

    /** 한 마리뿐인 사람에게 "모두 함께" 는 이상하다. 그 아이 하나를 말한다. */
    @Test
    fun `한 마리면 모두라고 안 한다`() {
        assertEquals("1마리와 나가요", walkDogPickLabel(listOf(pet("a")), setOf("a")))
    }

    /** 목록에 없는 id 가 섞여 들어와도 세는 것은 실재하는 아이뿐이다. */
    @Test
    fun `모르는 id 는 안 센다`() {
        assertEquals("1마리와 나가요", walkDogPickLabel(listOf(pet("a")), setOf("a", "없는아이")))
    }

    @Test
    fun `문구가 비어 있지 않다`() {
        val pets = listOf(pet("a"), pet("b"))
        listOf(emptySet(), setOf("a"), setOf("a", "b")).forEach {
            assertTrue(walkDogPickLabel(pets, it).isNotBlank())
        }
    }
}
