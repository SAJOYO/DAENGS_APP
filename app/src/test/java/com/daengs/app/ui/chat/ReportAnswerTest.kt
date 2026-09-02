package com.daengs.app.ui.chat

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReportAnswerTest {
    @Test
    fun `report is delivered to the published mailbox with the visible answer`() {
        val intent = reportAnswerIntent("산책은 하루 3시간 이상 시키세요.")

        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto", intent.data?.scheme)
        assertEquals(REPORT_EMAIL, intent.data?.schemeSpecificPart?.substringBefore('?'))
        val decodedUri = Uri.decode(intent.dataString.orEmpty())
        assertTrue(decodedUri.contains("subject=댕스 AI 답변 신고"))
        val body = decodedUri.substringAfter("&body=")
        assertTrue(body.startsWith("신고 이유를 적어 주세요:"))
        assertTrue(body.endsWith("산책은 하루 3시간 이상 시키세요."))
        assertFalse(body.contains("user_id", ignoreCase = true))
        assertFalse(body.contains("token", ignoreCase = true))
    }

    @Test
    fun `successful mail handoff is reported`() {
        var launched: Intent? = null

        val delivered = deliverReport(reportAnswerIntent("답변")) { launched = it }

        assertTrue(delivered)
        assertEquals(Intent.ACTION_SENDTO, launched?.action)
    }

    @Test
    fun `missing mail app is a visible failure instead of a crash`() {
        val delivered = deliverReport(reportAnswerIntent("답변")) {
            throw ActivityNotFoundException("no mail app")
        }

        assertFalse(delivered)
    }
}
