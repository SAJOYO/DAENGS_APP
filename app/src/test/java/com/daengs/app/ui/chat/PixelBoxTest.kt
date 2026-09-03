package com.daengs.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 확정한 네모를 **그림 안 픽셀 자리**로 바꾸는 셈.
 *
 * 이 값으로 말풍선에 올릴 사진을 자른다. 한 픽셀이라도 그림 밖을 가리키면
 * `Bitmap.createBitmap` 이 그 자리에서 던진다 — 사진을 보내려다 대화가 죽는다.
 * 그래서 **어떤 네모가 와도 그림 안에 들어오는지**를 잡는다.
 */
class PixelBoxTest {

    @Test
    fun `가운데 절반을 고르면 그 자리가 나온다`() {
        val at = pixelBoxOf(floatArrayOf(0.25f, 0.25f, 0.5f, 0.5f), 400, 200)!!

        assertEquals(100, at.left)
        assertEquals(50, at.top)
        assertEquals(200, at.width)
        assertEquals(100, at.height)
    }

    @Test
    fun `그림 전체면 그림 크기 그대로다`() {
        val at = pixelBoxOf(floatArrayOf(0f, 0f, 1f, 1f), 300, 500)!!

        assertEquals(0, at.left)
        assertEquals(0, at.top)
        assertEquals(300, at.width)
        assertEquals(500, at.height)
    }

    @Test
    fun `오른쪽 아래로 넘치면 그림 안으로 잘린다`() {
        val at = pixelBoxOf(floatArrayOf(0.8f, 0.8f, 0.5f, 0.5f), 100, 100)!!

        assertTrue("오른쪽 끝이 그림 안", at.left + at.width <= 100)
        assertTrue("아래 끝이 그림 안", at.top + at.height <= 100)
        assertEquals(20, at.width)
        assertEquals(20, at.height)
    }

    @Test
    fun `왼쪽 위로 넘쳐도 음수가 안 나온다`() {
        val at = pixelBoxOf(floatArrayOf(-0.5f, -0.5f, 0.4f, 0.4f), 100, 100)!!

        assertEquals(0, at.left)
        assertEquals(0, at.top)
        assertTrue(at.width >= 1)
        assertTrue(at.height >= 1)
    }

    @Test
    fun `가로세로가 0 이어도 최소 한 픽셀은 남는다`() {
        // `Bitmap.createBitmap` 은 폭이나 높이가 0 이면 던진다.
        val at = pixelBoxOf(floatArrayOf(0.5f, 0.5f, 0f, 0f), 100, 100)!!

        assertEquals(1, at.width)
        assertEquals(1, at.height)
    }

    @Test
    fun `오른쪽 끝에 붙어 있어도 한 픽셀은 남는다`() {
        val at = pixelBoxOf(floatArrayOf(1f, 1f, 0.5f, 0.5f), 100, 100)!!

        assertEquals(99, at.left)
        assertEquals(99, at.top)
        assertEquals(1, at.width)
        assertEquals(1, at.height)
    }

    @Test
    fun `그림이 비었으면 자를 자리가 없다`() {
        assertNull(pixelBoxOf(floatArrayOf(0f, 0f, 1f, 1f), 0, 100))
        assertNull(pixelBoxOf(floatArrayOf(0f, 0f, 1f, 1f), 100, 0))
    }

    @Test
    fun `네모가 모자라게 오면 자를 자리가 없다`() {
        assertNull(pixelBoxOf(floatArrayOf(0f, 0f, 1f), 100, 100))
    }

    @Test
    fun `가이드가 권장하는 네모는 그림 안에 들어온다`() {
        // 실제로 오는 값의 모양. 권장 밴드 한가운데를 사진 정중앙에 둔 네모다.
        val w = Band.CAPTURE_WIDTH
        val at = pixelBoxOf(floatArrayOf(0.5f - w / 2f, 0.5f - w / 2f, w, w), 1080, 1440)!!

        assertTrue(at.left >= 0 && at.top >= 0)
        assertTrue(at.left + at.width <= 1080)
        assertTrue(at.top + at.height <= 1440)
    }
}
