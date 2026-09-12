package com.daengs.app.pet

import com.daengs.app.auth.AuthApi
import org.json.JSONObject

/**
 * 미리보기에 실리는 초대 강아지. [InvitePetBrief] 에 "이미 구성원인가" 가 붙는다.
 *
 * **이미 구성원인 아이는 수락해도 그냥 지나간다** — 오류가 아니다. 화면이 그 사실을
 * 미리 알려 주면 사용자가 "왜 안 늘었지" 하지 않는다.
 */
data class InvitePreviewPet(
    val pet: InvitePetBrief,
    val alreadyMember: Boolean,
) {
    val petId: String get() = pet.petId
    val name: String get() = pet.name

    companion object {
        fun parse(json: JSONObject): InvitePreviewPet = InvitePreviewPet(
            pet = InvitePetBrief.parse(json),
            alreadyMember = json.optBoolean("already_member", false),
        )
    }
}

/**
 * 수락 화면이 한 번에 그릴 것. 저쪽 `POST /app/pet-invites/preview` 응답이다.
 *
 * **연결 후보를 따로 부르지 않는다.** 화면이 "초대된 아이 목록 + 각 줄의 연결 선택" 한
 * 장이라, 나누면 두 응답의 정합성을 앱이 맞춰야 한다.
 *
 * ⚠️ **이 응답이 200 이라는 것이 새 계약을 쓸 수 있다는 신호다.** 구 서버에는 이 경로가
 * 없어 404 인데, 그때 수락에 `links` 를 실어 보내면 구 서버가 pydantic 기본값으로 **조용히
 * 무시하고 200** 을 낸다 — 사용자가 고른 연결이 사라진 채 전부 새로 참여해 버린다.
 * 그래서 미리보기를 못 받으면 다중 초대 화면을 그리지 않는다.
 */
data class InvitePreview(
    /** 초대한 사람. 서버가 이름을 못 줄 수도 있다. */
    val invitedByNickname: String?,
    val expiresAtMs: Long,
    val pets: List<InvitePreviewPet>,
    /**
     * 내가 고를 수 있는 기존 강아지. 조건은 서버가 판정한다 — 앱이 다시 거르지 않는다.
     *
     * **비어 있는 것이 정상일 수 있다.** 연결할 만한 아이가 없으면 화면은 "새로 참여" 만
     * 내놓는다.
     */
    val linkCandidates: List<InvitePetBrief>,
) {
    /** 두 마리 이상이면 서버가 항목별 선택을 **요구한다** (409 `link_selection_required`). */
    val needsChoice: Boolean get() = pets.size > 1

    companion object {
        fun parse(json: JSONObject): InvitePreview {
            val pets = json.getJSONArray("pets")
            return InvitePreview(
                invitedByNickname = if (json.isNull("invited_by_nickname")) {
                    null
                } else {
                    json.optString("invited_by_nickname").takeIf(String::isNotBlank)
                },
                expiresAtMs = json.getString("expires_at").toEpochMs(),
                pets = (0 until pets.length()).map { InvitePreviewPet.parse(pets.getJSONObject(it)) },
                linkCandidates = InvitePetBrief.parseAll(json.optJSONArray("link_candidates")),
            )
        }
    }
}

/**
 * 초대 강아지 하나에 대한 선택. 저쪽 `InviteLink` 다.
 *
 * **[linkToPetId] 가 null 인 것도 선택이다** — "연결 없이 참여" 라는 뜻이고, 항목을 아예
 * 빼는 것과 다르다. 묶음에서는 모든 항목이 있어야 서버가 받아 준다.
 */
data class InviteLinkChoice(val petId: String, val linkToPetId: String?)

/**
 * 미리보기를 못 받았을 때. **왜 못 받았는지로 화면이 갈린다.**
 */
sealed interface PreviewOutcome {
    data class Ready(val preview: InvitePreview) : PreviewOutcome

    /** 404 — 없는 토큰이거나 남이 이미 쓴 링크. */
    data object NotFound : PreviewOutcome

    /** 410 — 만료됐거나 묶음 구성이 바뀌었다. */
    data object Expired : PreviewOutcome

    /**
     * **경로 자체가 없다.** 서버가 아직 다중 초대를 배포하지 않은 것이다.
     *
     * 이때 `links` 를 실어 수락하면 구 서버가 조용히 무시하므로, 화면은 다중 초대를
     * 그리지 않고 한 마리짜리 옛 흐름만 남긴다.
     */
    data object Unsupported : PreviewOutcome

    data class Failed(val message: String) : PreviewOutcome
}

private fun String.toEpochMs(): Long = with(AuthApi) { this@toEpochMs.toEpochMs() }
