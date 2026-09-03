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
 * 오케스트레이션 v1 은 상태가 없다 — 대화 기록이나 `requested_capability` 를 몰래
 * 얹으면 서버 의미 라우터가 앱의 판단을 신뢰하게 된다.
 */
class AssistantApiTest {

    private val 광화문 = GeoPoint(37.5665, 126.9780)

    private val 대화 = "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
    private val 메시지 = "6ba7b812-9dad-11d1-80b4-00c04fd430c8"
    private val 강아지 = "3f2504e0-4f89-11d3-9a0c-0305e82c3301"

    // ── 대화 저장 (PR #131) ──────────────────────────────────────────────────

    /**
     * **무상태 본문은 바이트 단위로 그대로다.** v0.0.0 앱이 보내던 것과 같아야 한다 —
     * 저장 인자를 안 주면 새 칸이 하나도 안 붙는다.
     */
    @Test
    fun `저장 인자가 없으면 본문이 예전과 똑같다`() {
        assertEquals(
            AssistantApi.requestBody("강아지가 손을 물어요", where = null),
            AssistantApi.requestBody("강아지가 손을 물어요", where = null, persistence = null),
        )
        assertEquals(
            AssistantApi.requestBody("오늘 산책 나가도 될까?", where = 광화문),
            AssistantApi.requestBody("오늘 산책 나가도 될까?", where = 광화문, persistence = null),
        )
        assertEquals("""{"query":"강아지가 손을 물어요"}""", AssistantApi.requestBody("강아지가 손을 물어요", null))
    }

    /** 저장하는 요청은 두 id 를 **반드시 같이** 싣는다. 한쪽만 있으면 저쪽이 422 다. */
    @Test
    fun `저장 인자가 있으면 두 id 와 고른 강아지를 같이 담는다`() {
        val body = JSONObject(
            AssistantApi.requestBody("밤에 짖어요", where = null, persistence = ChatPersistence(대화, 메시지, 강아지)),
        )
        assertEquals("밤에 짖어요", body.getString("query"))
        assertEquals(대화, body.getString("chat_session_id"))
        assertEquals(메시지, body.getString("client_message_id"))
        assertEquals(강아지, body.getString("active_dog_id"))
        assertFalse(body.has("requested_capability"))
        assertFalse(body.has("location"))
        assertEquals(4, body.keys().asSequence().count())
    }

    /** 위치 규칙은 저장해도 그대로다 — 한국 안이면 싣고, 밖이면 뺀다. */
    @Test
    fun `저장해도 위치 규칙은 그대로다`() {
        val inside = JSONObject(AssistantApi.requestBody("산책?", 광화문, ChatPersistence(대화, 메시지, 강아지)))
        assertEquals(37.5665, inside.getJSONObject("location").getDouble("lat"), 1e-9)
        assertEquals(5, inside.keys().asSequence().count())

        val 도쿄 = GeoPoint(35.6762, 139.6503)
        val outside = JSONObject(AssistantApi.requestBody("산책?", 도쿄, ChatPersistence(대화, 메시지, 강아지)))
        assertFalse(outside.has("location"))
        assertEquals(대화, outside.getString("chat_session_id"))
    }

    /** 고른 강아지가 없으면 `active_dog_id` 칸을 뺀다 — 빈 문자열은 저쪽이 422 로 막는다. */
    @Test
    fun `고른 강아지가 없으면 active_dog_id 칸을 뺀다`() {
        val body = JSONObject(AssistantApi.requestBody("질문", null, ChatPersistence(대화, 메시지, activeDogId = null)))
        assertFalse(body.has("active_dog_id"))
        assertEquals(3, body.keys().asSequence().count())
    }

    /**
     * 한쪽짜리 저장 인자는 **만들 수조차 없다.** 네트워크에 닿기 전에 여기서 죽는다 —
     * 저쪽에 보내면 422 로 질문이 통째로 버려진다.
     */
    @Test
    fun `한쪽만 있는 저장 인자는 보내기 전에 막힌다`() {
        assertThrows(IllegalArgumentException::class.java) { ChatPersistence("", 메시지, 강아지) }
        assertThrows(IllegalArgumentException::class.java) { ChatPersistence(대화, "", 강아지) }
        assertThrows(IllegalArgumentException::class.java) { ChatPersistence("not-a-uuid", 메시지, 강아지) }
        assertThrows(IllegalArgumentException::class.java) { ChatPersistence(대화, "not-a-uuid", 강아지) }
        assertThrows(IllegalArgumentException::class.java) { ChatPersistence(대화, 메시지, activeDogId = "  ") }
    }

    @Test
    fun `좌표가 없으면 query 하나만 담는다`() {
        val body = JSONObject(AssistantApi.requestBody("강아지가 손을 물어요", where = null))
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
        val body = JSONObject(AssistantApi.requestBody("오늘 산책 나가도 될까?", where = 광화문))
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
        val body = JSONObject(AssistantApi.requestBody("오늘 산책 나가도 될까?", where = 도쿄))
        assertFalse("범위 밖 좌표가 실리면 서버가 422 로 질문을 통째로 버린다", body.has("location"))
        assertEquals("오늘 산책 나가도 될까?", body.getString("query"))
    }

    @Test
    fun `경계값은 실린다`() {
        listOf(GeoPoint(33.0, 124.0), GeoPoint(39.0, 132.0)).forEach {
            val body = JSONObject(AssistantApi.requestBody("질문", where = it))
            assertTrue("$it 는 한국 범위 안이다", body.has("location"))
        }
    }
}
