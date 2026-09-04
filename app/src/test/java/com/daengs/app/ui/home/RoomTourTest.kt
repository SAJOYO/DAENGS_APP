package com.daengs.app.ui.home

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 방 둘러보기의 셈.
 *
 * 밝히는 자리 자체는 방이 알려 주므로 여기서 잡을 것이 없다. 여기서 잡는 것은
 * **말풍선이 구멍을 가리지 않는가** 와 **단계가 빠지지 않았는가** 다.
 */
class RoomTourTest {

    private val screen = 2400f
    private val caption = 400f
    private val gap = 40f

    @Test
    fun `자리가 남으면 구멍 아래에 둔다`() {
        val hole = Rect(100f, 600f, 400f, 900f)

        val top = tourCaptionTop(hole, screen, caption, gap)

        assertEquals(hole.bottom + gap, top, 0.01f)
        assertTrue("구멍을 안 가린다", top >= hole.bottom)
    }

    @Test
    fun `아래가 모자라면 위로 올린다`() {
        // 하단바를 가리킬 때가 이 경우다. 아래에 두면 화면 밖으로 나간다.
        val hole = Rect(100f, 2200f, 400f, 2360f)

        val top = tourCaptionTop(hole, screen, caption, gap)

        assertTrue("화면 안이어야 한다", top + caption <= screen)
        assertTrue("구멍을 안 가린다", top + caption <= hole.top)
    }

    @Test
    fun `위아래 다 모자라면 화면 밖으로는 안 나간다`() {
        // 구멍이 화면을 거의 다 차지하는 극단. 가리더라도 읽을 수는 있어야 한다.
        val hole = Rect(0f, 10f, 1080f, 2390f)

        val top = tourCaptionTop(hole, screen, caption, gap)

        assertTrue("위로 안 넘친다", top >= 0f)
    }

    @Test
    fun `다섯 단계가 다 있고 자리가 겹치지 않는다`() {
        assertEquals(5, TOUR_STEPS.size)
        assertEquals(TOUR_STEPS.size, TOUR_STEPS.map { it.stop }.toSet().size)
        TOUR_STEPS.forEach { assertTrue("할 말이 있어야 한다", it.text.isNotBlank()) }
    }

    @Test
    fun `방 안을 먼저 보고 방 밖으로 나간다`() {
        // 눈이 방과 화면 아래를 오가면 어지럽다. 방 셋이 앞에 모여 있어야 한다.
        val inRoom = setOf(TourStop.Door, TourStop.Turntable, TourStop.Frame)
        val firstOutside = TOUR_STEPS.indexOfFirst { it.stop !in inRoom }

        assertEquals(3, firstOutside)
        assertTrue(TOUR_STEPS.take(3).all { it.stop in inRoom })
    }

    @Test
    fun `자리를 등록하면 그 자리가 나온다`() {
        val spots = TourSpots()
        assertEquals(null, spots[TourStop.Door])

        val rect = Rect(1f, 2f, 3f, 4f)
        spots.put(TourStop.Door, rect)

        assertEquals(rect, spots[TourStop.Door])
        assertNotNull(spots[TourStop.Door])
        // 등록 안 한 자리는 없는 채로 남는다 — 겹이 그 단계를 건너뛴다.
        assertEquals(null, spots[TourStop.Storage])
    }
}
