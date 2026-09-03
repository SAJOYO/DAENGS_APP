package com.daengs.app.assistant

import com.daengs.app.location.GeoPoint
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `좌표도 대표 강아지도 없으면 query 하나만 담는다`() {
        val body = JSONObject(AssistantApi.requestBody("강아지가 손을 물어요", where = null, activeDogId = null))
        assertEquals("강아지가 손을 물어요", body.getString("query"))
        assertFalse(body.has("requested_capability"))
        assertFalse(body.has("source"))
        assertFalse(body.has("action"))
        assertFalse(body.has("active_dog_id"))
        assertFalse(body.has("location"))
        assertEquals(1, body.keys().asSequence().count())
    }

    /**
     * **대표 강아지를 실어야 견종·나이가 답에 반영된다.**
     *
     * 저쪽(`SAJOYO/DAENGS_dev#202`)이 이 id 로 `pets` 를 읽는다. 앱이 이 칸을 빼면
     * 저쪽이 머지돼 있어도 사용자에게는 아무 변화가 없다 — 조용히 안 될 뿐 안 깨진다.
     */
    @Test
    fun `대표 강아지가 있으면 active_dog_id 를 같이 담는다`() {
        val body = JSONObject(
            AssistantApi.requestBody(
                "우리 애 비행기 태울 수 있나요?",
                where = null,
                activeDogId = "3f2a1c88-0000-4b1e-9c77-1d2e3f4a5b6c",
            ),
        )
        assertEquals("3f2a1c88-0000-4b1e-9c77-1d2e3f4a5b6c", body.getString("active_dog_id"))
        assertEquals(2, body.keys().asSequence().count())
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
            val body = JSONObject(AssistantApi.requestBody("질문", where = null, activeDogId = 없는_값))
            assertFalse("[$없는_값] 이 실리면 서버가 422 로 질문을 통째로 버린다", body.has("active_dog_id"))
            assertEquals(1, body.keys().asSequence().count())
        }
    }

    /**
     * **좌표를 실으면 산책 질문이 한 번에 답한다.**
     *
     * 안 실으면 서버가 `CLARIFY` 로 위치를 되묻는데, 이어서 묻는 토큰이 없어서
     * (무상태) 사용자는 되묻는 문장만 보고 끝난다.
     */
    @Test
    fun `좌표가 있으면 location 을 같이 담는다`() {
        val body = JSONObject(AssistantApi.requestBody("오늘 산책 나가도 될까?", where = 광화문, activeDogId = null))
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
        val body = JSONObject(AssistantApi.requestBody("오늘 산책 나가도 될까?", where = 도쿄, activeDogId = null))
        assertFalse("범위 밖 좌표가 실리면 서버가 422 로 질문을 통째로 버린다", body.has("location"))
        assertEquals("오늘 산책 나가도 될까?", body.getString("query"))
    }

    @Test
    fun `경계값은 실린다`() {
        listOf(GeoPoint(33.0, 124.0), GeoPoint(39.0, 132.0)).forEach {
            val body = JSONObject(AssistantApi.requestBody("질문", where = it, activeDogId = null))
            assertTrue("$it 는 한국 범위 안이다", body.has("location"))
        }
    }
}
