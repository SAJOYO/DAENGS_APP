package com.daengs.app.ui.dex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 포토 열두 장이 서버 카탈로그(`daengs_cardimage/catalog.py`)와 어긋나지 않게 잡는다. */
class PhotoCardsTest {

    @Test
    fun `열두 달이 한 장씩 순서대로다`() {
        assertEquals((1..12).toList(), PHOTO_CARDS.map { it.no })
        assertTrue(PHOTO_CARDS.all { it.deck == DexDeck.Photo })
        assertEquals("photo-04", PHOTO_CARDS[3].id)
    }

    /** 제목판 카드명은 서버 것 그대로다. 9월은 사용자가 CHUSEOK 으로 정했다. */
    @Test
    fun `카드명이 서버와 같다`() {
        assertEquals(
            listOf("NEW YEAR", "LOVE", "FIRST DAY", "BLOSSOM", "HOME TEAM", "POOL",
                "BEACH", "RAIN", "CHUSEOK", "GHOST", "THANKS", "SANTA"),
            PHOTO_CARDS.map { it.name },
        )
    }

    @Test
    fun `야채 과일 id 와 겹치지 않는다`() {
        val ids = (DEX_CARDS + PHOTO_CARDS).map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    /** 서버 `DAENGS_CARDIMAGE_MONTHS` 기본값이 12달이 됐다 (`DAENGS_dev` #572). */
    @Test
    fun `연 달은 1월부터 12월까지다`() {
        assertEquals((1..12).toSet(), OPEN_PHOTO_MONTHS)
    }

    /** 효과를 나중에 손볼 자리. 한 달이라도 비면 그 달만 포일이 없다. */
    @Test
    fun `포일 표가 열두 달을 다 가진다`() {
        assertEquals((1..12).toSet(), PHOTO_FOIL.keys)
        PHOTO_CARDS.forEach { assertEquals(PHOTO_FOIL.getValue(it.no).foil, it.foil) }
    }

    @Test
    fun `달로 카드를 찾는다`() {
        assertEquals("BLOSSOM", photoCardFor(4)?.name)
        assertNull(photoCardFor(13))
    }

    @Test
    fun `포토 설명 줄은 번호 카드명 닮음이다`() {
        val rows = photoCardFor(4)!!.photoDetailRows(likeness = 4)
        assertEquals(listOf("No.", "Card", "Likeness"), rows.map { it.label })
        assertEquals("04 / 12", rows[0].value)
        assertEquals("BLOSSOM", rows[1].value)
        assertEquals("★★★★☆", rows[2].value)
    }

    @Test
    fun `닮음을 모르면 그 줄이 빠진다`() {
        assertEquals(listOf("No.", "Card"), photoCardFor(9)!!.photoDetailRows(likeness = null).map { it.label })
    }

    @Test
    fun `포토 캡션은 달 이름뿐이고 야채는 수치까지다`() {
        assertEquals("4월 벚꽃", photoCardFor(4)!!.gridCaption)
        assertEquals("배추 · CRUNCH 820", DEX_CARDS.first().gridCaption)
        assertTrue(photoCardFor(4)!!.isPhoto)
    }
}
