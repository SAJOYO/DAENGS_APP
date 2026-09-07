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
    fun `서버 신고가 실패해 물러선 메일은 turn id 와 고른 사유를 싣는다`() {
        val intent = reportAnswerIntent(
            answer = "산책은 하루 3시간 이상 시키세요.",
            turnId = "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
            reason = "위험한 조언이에요",
        )

        // 이 둘이 없으면 운영자가 콘솔에서 그 답변을 못 찾는다.
        val body = Uri.decode(intent.dataString.orEmpty()).substringAfter("&body=")
        assertTrue(body.contains("신고 이유: 위험한 조언이에요"))
        assertTrue(body.contains("답변 번호: 6ba7b810-9dad-11d1-80b4-00c04fd430c8"))
        assertTrue(body.endsWith("산책은 하루 3시간 이상 시키세요."))
    }

    @Test
    fun `고른 사유는 그 문구가 되고 직접 적기는 쓴 글이 된다`() {
        assertEquals("사실과 달라요", reportReasonText(ReportReason.WRONG, ""))
        assertEquals("사실과 달라요", reportReasonText(ReportReason.WRONG, "안 쓴 글은 무시한다"))
        assertEquals("출처가 이상해요", reportReasonText(ReportReason.OTHER, "  출처가 이상해요 "))
    }

    @Test
    fun `직접 적기를 골라 놓고 안 쓰면 보낼 것이 없다`() {
        // 저쪽이 422 로 버리기 전에 앱에서 막는다.
        assertEquals(null, reportReasonText(ReportReason.OTHER, "   "))
        assertEquals(null, reportReasonText(ReportReason.OTHER, ""))
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
