package com.daengs.app.pet

import com.daengs.app.member.MemberIdentity
import org.json.JSONObject

/**
 * 강아지 한 마리의 구성원 한 명. 계약은 저쪽 `schemas/pet_member.py` 의 `MemberOut` 이다.
 *
 * **`joined_at` 이 없다.** 돌보미는 `list_members` 가 id 만 주고, 대표는 애초에 "가입"
 * 개념이 없어서(`pets.created_at` 은 강아지 등록 시각이지 대표가 참여한 시각이 아니다)
 * 값을 못 채우는 자리라 서버가 계약에서 아예 뺐다.
 */
data class PetMember(
    val identity: MemberIdentity,
    val isOwner: Boolean,
) {
    companion object {
        fun parse(json: JSONObject): PetMember = PetMember(
            identity = MemberIdentity.parse(json),
            isOwner = json.getBoolean("is_owner"),
        )
    }
}

/**
 * `GET /app/pets/{pet_id}/members` 응답. **대표가 맨 앞이다** — 서버가 그 순서로 정렬해 준다.
 */
data class PetMemberList(val petId: String, val members: List<PetMember>) {
    companion object {
        fun parse(json: JSONObject): PetMemberList {
            val arr = json.getJSONArray("members")
            return PetMemberList(
                petId = json.getString("pet_id"),
                members = (0 until arr.length()).map { PetMember.parse(arr.getJSONObject(it)) },
            )
        }
    }
}
