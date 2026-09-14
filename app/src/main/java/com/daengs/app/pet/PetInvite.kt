package com.daengs.app.pet

import com.daengs.app.auth.AuthApi
import org.json.JSONObject

/**
 * 초대 하나가 지금 어떤 상태인가.
 *
 * **가르는 순서가 정해져 있다** — 수락된 초대는 24시간이 지나도 "만료" 가 아니라 "사용됨"
 * 이다. 서버가 수락 뒤에도 행을 `expires_at` 까지 남겨 두기 때문에(재시도 200 복구용)
 * 만료를 먼저 보면 이미 들어온 사람의 초대가 만료로 뒤집힌다.
 */
enum class InviteStatus { ACTIVE, ACCEPTED, EXPIRED }

/**
 * `GET /app/pets/{pet_id}/invites` 의 항목 하나. 저쪽 `schemas/pet_member.py` 의 `InviteOut`.
 *
 * **토큰이 없다.** 평문 토큰은 생성 응답에만 한 번 나오고 서버는 해시만 들고 있다
 * ([CreatedInvite]). 그래서 이 목록으로는 링크를 다시 만들 수 없고 **취소만** 할 수 있다.
 */
data class PetInvite(
    val id: String,
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
        fun parse(json: JSONObject): PetInvite = PetInvite(
            id = json.getString("id"),
            createdAtMs = json.getString("created_at").toEpochMs(),
            expiresAtMs = json.getString("expires_at").toEpochMs(),
            acceptedAtMs = if (json.isNull("accepted_at")) null else json.getString("accepted_at").toEpochMs(),
        )
    }
}

/**
 * 초대 목록 응답. **서버가 준 순서(만든 순)를 그대로 쓴다** — 앱이 다시 정렬하지 않는다.
 */
data class PetInviteList(val petId: String, val invites: List<PetInvite>) {
    companion object {
        fun parse(json: JSONObject): PetInviteList {
            val arr = json.getJSONArray("invites")
            return PetInviteList(
                petId = json.getString("pet_id"),
                invites = (0 until arr.length()).map { PetInvite.parse(arr.getJSONObject(it)) },
            )
        }
    }
}

/**
 * 방금 만든 초대. 저쪽 `InviteCreated` 다.
 *
 * ⚠️ **[token] 은 이 응답에만 있다.** 서버는 해시만 보관해 다시 못 보여 주므로, 목록을
 * 다시 받아도 이 값은 되살아나지 않는다. 잃으면 그 초대는 **취소하고 새로 만드는 수밖에**
 * 없다 — 그래서 [PetInviteHolder] 가 목록과 별도의 일회성 자리에 들고 있는다.
 *
 * **로그·오류 문구·테스트 출력에 이 값을 찍지 않는다.** 초대를 가로챌 수 있는 자격증명이다.
 */
data class CreatedInvite(
    val id: String,
    val petId: String,
    val token: String,
    val expiresAtMs: Long,
) {
    /** 실수로 토큰이 로그에 찍히지 않게 [toString] 을 덮는다. */
    override fun toString(): String = "CreatedInvite(id=$id, petId=$petId, token=<가림>, expiresAtMs=$expiresAtMs)"

    companion object {
        fun parse(json: JSONObject): CreatedInvite = CreatedInvite(
            id = json.getString("id"),
            petId = json.getString("pet_id"),
            token = json.getString("token"),
            expiresAtMs = json.getString("expires_at").toEpochMs(),
        )
    }
}

/**
 * 동시에 살아 있을 수 있는 초대. 저쪽 `MAX_ACTIVE_INVITES` 와 같은 수다.
 *
 * **앱이 세는 것은 버튼을 미리 가리려는 것뿐이고, 판정은 서버가 한다** — 다른 기기에서
 * 하나 더 만들었으면 앱의 셈이 늦으므로, 넘쳤을 때의 문장은 서버 409 의 것을 쓴다.
 */
const val MAX_ACTIVE_INVITES = 3

/** 지금 새로 보낼 수 있는 초대인가. 서버 `MAX_ACTIVE_INVITES` 와 같은 셈이다. */
fun List<PetInvite>.activeCount(nowMs: Long): Int = count { it.status(nowMs) == InviteStatus.ACTIVE }

/**
 * 이 사람이 그 아이의 대표인가.
 *
 * **목록에 대표가 있는지만 보면 안 된다** — 대표는 늘 한 명 있으므로 그 검사는 항상 참이다.
 * 지금 로그인한 사람과 같은 줄을 찾아 그 줄의 `isOwner` 를 봐야 한다.
 */
fun List<PetMember>.isOwnedBy(appUserId: String?): Boolean =
    appUserId != null && any { it.appUserId == appUserId && it.isOwner }

private fun String.toEpochMs(): Long = with(AuthApi) { this@toEpochMs.toEpochMs() }
