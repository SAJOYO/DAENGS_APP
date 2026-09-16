package com.daengs.app.pet

import android.content.Context
import android.content.Intent

/**
 * 초대장을 밖으로 내보내는 자리 — 공유 문구와 안드로이드 공유창.
 *
 * **카카오 SDK 를 쓰지 않는다.** `ACTION_SEND` + `text/plain` 으로 기본 공유창을 띄우고
 * 카카오톡이든 문자든 **사용자가 고른다.** 특정 앱을 버튼 이름에 박아 두면(「카카오톡으로
 * 보내기」) 그 앱이 없는 사람에게 거짓말이 되고, SDK 의존과 앱 키 관리가 따라붙는다.
 *
 * ⚠️ **여기 오는 링크에는 토큰이 들어 있다.** 로그로 찍거나 예외 메시지에 실으면 안 된다.
 */
object InviteShare {

    /** 링크가 없어 이름만 부를 때. 서버가 이름을 못 준 아이도 초대는 보낼 수 있어야 한다. */
    private const val UNNAMED = "우리 아이"

    /**
     * 공유 문구. **줄바꿈이 뜻을 나른다** — 안내와 링크 사이가 붙으면 받는 사람이
     * 링크를 문장의 일부로 읽는다. 링크는 **맨 끝에 한 번만** 둔다: 카톡 같은 앱이
     * 미리보기를 붙일 때 마지막 URL 을 집고, 두 번 적으면 붙여넣기가 "초대가 여러 개"로
     * 읽힐 여지를 준다.
     *
     * ⚠️ **누르라고 하지 않는다.** App Links 를 아직 얹지 않아 링크를 누르면 브라우저가
     * 빈 페이지를 연다. 받는 사람은 **앱에서 붙여넣어** 수락한다 — 문구가 그 길을
     * 그대로 안내해야 한다. App Links 가 붙는 날 이 문구를 다시 손본다.
     */
    fun message(petName: String?, inviteLink: String): String {
        val name = petName?.trim()?.takeIf { it.isNotEmpty() } ?: UNNAMED
        return buildString {
            append("🐶 ${name}의 공동 돌봄 초대장이 도착했어요!\n")
            append("\n")
            append("1. Daengs 앱을 설치하고 카카오로 로그인해 주세요.\n")
            append("2. 앱에서 「받은 초대 링크 넣기」를 선택해 주세요.\n")
            append("3. 이 메시지 전체나 아래 링크를 복사해 붙여넣어 주세요.\n")
            append("\n")
            append("※ 링크를 눌러도 열리지 않아요. 복사해서 붙여넣어야 합니다.\n")
            append("이 초대장은 24시간 동안 사용할 수 있습니다.\n")
            append("\n")
            append(inviteLink)
        }
    }

    /** 공유 인텐트. 받는 앱이 글자를 그대로 받도록 `text/plain` 이다. */
    fun intent(message: String): Intent =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, message)

    /**
     * 공유창을 띄운다. 받을 앱이 하나도 없으면 false.
     *
     * **보내는 쪽에는 클립보드 복사를 두지 않는다** — 한 초대장은 한 사람만 수락하는데,
     * 복사 버튼이 따로 있으면 여럿에게 돌리는 공용 링크로 읽혔다 (`InviteTicket`).
     */
    fun share(context: Context, message: String): Boolean = runCatching {
        context.startActivity(Intent.createChooser(intent(message), null))
        true
    }.getOrDefault(false)
}
