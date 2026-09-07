package com.daengs.app.ui.nickname

/**
 * 닉네임의 **모양과 상태.** 화면을 모르는 순수한 셈이라 단위 테스트로 잡는다
 * (`pet/PetWeight.kt` 와 같은 방식).
 *
 * ## 이 값은 서버가 지어 준다
 *
 * 사용자에게 입력을 강제하지 않는다 — 카카오 로그인 한 번으로 시작하게 하는 것이
 * 앱의 목표라, 첫 화면이 "이미 사용 중입니다" 로 거절하면 그 약속이 깨진다.
 * 서버가 가입할 때 하나 발급하고([com.daengs.app.auth.AppMe.nickname]),
 * 바꾸고 싶은 사람만 바꾼다.
 */

/**
 * 서버 `app_users.nickname` 이 `VARCHAR(30)` 이다.
 *
 * 이름표(`MAX_ROOM_NAME` = 20)보다 긴 이유는 **카카오 닉네임을 그대로 받을 수 있게**
 * 해서다. 저건 방 그림 위에 걸리는 자리라 길면 방을 덮지만, 이건 글자로만 나온다.
 */
const val MAX_NICKNAME = 30

/**
 * 이 글자로 저장을 시도해도 되나. **통과면 `null`**, 아니면 사용자에게 보여 줄 한 줄.
 *
 * **서버 규칙을 그대로 옮긴다** (`schemas/app_auth.py` 의 `AppProfileUpdate`) —
 * 앞뒤 공백을 떼고, 비면 거절하고, 30자를 넘으면 거절한다. 여기서 더 좁히지 않는
 * 이유는, 서버가 발급한 이름(카카오 닉네임을 그대로 쓴 것)이 우리 규칙에 걸려서
 * **자기 이름을 자기가 다시 저장 못 하는** 일이 생기기 때문이다.
 *
 * 줄바꿈만 따로 막는다. 서버는 받아 주지만 한 줄로 그리는 자리라 화면이 깨진다.
 */
fun nicknameErrorOf(raw: String): String? {
    val trimmed = raw.trim()
    return when {
        trimmed.isEmpty() -> "이름을 넣어 주세요."
        trimmed.length > MAX_NICKNAME -> "${MAX_NICKNAME}자까지 쓸 수 있어요."
        raw.any { it == '\n' || it == '\r' } -> "한 줄로 써 주세요."
        else -> null
    }
}

/** [nicknameErrorOf] 가 통과시키나. 읽기 좋으라고 둔 짝이다. */
fun nicknameAccepts(raw: String): Boolean = nicknameErrorOf(raw) == null

/**
 * 입력 칸 아래에 뜨는 상태. **넷이 아니라 여섯이다.**
 *
 * 처음에는 "쓸 수 있음 / 이미 쓰는 중" 둘로 두려다 말았다. 그러면 **통신이 실패한
 * 것도 초록불이 된다** — 사용자는 되는 줄 알고 저장을 눌렀다가 거절당한다.
 * 모르는 것은 모른다고 해야 한다.
 */
sealed interface NicknameCheck {
    /** 아직 안 물어봤다. 안 건드렸거나, 지금 이름 그대로다. */
    data object Idle : NicknameCheck

    /** 모양이 틀렸다. **로컬 검사라 즉시** 뜬다 — 왕복할 이유가 없다. */
    data class Shape(val message: String) : NicknameCheck

    /** 물어보는 중. */
    data object Checking : NicknameCheck

    /** 비어 있다. ⚠️ **참고용이다** — 저장할 때 남이 채갔을 수 있다. */
    data object Free : NicknameCheck

    /** 남이 쓰고 있다. */
    data object Taken : NicknameCheck

    /** 못 물어봤다(통신 실패). **초록불을 켜면 안 되는 자리다.** */
    data object Unknown : NicknameCheck
}

/** 그 상태에서 보여 줄 한 줄. `null` 이면 아무 말도 안 한다. */
fun nicknameCheckMessage(check: NicknameCheck): String? = when (check) {
    NicknameCheck.Idle -> null
    is NicknameCheck.Shape -> check.message
    NicknameCheck.Checking -> "확인하고 있어요"
    NicknameCheck.Free -> "사용할 수 있어요"
    NicknameCheck.Taken -> "다른 분이 쓰고 있어요"
    NicknameCheck.Unknown -> "지금은 확인하지 못했어요"
}

/**
 * 저장 버튼을 누를 수 있나.
 *
 * **[NicknameCheck.Unknown] 에서도 누를 수 있다.** 못 물어봤다고 저장까지 막으면,
 * 확인 API 만 안 되는 날에 이름을 영영 못 바꾼다. 진짜 방어는 서버의
 * `lower(nickname)` UNIQUE 인덱스와 409 이고, 그건 저장할 때 걸린다.
 */
fun nicknameSavable(raw: String, check: NicknameCheck): Boolean =
    nicknameAccepts(raw) && check != NicknameCheck.Taken && check != NicknameCheck.Checking

/**
 * 지금 물어봐야 하나.
 *
 * **모양 검사를 통과해야 부른다** — 빈 칸과 30자 초과에 왕복할 이유가 없다.
 * **지금 쓰는 이름이면 안 부른다** — 자기 자신에 걸려서 "다른 분이 쓰고 있어요" 가
 * 뜬다. 서버도 같은 규칙이라 사실 `true` 를 주지만, 그 왕복 자체가 낭비다.
 */
fun shouldAskAvailability(raw: String, current: String?): Boolean =
    nicknameAccepts(raw) && !raw.trim().equals(current?.trim(), ignoreCase = true)
