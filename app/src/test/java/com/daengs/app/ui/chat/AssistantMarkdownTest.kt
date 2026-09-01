package com.daengs.app.ui.chat

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [assistantMarkdown] 이 실제 관찰된 답변 표기만 다루는가. 실기기 스모크에서 나온
 * 원문 예시(`**1. 서열 정립 및 복종 교육**`, `* **원인:** ...`)를 그대로 입력으로 쓴다.
 */
class AssistantMarkdownTest {

    private fun boldRanges(text: String) = assistantMarkdown(text).spanStyles
        .filter { it.item == SpanStyle(fontWeight = FontWeight.Bold) }
        .map { it.start until it.end }

    @Test
    fun `굵게 표시는 마커 없이 보이고 전체 구간이 굵다`() {
        val r = assistantMarkdown("**1. 서열 정립 및 복종 교육**")
        assertEquals("1. 서열 정립 및 복종 교육", r.text)
        assertEquals(listOf(0 until r.text.length), boldRanges("**1. 서열 정립 및 복종 교육**"))
    }

    @Test
    fun `불릿 안의 굵은 라벨만 굵다`() {
        val text = "* **원인:** 흥분해서 물 수 있습니다."
        val r = assistantMarkdown(text)
        assertEquals("• 원인: 흥분해서 물 수 있습니다.", r.text)
        val boldStart = r.text.indexOf("원인:")
        val bold = boldRanges(text)
        assertEquals(listOf(boldStart until boldStart + "원인:".length), bold)
    }

    @Test
    fun `대시 목록도 불릿으로 바뀐다`() {
        val r = assistantMarkdown("- 첫 번째\n- 두 번째")
        assertEquals("• 첫 번째\n• 두 번째", r.text)
    }

    @Test
    fun `줄바꿈은 그대로 보존된다`() {
        val r = assistantMarkdown("첫 줄\n\n둘째 문단")
        assertEquals("첫 줄\n\n둘째 문단", r.text)
    }

    @Test
    fun `인용 표기는 그대로 보인다`() {
        val r = assistantMarkdown("답변입니다. [1] [2]")
        assertEquals("답변입니다. [1] [2]", r.text)
    }

    @Test
    fun `제목 마커는 지워지고 굵게 남는다`() {
        val r = assistantMarkdown("## 제목")
        assertEquals("제목", r.text)
        assertTrue(boldRanges("## 제목").isNotEmpty())
    }

    @Test
    fun `닫히지 않은 강조는 죽지 않고 원문을 남긴다`() {
        val r = assistantMarkdown("**닫히지 않은 강조")
        assertEquals("**닫히지 않은 강조", r.text)
        assertFalse(r.text.isEmpty())
    }

    @Test
    fun `실기기에서 본 원문 예시는 원시 표기가 안 남는다`() {
        val raw = "**1. 서열 정립 및 복종 교육**\n* **원인:** ..."
        val r = assistantMarkdown(raw)
        assertFalse(r.text.contains("**"))
        assertFalse(r.text.contains("* **"))
    }

    // ── 중첩 목록 (들여쓴 * / -) ──────────────────────────────────────────────

    @Test
    fun `들여쓴 항목은 별표 없이 부모보다 안쪽으로 들여써서 보인다`() {
        val input = "* 대처법:\n    * 첫 번째 방법\n    * 두 번째 방법"
        val r = assistantMarkdown(input)
        val lines = r.text.split("\n")
        assertEquals("• 대처법:", lines[0])
        assertEquals("    • 첫 번째 방법", lines[1])
        assertEquals("    • 두 번째 방법", lines[2])
        assertFalse("원시 별표가 남았다: ${r.text}", r.text.contains("*"))
        assertTrue(lines[1].indexOf("•") > lines[0].indexOf("•"))
        assertTrue(lines[2].indexOf("•") > lines[0].indexOf("•"))
    }

    @Test
    fun `들여쓴 항목 안의 굵은 글씨도 유지된다`() {
        val text = "    * **원인:** 설명"
        val r = assistantMarkdown(text)
        assertEquals("    • 원인: 설명", r.text)
        assertFalse(r.text.contains("**"))
        val boldStart = r.text.indexOf("원인:")
        assertEquals(listOf(boldStart until boldStart + "원인:".length), boldRanges(text))
    }

    @Test
    fun `실기기 중첩 목록 원문도 별표가 안 남고 내용은 보존된다`() {
        val raw = "• 대처법:\n" +
            "    *   배를 보이게 한 상태에서 진정시킵니다\n" +
            "    *   앞발과 뒷발을 손으로 부드럽게 잡아줍니다\n" +
            "    *   쵸크체인을 건 후 짧게 신호를 줍니다"
        val r = assistantMarkdown(raw)
        assertFalse("원시 별표가 남았다: ${r.text}", r.text.contains("*"))
        assertTrue(r.text.contains("배를 보이게 한 상태에서 진정시킵니다"))
        assertTrue(r.text.contains("앞발과 뒷발을 손으로 부드럽게 잡아줍니다"))
        assertTrue(r.text.contains("쵸크체인을 건 후 짧게 신호를 줍니다"))
    }
}
