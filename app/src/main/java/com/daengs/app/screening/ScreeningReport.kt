package com.daengs.app.screening

import androidx.compose.runtime.Immutable
import org.json.JSONObject

/**
 * 피부 스크리닝 결과. 저쪽 `src/agent.py` 의 `contract()` 가 만드는 JSON 그대로다.
 *
 * **"1등 병변" 필드는 없다. 없는 게 맞다.**
 * 저쪽 주석이 그 이유를 적어 뒀다 — 그런 필드가 생기는 순간 앱이 그걸 화면에 크게
 * 띄우고, 그게 막으려던 일이다. 사진 한 장으로 병명을 단정할 수 없다.
 * `tests/test_agent.py` 가 금지 키 목록(`top1`·`diagnosis`·`label`…)을 들고 감시한다.
 *
 * 그래서 이 화면도 [stage2] 를 **분포로만** 그린다. 1등을 골라 크게 쓰지 않는다.
 * 문장 셋([headline]·[body]·[action])은 서버가 사용자에게 보여 줄 말로 써 놓은 것이라
 * 앱에서 다시 쓰지 않고 **그대로 옮긴다.**
 */
@Immutable
data class ScreeningReport(
    val contractVersion: String,
    val verdict: Verdict,
    val headline: String,
    val body: String,
    val action: String,
    val stage1: Stage1,
    /** 병변 6종 분포. [Verdict.ABNORMAL] 이 아니면 비어 있다. */
    val stage2: List<Lesion>,
    val disclaimer: String,
) {
    /** 정상/이상/재촬영. 저쪽은 셋 중 하나만 보낸다. */
    enum class Verdict { NORMAL, ABNORMAL, RETAKE }

    /**
     * 1단계(정상/이상) 확률.
     *
     * [abnormalPercent] 는 **없을 수 있다** — 사진을 아예 못 읽어 재촬영으로 돌아온
     * 경우 서버가 null 을 준다. 그때 0% 로 그리면 "이상 없음"으로 읽혀서 정반대다.
     */
    @Immutable
    data class Stage1(
        val abnormalPercent: Float?,
        val thresholdPercent: Float?,
        /**
         * 확률이 보정(calibration)을 거쳤는가.
         *
         * 안 거친 확률은 순서만 뜻이 있고 **숫자 자체는 못 믿는다.** 저쪽이 굳이
         * 계약에 실어 보낸 값이라 화면에서도 숨기지 않는다.
         */
        val calibrated: Boolean,
    )

    /** 분포 한 줄. 이름은 서버가 준 한국어를 쓴다 — 앱에 병명 표를 두면 둘이 갈라진다. */
    @Immutable
    data class Lesion(val code: String, val nameKo: String, val percent: Float)

    companion object {
        fun parse(json: JSONObject): ScreeningReport {
            val s1 = json.optJSONObject("stage1")
            val s2 = json.optJSONObject("stage2")?.optJSONArray("distribution")
            return ScreeningReport(
                contractVersion = json.optString("contract_version"),
                verdict = when (json.optString("verdict")) {
                    "normal" -> Verdict.NORMAL
                    "abnormal" -> Verdict.ABNORMAL
                    // 모르는 값은 재촬영으로 본다. 새 verdict 가 생겨도 "다시 찍어
                    // 주세요"는 늘 안전한 쪽이다 — 정상으로 넘기면 위험하다.
                    else -> Verdict.RETAKE
                },
                headline = json.optString("headline"),
                body = json.optString("body"),
                action = json.optString("action"),
                stage1 = Stage1(
                    abnormalPercent = s1?.optFloatOrNull("abnormal_percent"),
                    thresholdPercent = s1?.optFloatOrNull("threshold")?.times(100f),
                    calibrated = s1?.optBoolean("calibrated") ?: false,
                ),
                stage2 = buildList {
                    for (i in 0 until (s2?.length() ?: 0)) {
                        val o = s2!!.getJSONObject(i)
                        add(
                            Lesion(
                                code = o.optString("code"),
                                nameKo = o.optString("name_ko").ifBlank { o.optString("code") },
                                percent = o.optDouble("percent", 0.0).toFloat(),
                            ),
                        )
                    }
                },
                disclaimer = json.optString("disclaimer"),
            )
        }

        /**
         * `optDouble` 은 JSON null 에도 기본값을 준다. 0 과 "없음"을 구분해야 해서
         * [JSONObject.isNull] 을 먼저 본다.
         */
        private fun JSONObject.optFloatOrNull(key: String): Float? =
            if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }?.toFloat()
    }
}
