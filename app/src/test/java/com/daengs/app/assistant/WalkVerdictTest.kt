package com.daengs.app.assistant

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * 저쪽 `daengs_life/app/dto/walk.py` `WalkOut` 을 우리가 제대로 읽는가.
 * [AssistantResponseTest] 와 같은 이유로 존재한다 — 저쪽이 필드를 바꾸면 여기가
 * 먼저 깨져야 한다.
 *
 * 특히 **모르는 값과 빠진 필드를 견디는지**를 본다. 이 파일이 저쪽 스키마에
 * 묶여 있어서, 안 견디면 저쪽 변경이 곧바로 크래시가 된다.
 */
class WalkVerdictTest {

    private fun walkResults(data: String) = JSONArray(
        """[{"capability": "walk", "status": "OK", "data": $data}]""",
    )

    @Test
    fun `등급과 이유와 시간대를 읽는다`() {
        val verdict = WalkVerdict.parse(
            JSONObject(
                """
                {
                  "now": {
                    "grade": "CAUTION",
                    "dominant": ["heat"],
                    "capped": false,
                    "axes": {
                      "heat": {"grade": "CAUTION", "note": "체감온도 33.3℃ (여름식)"},
                      "air": {"grade": "GOOD", "note": "PM10 42㎍/㎥"}
                    }
                  },
                  "windows": [
                    {"from": "2026-09-02T18:00:00+09:00", "to": "2026-09-02T20:00:00+09:00",
                     "grade": "GOOD"}
                  ],
                  "location": {"label": "역삼1동 (측정소: 강남구) 기준"},
                  "notes": ["한낮 아스팔트를 피해 주세요."]
                }
                """.trimIndent(),
            ),
        )

        assertEquals(WalkVerdict.Grade.CAUTION, verdict.grade)
        // dominant 가 가리킨 축만. 좋은 축(air)은 이유가 아니다.
        assertEquals(listOf("체감온도 33.3℃ (여름식)"), verdict.reasons)
        assertFalse(verdict.capped)
        assertEquals(1, verdict.windows.size)
        assertEquals(LocalDateTime.of(2026, 9, 2, 18, 0), verdict.windows[0].from)
        assertEquals(LocalDateTime.of(2026, 9, 2, 20, 0), verdict.windows[0].to)
        assertEquals(WalkVerdict.Grade.GOOD, verdict.windows[0].grade)
        assertEquals("역삼1동 (측정소: 강남구) 기준", verdict.locationLabel)
        assertEquals(listOf("한낮 아스팔트를 피해 주세요."), verdict.notes)
    }

    @Test
    fun `dominant 가 비면 축 전부를 이유로 쓴다`() {
        // 깎을 것이 없는 좋은 날. 여기서 이유가 비면 카드가 등급만 있는 지금과 같아진다.
        val verdict = WalkVerdict.parse(
            JSONObject(
                """
                {
                  "now": {
                    "grade": "GOOD",
                    "dominant": [],
                    "axes": {
                      "heat": {"note": "체감온도 21.4℃ (여름식)"},
                      "rain": {"note": "강수 없음"}
                    }
                  }
                }
                """.trimIndent(),
            ),
        )

        assertEquals(WalkVerdict.Grade.GOOD, verdict.grade)
        assertEquals(
            setOf("체감온도 21.4℃ (여름식)", "강수 없음"),
            verdict.reasons.toSet(),
        )
    }

    @Test
    fun `capped 를 읽는다`() {
        val verdict = WalkVerdict.parse(
            JSONObject("""{"now": {"grade": "CAUTION", "capped": true}}"""),
        )
        assertTrue(verdict.capped)
    }

    @Test
    fun `모르는 등급은 UNKNOWN 으로 떨어진다`() {
        // 저쪽이 등급을 하나 더 만드는 날 크래시하면 안 된다.
        val verdict = WalkVerdict.parse(JSONObject("""{"now": {"grade": "SCORCHING"}}"""))
        assertEquals(WalkVerdict.Grade.UNKNOWN, verdict.grade)
    }

