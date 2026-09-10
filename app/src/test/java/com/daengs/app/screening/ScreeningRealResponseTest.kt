package com.daengs.app.screening

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **서버가 실제로 보낸 응답**을 우리가 제대로 읽는가.
 *
 * 옆 `ScreeningReportTest` 는 손으로 쓴 JSON 을 씁니다 — 계약을 우리가 어떻게
 * 이해하고 있는지를 봅니다. 여기는 다릅니다: `res/screening/real_abnormal_response.json`
 * 은 **개발서버(`daengback`)에 사진을 넣어 받은 진짜 응답을 그대로 저장한 것**입니다
 * (2026-09-09, 가중치를 허깅페이스에서 받게 바꾼 뒤).
 *
 * 왜 둘 다 필요한가 — 손으로 쓴 JSON 은 **우리가 틀리게 이해한 모양**도 통과시킵니다.
 * 실제로 이 프로젝트에서 계약 필드 이름을 문서에서 베꼈다가 어긋난 적이 있습니다.
 * 서버가 보낸 바이트로 한 번 더 재면 그 종류의 착각이 걸립니다.
 *
 * ⚠️ 이 파일의 JSON 을 손으로 고치지 마세요. 서버가 바뀌어서 깨진 것이라면
 *    **다시 받아서 통째로 갈아 끼우세요** — 고쳐 맞추면 이 검사의 뜻이 없어집니다.
 */
class ScreeningRealResponseTest {

    private fun load(): ScreeningReport {
        val text = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("screening/real_abnormal_response.json"),
        ) { "테스트 리소스를 못 찾았습니다" }.bufferedReader().use { it.readText() }
        return ScreeningReport.parse(JSONObject(text))
    }

    @Test
    fun `실서버 응답을 읽는다`() {
        val r = load()
        assertEquals("1.0", r.contractVersion)
        assertEquals(ScreeningReport.Verdict.ABNORMAL, r.verdict)
        assertEquals(90.7f, r.stage1.abnormalPercent!!, 0.05f)
        assertTrue("보정된 확률이어야 합니다", r.stage1.calibrated)
        assertTrue("문장이 비면 화면이 빕니다", r.headline.isNotBlank())
        assertTrue(r.action.isNotBlank())
        assertTrue(r.disclaimer.isNotBlank())
    }

    @Test
    fun `계열 네 묶음이 다 온다`() {
        val g = load().groups
        assertEquals(4, g.size)
        assertEquals(
            listOf("솟아오른 변화", "피부 표면·색·두께 변화", "깊거나 단단한 혹", "벗겨지거나 패인 상처"),
            g.map { it.name },
        )
        assertEquals(71.5f, g[0].percent, 0.05f)
        // 확률 내림차순이어야 막대가 뒤집히지 않습니다.
        assertTrue(g.zipWithNext().all { (a, b) -> a.percent >= b.percent })
        // 네 묶음은 6종을 **더한 것**이라 합이 2단계 확률의 합과 같아야 합니다.
        assertEquals(89.8f, g.sumOf { it.percent.toDouble() }.toFloat(), 0.3f)
    }

    @Test
    fun `계열 한 줄이 온다`() {
        val line = load().group
        assertNotNull("확신이 충분한 응답이라 null 이면 안 됩니다", line)
        assertEquals("솟아오른 변화", line!!.name)
        assertTrue("서버가 준 문장을 그대로 씁니다", line.text.isNotBlank())
        // ★ 실서버가 특징을 같이 보내는가 (2026-09-10). 이름만 오면 보호자가
        //    자기 개 사진과 대조할 방법이 없습니다.
        assertEquals("돌기, 넓게 솟은 부위, 고름이 찬 병변", line.feature)
        assertTrue("자세히 보기 내용이 옵니다", line.detail.isNotBlank())
        assertTrue("단서를 빼지 않습니다", line.caveat.isNotBlank())
        // ⚠️ 긴급도 문구가 서버에서 딸려 오면 안 됩니다 (과잉 52.4%).
        assertTrue(
            "계열 문장에 긴급도가 섞였습니다: ${line.text}",
            listOf("조기 진료", "즉시", "응급").none { line.text.contains(it) },
        )
    }

    @Test
    fun `덩어리 경보는 이 사진에서 안 뜬다`() {
        // p(이상) 0.9065 x p(A6) 0.0199 = 0.018 < 문턱 0.40.
        // **안 뜨는 것이 정상**이고, 앱은 null 이면 통째로 안 그립니다.
        assertNull(load().alert)
    }

    @Test
    fun `6종은 계약에 오지만 화면 규칙은 그대로다`() {
        val r = load()
        // 계약에는 옵니다 — 관리자 콘솔이 보던 값이고, 줄이지 않았습니다.
        assertEquals(6, r.stage2.size)
        // ⚠️ 그런데 **화면은 groups 로 그립니다.** 6종 이름이 화면에 뜨면 안 됩니다.
        //    묶여 없어진 A1~A4 의 이름이 계열 이름에 새어 나오지 않았는지 봅니다
        //    (A5·A6 은 혼자 있는 묶음이라 계열 이름 == 클래스 이름입니다).
        val groupNames = r.groups.map { it.name }.toSet()
        val leaked = r.stage2
            .filter { it.code in listOf("A1", "A2", "A3", "A4") }
            .map { it.nameKo }
            .filter { it in groupNames }
        assertTrue("6종 이름이 계열로 새어 나왔습니다: $leaked", leaked.isEmpty())
    }
}
