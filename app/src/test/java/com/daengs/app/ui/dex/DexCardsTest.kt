package com.daengs.app.ui.dex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 카드 12장이 정본(`SAJOYO/DAENGS_CARDS` 의 `cards.mjs`)과 어긋나지 않게 잡는다.
 *
 * **글이 빠지는 것을 잡는 게 목적이다.** 카드 그림에는 제목·기술·수치가 구워져 있어서
 * 한 장의 설명이 통째로 비어도 그리드에서는 멀쩡해 보인다 — 그래서 예전에 설명 시트가
 * 통째로 빠진 것도 한동안 아무도 몰랐다.
 */
class DexCardsTest {

    @Test
    fun `열두 장이고 번호가 하나씩이다`() {
        assertEquals(12, DEX_CARDS.size)
        assertEquals((1..12).toList(), DEX_CARDS.map { it.no })
    }

    /** 한 장이라도 비면 그 카드만 설명이 텅 빈다. */
    @Test
    fun `모든 카드에 글이 다 있다`() {
        DEX_CARDS.forEach { card ->
            val where = "No.${card.no} ${card.id}"
            assertTrue("$where tagline", card.tagline.isNotBlank())
            assertTrue("$where ko", card.ko.isNotBlank())
            assertTrue("$where code", card.code.isNotBlank())
            assertTrue("$where type", card.type.isNotBlank())
            assertTrue("$where move", card.move.isNotBlank())
            assertTrue("$where flavor", card.flavor.isNotBlank())
            assertTrue("$where edition", card.edition.isNotBlank())
        }
    }

    /** 에셋 경로가 id 에서 나온다. 대문자나 밑줄이 섞이면 그림을 못 찾는다. */
    @Test
    fun `id 는 소문자와 붙임표뿐이다`() {
        DEX_CARDS.forEach { card ->
            assertTrue(card.id, card.id.matches(Regex("[a-z]+(-[a-z]+)*")))
            assertEquals("neo-hologram/art/${card.id}.webp", card.art)
        }
    }

    // -- 설명 시트의 표 ----------------------------------------------------

    @Test
    fun `표는 웹판과 같은 순서다`() {
        val labels = DEX_CARDS.first().detailRows().map { it.label }
        assertEquals(listOf("No.", "Code", "Type", "Move", "CRUNCH"), labels)
    }

    @Test
    fun `번호는 두 자리다`() {
        assertEquals("01 / 12", DEX_CARDS.first().detailRows().first().value)
        assertEquals("12 / 12", DEX_CARDS.last().detailRows().first().value)
    }

    /**
     * No.11 토마토만 스탯 바에 라벨이 안 찍혀 있다. 저쪽이 비워 뒀고, 웹은 그 자리에
     * `Stat` 을 쓴다. **비운 채로 두면 표에 라벨 없는 줄이 생긴다.**
     */
    @Test
    fun `스탯 라벨이 빈 카드는 Stat 으로 떨어진다`() {
        val tomato = DEX_CARDS.single { it.no == 11 }
        assertEquals("", tomato.statLabel)
        assertEquals("Stat", tomato.detailRows().last().label)
        assertEquals("840", tomato.detailRows().last().value)
    }

    /** 기술 부연은 **없는 카드가 있다.** 없으면 빈 문자열이고 화면에서 줄이 빠진다. */
    @Test
    fun `기술 부연은 있는 카드에만 붙는다`() {
        val move = { no: Int -> DEX_CARDS.single { it.no == no }.detailRows()[3] }
        assertTrue(move(1).note.isNotBlank())
        assertTrue(move(2).note.isBlank())
    }
}
