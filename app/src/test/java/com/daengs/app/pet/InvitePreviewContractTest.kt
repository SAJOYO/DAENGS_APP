package com.daengs.app.pet

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 수락 전 미리보기. 저쪽 `InvitePreviewResponse` 다.
 *
 * **한 응답이 화면 한 장을 다 채운다** — 초대된 아이들과 내가 고를 수 있는 기존 아이가
 * 같이 온다. 나누면 두 응답의 정합성을 앱이 맞춰야 한다.
 */
class InvitePreviewContractTest {

    @Test
    fun `초대된 아이와 연결 후보를 함께 읽는다`() {
        val preview = InvitePreview.parse(JSONObject(FULL))

        assertEquals("네옹집사", preview.invitedByNickname)
        assertEquals(listOf("롱이", "몽이"), preview.pets.map { it.name })
        assertEquals(listOf("롱롱씨"), preview.linkCandidates.map { it.name })
    }

    /** 이미 구성원인 아이는 오류가 아니다 — 수락하면 그냥 지나간다. */
    @Test
    fun `이미 구성원인 아이를 표시한다`() {
        val preview = InvitePreview.parse(JSONObject(FULL))

        assertFalse(preview.pets[0].alreadyMember)
        assertTrue(preview.pets[1].alreadyMember)
    }

    /**
     * **두 마리 이상이면 서버가 항목별 선택을 요구한다** (409 `link_selection_required`).
     * 화면이 이 값으로 선택 UI 를 낼지 정한다.
     */
    @Test
    fun `두 마리 이상이면 선택이 필요하다`() {
        assertTrue(InvitePreview.parse(JSONObject(FULL)).needsChoice)
        assertFalse("한 마리는 옛 계약으로도 통과한다", InvitePreview.parse(JSONObject(SINGLE)).needsChoice)
    }

    /** 초대한 사람의 이름을 서버가 못 줄 수 있다. `"null"` 문자열이 되면 화면에 그대로 뜬다. */
    @Test
    fun `초대한 사람 이름이 없으면 null 이다`() {
        val preview = InvitePreview.parse(JSONObject(SINGLE))

        assertNull(preview.invitedByNickname)
    }

    /** 연결할 만한 아이가 없는 것은 정상이다 — 화면은 "새로 참여" 만 내놓는다. */
    @Test
    fun `연결 후보가 비어 있어도 읽는다`() {
        val preview = InvitePreview.parse(JSONObject(SINGLE))

        assertTrue(preview.linkCandidates.isEmpty())
    }

    /** null 은 "연결 없이 참여" 라는 **선택**이지 빠진 것이 아니다. */
    @Test
    fun `연결 선택은 null 도 값이다`() {
        val choice = InviteLinkChoice("p1", null)

        assertEquals("p1", choice.petId)
        assertNull(choice.linkToPetId)
    }

    private companion object {
        const val FULL = """
        {"invited_by_nickname":"네옹집사","expires_at":"2026-09-13T00:00:00Z",
         "pets":[{"pet_id":"p1","name":"롱이","breed":"beagle","has_photo":true,"already_member":false},
                 {"pet_id":"p2","name":"몽이","breed":null,"has_photo":false,"already_member":true}],
         "link_candidates":[{"pet_id":"m1","name":"롱롱씨","breed":"yorkshire","has_photo":true}]}
        """

        const val SINGLE = """
        {"invited_by_nickname":null,"expires_at":"2026-09-13T00:00:00Z",
         "pets":[{"pet_id":"p1","name":"롱이","breed":"beagle","has_photo":false,"already_member":false}],
         "link_candidates":[]}
        """
    }
}
