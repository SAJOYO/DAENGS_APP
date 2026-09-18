package com.daengs.app.notify

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 종을 누르면 보이는 목록.
 *
 * 사용자가 짚어서 생겼다 — *"지금 벨 모양 누르면 알림 설정창으로 가는데, 여기에 알림이
 * 있으면 불이 들어오고 그 내용이 떠야하는거 아니야?"*.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NoticeInboxTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @After
    fun 비운다() {
        NoticeInbox.clear(context)
    }

    private fun notice(id: Int, title: String = "제목", at: Long = 1L) = DaengsNotice(
        id = id,
        channelId = RESULT_CHANNEL_ID,
        title = title,
        text = "본문",
        atMillis = at,
    )

    @Test
    fun `새 알림이 맨 앞에 온다`() {
        NoticeInbox.add(context, notice(1, "먼저"))
        NoticeInbox.add(context, notice(2, "나중"))

        assertEquals(listOf("나중", "먼저"), NoticeInbox.notices.value.map { it.title })
    }

    /** 시스템 알림과 **같은 규칙**이다. 같은 산책의 일기가 두 번 준비돼도 줄이 하나다. */
    @Test
    fun `같은 자리의 알림은 겹쳐 쓴다`() {
        NoticeInbox.add(context, notice(1, "처음"))
        NoticeInbox.add(context, notice(1, "다시"))

        assertEquals(listOf("다시"), NoticeInbox.notices.value.map { it.title })
    }

    @Test
    fun `담을 수 있는 수를 넘으면 오래된 것이 빠진다`() {
        repeat(NoticeInbox.CAPACITY + 5) { index -> NoticeInbox.add(context, notice(index)) }

        assertEquals(NoticeInbox.CAPACITY, NoticeInbox.notices.value.size)
        // 가장 오래된 것(0번)은 밀려 나갔다.
        assertFalse(NoticeInbox.notices.value.any { it.id == 0 })
    }

    @Test
    fun `목록을 열면 안 읽은 것이 없어진다`() {
        NoticeInbox.add(context, notice(1))
        assertTrue(NoticeInbox.notices.value.any { !it.read })

        NoticeInbox.markAllRead(context)

        assertTrue(NoticeInbox.notices.value.none { !it.read })
    }

    /** 앱을 껐다 켜도 남아 있어야 한다 — prefs 에 적는다. */
    @Test
    fun `프로세스가 다시 시작해도 목록이 남는다`() {
        NoticeInbox.add(context, notice(1, "남아야 한다"))

        NoticeInbox.load(context)

        assertEquals(listOf("남아야 한다"), NoticeInbox.notices.value.map { it.title })
    }

    @Test
    fun `어디로 갈지도 같이 남는다`() {
        NoticeInbox.add(context, notice(1).copy(extras = mapOf(EXTRA_OPEN_WALK_DIARY to "session-1")))

        NoticeInbox.load(context)

        assertEquals("session-1", NoticeInbox.notices.value.single().extras[EXTRA_OPEN_WALK_DIARY])
    }

    @Test
    fun `비우면 빈 목록이 된다`() {
        NoticeInbox.add(context, notice(1))

        NoticeInbox.clear(context)

        assertTrue(NoticeInbox.notices.value.isEmpty())
        NoticeInbox.load(context)
        assertTrue(NoticeInbox.notices.value.isEmpty())
    }
}
