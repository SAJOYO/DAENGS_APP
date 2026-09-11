package com.daengs.app.care

import org.json.JSONObject

/**
 * 케어 기록을 남긴 사람. 저쪽 `schemas/care_event.py` 의 `ActorOut` 이다.
 *
 * **두 칸 다 null 일 수 있다.** 저쪽 라우터(`_to_response`)는 `actor_app_user_id` 가
 * 비어 있어도 `actor` 객체 자체는 항상 만들어 보낸다 — 이 컬럼이 생기기 전에 쌓인
 * 기록과 탈퇴자가 그렇다(`models/care_event.py` 의 `ON DELETE SET NULL`).
 *
 * ⚠️ **`getString` 으로 읽지 않는다.** 안드로이드의 `org.json` 은 JSON null 에
 * 문자열 `"null"` 을 돌려주고(테스트 JVM 의 참조 구현은 대신 예외를 던진다), 그러면
 * 기기에서는 `"null"` 이라는 가짜 id 가 모델에 앉고 단위 테스트에서는 케어 기록이
 * 통째로 안 읽힌다. `isNull` 로 먼저 가른 뒤 읽어야 두 구현이 같은 답을 낸다.
 *
 * **구성원 목록의 [PetMember][com.daengs.app.pet.PetMember] 와 다른 타입이다.**
 * 저쪽 `MemberOut.app_user_id` 는 non-null 이라 그쪽은 없으면 계약 위반이고, 여기는
 * null 이 정상이다. 한 타입으로 묶으면 둘 중 하나의 계약이 느슨해진다.
 */
data class CareActor(
    /** 누구인지 모르면 null. **null 을 "나" 로 착각하면 안 된다** */
    val appUserId: String?,
    /** 지금도 그 아이의 구성원일 때만 온다. 아니면 null 이고 [displayName] 이 대신 말한다. */
    val nickname: String?,
) {
    val displayName: String get() = nickname ?: "이전 보호자"

    companion object {
        fun parse(json: JSONObject): CareActor = CareActor(
            appUserId = json.optStringOrNull("app_user_id"),
            nickname = json.optStringOrNull("nickname"),
        )

        private fun JSONObject.optStringOrNull(key: String): String? =
            if (isNull(key)) null else optString(key).ifBlank { null }
    }
}
