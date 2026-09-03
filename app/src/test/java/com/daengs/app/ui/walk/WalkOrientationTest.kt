package com.daengs.app.ui.walk

import org.junit.Assert.assertEquals
import org.junit.Test

class WalkOrientationTest {
    @Test
    fun `세로 창은 세로 배치를 고른다`() {
        assertEquals(WalkLayoutMode.PORTRAIT, walkLayoutMode(widthDp = 411f, heightDp = 891f))
    }

    @Test
    fun `가로 창은 가로 배치를 고른다`() {
        assertEquals(WalkLayoutMode.LANDSCAPE, walkLayoutMode(widthDp = 891f, heightDp = 411f))
    }

    @Test
    fun `정사각형 창은 넓은 배치를 고른다`() {
        assertEquals(WalkLayoutMode.LANDSCAPE, walkLayoutMode(widthDp = 600f, heightDp = 600f))
    }

    @Test
    fun `회전 요청은 실제 배치의 반대 방향이다`() {
        assertEquals(WalkOrientation.LANDSCAPE, WalkLayoutMode.PORTRAIT.oppositeOrientation)
        assertEquals(WalkOrientation.PORTRAIT, WalkLayoutMode.LANDSCAPE.oppositeOrientation)
    }
}
