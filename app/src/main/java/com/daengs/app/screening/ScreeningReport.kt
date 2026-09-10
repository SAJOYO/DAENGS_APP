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
 * 그래서 이 화면은 [groups] 를 **분포로만** 그린다. 1등을 골라 크게 쓰지 않는다.
 * 문장 셋([headline]·[body]·[action])은 서버가 사용자에게 보여 줄 말로 써 놓은 것이라
 * 앱에서 다시 쓰지 않고 **그대로 옮긴다.**
 *
 * ## 2026-09-08 — 화면이 6종에서 **계열 4묶음**으로 바뀌었다
 *
 * 6종 이름은 저쪽 holdout 에서 커버리지 41.1% 라 못 쓰는데, 네 묶음으로 굵게 물으면
 * **66.5%** 다. 그래서 보호자 화면은 [groups] 를 그리고 [stage2] 는 **안 그린다.**
 *
 * ⚠️ **[stage2] 를 지우지 않는다.** 계약에 그대로 오고 관리자 콘솔이 쓴다.
 *    화면 결정이 뒤집혀도 여기 한 줄이지 계약을 다시 고치는 일이 아니다.
 *
 * ⚠️ 네 묶음은 6종을 **자른 게 아니라 더한 것**이다 — 여섯 개가 전부 어딘가에
 *    들어가 있어 숨기는 게 없다. "상위 몇 개로 자르지 마라" 는 규칙과 다르다.
 */
@Immutable
data class ScreeningReport(
    val contractVersion: String,
    val verdict: Verdict,
    val headline: String,
    val body: String,
    val action: String,
    val stage1: Stage1,
    /**
     * 병변 6종 분포. [Verdict.ABNORMAL] 이 아니면 비어 있다.
     *
     * ⚠️ **화면에 안 그린다** (2026-09-08). 관리자 콘솔용이고, 여기서는 계약이
     *    오는지 확인하는 용도로만 들고 있다.
     */
    val stage2: List<Lesion>,
    /** 계열 네 묶음 **분포**. 확신과 무관하게 늘 온다 — 막대는 이걸로 그린다. */
    val groups: List<Group>,
    /**
     * 계열 **주장** 한 줄. 확신이 모자라면 **null** 이고, 그때는 통째로 안 그린다
     * (저쪽 실측으로 셋에 하나쯤). 막대만 남는다.
     */
    val group: GroupLine?,
    /** "덩어리가 의심됩니다" 경보. 안 뜨면 **null**. */
    val alert: Alert?,
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

    /**
     * 계열 막대 한 줄. 이름도 서버가 준 것을 쓴다 — 묶음표를 앱에 두면 갈라진다.
     * 저쪽은 묶음을 정하는 코드를 `agent.lesion_group()` **한 곳**으로 모아 뒀다.
     */
    @Immutable
    data class Group(val name: String, val percent: Float)

    /**
     * 계열 한 줄. [text] 와 [caveat] 를 **그대로** 띄운다.
     *
     * ⚠️ 앱이 문장을 지어 쓰면 저쪽과 표현이 갈리고, 갈리면 한쪽이 단정적으로 읽힌다.
     * ⚠️ **긴급도 문구를 붙이지 않는다.** 묶음의 긴급도는 높은 쪽으로 잡혀서,
     *    붙이면 말한 것의 절반이 한 단계 부풀려진다 (저쪽 실측 과잉 52.4%).
     */
    @Immutable
    data class GroupLine(
        val name: String,
        val percent: Float,
        val text: String,
        val caveat: String,
        /**
         * 보호자가 **사진에서 직접 확인할 수 있는** 특징 (2026-09-10).
         * 이름만으로는 자기 개 사진과 대조가 안 된다 — `표면 변화` 는 뜻이 안 잡히고
         * `딱지, 둥근 비늘, 검어진 피부` 는 바로 보인다. 계열 줄 **바로 아래**에 띄운다.
         * 옛 서버는 안 보내므로 빈 문자열일 수 있다.
         */
        val feature: String = "",
        /**
         * 수의학적 의미 (primary/secondary 등). **"자세히 보기" 안에만** 띄운다.
         *
         * ⚠️ 본문에 올리면 안 된다. 그 축은 *진단 순서*의 축이지 *보호자에게 뭐라고
         *    부를지*의 축이 아니고, 저쪽에서 그 축으로 묶었다가 **과잉 분류 88.4%** 로
         *    기각했다. 옛 서버는 안 보내므로 빈 문자열일 수 있다.
         */
        val detail: String = "",
    )

    /**
     * "덩어리가 의심됩니다" — **계약에서 유일하게 병변 이름을 말하는 자리**다.
     *
     * 나머지가 전부 "이름을 말하지 마라" 인데 여기만 예외인 이유는 저쪽
     * `config.A6_ALERT_MIN` 에 적혀 있다 — 임상 해설이 *"결절·종괴로 오탐하는 건
     * 상대적으로 안전"* 이라 했고(병원에 가서 확인하면 되니까), **놓치는 쪽이
     * 훨씬 나쁘다.**
     *
     * ⚠️ 문턱을 앱에서 다시 재지 않는다. [score]·[threshold] 는 **보여 주기용**이고
     *    켤지 말지는 서버가 이미 정했다. 여기서 다시 재면 둘이 갈라진다.
     */
    @Immutable
    data class Alert(
        val code: String,
        val text: String,
        val action: String,
        val caveat: String,
        val score: Float,
        val threshold: Float,
    )

    companion object {
        fun parse(json: JSONObject): ScreeningReport {
            val s1 = json.optJSONObject("stage1")
            val s2obj = json.optJSONObject("stage2")
            val s2 = s2obj?.optJSONArray("distribution")
            val gs = s2obj?.optJSONArray("groups")
            // ⚠️ `optJSONObject` 는 JSON null 에도 null 을 준다 — 그래서 "안 뜸"과
            //    "필드가 없음"이 같게 읽힌다. 둘 다 안 그리는 게 맞으므로 괜찮다.
            val g = s2obj?.optJSONObject("group")
            val al = s2obj?.optJSONObject("alert")
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
                groups = buildList {
                    for (i in 0 until (gs?.length() ?: 0)) {
                        val o = gs!!.getJSONObject(i)
                        val name = o.optString("name")
                        // 이름이 비면 막대만 남아서 무엇인지 못 읽는다 — 통째로 뺀다.
                        if (name.isNotBlank()) {
                            add(Group(name, o.optDouble("percent", 0.0).toFloat()))
                        }
                    }
                },
                group = g?.let {
                    val text = it.optString("text")
                    // 문장이 없으면 그릴 게 없다. 앱이 대신 지어 쓰지 않는다.
                    if (text.isBlank()) null else GroupLine(
                        name = it.optString("name"),
                        percent = it.optDouble("percent", 0.0).toFloat(),
                        text = text,
                        caveat = it.optString("caveat"),
                        feature = it.optString("feature"),
                        detail = it.optString("detail"),
                    )
                },
                alert = al?.let {
                    val text = it.optString("text")
                    if (text.isBlank()) null else Alert(
                        code = it.optString("code"),
                        text = text,
                        action = it.optString("action"),
                        caveat = it.optString("caveat"),
                        score = it.optDouble("score", 0.0).toFloat(),
                        threshold = it.optDouble("threshold", 0.0).toFloat(),
                    )
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
