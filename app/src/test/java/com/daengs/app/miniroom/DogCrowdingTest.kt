package com.daengs.app.miniroom

import androidx.compose.ui.unit.IntOffset
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.miniroom.art.ItemBoxes
import com.daengs.app.miniroom.art.footprintFacing
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **한 계정 몇 마리까지인가.** 그 숫자를 여기서 정했다.
 *
 * 등록한 강아지가 방에 서게 되면서, 상한은 "몇 마리가 자연스러운가"가 아니라
 * **12x12 격자에 가구가 찬 방에 몇 마리까지 담기나**가 된다. 그래서 눈으로 보지 않고
 * 쟀다 — 기본 배치(막힌 칸 27 / 144)에서 마리 수를 늘려가며, **한 프레임에 그림이
 * 겹쳐 보이는 쌍이 평균 몇 개인지**를 셌다 (시드 12판 x 3000프레임).
 *
 * ```
 * 마리   겹치는 쌍 평균   겹치는 프레임 %   최근접 평균(칸)
 *   2        0.06              5.5             5.08
 *   4        0.27             24.3             3.01   <- 예전 고정값
 *   5        0.50             42.4             2.79   <- 지금 상한
 *   6        0.61             47.9             2.51
 *   7        0.82             53.5             2.40
 *   8        1.15             72.8             2.20   <- 여기서 1을 넘는다
 *  10        2.35             95.3             1.94
 *  12        3.69             93.8             1.83
 * ```
 *
 * **8마리에서 1을 넘는다** — 언제 봐도 어딘가 두 마리가 겹쳐 있다는 뜻이다.
 * 5마리는 0.50 으로 예전 4마리(0.27)의 두 배지만 아직 "가끔"이다. 그래서 **5로 둔다.**
 * 늘린다면 7이 한계고, 8부터는 방을 넓히거나 마리당 자리를 다시 봐야 한다.
 *
 * 재는 것이 **몸 반경이 아니라 스프라이트 폭**인 게 중요하다. 몸 반경으로 재면
 * 12마리도 겹침이 0.1% 라 "괜찮다"고 나오는데, 화면에서는 이미 뭉쳐 보인다 —
 * 그림 폭(13% 기준 1.56칸)이 [DogHerd.MIN_DOG_GAP](1.3칸)보다 넓어서,
 * 목적지를 규칙대로 벌려도 그림은 겹친다.
 */
class DogCrowdingTest {

    /** 서버의 `MAX_PETS_PER_USER` 와 같은 값. 여기가 그 숫자의 근거다. */
    private val cap = 5

    /** 화면에서 겹쳐 보이는 폭(격자 칸). **몸 반경이 아니라 그림 폭**이다. */
    private fun spriteCells(b: DogBreed) = b.visualWidth / 100f * RoomSpec.GRID

    /** 기본 배치가 막는 칸. 소품을 다 꺼낸 빈 방이 아니라 **처음 보는 방**에서 잰다. */
    private fun blockedCells(): Set<IntOffset> = buildSet {
        for (item in RoomDefaults.STARTER_ROOM) {
            val box = ItemBoxes[item.itemId] ?: continue
            if (box.flat) continue
            val fp = box.footprintFacing(item.facing)
            for (dc in 0 until fp.width) for (dr in 0 until fp.height) {
                add(IntOffset(item.col + dc, item.row + dr))
            }
        }
    }

    /** 한 프레임에 그림이 겹쳐 보이는 쌍의 평균. */
    private fun overlappingPairsPerFrame(n: Int, seeds: Int = 6): Double {
        val blocked = blockedCells()
        var frames = 0
        var pairs = 0.0
        for (seed in 1..seeds) {
            val herd = DogHerd(List(n) { DogBreed.ALL[it % DogBreed.ALL.size] }, seed = seed)
            var t = 0L
            herd.update(t, blocked)
            repeat(2000) {
                t += 16L
                herd.update(t, blocked)
                if (t % 160L != 0L) return@repeat
                frames++
                val dogs = herd.dogs
                for (i in dogs.indices) for (j in i + 1 until dogs.size) {
                    val gap = (spriteCells(dogs[i].breed) + spriteCells(dogs[j].breed)) / 2f
                    if ((dogs[i].pos - dogs[j].pos).getDistance() < gap) pairs++
                }
            }
        }
        return pairs / frames
    }

    @Test
    fun `상한만큼 세워도 방이 뭉쳐 보이지 않는다`() {
        val crowd = overlappingPairsPerFrame(cap)
        assertTrue(
            "${cap}마리에서 겹치는 쌍이 평균 %.2f — 1을 넘으면 언제 봐도 어딘가 겹쳐 있다".format(crowd),
            crowd < 1.0,
        )
    }

    /**
     * 상한 두 배는 확실히 뭉친다. **상한이 임의값이 아니라는 것**을 이 대조군이 잡는다 —
     * 위 검사만 있으면 방을 넓히지 않고 상한만 올려도 통과할 수 있다.
     */
    @Test
    fun `상한 두 배는 뭉쳐 보인다`() {
        assertTrue(overlappingPairsPerFrame(cap * 2) > 1.0)
    }
}
