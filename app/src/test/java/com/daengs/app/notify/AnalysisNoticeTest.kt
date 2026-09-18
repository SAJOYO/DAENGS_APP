package com.daengs.app.notify

import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

/**
 * 알림이 **실제로 올라가는지**와, 채널이 갈리는지.
 *
 * 규격만 재는 테스트로는 못 잡는 것이 하나 있다 — 옛 `gait_analysis` 채널을 지우는 일은
 * 이름 비교가 아니라 `NotificationManager` 를 부르는 일이어서, 부르지 않으면 조용히 안
 * 지워진다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnalysisNoticeTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Test
    fun `채널을 만들고 옛 채널을 지운다`() {
        manager.createNotificationChannel(
            android.app.NotificationChannel(
                LEGACY_GAIT_CHANNEL_ID,
                "보행 분석 완료",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )

        ensureDaengsChannels(context)

        assertNotNull(manager.getNotificationChannel(ANALYSIS_CHANNEL_ID))
        assertNotNull(manager.getNotificationChannel(WALK_REMINDER_CHANNEL_ID))
        assertNull(manager.getNotificationChannel(LEGACY_GAIT_CHANNEL_ID))
    }

    /**
     * **산책 알림은 다른 채널이다.** 앱이 먼저 말을 거는 것이라, 이것만 끄고 기다리던
     * 결과는 받고 싶을 수 있다.
     */
    @Test
    fun `산책 알림은 분석 결과와 다른 채널에 뜬다`() {
        postDaengsNotice(
            context = context,
            channelId = WALK_REMINDER_CHANNEL_ID,
            id = WALK_REMINDER_NOTICE_ID,
            title = WALK_REMINDER_TITLE,
            text = WALK_REMINDER_TEXT,
        )

        assertEquals(WALK_REMINDER_CHANNEL_ID, shadowOf(manager).allNotifications.single().channelId)
    }

    @Test
    fun `알림을 분석 결과 채널에 띄운다`() {
        assertTrue(postAnalysisNotice(context, id = 7, title = "제목", text = "본문"))

        val posted = shadowOf(manager).allNotifications.single()
        assertEquals(ANALYSIS_CHANNEL_ID, posted.channelId)
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
}
