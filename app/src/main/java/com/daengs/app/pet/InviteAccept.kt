package com.daengs.app.pet

import org.json.JSONObject

/**
 * 수락 결과 한 줄. 저쪽 `AcceptedPetOut` 이다.
 *
 * ⚠️ **[invitedPetId] 와 [displayPetId] 를 갈라 두는 것이 핵심이다.** 연결했으면 이후
 * 케어·산책 요청에 쓸 id 는 초대에 담겼던 아이가 아니라 **내 기존 아이**다. 하나로 뭉치면
 * 받는 사람이 초대한 사람의 행에 기록을 쓴다.
 */
data class AcceptedPet(
    /** 초대 묶음에 담겨 있던 원본 행. */
    val invitedPetId: String,
    /** 내 화면과 **이후 요청**에 쓸 행. 연결 안 했으면 위와 같다. */
    val displayPetId: String,
    val name: String,
    val result: AcceptResult,
) {
    companion object {
        fun parse(json: JSONObject): AcceptedPet = AcceptedPet(
            invitedPetId = json.getString("invited_pet_id"),
            displayPetId = json.getString("display_pet_id"),
            name = json.optString("name"),
            result = AcceptResult.of(json.optString("result")),
        )
    }
}

/**
 * 항목 하나가 어떻게 끝났나.
 *
 * **[Unknown] 을 두는 이유는 서버가 값을 늘릴 수 있어서다** — 모르는 값에 앱이 죽거나
 * 조용히 다른 결과로 뭉개지 않게, 모른다고 들고 있다가 화면에서 무난한 문장을 쓴다.
 */
enum class AcceptResult {
    /** 내 기존 아이와 이어졌다. */
    LINKED,

    /** 새 공동 보호자로 참여했다. */
    JOINED,

    /** 이미 구성원이었다. 오류가 아니다. */
    ALREADY_MEMBER,

    /** 내가 이미 그 아이의 대표였다. 오류가 아니다. */
    ALREADY_OWNER,

    UNKNOWN,
    ;

    companion object {
        fun of(raw: String?): AcceptResult = when (raw) {
            "linked" -> LINKED
            "joined" -> JOINED
            "already_member" -> ALREADY_MEMBER
            "already_owner" -> ALREADY_OWNER
            else -> UNKNOWN
        }
    }
}

/**
 * 수락 성공 응답. 저쪽 `InviteAcceptResponse` 다.
 *
 * **[petId]·[name] 은 구 앱 호환 앵커다.** 옛 계약이 그 두 키를 읽으므로 서버가 지우지
 * 않았을 뿐, **새 앱은 [pets] 의 항목별 `displayPetId` 를 쓴다.** 앵커만 보면 여러 마리를
 * 받았을 때 나머지가 사라진다.
 *
 * 수락 뒤에는 목록을 **서버에서 다시 받는다.** 서버가 수락과 함께 조용히 하는 일이
 * 있어서다 — 등록한 강아지가 없던 사람은 그 아이가 **대표 강아지**로 세워진다.
 */
data class AcceptedInvite(
    val petId: String,
    val name: String,
    /** 항목별 결과. 옛 서버는 안 주므로 그때는 앵커 한 줄로 채운다. */
    val pets: List<AcceptedPet> = emptyList(),
) {
    /** 화면이 쓸 줄. 항목이 없으면(옛 서버) 앵커를 한 줄로 세운다. */
    val rows: List<AcceptedPet>
        get() = pets.ifEmpty {
            listOf(AcceptedPet(petId, petId, name, AcceptResult.JOINED))
        }

    companion object {
        fun parse(json: JSONObject): AcceptedInvite {
            val arr = json.optJSONArray("pets")
            return AcceptedInvite(
                petId = json.getString("pet_id"),
                name = json.optString("name"),
                pets = (0 until (arr?.length() ?: 0)).map { AcceptedPet.parse(arr!!.getJSONObject(it)) },
            )
        }
    }
}

/**
 * 수락이 어떻게 끝났나. **상태 코드마다 사용자에게 할 말이 다르다.**
 *
 * 404 와 410 을 한 문장으로 묶으면 "링크가 잘못됐다" 와 "시간이 지났다" 를 구별해 줄 수
 * 없다 — 뒤쪽은 새 초대를 받으면 되는데 앞쪽은 아니다.
 */
sealed interface AcceptOutcome {
    /** 성공. 같은 사람의 재시도와 이미 구성원인 경우도 서버가 200 으로 준다. */
    data class Joined(val pet: AcceptedInvite) : AcceptOutcome

    /** 404 — 없는 토큰이거나 **다른 사람이 이미 쓴** 링크. 서버는 둘을 구별해 주지 않는다. */
    data object NotFound : AcceptOutcome

    /** 410 — 만료됐거나 대표가 바뀌어 죽은 링크. */
    data object Expired : AcceptOutcome

    /**
     * 409 — 이미 대표 · 보호자 상한 · 마릿수 상한 · 부적격 연결 · 선택 누락.
     *
     * **앱이 문장을 다시 짓지 않는다.** 서버가 사용자에게 보여 줄 문장으로 `detail` 을
     * 써 놨으므로 [failure] 의 `message` 를 그대로 띄운다. 다만 **`code` 로는 분기한다** —
     * 부적격 연결은 사용자가 다른 아이를 고르면 되고, 상한은 그렇지 않다.
     */
    data class Conflict(
        val message: String,
        /** `detail.code`. 문자열 `detail` 이면 null 이라 화면은 [message] 만 쓴다. */
        val code: String? = null,
        /** `link_selection_required` 일 때만 채워진다. */
        val missingPetIds: List<String> = emptyList(),
        /** `link_not_allowed` 일 때 서버가 준 사유. */
        val reason: String? = null,
    ) : AcceptOutcome

    /**
     * 422 — 앱이 잘못 보냈다. 초대에 없는 id 이거나 같은 대상을 두 번 골랐다.
     *
     * **사용자 잘못이 아니다.** 화면이 중복 선택을 막고 있으므로 여기까지 오면 앱 결함이거나
     * 미리보기 이후 상태가 바뀐 것이다 — 다시 불러오라고 안내한다.
     */
    data class Invalid(val message: String, val code: String? = null) : AcceptOutcome

    /** 망이 끊겼거나 서버가 500 을 준 경우. 다시 시도할 수 있다. */
    data class Failed(val message: String) : AcceptOutcome
}
