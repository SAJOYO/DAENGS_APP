package com.daengs.app.ui.dex

import com.daengs.app.dogcard.photo.PhotoCardKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 포토 열넷이 서버 카탈로그(`daengs_cardimage/catalog.py`)와 어긋나지 않게 잡는다. */
class PhotoCardsTest {

    @Test
    fun `열두 달 다음에 종류가 순서대로다`() {
        assertEquals((1..14).toList(), PHOTO_CARDS.map { it.no })
        assertTrue(PHOTO_CARDS.all { it.deck == DexDeck.Photo })
        assertEquals("photo-04", PHOTO_CARDS[3].id)
    }

    /**
     * **종류 칸 id 는 번호가 아니라 키다.** 저쪽이 종류를 중간에 끼워 넣어 번호가 밀려도
     * 이미 가진 카드가 다른 칸으로 옮겨 가면 안 된다 (docs/photo-cards.md §10.2).
     */
    @Test
    fun `종류 칸은 키로 id 를 짓는다`() {
        assertEquals("photo-strawberry", photoCardFor(PhotoCardKey.Strawberry)?.id)
        assertEquals("photo-lettuce", photoCardFor(PhotoCardKey.Lettuce)?.id)
    }

    /** 제목판 카드명은 서버 것 그대로다. 9월은 사용자가 CHUSEOK 으로, 딸기는 저쪽이 BERRY 로 정했다. */
    @Test
    fun `카드명이 서버와 같다`() {
        assertEquals(
            listOf("NEW YEAR", "LOVE", "FIRST DAY", "BLOSSOM", "HOME TEAM", "POOL",
                "BEACH", "RAIN", "CHUSEOK", "GHOST", "THANKS", "SANTA", "BERRY", "LETTUCE"),
            PHOTO_CARDS.map { it.name },
        )
    }

    /** 한국어 이름은 저쪽 `catalog.KIND_LABELS` 와 같아야 한다 — 서버 409 문장이 그 글자를 쓴다. */
    @Test
    fun `종류 이름이 서버 한국어 표기와 같다`() {
        assertEquals("딸기", PhotoCardKey.Strawberry.label)
        assertEquals("상추", PhotoCardKey.Lettuce.label)
        assertEquals("딸기", photoCardFor(PhotoCardKey.Strawberry)?.ko)
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

    /** 효과를 나중에 손볼 자리. 한 칸이라도 비면 그 칸만 포일이 없다. */
    @Test
    fun `포일 표가 열넷을 다 가진다`() {
        assertEquals((1..14).toSet(), PHOTO_FOIL.keys)
        PHOTO_CARDS.forEach { assertEquals(PHOTO_FOIL.getValue(it.no).foil, it.foil) }
    }

    @Test
    fun `카드 키로 카드를 찾는다`() {
        assertEquals("BLOSSOM", photoCardFor(PhotoCardKey.of(4))?.name)
        assertEquals("BERRY", photoCardFor(PhotoCardKey.Strawberry)?.name)
        assertNull(photoCardFor(PhotoCardKey("13")))
        assertNull(photoCardFor(PhotoCardKey("tomato")))
    }

    /** 칸에서 키를 되찾는다 — 잠긴 칸을 눌러 만들러 갈 때 쓴다. */
    @Test
    fun `칸이 제 카드 키를 안다`() {
        assertEquals(PhotoCardKey.of(4), photoCardFor(PhotoCardKey.of(4))?.photoKey)
        assertEquals(PhotoCardKey.Lettuce, photoCardFor(PhotoCardKey.Lettuce)?.photoKey)
        assertNull(DEX_CARDS.first().photoKey)
    }

    @Test
    fun `포토 설명 줄은 번호 카드명 닮음이다`() {
        val rows = photoCardFor(PhotoCardKey.of(4))!!.photoDetailRows(likeness = 4)
        assertEquals(listOf("No.", "Card", "Likeness"), rows.map { it.label })
        assertEquals("04 / 14", rows[0].value)
        assertEquals("BLOSSOM", rows[1].value)
        assertEquals("★★★★☆", rows[2].value)
    }

    @Test
    fun `닮음을 모르면 그 줄이 빠진다`() {
        assertEquals(
            listOf("No.", "Card"),
            photoCardFor(PhotoCardKey.of(9))!!.photoDetailRows(likeness = null).map { it.label },
        )
    }

    @Test
    fun `포토 캡션은 달 이름뿐이고 야채는 수치까지다`() {
        assertEquals("4월 벚꽃", photoCardFor(PhotoCardKey.of(4))!!.gridCaption)
        assertEquals("딸기", photoCardFor(PhotoCardKey.Strawberry)!!.gridCaption)
        assertEquals("배추 · CRUNCH 820", DEX_CARDS.first().gridCaption)
        assertTrue(photoCardFor(PhotoCardKey.of(4))!!.isPhoto)
    }
}
