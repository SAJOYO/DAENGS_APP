package com.daengs.app.pet

/**
 * 몸무게 칸이 받는 값.
 *
 * **막는 자리는 화면이다.** 서버도 422 로 막지만, 갔다 와서 알려 주면 사용자는 저장을
 * 눌렀다가 알 수 없는 글을 받는다 — 비공개 테스트에서 18자리를 넣었더니 저쪽 검증
 * 오류가 JSON 그대로 화면에 찍혔다 (`[{"type":"less_than_equal", …}]`).
 *
 * **비워 두는 것은 막지 않는다.** 이름 말고는 전부 비울 수 있는 것이 이 폼의 규칙이다 —
 * 필수로 하면 모르는 사람이 아무 값이나 넣어서 그 값이 데이터로 못 쓰인다.
 * 막는 것과 비우는 것은 다른 이야기다.
 */
object PetWeight {

    /**
     * 저쪽이 받는 최대 몸무게(kg).
     *
     * ⚠️ **저쪽 숫자다.** 422 본문의 `ctx.le` 가 이 값이었다. 저쪽이 바꾸면 여기도
     * 같이 바꿔야 한다 — 화면이 "괜찮다" 고 한 값을 서버가 되돌려보내면 사용자는
     * 무엇이 잘못됐는지 알 길이 없다. (`ui/chat/GuideFrame.kt` 의 `Band` 가 같은
     * 이유로 저쪽 숫자를 한 자리에 모아 두고 있다.)
     */
    const val MAX_KG = 200

    /**
     * 찍히는 것을 허용하는 모양. 정수 세 자리에 소수점 두 자리까지다.
     *
     * **저장할 때만 막지 않고 찍히는 것부터 막는다.** 18자리가 칸에 들어간 뒤에
     * "안 된다" 고 하면, 사용자는 지우고 다시 쓰는 일부터 해야 한다.
     * 정수 세 자리면 [MAX_KG] 를 덮고, 소수 두 자리면 어떤 강아지든 적을 수 있다.
     */
    private val SHAPE = Regex("""^\d{0,3}(\.\d{0,2})?$""")

    /** 이 글자를 칸에 받아도 되나. 지우는 중(빈 칸)도 받는다. */
    fun accepts(text: String): Boolean = text.isEmpty() || SHAPE.matches(text)

    /**
     * 저장을 막아야 하는 이유. 보낼 수 있으면 null 이다.
     *
     * 빈 칸은 이유가 아니다 — 몸무게는 모르면 비워 두는 칸이다.
     */
    fun errorOf(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val value = trimmed.toFloatOrNull() ?: return "숫자로 적어 주세요."
        if (value <= 0f) return "0보다 크게 적어 주세요."
        if (value > MAX_KG) return "${MAX_KG}kg 까지 넣을 수 있어요."
        return null
    }
}
