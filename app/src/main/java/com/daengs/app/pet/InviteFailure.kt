package com.daengs.app.pet

import org.json.JSONObject

/**
 * 서버가 실패를 설명하는 두 가지 모양.
 *
 * **`detail` 이 문자열일 때도 있고 객체일 때도 있다.** 저쪽 라우터가 상황에 따라 다르게
 * 던진다 — 상한·이미 대표는 문장 한 줄이고, 연결 관련 오류는 `{code, message, …}` 다.
 * 앱이 둘 중 하나만 읽으면 나머지 경우에 화면이 빈 말을 한다.
 *
 * ⚠️ **[code] 로 분기하되 [message] 는 서버 것을 그대로 쓴다.** 앱이 문장을 새로 지으면
 * 서버가 문구를 고쳐도 앱이 안 따라간다.
 */
data class InviteFailure(
    /** `detail.code`. 문자열 `detail` 이면 null 이다. */
    val code: String? = null,
    /** 사용자에게 보여 줄 문장. 서버가 준 것이거나, 없으면 부르는 쪽이 넣은 기본값. */
    val message: String,
    /**
     * `link_selection_required` 가 같이 주는 것 — 선택이 빠진 초대 강아지들.
     *
     * **이 값이 오면 앱이 화면을 못 그렸다는 뜻이다.** 새 앱이 미리보기를 거쳤다면
     * 나올 수 없는 오류라, 사용자에게는 "앱을 업데이트해 주세요" 로 보인다.
     */
    val missingPetIds: List<String> = emptyList(),
    /** `link_not_allowed` 가 같이 주는 것. 어느 선택이 왜 거절됐나. */
    val petId: String? = null,
    val linkToPetId: String? = null,
    val reason: String? = null,
) {
    companion object {
        /** 서버가 아무 말도 안 했을 때. 상태 코드마다 부르는 쪽이 문장을 넣는다. */
        fun of(message: String): InviteFailure = InviteFailure(message = message)

        /**
         * 오류 본문을 읽는다. 모양이 아니면 [fallback] 을 쓴다.
         *
         * **`getString` 을 쓰지 않는다.** 안드로이드 org.json 은 JSON null 에 대해
         * 문자열 `"null"` 을 돌려주고 참조 구현은 던진다 — 둘 다 틀린 값이라
         * [optStringOrNull] 로 `isNull` 을 먼저 본다.
         */
        fun parse(body: String?, fallback: String): InviteFailure {
            val json = runCatching { JSONObject(body.orEmpty()) }.getOrNull()
                ?: return of(fallback)
            return when (val raw = json.opt("detail")) {
                is String -> InviteFailure(message = raw.takeIf(String::isNotBlank) ?: fallback)
                is JSONObject -> InviteFailure(
                    code = raw.optStringOrNull("code"),
                    message = raw.optStringOrNull("message") ?: fallback,
                    missingPetIds = raw.optJSONArray("missing_pet_ids")?.let { arr ->
                        (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
                    }.orEmpty(),
                    petId = raw.optStringOrNull("pet_id"),
                    linkToPetId = raw.optStringOrNull("link_to_pet_id"),
                    reason = raw.optStringOrNull("reason"),
                )
                else -> of(fallback)
            }
        }
    }
}

/**
 * 서버가 정해 둔 `detail.code` 들.
 *
 * **문자열을 화면 코드에 흩어 적지 않는다** — 오타가 나면 분기가 조용히 안 걸리고,
 * 그때 사용자는 아무 설명 없는 기본 문구를 본다.
 */
object InviteErrorCode {
    /** 409 — 묶음인데 선택이 빠졌다. 새 앱에서는 나오면 안 되는 오류다. */
    const val LINK_SELECTION_REQUIRED = "link_selection_required"

    /** 422 — 초대에 없는 강아지 id 를 보냈다. */
    const val UNKNOWN_INVITED_PET = "unknown_invited_pet"

    /** 422 — 같은 기존 강아지를 두 항목에 골랐다. */
    const val DUPLICATE_LINK_TARGET = "duplicate_link_target"

    /** 409 — 고른 기존 강아지가 연결 조건에 안 맞는다. `reason` 이 같이 온다. */
    const val LINK_NOT_ALLOWED = "link_not_allowed"

    /** 409 — 연결된 보호자에게는 아직 대표를 넘길 수 없다. */
    const val LINKED_OWNER_TRANSFER_UNSUPPORTED = "linked_owner_transfer_unsupported"

    /** 409 — 연결된 아이를 그룹 주보호자가 아닌 사람이 고치거나 지우려 했다. */
    const val NOT_GROUP_OWNER = "not_group_owner"
}

/** `isNull` 을 먼저 본다. 안드로이드 org.json 은 JSON null 을 `"null"` 로 준다. */
private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)
