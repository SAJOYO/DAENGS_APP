package com.daengs.app.pet

import com.daengs.app.auth.AuthApi
import org.json.JSONArray
import org.json.JSONObject

/**
 * 초대 묶음에 담긴 강아지 한 마리. 저쪽 `InvitePetBrief` 다.
 *
 * **건강정보가 없는 것이 의도다** — 수락 전에는 구성원이 아니라서, 토큰 하나로 남의 집
 * 지병·복약을 읽는 자리를 만들지 않는다.
 */
data class InvitePetBrief(
    val petId: String,
    val name: String,
    val breed: String?,
    val hasPhoto: Boolean,
) {
    companion object {
        fun parse(json: JSONObject): InvitePetBrief = InvitePetBrief(
            petId = json.getString("pet_id"),
            name = json.optString("name"),
            breed = json.optStringOrNull("breed"),
            hasPhoto = json.optBoolean("has_photo", false),
        )

        fun parseAll(arr: JSONArray?): List<InvitePetBrief> =
            (0 until (arr?.length() ?: 0)).map { parse(arr!!.getJSONObject(it)) }
    }
}

/**
 * `GET /app/pet-invites` 의 항목 하나. 저쪽 `InviteBundleOut`.
 *
 * **토큰이 없다.** 평문 토큰은 생성 응답에만 한 번 나오고 서버는 해시만 들고 있다
 * ([CreatedInviteBundle]). 그래서 이 목록으로는 링크를 다시 만들 수 없고 **취소만** 된다.
 */
data class InviteBundle(
    val id: String,
    val pets: List<InvitePetBrief>,
    val createdAtMs: Long,
    val expiresAtMs: Long,
    /** 누군가 수락한 시각. null 이면 아직 아무도 안 눌렀다. */
    val acceptedAtMs: Long?,
) {
    /**
     * @param nowMs 지금. **기기 시계를 여기서 읽지 않는다** — 부르는 쪽이 넣어야 테스트가
     *   실제 시각에 기대지 않는다.
     */
    fun status(nowMs: Long): InviteStatus = when {
        acceptedAtMs != null -> InviteStatus.ACCEPTED
        expiresAtMs <= nowMs -> InviteStatus.EXPIRED
        else -> InviteStatus.ACTIVE
    }

    companion object {
        fun parse(json: JSONObject): InviteBundle = InviteBundle(
            id = json.getString("id"),
            pets = InvitePetBrief.parseAll(json.optJSONArray("pets")),
            createdAtMs = json.getString("created_at").toEpochMs(),
            expiresAtMs = json.getString("expires_at").toEpochMs(),
            acceptedAtMs = if (json.isNull("accepted_at")) null else json.getString("accepted_at").toEpochMs(),
        )
    }
}

/** 묶음 목록 응답. **서버가 준 순서를 그대로 쓴다** — 앱이 다시 정렬하지 않는다. */
data class InviteBundleList(val invites: List<InviteBundle>) {
    companion object {
        fun parse(json: JSONObject): InviteBundleList {
            val arr = json.getJSONArray("invites")
            return InviteBundleList((0 until arr.length()).map { InviteBundle.parse(arr.getJSONObject(it)) })
        }
    }
}

/**
 * 방금 만든 묶음. 저쪽 `InviteBundleCreated`.
 *
 * ⚠️ **[token] 은 이 응답에만 있다.** 서버는 해시만 보관해 다시 못 보여 주므로, 목록을
 * 다시 받아도 이 값은 되살아나지 않는다. 잃으면 **취소하고 새로 만드는 수밖에** 없다.
 *
 * **로그·오류 문구·테스트 출력에 이 값을 찍지 않는다.** 초대를 가로챌 자격증명이다.
 */
data class CreatedInviteBundle(
    val id: String,
    val petIds: List<String>,
    val token: String,
    val expiresAtMs: Long,
) {
    /** 실수로 토큰이 로그에 찍히지 않게 [toString] 을 덮는다. */
    override fun toString(): String =
        "CreatedInviteBundle(id=$id, petIds=$petIds, token=<가림>, expiresAtMs=$expiresAtMs)"

    companion object {
        fun parse(json: JSONObject): CreatedInviteBundle {
            val arr = json.getJSONArray("pet_ids")
            return CreatedInviteBundle(
                id = json.getString("id"),
                petIds = (0 until arr.length()).map { arr.getString(it) },
                token = json.getString("token"),
                expiresAtMs = json.getString("expires_at").toEpochMs(),
            )
        }
    }
}

/**
 * 한 묶음에 담을 수 있는 강아지 수. 저쪽 `InviteBundleCreate` 의 `max_length` 와 같다.
 *
 * **한 사람이 돌볼 수 있는 마릿수와 같은 수다** — 그보다 많이 담아 봐야 받는 쪽이 수락에서
 * 409 를 받는다.
 */
const val MAX_PETS_PER_INVITE = 5

/**
 * 살아 있을 수 있는 묶음 수. **강아지당이 아니라 주보호자당이다** — 다중 초대로 바뀌면서
 * 세는 단위가 옮겨졌다 (단일 초대 시절의 [MAX_ACTIVE_INVITES] 는 강아지당이었다).
 *
 * **앱이 세는 것은 버튼을 미리 가리려는 것뿐이고 판정은 서버가 한다** — 다른 기기에서
 * 하나 더 만들었으면 앱의 셈이 늦으므로, 넘쳤을 때의 문장은 서버 409 의 것을 쓴다.
 */
const val MAX_ACTIVE_INVITE_BUNDLES = 3

/** 지금 살아 있는 묶음 수. 서버의 같은 셈이다. */
fun List<InviteBundle>.activeBundleCount(nowMs: Long): Int =
    count { it.status(nowMs) == InviteStatus.ACTIVE }

private fun String.toEpochMs(): Long = with(AuthApi) { this@toEpochMs.toEpochMs() }

private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)
