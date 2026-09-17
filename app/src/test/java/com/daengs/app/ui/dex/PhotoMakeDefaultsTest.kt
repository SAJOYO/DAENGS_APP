package com.daengs.app.ui.dex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoMakeDefaultsTest {

    /** 규칙은 연 달이 몇 개든 같다. 닫힌 달이 있는 경우를 보려고 옛 목록(4·9월)을 인자로 준다. */
    private val someOpen = setOf(4, 9)

    @Test
    fun `잠긴 칸을 눌러 왔으면 그 달이다`() {
        assertEquals(4, defaultPhotoMonth(start = 4, today = 9, open = someOpen))
    }

    @Test
    fun `이번 달이 열려 있으면 이번 달이다`() {
        assertEquals(9, defaultPhotoMonth(start = null, today = 9, open = someOpen))
    }

    /** 닫힌 달을 기본으로 고르면 누르자마자 404 가 난다. */
    @Test
    fun `이번 달이 닫혔으면 열린 첫 달이다`() {
        assertEquals(4, defaultPhotoMonth(start = null, today = 1, open = someOpen))
        assertEquals(4, defaultPhotoMonth(start = 12, today = 1, open = someOpen))
    }

    @Test
    fun `12달이 다 열렸으면 이번 달이 기본이다`() {
        assertEquals(1, defaultPhotoMonth(start = null, today = 1))
        assertEquals(12, defaultPhotoMonth(start = 12, today = 1))
    }

    @Test
    fun `달 칸은 한 줄에 넷씩 세 줄이다`() {
        assertEquals(
            listOf(listOf(1, 2, 3, 4), listOf(5, 6, 7, 8), listOf(9, 10, 11, 12)),
            photoMonthRows(OPEN_PHOTO_MONTHS),
        )
    }

    /** 연 달이 적으면 줄도 준다. 카탈로그에 없는 달은 칸이 안 생긴다. */
    @Test
    fun `연 달이 적으면 첫 줄만 채운다`() {
        assertEquals(listOf(listOf(4, 9)), photoMonthRows(setOf(9, 4, 13)))
    }

    @Test
    fun `대표 강아지가 먼저다`() {
        val dogs = listOf(PhotoDog("a", "콩이", false), PhotoDog("b", "보리", true))
        assertEquals("b", defaultPhotoDog(dogs)?.id)
        assertEquals("a", defaultPhotoDog(dogs.take(1))?.id)
        assertNull(defaultPhotoDog(emptyList()))
    }
}
