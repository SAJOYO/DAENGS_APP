package com.daengs.app.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「산책 일기 장면이 준비됐어요」를 **언제** 띄우나.
 *
 * 전달 작업은 네트워크가 돌아올 때마다 다시 돌 수 있다. 그래서 규칙이 "장면이 있다" 가
 * 아니라 **"이번에 준비됐다"** 다.
 */
class WalkDiaryNoticeTest {

    @Test
    fun `장면이 이번에 준비되면 알린다`() {
        assertTrue(diarySceneBecameReady(before = "running", after = "ready"))
    }

    @Test
    fun `기록이 아직 없던 산책도 알린다`() {
        assertTrue(diarySceneBecameReady(before = null, after = "ready"))
    }

    /** 전달 작업이 다시 돌았을 때. 준비된 장면 하나로 알림이 여러 번 뜨면 안 된다. */
    @Test
    fun `이미 준비돼 있었으면 다시 알리지 않는다`() {
        assertFalse(diarySceneBecameReady(before = "ready", after = "ready"))
    }

    @Test
    fun `아직 만드는 중이거나 실패했으면 알리지 않는다`() {
        assertFalse(diarySceneBecameReady(before = null, after = "running"))
        assertFalse(diarySceneBecameReady(before = "running", after = "failed"))
        assertFalse(diarySceneBecameReady(before = null, after = null))
    }

    /**
     * **보행 완료 알림과 자리가 겹치지 않는다.**
     *
     * 저쪽은 `recordId.hashCode()` 를 쓴다. 둘 다 서버가 준 문자열 id 라, 날것으로
     * 해싱하면 같은 값이 나올 수 있고 그러면 한쪽이 다른 쪽을 조용히 덮어쓴다.
     */
    @Test
    fun `알림 자리는 날것의 해시와 다르다`() {
        val sessionId = "session-1"
        assertNotEquals(sessionId.hashCode(), diaryNoticeId(sessionId))
    }

    @Test
    fun `같은 산책이면 같은 자리다`() {
        assertEquals(diaryNoticeId("session-1"), diaryNoticeId("session-1"))
        assertNotEquals(diaryNoticeId("session-1"), diaryNoticeId("session-2"))
    }
}
