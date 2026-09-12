package com.daengs.app.pet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import androidx.core.content.getSystemService

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

    /** 공유창을 띄운다. 받을 앱이 하나도 없으면 false — 화면이 대신 복사를 권한다. */
    fun share(context: Context, message: String): Boolean = runCatching {
        context.startActivity(Intent.createChooser(intent(message), null))
        true
    }.getOrDefault(false)

    /**
     * 링크를 클립보드에 담는다. **사용자가 「링크 복사」를 누를 때만 부른다** —
     * 화면에 떴다는 이유로 미리 담아 두면 토큰이 다른 앱에 새어 나간다.
     */
    fun copy(context: Context, inviteLink: String): Boolean = runCatching {
        val clipboard = context.getSystemService<ClipboardManager>() ?: return false
        clipboard.setPrimaryClip(sensitiveClip(inviteLink))
        true
    }.getOrDefault(false)

    /**
     * 민감한 클립으로 표시한다.
     *
     * **안드로이드 13(API 33)부터 복사하면 시스템이 미리보기를 띄운다.** 아무 표시도 안 하면
     * 그 팝업에 초대 링크가 **토큰째로** 뜬다 — 어깨너머로 읽히고 스크린샷에 남는다.
     * `EXTRA_IS_SENSITIVE` 를 켜면 시스템이 내용을 가리고 "복사됨" 만 보여 준다.
     *
     * 상수는 API 33 에 생겼지만 **키는 문자열이라 낮은 기기에서도 넣어 두면 그만이다** —
     * 모르는 키는 무시된다. 그래서 버전 분기 없이 항상 켠다.
     */
    internal fun sensitiveClip(inviteLink: String): ClipData =
        ClipData.newPlainText("공동 돌봄 초대 링크", inviteLink).apply {
            description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }

    /**
     * 복사한 뒤 앱이 따로 알려야 하나.
     *
     * **안드로이드 13부터는 시스템이 알려 준다.** 그 위에 토스트를 얹으면 같은 말이 두 번
     * 뜬다(구글의 클립보드 가이드가 명시적으로 하지 말라고 하는 것이다). 12L 이하만 앱이
     * 말한다.
     */
    fun needsCopiedNotice(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        sdkInt < Build.VERSION_CODES.TIRAMISU
}
