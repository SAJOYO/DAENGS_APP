package com.daengs.app.screening

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 저쪽 계약을 우리가 제대로 읽는가.
 *
 * 아래 JSON 은 `gayeoniee/deeplearning_test` 의 `src/agent.py` `contract()` 가 만드는
 * 모양 그대로다. 저쪽이 필드를 바꾸면 여기가 먼저 깨져야 한다 — 안 그러면 화면에
 * 0% 나 빈 칸이 조용히 뜬다.
 */
class ScreeningReportTest {

    private val abnormalJson = """
        {
          "contract_version": "1.0",
          "verdict": "abnormal",
          "headline": "피부에 이상 소견이 보입니다.",
          "body": "어떤 병변인지는 이 사진만으로 판단할 수 없습니다.",
          "action": "수의사 진료를 받아보시기를 권합니다.",
          "stage1": {
            "abnormal_prob": 0.7412,
            "abnormal_percent": 74.1,
            "threshold": 0.1823,
            "calibrated": true
          },
          "stage2": {
            "shown": true,
            "groups": [
              {"name": "표면 변화", "prob": 0.75, "percent": 75.0},
              {"name": "융기·발진", "prob": 0.17, "percent": 17.0},
              {"name": "미란·궤양", "prob": 0.05, "percent": 5.0},
              {"name": "결절·종괴", "prob": 0.03, "percent": 3.0}
            ],
            "group": {
              "name": "표면 변화", "prob": 0.75, "percent": 75.0, "confidence": 0.6225,
              "text": "모양만 보면 표면 변화 계열에 가깝습니다.",
              "caveat": "진단이 아닙니다. 같은 계열 안에서도 원인 질환은 여럿입니다."
            },
            "alert": null,
            "distribution": [
              {"code": "A2", "name_ko": "농포", "name_en": "pustule", "prob": 0.41, "percent": 41.0},
              {"code": "A1", "name_ko": "구진", "name_en": "papule", "prob": 0.33, "percent": 33.0},
              {"code": "A6", "name_ko": "결절", "name_en": "nodule", "prob": 0.26, "percent": 26.0}
            ]
          },
          "text": "",
          "disclaimer": "이 결과는 수의학적 진단이 아니며, 수의사의 진료를 대체하지 않습니다.",
          "meta": {}
        }
    """.trimIndent()

    @Test
    fun `이상 응답을 통째로 읽는다`() {
        val r = ScreeningReport.parse(JSONObject(abnormalJson))
        assertEquals("1.0", r.contractVersion)
        assertEquals(ScreeningReport.Verdict.ABNORMAL, r.verdict)
        assertEquals("피부에 이상 소견이 보입니다.", r.headline)
        assertEquals(74.1f, r.stage1.abnormalPercent!!, 0.01f)
        assertTrue(r.stage1.calibrated)
        assertEquals(3, r.stage2.size)
        assertTrue("면책 문구가 비었다", r.disclaimer.isNotBlank())
    }

    /**
     * **임계값은 0~1 로 온다.** 확률(`abnormal_percent`)은 이미 퍼센트인데 임계값은
     * 아니라서, 그대로 나란히 그리면 74.1% 옆에 "기준 0.2%" 가 뜬다.
     */
    @Test
    fun `임계값은 퍼센트로 환산된다`() {
        val r = ScreeningReport.parse(JSONObject(abnormalJson))
        assertEquals(18.23f, r.stage1.thresholdPercent!!, 0.01f)
    }

    /**
     * 분포의 **순서는 서버 것이다.** 저쪽 `_dist()` 가 이미 확률 내림차순으로 정렬해
     * 보내고, 앱이 다시 정렬하면 두 정렬이 갈라질 때 아무도 모른다.
     */
    @Test
    fun `분포 순서를 건드리지 않는다`() {
        val r = ScreeningReport.parse(JSONObject(abnormalJson))
        assertEquals(listOf("농포", "구진", "결절"), r.stage2.map { it.nameKo })
    }

    /**
     * `abnormal_percent` 가 null 이면 **null 이어야 한다.**
     *
     * `optDouble` 은 JSON null 에도 기본값을 주므로 그냥 읽으면 0.0 이 된다.
     * 화면에서 0% 는 "이상 없음"으로 읽혀서, 판단을 못 한 사진이 정반대로 보인다.
     */
    @Test
    fun `확률이 없으면 0이 아니라 없음이다`() {
        val json = """
            {
              "contract_version": "1.0",
              "verdict": "retake",
              "headline": "판단이 어려운 사진입니다.",
              "body": "다시 찍어주세요.",
              "action": "사진을 다시 찍어주세요.",
              "stage1": {
                "abnormal_prob": null, "abnormal_percent": null,
                "threshold": null, "calibrated": false
              },
              "stage2": {"shown": false, "distribution": []},
              "text": "", "disclaimer": "참고용입니다.", "meta": {}
            }
        """.trimIndent()
        val r = ScreeningReport.parse(JSONObject(json))
        assertEquals(ScreeningReport.Verdict.RETAKE, r.verdict)
        assertNull(r.stage1.abnormalPercent)
        assertNull(r.stage1.thresholdPercent)
        assertTrue(r.stage2.isEmpty())
    }

    /**
     * 모르는 verdict 는 **재촬영**으로 본다.
     *
     * 저쪽이 넷째 값을 추가하는 날, 앱이 그걸 "정상"으로 떨어뜨리면 사용자에게
     * 이상 없다고 말하게 된다. 다시 찍어 달라는 쪽은 어느 경우에도 안전하다.
     */
    @Test
    fun `모르는 판정은 재촬영으로 본다`() {
        val json = """
            {"verdict": "urgent", "headline": "", "body": "", "action": "",
             "stage1": {}, "stage2": {"shown": false, "distribution": []}}
        """.trimIndent()
        assertEquals(
            ScreeningReport.Verdict.RETAKE,
            ScreeningReport.parse(JSONObject(json)).verdict,
        )
    }

