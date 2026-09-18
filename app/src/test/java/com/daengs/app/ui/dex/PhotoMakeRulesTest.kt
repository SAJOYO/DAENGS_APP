package com.daengs.app.ui.dex

import com.daengs.app.dogcard.photo.PhotoCardKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** §9.2 · §10.2 — 남은 횟수 · 카드 막기 · 제목 이름의 순수 규칙. 화면은 이 함수들만 부른다. */
class PhotoMakeRulesTest {

    @Test
    fun `한글 이름은 받침 여부로 은 는을 가른다`() {
        assertEquals("안녕은", topicName("안녕"))
        assertEquals("보리는", topicName("보리"))
    }

    @Test
    fun `한글이 아닌 마지막 글자는 은는을 같이 적는다`() {
        assertEquals("NEO은(는)", topicName("NEO"))
    }

    @Test
    fun `남은 횟수 문장`() {
        assertNull(photoRemainingText(null))
        assertTrue(photoRemainingText(0)!!.startsWith("오늘은 다 만들었어요"))
        assertEquals("오늘 1번 남았어요", photoRemainingText(1))
    }

    @Test
    fun `카드 고르기는 안 막힌 첫 카드로 넘어간다`() {
        val open = setOf(4, 9)
        assertEquals(m(9), choosePhotoCard(m(9), emptySet(), open))
        assertEquals(m(4), choosePhotoCard(m(9), setOf(m(9)), open))
    }

    /**
     * **달이 다 차도 종류가 남아 있으면 그리로 넘어간다** (#593, D-085) — 한도가 카드
     * 종류마다라 「12달을 다 모았으니 이제 못 만든다」 가 아니다.
     */
    @Test
    fun `달이 다 차면 종류로 넘어간다`() {
        val open = setOf(4, 9)
        assertEquals(PhotoCardKey.Strawberry, choosePhotoCard(m(9), setOf(m(4), m(9)), open))
        assertEquals(
            PhotoCardKey.Lettuce,
            choosePhotoCard(m(9), setOf(m(4), m(9), PhotoCardKey.Strawberry), open),
        )
    }

    /** 다 막혔으면 null — 고르던 카드를 그대로 두고 제출을 막는다 (§9.2). */
    @Test
    fun `다 막히면 고를 카드가 없다`() {
        val open = setOf(4, 9)
        val all = setOf(m(4), m(9), PhotoCardKey.Strawberry, PhotoCardKey.Lettuce)
        assertNull(choosePhotoCard(m(9), all, open))
    }

    private fun m(month: Int) = PhotoCardKey.of(month)
}
