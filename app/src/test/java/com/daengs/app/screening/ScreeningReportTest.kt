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
}
