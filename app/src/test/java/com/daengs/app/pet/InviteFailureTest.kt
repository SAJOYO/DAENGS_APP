package com.daengs.app.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 서버가 실패를 설명하는 두 모양을 앱이 다 읽는가.
 *
 * **저쪽 라우터가 상황에 따라 다르게 던진다** — 상한·이미 대표는 `detail` 이 문장 한 줄이고,
 * 연결 관련 오류는 `{code, message, …}` 객체다. 한 쪽만 읽으면 나머지에서 화면이 빈 말을 한다.
 */
class InviteFailureTest {

    @Test
    fun `문장 하나짜리 detail 을 그대로 쓴다`() {
        val failure = InviteFailure.parse("""{"detail":"돌보는 아이가 너무 많습니다."}""", "기본값")

        assertEquals("돌보는 아이가 너무 많습니다.", failure.message)
        assertNull("문자열 detail 에는 code 가 없다", failure.code)
    }

    /** 구 앱이 묶음을 조용히 수락하는 것을 막는 오류다. 어느 아이가 빠졌는지 같이 온다. */
    @Test
    fun `선택 누락은 code 와 빠진 아이들을 읽는다`() {
        val failure = InviteFailure.parse(
            """
            {"detail":{"code":"link_selection_required",
                       "message":"이 초대에는 아이가 여러 마리예요. 앱을 업데이트해 주세요.",
                       "missing_pet_ids":["p1","p2"]}}
            """.trimIndent(),
            "기본값",
        )

        assertEquals(InviteErrorCode.LINK_SELECTION_REQUIRED, failure.code)
        assertEquals(listOf("p1", "p2"), failure.missingPetIds)
        assertTrue(failure.message.contains("여러 마리"))
    }

    /** 어느 선택이 왜 거절됐는지 알아야 화면이 그 줄만 다시 고르게 할 수 있다. */
    @Test
    fun `부적격 연결은 대상과 사유를 읽는다`() {
        val failure = InviteFailure.parse(
            """
            {"detail":{"code":"link_not_allowed","message":"선택한 아이는 연결할 수 없어요.",
                       "pet_id":"p1","link_to_pet_id":"m1","reason":"has_other_members"}}
            """.trimIndent(),
            "기본값",
        )

        assertEquals(InviteErrorCode.LINK_NOT_ALLOWED, failure.code)
        assertEquals("p1", failure.petId)
        assertEquals("m1", failure.linkToPetId)
        assertEquals("has_other_members", failure.reason)
    }

    @Test
    fun `422 두 종류를 code 로 가른다`() {
        val unknown = InviteFailure.parse(
            """{"detail":{"code":"unknown_invited_pet","message":"초대에 없는 아이예요."}}""",
            "기본값",
        )
        val duplicate = InviteFailure.parse(
            """{"detail":{"code":"duplicate_link_target","message":"같은 아이를 두 번 골랐어요."}}""",
            "기본값",
        )

        assertEquals(InviteErrorCode.UNKNOWN_INVITED_PET, unknown.code)
        assertEquals(InviteErrorCode.DUPLICATE_LINK_TARGET, duplicate.code)
    }

    /** 모양을 모르면 부르는 쪽이 정한 문장을 쓴다 — 영어 스택트레이스를 화면에 띄우지 않는다. */
    @Test
    fun `모르는 모양이면 기본 문장을 쓴다`() {
        assertEquals("기본값", InviteFailure.parse(null, "기본값").message)
        assertEquals("기본값", InviteFailure.parse("", "기본값").message)
        assertEquals("기본값", InviteFailure.parse("<html>502</html>", "기본값").message)
        // FastAPI 의 422 검증 오류는 detail 이 배열이다.
        assertEquals("기본값", InviteFailure.parse("""{"detail":[{"loc":["body"]}]}""", "기본값").message)
        // 빈 문자열 detail 도 문장이 아니다.
        assertEquals("기본값", InviteFailure.parse("""{"detail":""}""", "기본값").message)
    }

    /** 안드로이드 org.json 은 JSON null 을 `"null"` 로 준다. 그 값이 화면에 뜨면 안 된다. */
    @Test
    fun `null 필드를 문자열 null 로 읽지 않는다`() {
        val failure = InviteFailure.parse(
            """{"detail":{"code":"link_not_allowed","message":"안 돼요","reason":null}}""",
            "기본값",
        )

        assertNull(failure.reason)
        assertNull(failure.petId)
    }
}
