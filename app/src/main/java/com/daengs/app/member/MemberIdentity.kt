package com.daengs.app.member

import org.json.JSONObject

/**
 * 기록을 만든 보호자. 닉네임은 그 사람이 지금도 해당 강아지의 구성원일 때만 온다.
 * null 닉네임을 빈 문자열로 바꾸면 옛 기록에 재가입 뒤의 새 이름을 붙이게 되므로 그대로 둔다.
 */
data class MemberIdentity(
    val appUserId: String,
    val nickname: String?,
) {
    val displayName: String get() = nickname ?: "이전 보호자"

    companion object {
        fun parse(json: JSONObject): MemberIdentity = MemberIdentity(
            appUserId = json.getString("app_user_id"),
            nickname = if (json.isNull("nickname")) null else json.optString("nickname").ifBlank { null },
        )
    }
}
