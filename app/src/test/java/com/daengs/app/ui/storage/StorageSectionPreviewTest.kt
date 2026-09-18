package com.daengs.app.ui.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 저장소 탭의 섹션을 접는 규칙.
 *
 * 저장소 탭이 세 섹션을 한 목록에 이어 붙여서 기록이 쌓이면 스크롤만 길어졌다.
 * 실기기 실측으로 케어 기록이 화면의 60%, 대화 보관함은 화면 밖이었다.
 * **탭 높이가 내용과 무관하게 고정되는 것**이 이 규칙의 목적이다.
 */
class StorageSectionPreviewTest {

    @Test
    fun `비어 있으면 아무것도 안 그린다`() {
        assertEquals(0, StorageSectionPreview.shown(0))
        assertFalse(StorageSectionPreview.showsAll(0))
        assertEquals(0, StorageSectionPreview.hidden(0))
    }

    @Test
    fun `적으면 있는 만큼 다 그린다`() {
        assertEquals(1, StorageSectionPreview.shown(1))
        assertEquals(2, StorageSectionPreview.shown(2))
    }

    /**
     * **경계.** 딱 미리보기 개수만큼일 때 전체보기를 띄우면, 눌러도 같은 것이 나와서
     * 아무 일도 안 일어난 것처럼 느껴진다.
     */
    @Test
    fun `딱 미리보기 개수면 전체보기를 안 띄운다`() {
        assertFalse(StorageSectionPreview.showsAll(StorageSectionPreview.PREVIEW))
        assertEquals(0, StorageSectionPreview.hidden(StorageSectionPreview.PREVIEW))
    }

    @Test
    fun `하나라도 접히면 전체보기를 띄운다`() {
        assertTrue(StorageSectionPreview.showsAll(StorageSectionPreview.PREVIEW + 1))
        assertEquals(1, StorageSectionPreview.hidden(StorageSectionPreview.PREVIEW + 1))
    }

    /**
     * **많아져도 탭에 그리는 양은 그대로다.** 이게 이 작업의 전부다 — 기록이 천 건이어도
     * 저장소 탭의 높이가 안 변해야 한다.
     */
    @Test
    fun `많아져도 그리는 개수는 안 늘어난다`() {
        assertEquals(StorageSectionPreview.PREVIEW, StorageSectionPreview.shown(7))
        assertEquals(StorageSectionPreview.PREVIEW, StorageSectionPreview.shown(1000))
        assertEquals(998, StorageSectionPreview.hidden(1000))
    }

    /** 미리보기가 셋 이상이면 탭이 다시 한 화면을 넘는다 (실측 기준). */
    @Test
    fun `미리보기는 둘이다`() {
        assertEquals(2, StorageSectionPreview.PREVIEW)
    }
}
