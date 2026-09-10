package com.daengs.app.pet

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        assertEquals("u1", list.members[0].identity.appUserId)
        assertEquals("아빠", list.members[0].identity.displayName)
        assertTrue(list.members[0].isOwner)
        assertFalse(list.members[1].isOwner)
    }

    @Test
    fun `지금 구성원이 아니면 닉네임이 없다`() {
        val member = PetMember.parse(
            JSONObject("""{"app_user_id": "u1", "nickname": null, "is_owner": false}"""),
        )

        assertNull(member.identity.nickname)
        assertEquals("이전 보호자", member.identity.displayName)
    }
}
