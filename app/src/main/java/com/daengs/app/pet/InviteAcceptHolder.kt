package com.daengs.app.pet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 초대받기 화면의 상태.
 *
 * ⚠️ **토큰은 여기 메모리에만 있다.** `rememberSaveable` 도, 디스크도 쓰지 않는다 —
 * 프로세스가 죽으면 사라지고 사용자가 링크를 다시 붙여넣는 것이 맞다. 자격증명을
 * 되살리려고 어딘가에 적어 두는 순간 그 자리가 새는 곳이 된다.
 */
class InviteAcceptHolder(private val api: InviteAcceptApi = InviteAcceptApi()) {

    /** 사용자가 붙여넣은 글 그대로. 화면이 그린다. */
    var pasted: String by mutableStateOf("")
        private set

    /** 붙여넣은 글에서 찾아낸 것. 토큰은 [InvitePaste.Result.Found] 안에만 있다. */
    var parsed: InvitePaste.Result by mutableStateOf(InvitePaste.Result.Empty)
        private set

    var busy: Boolean by mutableStateOf(false)
        private set

    /** 수락이 끝났으면 그 결과. 성공도 실패도 여기 담긴다. */
    var outcome: AcceptOutcome? by mutableStateOf(null)
        private set

    /** 지금 수락할 수 있나. 유효한 링크가 있고 처리 중이 아니어야 한다. */
    val canAccept: Boolean
        get() = !busy && parsed is InvitePaste.Result.Found && outcome !is AcceptOutcome.Joined

    fun paste(text: String) {
        pasted = text
        parsed = InvitePaste.parse(text)
        // 새로 붙여넣으면 앞 시도의 결과는 더 이상 이 입력의 것이 아니다.
        outcome = null
    }

    /** 화면을 닫거나 로그아웃할 때. **입력과 토큰을 같이 버린다.** */
    fun forget() {
        pasted = ""
        parsed = InvitePaste.Result.Empty
        busy = false
        outcome = null
    }

    /**
     * 수락한다. **누른 순간에만 부른다** — 링크를 붙여넣었다는 이유로 미리 보내지 않는다.
     *
     * @return 성공하면 참여한 아이. 부르는 쪽이 이것으로 강아지 목록을 다시 받는다.
     */
    suspend fun accept(accessToken: String): AcceptedInvite? {
        val token = (parsed as? InvitePaste.Result.Found)?.token ?: return null
        if (busy) return null // 연타 방지. 같은 초대를 두 번 보내도 서버는 200 이지만 화면이 흔들린다.
        busy = true
        val result = api.accept(accessToken, token)
        busy = false
        outcome = result
        return when (result) {
            is AcceptOutcome.Joined -> {
                // 성공했으면 이 토큰은 더 쓸 일이 없다 — 화면에서도 지운다.
                pasted = ""
                parsed = InvitePaste.Result.Empty
                result.pet
            }
            // 실패는 입력을 남겨 둔다. 망이 끊긴 것이면 그대로 다시 누르면 된다.
            else -> null
        }
    }
}

@Composable
fun rememberInviteAcceptHolder(): InviteAcceptHolder = remember { InviteAcceptHolder() }
