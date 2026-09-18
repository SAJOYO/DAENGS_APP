package com.daengs.app.ui.dex

import com.daengs.app.dogcard.photo.PhotoCardKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoMakeDefaultsTest {

    /** 규칙은 연 달이 몇 개든 같다. 닫힌 달이 있는 경우를 보려고 옛 목록(4·9월)을 인자로 준다. */
    private val someOpen = setOf(4, 9)

    private fun m(month: Int) = PhotoCardKey.of(month)

    @Test
    fun `잠긴 칸을 눌러 왔으면 그 카드다`() {
        assertEquals(m(4), defaultPhotoCard(start = m(4), today = 9, openMonths = someOpen))
    }

    /** 종류 카드로 들어왔으면 그대로 쓴다 — 종류에는 닫힘이 없다 (D-085). */
    @Test
    fun `종류 칸을 눌러 왔으면 그 종류다`() {
        assertEquals(
            PhotoCardKey.Strawberry,
            defaultPhotoCard(start = PhotoCardKey.Strawberry, today = 9, openMonths = someOpen),
        )
    }

    /** 앱이 모르는 종류로는 못 간다 — 칸이 없어서 화면에 그릴 수가 없다. */
    @Test
    fun `모르는 종류로 들어오면 이번 달로 떨어진다`() {
        assertEquals(m(9), defaultPhotoCard(start = PhotoCardKey("tomato"), today = 9, openMonths = someOpen))
    }

    @Test
    fun `이번 달이 열려 있으면 이번 달이다`() {
        assertEquals(m(9), defaultPhotoCard(start = null, today = 9, openMonths = someOpen))
    }

    /** 닫힌 달을 기본으로 고르면 누르자마자 404 가 난다. */
    @Test
    fun `이번 달이 닫혔으면 열린 첫 달이다`() {
        assertEquals(m(4), defaultPhotoCard(start = null, today = 1, openMonths = someOpen))
        assertEquals(m(4), defaultPhotoCard(start = m(12), today = 1, openMonths = someOpen))
    }

    @Test
    fun `12달이 다 열렸으면 이번 달이 기본이다`() {
        assertEquals(m(1), defaultPhotoCard(start = null, today = 1))
        assertEquals(m(12), defaultPhotoCard(start = m(12), today = 1))
    }

    /** 고를 수 있는 것 — 열린 달 + 앱이 아는 종류. */
    @Test
    fun `고를 수 있는 카드를 가린다`() {
        assertTrue(photoCardSelectable(m(4), someOpen))
        assertFalse(photoCardSelectable(m(12), someOpen))
        assertFalse(photoCardSelectable(m(13)))
        assertTrue(photoCardSelectable(PhotoCardKey.Lettuce, someOpen))
        assertFalse(photoCardSelectable(PhotoCardKey("tomato")))
    }

    /** 달이 앞, 종류가 뒤 — 칸 순서와 같다. */
    @Test
    fun `고를 수 있는 카드는 달 다음에 종류다`() {
        assertEquals(
            listOf(m(4), m(9), PhotoCardKey.Strawberry, PhotoCardKey.Lettuce),
            photoSelectableCards(someOpen),
        )
        assertEquals(14, photoSelectableCards().size)
    }

    /** 종류는 이 격자에 안 들어간다 — 달력 모양이 깨진다 (docs §10.2). */
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
