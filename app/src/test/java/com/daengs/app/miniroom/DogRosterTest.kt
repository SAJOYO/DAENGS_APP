package com.daengs.app.miniroom

import com.daengs.app.miniroom.art.DogBreed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 방에 서는 강아지는 **사용자가 등록한 강아지**다.
 *
 * 여기서 잡는 것은 셋이다 — 명부대로 서는가, 명부가 바뀔 때 **걷던 아이가
 * 순간이동하지 않는가**, 개발자 도구를 되돌리면 **내 강아지로** 돌아오는가.
 */
class DogRosterTest {

    private val beagle = DogBreed.BEAGLE
    private val corgi = DogBreed.WELSH_CORGI
    private val husky = DogBreed.SIBERIAN_HUSKY

    @Test
    fun `명부대로 선다`() {
        val herd = DogHerd(listOf(beagle, corgi))
        assertEquals(listOf(beagle, corgi), herd.dogs.map { it.breed })
    }

    /**
     * 강아지를 하나 등록하면 목록을 다시 받아 오는데(`PetHolder`), 그때 방을 새로
     * 세우면 **멀쩡히 걷던 아이들이 순간이동한다.**
     */
    @Test
    fun `명부가 늘어도 있던 아이는 그 자리에 있다`() {
        val herd = DogHerd(listOf(beagle, corgi))
        val before = herd.dogs.map { it.pos }

        herd.setRoster(listOf(beagle, corgi, husky))

        assertEquals(3, herd.dogs.size)
        assertEquals(before, herd.dogs.take(2).map { it.pos })
        assertEquals(husky, herd.dogs[2].breed)
    }

    @Test
    fun `견종만 바뀌면 자리는 그대로다`() {
        val herd = DogHerd(listOf(beagle, corgi))
        val before = herd.dogs.map { it.pos }

        herd.setRoster(listOf(beagle, husky))

        assertEquals(before, herd.dogs.map { it.pos })
        assertEquals(listOf(beagle, husky), herd.dogs.map { it.breed })
    }

    @Test
    fun `명부가 줄면 뒤에서 빠진다`() {
        val herd = DogHerd(listOf(beagle, corgi, husky))
        val first = herd.dogs.first().pos

        herd.setRoster(listOf(beagle))

        assertEquals(1, herd.dogs.size)
        assertEquals(first, herd.dogs.single().pos)
    }

    /**
     * 예전엔 되돌리면 [DogBreed.ROOM_BREEDS] 로 갔다. 그러면 개발자 도구를 껐을 때
     * 방에 **내 강아지가 아닌 개**가 남는다.
     */
    @Test
    fun `개발자 도구를 되돌리면 내 강아지로 돌아온다`() {
        val roster = listOf(beagle, corgi)
        val herd = DogHerd(roster)

        herd.setBreedOverride(husky)
        assertEquals(listOf(husky, husky), herd.dogs.map { it.breed })

        herd.setBreedOverride(null)
        assertEquals(roster, herd.dogs.map { it.breed })
    }

    /** 도구로 덮어쓴 채로 강아지를 등록해도, 되돌리면 **새 명부**로 돌아온다. */
    @Test
    fun `덮어쓴 동안 명부가 바뀌어도 되돌리면 새 명부다`() {
        val herd = DogHerd(listOf(beagle))
        herd.setBreedOverride(husky)

        herd.setRoster(listOf(beagle, corgi))
        assertEquals(listOf(husky, husky), herd.dogs.map { it.breed })

        herd.setBreedOverride(null)
        assertEquals(listOf(beagle, corgi), herd.dogs.map { it.breed })
    }

    /**
     * 그림이 없는 견종(믹스)의 대역.
     *
     * **같은 아이는 언제나 같은 종이어야 한다** — 앱을 켤 때마다 방의 개가 바뀌면
     * 사용자는 자기 개를 못 알아본다.
     */
    @Test
    fun `대역은 같은 값이면 같은 종이다`() {
        val id = "3f2a-b71c"
        assertEquals(DogBreed.roomStandIn(id), DogBreed.roomStandIn(id))
        assertTrue(DogBreed.roomStandIn(id) in DogBreed.ROOM_BREEDS)
    }

    /** 음수 해시에서도 골라진다 — `%` 만 쓰면 인덱스가 음수가 되어 터진다. */
    @Test
    fun `대역은 어떤 값이든 고른다`() {
        val keys = List(50) { "pet-$it" } + listOf("", "믹스", "-1")
        assertTrue(keys.all { DogBreed.roomStandIn(it) in DogBreed.ROOM_BREEDS })
        assertNotEquals(1, keys.map { DogBreed.roomStandIn(it) }.distinct().size)
    }
}
