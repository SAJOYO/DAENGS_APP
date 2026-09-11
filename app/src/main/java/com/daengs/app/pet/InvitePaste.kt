package com.daengs.app.pet

/**
 * 붙여넣은 글에서 초대 토큰을 찾는다.
 *
 * **사용자는 링크만 복사하지 않는다.** 카톡에서 길게 눌러 복사하면 공유 문구가 통째로
 * 딸려 온다("🐶 네옹의 공동 돌봄 초대장이…"). 그래서 글 안에서 우리 링크를 찾아낸다.
 *
 * ⚠️ **클립보드를 앱이 몰래 읽지 않는다.** 사용자가 직접 붙여넣은 문자열만 여기로 온다 —
 * 안드로이드 12 부터는 클립보드를 읽으면 시스템이 알리기도 하고, 무엇보다 남의 복사물을
 * 우리가 뒤질 이유가 없다.
 */
object InvitePaste {

    /** 붙여넣기 결과. **토큰은 [Found] 안에만 있고 [toString] 에서 가려진다.** */
    sealed interface Result {
        data class Found(val token: String) : Result {
            override fun toString(): String = "Found(token=<가림>)"
        }

        /** 아직 아무것도 안 붙여넣었다. 오류로 그리지 않는다. */
        data object Empty : Result

        /** 우리 초대 링크를 못 찾았다. */
        data object NoLink : Result

        /**
         * 서로 다른 초대가 여럿이다. **어느 것을 쓸지 앱이 고르지 않는다** — 잘못 고르면
         * 엉뚱한 아이의 보호자가 되고, 쓴 초대는 되돌릴 수 없다.
         */
        data object Ambiguous : Result
    }

    /** 링크처럼 생긴 토막을 고른다. 공백·줄바꿈으로 자른 뒤 우리 것만 남긴다. */
    private val SEPARATORS = charArrayOf(' ', '\n', '\r', '\t', ' ')

    fun parse(pasted: String?): Result {
        val text = pasted?.trim().orEmpty()
        if (text.isEmpty()) return Result.Empty

        val tokens = text
            .split(*SEPARATORS)
            .mapNotNull { piece -> InviteLink.tokenOf(piece.trim().trim('<', '>', '"', '\'', '(', ')')) }
            .distinct()

        return when (tokens.size) {
            0 -> Result.NoLink
            1 -> Result.Found(tokens.first())
            // 같은 링크가 두 번 붙어 온 것은 위에서 distinct 로 걸렀다. 여기 오면 **다른** 초대다.
            else -> Result.Ambiguous
        }
    }
}