    /**
     * 계약에 **"1등 병변" 필드가 없다.** 저쪽 `tests/test_agent.py` 가 같은 목록을
     * 들고 감시하고 있다. 여기 걸리면 저쪽 계약이 바뀐 것이고, 앱이 그 필드를
     * 읽어 화면에 크게 띄우기 전에 먼저 이야기를 해야 한다.
     */
    @Test
    fun `계약에 일등 병변 필드가 없다`() {
        val banned = listOf(
            "top1", "top_1", "topk", "top_k", "predicted", "prediction",
            "predicted_class", "diagnosis", "diagnosed", "best", "best_class",
            "winner", "label", "answer",
        )
        val root = JSONObject(abnormalJson)
        val keys = buildList {
            root.keys().forEach { key ->
                add(key)
                (root.opt(key) as? JSONObject)?.keys()?.forEach { add(it) }
            }
        }
        val found = keys.filter { it.lowercase() in banned }
        assertTrue("계약에 금지된 필드가 생겼다: $found", found.isEmpty())
    }

    // ── 계열 4묶음 (2026-09-08) ────────────────────────────────

    /**
     * 보호자 화면이 그리는 것은 [ScreeningReport.groups] 다. 6종([stage2])은 계약에
     * 그대로 오지만 **안 그린다** — 관리자 콘솔이 본다.
     */
    @Test
    fun `계열 네 묶음을 읽고 6종도 그대로 들고 있다`() {
        val r = ScreeningReport.parse(JSONObject(abnormalJson))
        assertEquals(4, r.groups.size)
        assertEquals("표면 변화", r.groups[0].name)
        assertEquals(75.0f, r.groups[0].percent, 0.01f)
        // 계약이 6종을 안 줄였다는 것까지 같이 못 박는다
        assertEquals(3, r.stage2.size)
    }

    /** 묶음 순서도 서버 것이다. 앱이 다시 정렬하면 두 정렬이 갈라진다. */
    @Test
    fun `묶음 순서를 건드리지 않는다`() {
        val r = ScreeningReport.parse(JSONObject(abnormalJson))
        assertEquals(
            listOf("표면 변화", "융기·발진", "미란·궤양", "결절·종괴"),
            r.groups.map { it.name },
        )
    }

    /** 계열 한 줄은 문장을 **그대로** 옮긴다. 앱이 지어 쓰면 저쪽과 갈라진다. */
    @Test
    fun `계열 한 줄을 그대로 읽는다`() {
        val g = ScreeningReport.parse(JSONObject(abnormalJson)).group!!
        assertEquals("모양만 보면 표면 변화 계열에 가깝습니다.", g.text)
        assertTrue("면책이 비었다", g.caveat.isNotBlank())
    }

    /**
     * ★ **확신이 낮으면 서버가 `group` 을 null 로 준다** — 셋에 하나쯤이다.
     * 그때 앱이 막대 1등을 대신 문장으로 만들면, 확신 없을 때 말하지 않기로 한
     * 규칙이 앱에서 무너진다. 그러니 **null 이면 null 이어야** 한다.
     */
    @Test
    fun `확신이 낮으면 계열 한 줄이 없다`() {
        // 문자열을 자르지 않고 JSON 을 손본다 — 정규식으로 도려내면 예시가
        // 바뀔 때 조용히 안 맞는다.
        val o = JSONObject(abnormalJson)
        o.getJSONObject("stage2").put("group", JSONObject.NULL)
        val r = ScreeningReport.parse(o)
        assertNull(r.group)
        assertEquals("막대는 남아야 한다", 4, r.groups.size)
    }

    /**
     * 덩어리 경보. **계약에서 유일하게 병변 이름을 말하는 자리**다.
     * 안 뜨면 null 이고, 그때 앱이 대신 문턱을 재지 않는다.
     */
    @Test
    fun `덩어리 경보를 읽고 안 뜨면 없음이다`() {
        assertNull("alert 가 null 이면 없음", ScreeningReport.parse(JSONObject(abnormalJson)).alert)

        val o2 = JSONObject(abnormalJson)
        o2.getJSONObject("stage2").put(
            "alert",
            JSONObject(mapOf(
                "code" to "A6", "score" to 0.72, "threshold" to 0.40,
                "text" to "덩어리가 의심됩니다.", "action" to "빠른 진료를 권합니다.",
                "caveat" to "진단이 아닙니다.",
            )),
        )
        val a = ScreeningReport.parse(o2).alert!!
        assertEquals("A6", a.code)
        assertEquals(0.72f, a.score, 0.001f)
        assertEquals(0.40f, a.threshold, 0.001f)
        assertTrue(a.text.isNotBlank() && a.action.isNotBlank())
    }

    /**
     * ★ **옛 응답에는 `groups` 가 없다.** 이 기능 전에 저장된 기록이 그렇다.
     * 그때 터지지 않고 빈 목록이어야 한다 — 화면은 문장 셋만 남는다.
     */
    @Test
    fun `옛 응답에 묶음이 없어도 안 터진다`() {
        val json = """
            {"verdict": "abnormal", "headline": "", "body": "", "action": "",
             "stage1": {}, "stage2": {"shown": true, "distribution": []}}
        """.trimIndent()
        val r = ScreeningReport.parse(JSONObject(json))
        assertTrue(r.groups.isEmpty())
        assertNull(r.group)
        assertNull(r.alert)
    }
}
