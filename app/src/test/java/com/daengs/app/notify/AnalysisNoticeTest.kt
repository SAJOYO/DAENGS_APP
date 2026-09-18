package com.daengs.app.notify

import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 알림이 **실제로 올라가는지**, 채널이 갈리는지, 그리고 **앱의 알림함에 남는지**.
 *
 * 규격만 재는 테스트로는 못 잡는 것이 둘이다 — 옛 채널을 지우는 일은 이름 비교가 아니라
 * `NotificationManager` 를 부르는 일이어서 안 부르면 조용히 안 지워지고, 알림함에 적는
 * 순서(권한 확인보다 먼저)도 호출 순서라 규격이 아니다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnalysisNoticeTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val manager = context.getSystemService(NotificationManager::class.java)

    @After
    fun 비운다() {
        // [NoticeInbox] 는 프로세스에 하나라 테스트 사이에 값이 넘어간다.
        NoticeInbox.clear(context)
    }

    @Test
    fun `채널을 만들고 옛 채널을 지운다`() {
        LEGACY_CHANNEL_IDS.forEach { legacy ->
            manager.createNotificationChannel(
                android.app.NotificationChannel(legacy, legacy, NotificationManager.IMPORTANCE_DEFAULT),
            )
        }

        ensureDaengsChannels(context)

        assertNotNull(manager.getNotificationChannel(RESULT_CHANNEL_ID))
        assertNotNull(manager.getNotificationChannel(WALK_REMINDER_CHANNEL_ID))
        LEGACY_CHANNEL_IDS.forEach { assertNull(manager.getNotificationChannel(it)) }
    }

    @Test
    fun `알림을 완성 알림 채널에 띄운다`() {
        assertTrue(postResultNotice(context, id = 7, title = "제목", text = "본문"))

        assertEquals(RESULT_CHANNEL_ID, shadowOf(manager).allNotifications.single().channelId)
    }

    /**
     * **산책 알림은 다른 채널이다.** 앱이 먼저 말을 거는 것이라, 이것만 끄고 기다리던
     * 결과는 받고 싶을 수 있다.
     */
    @Test
    fun `산책 알림은 완성 알림과 다른 채널에 뜬다`() {
        postDaengsNotice(
            context = context,
            channelId = WALK_REMINDER_CHANNEL_ID,
            id = WALK_REMINDER_NOTICE_ID,
            title = WALK_REMINDER_TITLE,
            text = WALK_REMINDER_TEXT,
        )

        assertEquals(WALK_REMINDER_CHANNEL_ID, shadowOf(manager).allNotifications.single().channelId)
    }

    /**
     * **산책 일기 알림은 그 산책 자리에 하나만 쌓인다.** 전달 작업이 여러 번 돌아도
     * 같은 id 로 덮어쓴다 ([diaryNoticeId]).
     */
    @Test
    fun `같은 산책의 일기 알림은 겹쳐 쓴다`() {
        postWalkDiaryNotice(context, "session-1")
        postWalkDiaryNotice(context, "session-1")

        assertEquals(1, shadowOf(manager).allNotifications.size)
    }

    @Test
    fun `일기 알림은 어느 산책인지 실어 보낸다`() {
        postWalkDiaryNotice(context, "session-1")

        val intent = shadowOf(
            shadowOf(manager).allNotifications.single().contentIntent,
        ).savedIntent
        assertEquals("session-1", intent.getStringExtra(EXTRA_OPEN_WALK_DIARY))
    }

    @Test
    fun `띄운 알림은 앱의 알림함에도 남는다`() {
        postWalkDiaryNotice(context, "session-1")

        val notice = NoticeInbox.notices.value.single()
        assertEquals("session-1", notice.extras[EXTRA_OPEN_WALK_DIARY])
        assertEquals(RESULT_CHANNEL_ID, notice.channelId)
        assertFalse(notice.read)
    }

    /**
     * **권한을 안 줬어도 알림함에는 남는다.**
     *
     * 사용자가 끈 것은 *시스템이 부르지 마라* 였고 *앱에서도 숨겨라* 가 아니다. 이 순서가
     * 뒤집히면 종을 눌러도 빈 목록이 뜬다.
     */
    @Test
    fun `알림 권한이 꺼져 있어도 알림함에는 남는다`() {
        shadowOf(manager).setNotificationsEnabled(false)

        assertFalse(postWalkDiaryNotice(context, "session-1"))

        assertEquals(0, shadowOf(manager).allNotifications.size)
        assertEquals(1, NoticeInbox.notices.value.size)
    }
}
