package com.daengs.app.ui.notify

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.daengs.app.notify.DaengsNotice
import com.daengs.app.notify.EXTRA_OPEN_WALK_DIARY
import com.daengs.app.notify.RESULT_CHANNEL_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * 종을 눌러 여는 목록.
 *
 * 사용자가 짚은 것이 둘이었다 — **불이 들어와야 하고**(종 쪽은 `DaengsTopBar`)
 * **내용이 떠야 한다**(이 화면).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NoticeInboxScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val notice = DaengsNotice(
        id = 1,
        channelId = RESULT_CHANNEL_ID,
        title = "포토 카드가 완성됐어요.",
        text = "도감에서 뒤집어 보세요.",
        atMillis = 0L,
        extras = mapOf(EXTRA_OPEN_WALK_DIARY to "session-1"),
    )

    @Test
    fun `알림의 제목과 내용이 뜬다`() {
        rule.setContent { NoticeInboxScreen(listOf(notice), onBack = {}, onOpen = {}, onClear = {}) }

        rule.onNodeWithText("포토 카드가 완성됐어요.").assertIsDisplayed()
        rule.onNodeWithText("도감에서 뒤집어 보세요.").assertIsDisplayed()
    }

    @Test
    fun `줄을 누르면 그 알림을 연다`() {
        var opened: DaengsNotice? = null
        rule.setContent {
            NoticeInboxScreen(listOf(notice), onBack = {}, onOpen = { opened = it }, onClear = {})
        }

        rule.onNodeWithText("포토 카드가 완성됐어요.").performClick()

        assertEquals(notice, opened)
    }

    @Test
    fun `받은 알림이 없으면 그렇게 말한다`() {
        rule.setContent { NoticeInboxScreen(emptyList(), onBack = {}, onOpen = {}, onClear = {}) }

        rule.onNodeWithText("아직 받은 알림이 없어요.").assertIsDisplayed()
    }

    /** 지울 것이 없는데 눌리는 글자를 남겨 두면 눌러 보게 된다. */
    @Test
    fun `빈 목록에서는 다 지우기가 없다`() {
        rule.setContent { NoticeInboxScreen(emptyList(), onBack = {}, onOpen = {}, onClear = {}) }

        rule.onNodeWithText("다 지우기").assertDoesNotExist()
    }

    @Test
    fun `알림 설정으로 가는 길은 남아 있다`() {
        rule.setContent { NoticeInboxScreen(listOf(notice), onBack = {}, onOpen = {}, onClear = {}) }

        rule.onNodeWithText("알림 설정").assertIsDisplayed()
    }

    @Test
    fun `오늘 온 알림에는 날짜를 안 붙인다`() {
        val now = LocalDateTime.of(2026, 9, 19, 20, 30)
        val at = now.withHour(8).withMinute(12)
        val millis = at.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

        val label = noticeWhen(millis, now)

        assertTrue(label, label.contains("8:12"))
        assertTrue(label, !label.contains("월"))
    }

    @Test
    fun `어제 온 알림은 어제라고 말한다`() {
        val now = LocalDateTime.of(2026, 9, 19, 20, 30)
        val at = now.minusDays(1)
        val millis = at.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

        assertTrue(noticeWhen(millis, now).startsWith("어제"))
    }

    @Test
    fun `더 오래된 알림은 날짜로 말한다`() {
        val now = LocalDateTime.of(2026, 9, 19, 20, 30)
        val at = now.minusDays(3)
        val millis = at.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

        assertEquals("9월 16일", noticeWhen(millis, now))
    }
}
