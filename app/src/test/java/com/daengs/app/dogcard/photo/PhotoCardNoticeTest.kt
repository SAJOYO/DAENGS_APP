package com.daengs.app.dogcard.photo

import com.daengs.app.notify.diaryNoticeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 포토 카드 완성 알림의 자리.
 *
 * 알림 자리가 겹치면 **한쪽이 다른 쪽을 조용히 덮어쓴다.** 셋 다 서버가 준 문자열 id 를
 * 해싱해서 쓰기 때문에, 접두사 없이는 같은 값이 나올 수 있다.
 */
class PhotoCardNoticeTest {

    @Test
    fun `같은 카드면 같은 자리다`() {
        assertEquals(photoCardNoticeId("card-1"), photoCardNoticeId("card-1"))
        assertNotEquals(photoCardNoticeId("card-1"), photoCardNoticeId("card-2"))
    }

    @Test
    fun `날것의 해시와 다르다`() {
        assertNotEquals("card-1".hashCode(), photoCardNoticeId("card-1"))
    }

    /** id 가 같아도 종류가 다르면 다른 자리여야 한다 — 산책 일기와 포토 카드. */
    @Test
    fun `산책 일기와 같은 id 를 써도 자리가 겹치지 않는다`() {
        assertNotEquals(diaryNoticeId("same-id"), photoCardNoticeId("same-id"))
    }
}
