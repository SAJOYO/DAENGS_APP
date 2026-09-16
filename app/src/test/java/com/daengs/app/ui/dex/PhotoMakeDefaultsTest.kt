package com.daengs.app.ui.dex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoMakeDefaultsTest {

    @Test
    fun `잠긴 칸을 눌러 왔으면 그 달이다`() {
        assertEquals(4, defaultPhotoMonth(start = 4, today = 9))
    }

    @Test
    fun `이번 달이 열려 있으면 이번 달이다`() {
        assertEquals(9, defaultPhotoMonth(start = null, today = 9))
    }

    /** 닫힌 달을 기본으로 고르면 누르자마자 404 가 난다. */
    @Test
    fun `이번 달이 닫혔으면 열린 첫 달이다`() {
        assertEquals(4, defaultPhotoMonth(start = null, today = 1))
        assertEquals(4, defaultPhotoMonth(start = 12, today = 1))
    }

    @Test
    fun `대표 강아지가 먼저다`() {
        val dogs = listOf(PhotoDog("a", "콩이", false), PhotoDog("b", "보리", true))
        assertEquals("b", defaultPhotoDog(dogs)?.id)
        assertEquals("a", defaultPhotoDog(dogs.take(1))?.id)
        assertNull(defaultPhotoDog(emptyList()))
    }
}
