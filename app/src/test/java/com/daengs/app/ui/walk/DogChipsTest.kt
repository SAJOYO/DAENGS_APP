package com.daengs.app.ui.walk

import com.daengs.app.pet.Pet
import com.daengs.app.walk.WalkSummary
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 목록에 이름을 붙이고 아이별로 거르는 규칙.
 *
 * 화면이 예쁜지는 못 잡지만, **누구의 산책인지**는 여기서 잡는다. 남의 아이 이름이
 * 붙거나 이 아이와 안 간 산책이 끼면 그건 그림 문제가 아니라 거짓말이다.
 */
class DogChipsTest {

    @Test
    fun `나간 아이들의 지금 이름을 붙인다`() {
        assertEquals(listOf("네옹", "댕댕"), dogNames(listOf("p1", "p2"), pets()))
    }

    /**
     * 지운 강아지의 산책은 그 자리가 **통째로 빈다.**
     *
     * "알 수 없는 아이" 같은 말로 채우면 없는 사실을 만드는 것이다.
     */
    @Test
    fun `모르는 아이는 지어내지 않는다`() {
        assertEquals(listOf("네옹"), dogNames(listOf("p1", "지워진-아이"), pets()))
        assertEquals(emptyList<String>(), dogNames(listOf("지워진-아이"), pets()))
    }

    @Test
    fun `아무도 안 나간 산책은 이름이 없다`() {
        assertEquals(emptyList<String>(), dogNames(emptyList(), pets()))
    }

    @Test
    fun `이름 순서는 기록에 남은 순서다`() {
        assertEquals(listOf("댕댕", "네옹"), dogNames(listOf("p2", "p1"), pets()))
    }

    @Test
    fun `이 아이와 나간 산책만 거른다`() {
        val walks = listOf(
            walk("둘이서", listOf("p1", "p2")),
            walk("네옹만", listOf("p1")),
            walk("혼자", emptyList()),
        )

        assertEquals(listOf("둘이서", "네옹만"), walks.walkedWith("p1").map { it.sessionId })
        assertEquals(listOf("둘이서"), walks.walkedWith("p2").map { it.sessionId })
    }

    /** 전체는 거르지 않는다 — 아무도 안 데리고 나간 산책도 목록에 남는다. */
    @Test
    fun `전체면 그대로 둔다`() {
        val walks = listOf(walk("둘이서", listOf("p1", "p2")), walk("혼자", emptyList()))

        assertEquals(walks, walks.walkedWith(null))
    }

    private fun walk(id: String, dogIds: List<String>) = WalkSummary(
        sessionId = id,
        dogIds = dogIds,
        startedAtMillis = 1_000L,
        endedAtMillis = 2_000L,
        weather = null,
        distanceMeters = 500.0,
        activeDurationMillis = 600_000L,
        segments = emptyList(),
        anchor = null,
    )

    private fun pets() = listOf(pet("p1", "네옹"), pet("p2", "댕댕"))

    private fun pet(id: String, name: String) = Pet(
        id = id,
        name = name,
        breed = "dog_beagle",
        sex = null,
        neutered = null,
        weightKg = null,
        birthDate = null,
        birthDateKind = null,
        isPrimary = false,
    )
}
