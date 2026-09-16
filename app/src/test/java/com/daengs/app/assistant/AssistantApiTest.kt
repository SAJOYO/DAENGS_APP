package com.daengs.app.assistant

import com.daengs.app.chat.ChatPersistence
import com.daengs.app.location.GeoPoint
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 요청 본문 모양을 여기서 고정한다.
 *
 * 서버 스키마가 `extra="forbid"` 라 **모르는 칸이 하나라도 있으면 422** 다.
 * 그래서 "무엇을 보내는가" 뿐 아니라 **"무엇을 안 보내는가" 도 계약**이다.
 *
 * 칸은 넷이고 생애가 둘로 갈린다:
 * - `active_dog_id` 는 **저장하든 안 하든 매 질의에 싣는다** (PR #112). 저쪽이 그 id 로
 *   `pets` 를 읽어 견종·나이를 얹는다.
 * - `chat_session_id` · `client_message_id` 는 **저장하는 질의에만**, 그리고 **반드시
 *   둘 같이** (PR #131). 한쪽만 오면 저쪽이 422 다.
 */
class AssistantApiTest {

    @Test fun `시설 문맥은 문자열이 아닌 중첩 객체로 보낸다`() {
        val facility = kotlinx.serialization.json.Json.parseToJsonElement("""
            {"client_request_id":"22222222-2222-4222-8222-222222222222"}
        """).let { it as kotlinx.serialization.json.JsonObject }
        val body = JSONObject(AssistantApi.requestBody("카페 찾아줘", null, null, null, facility, null, null))
        assertEquals("22222222-2222-4222-8222-222222222222", body.getJSONObject("facility").getString("client_request_id"))
        assertEquals(setOf("query", "facility"), body.keys().asSequence().toSet())
    }

    private val 광화문 = GeoPoint(37.5665, 126.9780)

    private val 대화 = "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
    private val 메시지 = "6ba7b812-9dad-11d1-80b4-00c04fd430c8"
    private val 강아지 = "3f2504e0-4f89-11d3-9a0c-0305e82c3301"

    // ── 대표 강아지 (PR #112) — 두 경로가 똑같이 싣는다 ────────────────────────

    /**
     * **대표 강아지를 실어야 견종·나이가 답에 반영된다.**
     *
     * 저쪽(`SAJOYO/DAENGS_dev#202`)이 이 id 로 `pets` 를 읽는다. 앱이 이 칸을 빼면
     * 저쪽이 머지돼 있어도 사용자에게는 아무 변화가 없다 — 조용히 안 될 뿐 안 깨진다.
     */
    @Test
    fun `무상태 질의도 대표 강아지를 싣는다`() {
        val body = JSONObject(
            AssistantApi.requestBody(
                "우리 애 비행기 태울 수 있나요?",
                where = null,
                activeDogId = 강아지,
                persistence = null, facility = null,
                screening = null,
                gait = null,
            ),
        )
        assertEquals("우리 애 비행기 태울 수 있나요?", body.getString("query"))
        assertEquals(강아지, body.getString("active_dog_id"))
        assertEquals(2, body.keys().asSequence().count())
    }

    /** 무상태 질의는 저장 id 를 **하나도** 안 싣는다. 그게 무상태의 정의다. */
    @Test
    fun `무상태 질의는 저장 id 를 싣지 않는다`() {
        val body = JSONObject(
            AssistantApi.requestBody("밤에 짖어요", where = null, activeDogId = 강아지, persistence = null, facility = null, screening = null, gait = null),
        )
        assertFalse("무상태인데 chat_session_id 가 실렸다", body.has("chat_session_id"))
        assertFalse("무상태인데 client_message_id 가 실렸다", body.has("client_message_id"))
        assertEquals(강아지, body.getString("active_dog_id"))
    }

    /**
     * **대표가 없으면 칸 자체를 뺀다.**
     *
     * 서버 스키마가 `extra="forbid"` 라 `active_dog_id: null` 이나 `""` 을 넣으면
     * 질문이 통째로 422 다. 아직 강아지를 등록하지 않은 사용자가 여기 걸린다.
     */
    @Test
    fun `대표 강아지가 없으면 칸 자체를 뺀다`() {
        listOf(null, "", "   ").forEach { 없는_값 ->
            val body = JSONObject(
                AssistantApi.requestBody("질문", where = null, activeDogId = 없는_값, persistence = null, facility = null, screening = null, gait = null),
            )
            assertFalse("[$없는_값] 이 실리면 서버가 422 로 질문을 통째로 버린다", body.has("active_dog_id"))
            assertEquals(1, body.keys().asSequence().count())
        }
    }

    /**
     * **본문 만들기에는 기본값이 하나도 없다.**
     *
     * 기본값이 있으면 부르는 쪽이 칸을 **빠뜨린 것**과 **일부러 뺀 것**이 안 갈린다.
     * `active_dog_id` 가 그렇게 빠지면 저쪽 개인화가 조용히 안 되고 아무도 안 깨진다 —
     * 정확히 PR #112 가 막으려던 것이다.
     *
     * 코틀린은 기본값이 하나라도 있으면 `…$default` 다리 메서드를 만든다. 그 다리가
     * 없다는 것이 "칸을 조용히 뺄 수 없다" 의 기계적 증거다.
     */
    @Test
    fun `본문 만들기는 어떤 칸도 조용히 빠뜨릴 수 없다`() {
        val 만드는_것 = AssistantApi::class.java.declaredMethods.filter { it.name.startsWith("requestBody") }
        assertTrue("requestBody 를 못 찾았다", 만드는_것.isNotEmpty())
        assertTrue(
            "기본값이 있으면 \$default 다리가 생긴다 — 그러면 칸을 조용히 뺄 수 있다: ${만드는_것.map { it.name }}",
            만드는_것.none { it.name.endsWith("\$default") },
        )
    }

    // ── 대화 저장 (PR #131) — 저장하는 질의에만 붙는 두 id ─────────────────────

    /** 저장하는 요청은 세 칸을 **같이** 싣는다. 저장 id 는 한쪽만 있으면 저쪽이 422 다. */
    @Test
    fun `저장 인자가 있으면 두 id 와 대표 강아지를 같이 담는다`() {
        val body = JSONObject(
            AssistantApi.requestBody(
                "밤에 짖어요",
                where = null,
                activeDogId = 강아지,
                persistence = ChatPersistence(대화, 메시지), facility = null,
                screening = null,
                gait = null,
            ),
        )
        assertEquals("밤에 짖어요", body.getString("query"))
        assertEquals(대화, body.getString("chat_session_id"))
        assertEquals(메시지, body.getString("client_message_id"))
        assertEquals(강아지, body.getString("active_dog_id"))
        assertFalse(body.has("requested_capability"))
        assertFalse(body.has("location"))
        assertEquals(4, body.keys().asSequence().count())
    }

    /**
     * 한쪽짜리 저장 인자는 **만들 수조차 없다.** 네트워크에 닿기 전에 여기서 죽는다 —
     * 저쪽에 보내면 422 로 질문이 통째로 버려진다.
     */
    @Test
    fun `한쪽만 있는 저장 인자는 보내기 전에 막힌다`() {
        assertThrows(IllegalArgumentException::class.java) { ChatPersistence("", 메시지) }
        assertThrows(IllegalArgumentException::class.java) { ChatPersistence(대화, "") }
        assertThrows(IllegalArgumentException::class.java) { ChatPersistence("not-a-uuid", 메시지) }
        assertThrows(IllegalArgumentException::class.java) { ChatPersistence(대화, "not-a-uuid") }
    }

    /**
     * 저장 여부는 **대표 강아지와 상관없다.** 두 값의 생애가 달라서, 저장 id 를 얹고
     * 빼도 `active_dog_id` 는 그대로 있어야 한다.
     */
    @Test
    fun `저장을 얹어도 대표 강아지는 같은 자리에 그대로 있다`() {
        val 무상태 = JSONObject(
            AssistantApi.requestBody("같은 질문", where = null, activeDogId = 강아지, persistence = null, facility = null, screening = null, gait = null),
        )
        val 저장 = JSONObject(
            AssistantApi.requestBody(
                "같은 질문",
                where = null,
                activeDogId = 강아지,
                persistence = ChatPersistence(대화, 메시지), facility = null,
                screening = null,
                gait = null,
            ),
        )
        assertEquals(무상태.getString("active_dog_id"), 저장.getString("active_dog_id"))
        // 늘어난 것은 저장 id 둘뿐이다.
        assertEquals(무상태.keys().asSequence().count() + 2, 저장.keys().asSequence().count())
    }

    /** 위치 규칙은 저장해도 그대로다 — 한국 안이면 싣고, 밖이면 뺀다. */
    @Test
    fun `저장해도 위치 규칙은 그대로다`() {
        val inside = JSONObject(
            AssistantApi.requestBody("산책?", 광화문, 강아지, ChatPersistence(대화, 메시지), null, null, null),
        )
        assertEquals(37.5665, inside.getJSONObject("location").getDouble("lat"), 1e-9)
        assertEquals(5, inside.keys().asSequence().count())

        val 도쿄 = GeoPoint(35.6762, 139.6503)
        val outside = JSONObject(
            AssistantApi.requestBody("산책?", 도쿄, 강아지, ChatPersistence(대화, 메시지), null, null, null),
        )
        assertFalse(outside.has("location"))
        assertEquals(대화, outside.getString("chat_session_id"))
    }

    // ── 무상태 기본 계약 (v0.0.0 부터) ────────────────────────────────────────

    @Test
    fun `좌표도 대표 강아지도 없으면 query 하나만 담는다`() {
        val body = JSONObject(
            AssistantApi.requestBody("강아지가 손을 물어요", where = null, activeDogId = null, persistence = null, facility = null, screening = null, gait = null),
        )
        assertEquals("강아지가 손을 물어요", body.getString("query"))
        assertFalse(body.has("requested_capability"))
        assertFalse(body.has("source"))
        assertFalse(body.has("action"))
        assertFalse(body.has("active_dog_id"))
        assertFalse(body.has("location"))
        assertEquals(1, body.keys().asSequence().count())
    }

    /**
     * **좌표를 실으면 산책 질문이 한 번에 답한다.**
     *
     * 안 실으면 서버가 `CLARIFY` 로 위치를 되묻는데, 이어서 묻는 토큰이 없어서
     * (무상태) 사용자는 되묻는 문장만 보고 끝난다.
     */
    @Test
    fun `좌표가 있으면 location 을 같이 담는다`() {
        val body = JSONObject(
            AssistantApi.requestBody("오늘 산책 나가도 될까?", where = 광화문, activeDogId = null, persistence = null, facility = null, screening = null, gait = null),
        )
        assertEquals("오늘 산책 나가도 될까?", body.getString("query"))
        val location = body.getJSONObject("location")
        assertEquals(37.5665, location.getDouble("lat"), 1e-9)
        assertEquals(126.9780, location.getDouble("lon"), 1e-9)
        // 서버가 `lat`·`lon` 만 받는다. `alt` 같은 걸 더하면 422 다.
        assertEquals(2, location.keys().asSequence().count())
        assertEquals(2, body.keys().asSequence().count())
    }

    /**
     * ⚠️ **서버는 한국 좌표만 받는다** (`lat` 33~39, `lon` 124~132).
     *
     * 밖이면 422 라 질문 자체가 죽는다. 위치는 곁들이는 정보고 질문이 본체이므로,
     * 범위 밖이면 **좌표만 빼고 질문은 보낸다.**
     */
    @Test
    fun `한국 밖 좌표는 빼고 질문만 보낸다`() {
        val 도쿄 = GeoPoint(35.6762, 139.6503)
        val body = JSONObject(
            AssistantApi.requestBody("오늘 산책 나가도 될까?", where = 도쿄, activeDogId = null, persistence = null, facility = null, screening = null, gait = null),
        )
        assertFalse("범위 밖 좌표가 실리면 서버가 422 로 질문을 통째로 버린다", body.has("location"))
        assertEquals("오늘 산책 나가도 될까?", body.getString("query"))
    }

    @Test
    fun `경계값은 실린다`() {
        listOf(GeoPoint(33.0, 124.0), GeoPoint(39.0, 132.0)).forEach {
            val body = JSONObject(
                AssistantApi.requestBody("질문", where = it, activeDogId = null, persistence = null, facility = null, screening = null, gait = null),
            )
            assertTrue("$it 는 한국 범위 안이다", body.has("location"))
        }
    }

    // ── 피부 판정 이어 묻기 (백엔드 D-079) ─────────────────────────────────

    /** 신호와 기록 id 는 **같이** 간다. 한쪽만 가면 저쪽이 사진 등록 안내로 답한다. */
    @Test
    fun `피부 판정 이어 묻기는 skin 신호와 기록 id 를 함께 싣는다`() {
        val body = JSONObject(
            AssistantApi.requestBody(
                "이 결과가 무슨 뜻이에요?",
                where = null,
                activeDogId = "dog-1",
                persistence = null,
                facility = null,
                screening = ScreeningFollowUp("rec-1", explicit = true),
                gait = null,
            ),
        )
        assertEquals("skin", body.getString("requested_capability"))
        assertEquals("rec-1", body.getString("screening_record_id"))
        assertEquals("dog-1", body.getString("active_dog_id"))
    }

    /** 이어서 친 질문은 기록 id 만 간다 — 신호까지 보내면 산책 질문이 피부로 끌려간다 (`#569`). */
    @Test
    fun `이어서 친 질문은 기록 id 만 싣고 신호는 안 싣는다`() {
        val body = JSONObject(
            AssistantApi.requestBody(
                "그럼 언제 다시 찍어?",
                where = null,
                activeDogId = null,
                persistence = null,
                facility = null,
                screening = ScreeningFollowUp("rec-1", explicit = false),
                gait = null,
            ),
        )
        assertEquals("rec-1", body.getString("screening_record_id"))
        assertFalse(body.has("requested_capability"))
    }

    /** 보통 질문에는 두 칸이 **아예 없다** — 저쪽 스키마가 `extra="forbid"` 라 null 도 안 싣는다. */
    @Test
    fun `이어 묻기가 아니면 신호도 기록 id 도 칸 자체가 없다`() {
        val body = JSONObject(
            AssistantApi.requestBody(
                "밤에 짖어요",
                where = null,
                activeDogId = null,
                persistence = null,
                facility = null,
                screening = null,
                gait = null,
            ),
        )
        assertFalse(body.has("requested_capability"))
        assertFalse(body.has("screening_record_id"))
    }

    // ── 보행 비교 이어 묻기 (백엔드 D-080) ─────────────────────────────────

    /** 신호와 기록 참조는 **같이** 간다. 저쪽 `GaitCompareRef` 는 두 id 가 다 있어야 한다. */
    @Test
    fun `보행 비교 이어 묻기는 gait 신호와 기록 id 둘을 함께 싣는다`() {
        val body = JSONObject(
            AssistantApi.requestBody(
                "이 변화가 무슨 뜻이에요?",
                where = null,
                activeDogId = "dog-1",
                persistence = null,
                facility = null,
                screening = null,
                gait = GaitFollowUp(recentId = "rec-new", pastId = "rec-old"),
            ),
        )
        assertEquals("gait", body.getString("requested_capability"))
        val compare = body.getJSONObject("gait_compare")
        assertEquals("rec-new", compare.getString("recent_record_id"))
        assertEquals("rec-old", compare.getString("past_record_id"))
        assertEquals("dog-1", body.getString("active_dog_id"))
    }

    /** 비교 결과가 아니면 **칸 자체가 없다** — 저쪽 스키마가 `extra="forbid"` 라 null 도 안 싣는다. */
    @Test
    fun `보행 이어 묻기가 아니면 gait_compare 칸 자체가 없다`() {
        val body = JSONObject(
            AssistantApi.requestBody(
                "밤에 짖어요",
                where = null,
                activeDogId = null,
                persistence = null,
                facility = null,
                screening = null,
                gait = null,
            ),
        )
        assertFalse(body.has("gait_compare"))
        assertFalse(body.has("requested_capability"))
    }

    /**
     * 비교 참조는 **id 둘뿐**이다. 관절 수치나 앱이 지은 판정 문장을 실으면, 저장된 대화에서
     * 앱이 보낸 비교를 되돌릴 수 없다 — 비교는 서버가 두 기록을 읽어 다시 한다.
     */
    @Test
    fun `보행 이어 묻기는 비교 내용을 싣지 않는다`() {
        val body = JSONObject(
            AssistantApi.requestBody(
                "이 변화가 무슨 뜻이에요?",
                where = null,
                activeDogId = null,
                persistence = null,
                facility = null,
                screening = null,
                gait = GaitFollowUp(recentId = "rec-new", pastId = "rec-old"),
            ),
        )
        val compare = body.getJSONObject("gait_compare")
        assertEquals(setOf("recent_record_id", "past_record_id"), compare.keys().asSequence().toSet())
        assertFalse(body.has("screening_record_id"))
    }
}
