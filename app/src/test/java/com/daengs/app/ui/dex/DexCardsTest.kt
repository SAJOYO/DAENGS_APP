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

    /**
      * **번호가 벌마다 1부터다.** 야채 01~12, 과일 01~13.
      *
      * 그래서 `no` 는 카드를 가리키는 열쇠가 못 된다 — 이머시브 무대도 `id` 로 찾는다
      * (`IMMERSIVE_SCENES`). 번호를 키로 두면 과일 1번에 배추 무대가 붙는다.
      */
     @Test
     fun `벌마다 번호가 1부터 하나씩이다`() {
         assertEquals(25, DEX_CARDS.size)
         DexDeck.entries.forEach { deck ->
             val nos = DEX_CARDS.filter { it.deck == deck }.map { it.no }
             assertEquals("$deck", (1..nos.size).toList(), nos)
         }
         assertEquals(12, DEX_CARDS.count { it.deck == DexDeck.Veggie })
         assertEquals(13, DEX_CARDS.count { it.deck == DexDeck.Fruit })
     }

     /** 두 벌이 한 목록에 있으니 id 는 **전체에서** 하나뿐이어야 한다. */
     @Test
     fun `id 가 겹치지 않는다`() {
         assertEquals(DEX_CARDS.size, DEX_CARDS.map { it.id }.toSet().size)
     }

    /** 한 장이라도 비면 그 카드만 설명이 텅 빈다. */
    @Test
    fun `모든 카드에 글이 다 있다`() {
        DEX_CARDS.forEach { card ->
            val where = "No.${card.no} ${card.id}"
            assertTrue("$where tagline", card.tagline.isNotBlank())
            assertTrue("$where ko", card.ko.isNotBlank())
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
        val labels = DEX_CARDS.first().detailRows(code = "DG-0824").map { it.label }
        assertEquals(listOf("No.", "Code", "Type", "Move", "CRUNCH"), labels)
    }

    /**
     * **번호판은 내 카드에만 있다.** 카탈로그에는 번호가 없다 — 예전에는 저쪽 카드에
     * 인쇄된 `NEO-0824` 를 박아 뒀는데, 그건 이 저장소를 만든 사람의 강아지 이름이라
     * 다른 사람 화면에 나오면 안 되는 값이었다. 번호는 아이 생일에서 만든다.
     */
    @Test
    fun `번호를 안 주면 그 줄이 빠진다`() {
        val labels = DEX_CARDS.first().detailRows().map { it.label }
        assertEquals(listOf("No.", "Type", "Move", "CRUNCH"), labels)
    }

    /** 분모는 **그 벌의 장수**다. 도감이 탭으로 갈려 있어 전체를 세면 거짓말이 된다. */
    @Test
    fun `번호는 두 자리고 분모가 벌 기준이다`() {
        val veggie = DEX_CARDS.filter { it.deck == DexDeck.Veggie }
        val fruit = DEX_CARDS.filter { it.deck == DexDeck.Fruit }
        assertEquals("01 / 12", veggie.first().detailRows(veggie.size).first().value)
        assertEquals("12 / 12", veggie.last().detailRows(veggie.size).first().value)
        assertEquals("13 / 13", fruit.last().detailRows(fruit.size).first().value)
    }

    /**
     * No.11 토마토만 스탯 바에 라벨이 안 찍혀 있다. 저쪽이 비워 뒀고, 웹은 그 자리에
     * `Stat` 을 쓴다. **비운 채로 두면 표에 라벨 없는 줄이 생긴다.**
     */
    @Test
    fun `스탯 라벨이 빈 카드는 Stat 으로 떨어진다`() {
        val tomato = DEX_CARDS.single { it.id == "tomato" }
        assertEquals("", tomato.statLabel)
        assertEquals("Stat", tomato.detailRows().last().label)
        assertEquals("840", tomato.detailRows().last().value)
    }

    /** 기술 부연은 **없는 카드가 있다.** 없으면 빈 문자열이고 화면에서 줄이 빠진다. */
    @Test
    fun `기술 부연은 있는 카드에만 붙는다`() {
        // 번호판 줄이 빠져서 기술은 셋째 줄이다.
        val move = { id: String -> DEX_CARDS.single { it.id == id }.detailRows()[2] }
        assertTrue(move("cabbage").note.isNotBlank())
        assertTrue(move("pepper").note.isBlank())
    }
}
