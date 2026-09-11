package com.daengs.app.pet

import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 저쪽 계약을 우리가 제대로 읽는가.
 *
 * 아래 JSON 은 `DAENGS_dev` 의 `schemas/pet_member.py` (`MemberListResponse` · `MemberOut`)가
 * 만드는 모양 그대로다. 저쪽이 필드를 바꾸면 여기가 먼저 깨져야 한다.
 */
class PetMemberContractTest {

    @Test
    fun `대표가 맨 앞인 구성원 목록을 읽는다`() {
        val list = PetMemberList.parse(
            JSONObject(
                """{
                    "pet_id": "p1",
                    "members": [
                        {"app_user_id": "u1", "nickname": "아빠", "is_owner": true},
                        {"app_user_id": "u2", "nickname": "엄마", "is_owner": false}
                    ]
                }""",
            ),
        )

        assertEquals("p1", list.petId)
        assertEquals(2, list.members.size)
        assertEquals("u1", list.members[0].appUserId)
        assertEquals("아빠", list.members[0].nickname)
        assertTrue(list.members[0].isOwner)
        assertFalse(list.members[1].isOwner)
    }

    @Test
    fun `이름을 못 찾은 구성원은 닉네임만 비어 온다`() {
        val member = PetMember.parse(
            JSONObject("""{"app_user_id": "u1", "nickname": null, "is_owner": false}"""),
        )

        assertEquals("u1", member.appUserId)
        assertNull(member.nickname)
    }

    /**
     * **`app_user_id` 는 저쪽 계약상 non-null 이다** (`MemberOut.app_user_id: uuid.UUID`).
     * 케어 기록의 `actor` 는 null 이 정상이라 느슨하게 읽지만, 이쪽까지 같이 느슨해지면
     * 계약이 바뀐 것을 아무도 모르게 된다. 여기서는 깨지는 것이 맞다.
     */
    @Test
    fun `구성원에 app_user_id 가 없으면 조용히 넘기지 않는다`() {
        assertThrows(JSONException::class.java) {
            PetMember.parse(JSONObject("""{"app_user_id": null, "nickname": "아빠", "is_owner": true}"""))
        }
    }
}
