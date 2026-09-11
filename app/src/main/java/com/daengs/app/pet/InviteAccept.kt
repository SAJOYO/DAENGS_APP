package com.daengs.app.pet

import org.json.JSONObject

/**
 * 수락 성공 응답. 저쪽 `POST /app/pet-invites/accept` 가 주는 `{pet_id, name}` 이다.
 *
 * **이 둘로 목록을 재조회한다.** 서버가 수락과 함께 조용히 하는 일이 있어서다 — 등록한
 * 강아지가 없던 사람은 이 아이가 **대표 강아지**로 세워진다. 응답만 보고 앱이 상태를
 * 지어내면 그 규칙이 두 벌이 된다.
 */
data class AcceptedInvite(val petId: String, val name: String) {
    companion object {
        fun parse(json: JSONObject): AcceptedInvite = AcceptedInvite(
            petId = json.getString("pet_id"),
            name = json.optString("name"),
        )
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
     * 409 — 이미 대표 · 보호자 5명 초과 · 돌보는 아이가 너무 많음.
     *
     * **셋을 앱이 다시 가르지 않는다.** 서버가 사용자에게 보여 줄 문장으로 `detail` 을
     * 써 놨으므로 [message] 를 그대로 띄운다.
     */
    data class Conflict(val message: String) : AcceptOutcome

    /** 망이 끊겼거나 서버가 500 을 준 경우. 다시 시도할 수 있다. */
    data class Failed(val message: String) : AcceptOutcome
}
