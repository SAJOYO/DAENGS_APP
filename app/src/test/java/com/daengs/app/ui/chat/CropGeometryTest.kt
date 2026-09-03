package com.daengs.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 자르는 네모의 셈.
 *
 * **손짓은 기기에서 봐야 알지만 좌표는 눈으로 못 잡는다.** 오른쪽 아래 모서리만
 * 손잡이였던 것이 그런 종류였다 — 다른 세 모서리를 끌면 네모가 조용히 이동만 했다.
 */
class CropGeometryTest {

    /** 세로가 가로보다 긴 사진 (aspect = 가로/세로 < 1 이 아니라 여기선 h/w 배수다). */
    private val square = 1f

    @Test
    fun `네 모서리가 다 손잡이다`() {
        val box = CropBox(0.3f, 0.3f, 0.4f)
        val grab = 0.07f
        val corners = listOf(
            0.3f to 0.3f,   // 왼쪽 위
            0.7f to 0.3f,   // 오른쪽 위
            0.3f to 0.7f,   // 왼쪽 아래
            0.7f to 0.7f,   // 오른쪽 아래 — 예전에는 이 하나만 됐다
        )
        corners.forEach { (x, y) ->
            assertTrue("($x, $y) 가 손잡이여야 한다", grabsCorner(x, y, box, square, grab))
        }
    }

    @Test
    fun `가운데를 짚으면 이동이다`() {
        val box = CropBox(0.3f, 0.3f, 0.4f)
        assertFalse(grabsCorner(0.5f, 0.5f, box, square, 0.07f))
    }

    /**
     * **중심이 안 움직여야 한다.** 모서리를 기준으로 늘리면 크기를 맞추는 동안
     * 가운데 정렬이 풀려서 "가운데에서 벗어났어요" 에 계속 걸린다.
     */
    @Test
    fun `크기를 바꿔도 중심이 그대로다`() {
        val box = CropBox(0.3f, 0.3f, 0.4f)
        val bigger = resizeAroundCenter(box, square, 0.6f)
        assertEquals(0.5f, bigger.x + bigger.w / 2f, 0.001f)
        assertEquals(0.5f, bigger.y + heightOf(bigger.w, square) / 2f, 0.001f)
    }

    @Test
    fun `최소보다 작게는 못 줄인다`() {
        val box = CropBox(0.4f, 0.4f, 0.2f)
        assertEquals(MIN_CROP_WIDTH, resizeAroundCenter(box, square, 0.01f).w, 0.001f)
    }

    /**
     * 네모가 가장자리에 붙어 있으면 남은 여유가 최소 폭보다 작을 수 있다.
     * **위아래가 뒤집힌 범위를 주면 `coerceIn` 이 던진다** — 그래서 안 뒤집는다.
     */
    @Test
    fun `가장자리에 붙어 있어도 안 던진다`() {
        val box = CropBox(0f, 0f, 0.12f)
        val grown = resizeAroundCenter(box, square, 0.9f)
        assertTrue("가로 ${grown.w}", grown.w >= MIN_CROP_WIDTH)
        assertTrue("왼쪽 ${grown.x}", grown.x >= 0f)
    }

    @Test
    fun `사진 밖으로는 못 나간다`() {
        val box = CropBox(0.8f, 0.8f, 0.2f)
        val moved = moveBy(box, square, 0.5f, 0.5f)
        assertEquals(0.8f, moved.x, 0.001f)
        assertEquals(0.8f, moved.y, 0.001f)
    }

    @Test
    fun `가운데에서 벗어난 정도를 잰다`() {
        assertEquals(0f, centerOffset(CropBox(0.3f, 0.3f, 0.4f), square), 0.001f)
        assertEquals(0.2f, centerOffset(CropBox(0.5f, 0.3f, 0.4f), square), 0.001f)
    }
}
