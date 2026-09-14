package com.daengs.app.miniroom

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **현관 마중.** 문 앞 자리를 주면 강아지들이 달려와 앉고, 얼마 있다가 다시 제 갈 길을 간다.
 *
 * 잠그는 것은 네 가지 — 다 온다, 시차를 두고 출발한다, 평소보다 빨리 온다, 그리고
 * **박제되지 않는다** (앉아 있다가 다시 돌아다닌다). 마지막이 제일 중요하다. 마중을
 * 넣었더니 방이 멈춰 버리면 연출이 아니라 고장이다.
 */
class DogGreetTest {

    private val spot = Offset(2.0f, 9.0f)

    /** 16ms 씩 [untilMs] 까지 돌리며 매 프레임 [each] 를 부른다. */
    private fun run(herd: DogHerd, from: Long, untilMs: Long, each: (Long) -> Unit = {}) {
        var t = from
        while (t < untilMs) {
            t += 16L
            herd.update(t)
            each(t)
        }
    }

    private fun settled(herd: DogHerd): DogHerd {
        herd.update(0L)
        herd.update(16L)
        return herd
    }

    @Test
    fun `모두 문 앞으로 와서 앉는다`() {
        val herd = settled(DogHerd(count = 3))
        herd.greet(spot, nowMs = 16L)
        // 평소 걸음(칸/초 0.4)이면 반대편에서 30초다. 마중 걸음이면 문 반대편 모서리에서도
        // 7초 안쪽이고, 먼저 온 아이는 그동안 앉아서 기다린다([DogHerd.GREET_SIT_MS]).
        val sat = BooleanArray(herd.dogs.size)
        run(herd, 16L, 8_000L) {
            herd.dogs.forEachIndexed { i, d ->
                if ((d.pos - spot).getDistance() < 2.2f && !d.moving) sat[i] = true
            }
        }
        assertTrue("8초 안에 문 앞에 앉지 못한 아이가 있다: ${herd.dogs.map { it.pos }}", sat.all { it })
    }

    @Test
    fun `시차를 두고 출발한다`() {
        val herd = settled(DogHerd(count = 3))
        herd.greet(spot, nowMs = 16L)
        val firstMove = LongArray(3) { -1L }
        run(herd, 16L, 3_000L) { t ->
            herd.dogs.forEachIndexed { i, d -> if (d.moving && firstMove[i] < 0) firstMove[i] = t }
        }
        assertTrue("출발 안 한 아이가 있다: ${firstMove.toList()}", firstMove.all { it > 0 })
        assertTrue("시차가 없다: ${firstMove.toList()}", firstMove[0] < firstMove[1] && firstMove[1] < firstMove[2])
        val gap = firstMove[1] - firstMove[0]
        assertTrue("시차 $gap ms — 너무 짧거나 길다", gap in (DogHerd.GREET_STAGGER_MS - 40)..(DogHerd.GREET_STAGGER_MS + 40))
    }

    @Test
    fun `마중은 평소보다 빠르다`() {
        val herd = settled(DogHerd(count = 1))
        val dog = herd.dogs[0]
        // 멀리서 출발시킨다
        dog.pos = Offset(10f, 2f)
        herd.greet(spot, nowMs = 16L)
        val start = dog.pos
        run(herd, 16L, 16L + 400L)
        val travelled = (dog.pos - start).getDistance()
        val normal = dog.speed * 0.4f
        assertTrue(
            "0.4초에 $travelled 칸 — 평소(${normal})보다 빨라야 한다",
            travelled > normal * 1.3f,
        )
    }

    @Test
    fun `앉아 있다가 다시 돌아다닌다 - 박제되지 않는다`() {
        val herd = settled(DogHerd(count = 2))
        herd.greet(spot, nowMs = 16L)
        // 마리마다 따로 잰다 — 먼저 온 아이의 앉는 시간은 나중 온 아이와 겹치지 않는다.
        val arrivedAt = LongArray(herd.dogs.size) { -1L }
        var walkedWhileSitting = false
        var wandered = false
        run(herd, 16L, 40_000L) { t ->
            herd.dogs.forEachIndexed { i, d ->
                if (arrivedAt[i] < 0 && !d.greeting && !d.moving) arrivedAt[i] = t
                val a = arrivedAt[i]
                if (a > 0 && t < a + DogHerd.GREET_SIT_MS - 200L && d.moving) walkedWhileSitting = true
                if (a > 0 && (d.pos - spot).getDistance() > 3f) wandered = true
            }
        }
        assertTrue("도착을 못 한 아이가 있다: ${arrivedAt.toList()}", arrivedAt.all { it > 0 })
        assertFalse("앉아 있어야 할 때 걸었다", walkedWhileSitting)
        assertTrue("마중 뒤 40초 동안 아무도 문 앞을 안 떠났다", wandered)
        assertFalse(herd.dogs.any { it.greeting })
    }

    @Test
    fun `마중 가다 잡히면 마중을 잊는다 - 놓아도 문 앞으로 안 뛴다`() {
        val herd = settled(DogHerd(count = 1))
        val dog = herd.dogs[0]
        dog.pos = Offset(10f, 3f)
        herd.greet(spot, nowMs = 16L)
        run(herd, 16L, 300L)
        assertTrue(dog.greeting)
        herd.draggingId = dog.id
        run(herd, 300L, 500L)
        assertFalse("잡혀 있는데 마중 깃발이 남았다", dog.greeting)
        herd.draggingId = null
        val at = dog.pos
        run(herd, 500L, 900L)
        val travelled = (dog.pos - at).getDistance()
        assertTrue("놓인 뒤에도 마중 걸음으로 뛰었다: $travelled", travelled <= dog.speed * 0.4f * 1.05f)
    }

    @Test
    fun `손에 잡힌 아이는 안 온다`() {
        val herd = settled(DogHerd(count = 2))
        val held = herd.dogs[1]
        held.pos = Offset(10f, 3f)
        herd.draggingId = held.id
        herd.greet(spot, nowMs = 16L)
        run(herd, 16L, 3_000L)
        assertEquals(Offset(10f, 3f), held.pos)
        assertFalse(held.greeting)
    }

    @Test
    fun `쉬고 있던 아이도 바로 일어난다`() {
        val herd = settled(DogHerd(count = 1))
        val dog = herd.dogs[0]
        dog.pos = Offset(9f, 3f)
        dog.restUntil = 60_000L // 한참 쉴 참이었다
        herd.greet(spot, nowMs = 16L)
        var moved = false
        run(herd, 16L, 500L) { if (dog.moving) moved = true }
        assertTrue("쉬는 시간이 끝날 때까지 안 움직였다", moved)
    }
}