    @Test
    fun `소문자 unknown 도 UNKNOWN 이다`() {
        val verdict = WalkVerdict.parse(JSONObject("""{"now": {"grade": "unknown"}}"""))
        assertEquals(WalkVerdict.Grade.UNKNOWN, verdict.grade)
    }

    @Test
    fun `now 가 통째로 없어도 안 죽는다`() {
        val verdict = WalkVerdict.parse(JSONObject("{}"))
        assertEquals(WalkVerdict.Grade.UNKNOWN, verdict.grade)
        assertTrue(verdict.reasons.isEmpty())
        assertTrue(verdict.windows.isEmpty())
        assertEquals("", verdict.locationLabel)
    }

    @Test
    fun `시간이 깨진 시간대는 그 줄만 버린다`() {
        // 카드가 통째로 사라지는 것보다 칩 하나가 빠지는 편이 낫다.
        val verdict = WalkVerdict.parse(
            JSONObject(
                """
                {
                  "now": {"grade": "GOOD"},
                  "windows": [
                    {"from": "어제", "to": "오늘", "grade": "GOOD"},
                    {"from": "2026-09-02T06:00:00+09:00", "to": "2026-09-02T08:00:00+09:00",
                     "grade": "GOOD"}
                  ]
                }
                """.trimIndent(),
            ),
        )
        assertEquals(1, verdict.windows.size)
        assertEquals(6, verdict.windows[0].from.hour)
    }

    @Test
    fun `오프셋 없는 시각도 읽는다`() {
        val verdict = WalkVerdict.parse(
            JSONObject(
                """
                {
                  "now": {"grade": "GOOD"},
                  "windows": [{"from": "2026-09-02T06:00:00", "to": "2026-09-02T08:00:00"}]
                }
                """.trimIndent(),
            ),
        )
        assertEquals(1, verdict.windows.size)
    }

    @Test
    fun `results 에서 산책 결과를 찾는다`() {
        assertNotNull(WalkVerdict.from(walkResults("""{"now": {"grade": "GOOD"}}""")))
    }

    @Test
    fun `산책이 아니면 null 이다`() {
        val results = JSONArray(
            """[{"capability": "training", "status": "OK", "data": {"answer": "훈련 답변"}}]""",
        )
        assertNull(WalkVerdict.from(results))
    }

    @Test
    fun `results 가 여럿이어도 산책을 골라낸다`() {
        // 순서는 약속돼 있지 않다. results[0] 을 산책이라고 가정하면 안 된다.
        val results = JSONArray(
            """
            [
              {"capability": "training", "status": "OK", "data": {"answer": "훈련 답변"}},
              {"capability": "walk", "status": "OK", "data": {"now": {"grade": "UNSAFE"}}}
            ]
            """.trimIndent(),
        )
        assertEquals(WalkVerdict.Grade.UNSAFE, WalkVerdict.from(results)?.grade)
    }

    @Test
    fun `data 가 없는 산책 결과는 건너뛴다`() {
        // ABSTAINED·ERROR 는 data 없이 오기도 한다.
        val results = JSONArray("""[{"capability": "walk", "status": "ERROR"}]""")
        assertNull(WalkVerdict.from(results))
    }

    @Test
    fun `results 가 없으면 null 이다`() {
        assertNull(WalkVerdict.from(null))
    }

    @Test
    fun `응답 전체에서 산책 판정과 결과 수를 읽는다`() {
        val json = """
            {
              "request_id": "req-9",
              "status": "ANSWERED",
              "message": "현재 산책 판단: GOOD",
              "results": [
                {"capability": "walk", "status": "OK", "data": {"now": {"grade": "GOOD"}}}
              ],
              "handoffs": []
            }
        """.trimIndent()
        val response = AssistantResponse.parse(JSONObject(json))
        assertEquals(WalkVerdict.Grade.GOOD, response.walk?.grade)
        assertEquals(1, response.resultCount)
    }
}
