package com.daengs.app.pet

import org.json.JSONObject

/**
 * 강아지 한 마리의 구성원 한 명. 계약은 저쪽 `schemas/pet_member.py` 의 `MemberOut` 이다.
 *
 * **`app_user_id` 는 non-null 이다.** 저쪽이 `uuid.UUID` 로 못 박아 뒀고, 여기 실리는
 * 사람은 전부 **지금 구성원**이라 id 가 없을 수가 없다. 그래서 없으면 조용히 null 로
 * 넘기지 않고 파싱에서 바로 깨뜨린다 — 계약이 바뀌면 그 사실이 드러나야 한다.
 * 케어 기록의 [CareActor][com.daengs.app.care.CareActor] 와 타입을 나눠 둔 이유가
 * 이것이다: 그쪽은 null 이 정상이고 이쪽은 계약 위반이다.
 *
 * **`joined_at` 이 없다.** 돌보미는 `list_members` 가 id 만 주고, 대표는 애초에 "가입"
 * 개념이 없어서(`pets.created_at` 은 강아지 등록 시각이지 대표가 참여한 시각이 아니다)
 * 값을 못 채우는 자리라 서버가 계약에서 아예 뺐다.
 */
data class PetMember(
    val appUserId: String,
    /**
     * 표시할 이름. 목록에 실리는 사람은 전부 현재 구성원이라 보통 값이 있다.
     * **없다고 "이전 보호자" 로 그리지 않는다** — 그건 케어 기록 쪽 규칙이고,
     * 여기서 null 은 사용자 행이 없어진 드문 경우다.
     */
    val nickname: String?,
    val isOwner: Boolean,
) {
    companion object {
        fun parse(json: JSONObject): PetMember = PetMember(
            appUserId = json.getString("app_user_id"),
            nickname = if (json.isNull("nickname")) null else json.optString("nickname").ifBlank { null },
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
