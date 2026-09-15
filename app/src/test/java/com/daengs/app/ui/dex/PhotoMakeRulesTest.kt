package com.daengs.app.ui.dex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** §9.2 — 남은 횟수 · 달 막기 · 제목 이름의 순수 규칙. 화면은 이 함수들만 부른다. */
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
    fun `달 고르기는 안 막힌 첫 열린 달로 넘어간다`() {
        assertEquals(9, choosePhotoMonth(9, setOf(4, 9), emptySet()))
        assertEquals(4, choosePhotoMonth(9, setOf(4, 9), setOf(9)))
        assertNull(choosePhotoMonth(9, setOf(4, 9), setOf(4, 9)))
    }
}
