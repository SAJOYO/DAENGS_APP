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
 *
 * 흐름은 셋이다: 붙여넣기 → **미리보기** → 아이마다 고르기 → 한 번에 수락.
 * 미리보기가 가운데 있는 이유는 [previewed] 를 보라.
 */
class InviteAcceptHolder(
    private val api: InviteAcceptApi = InviteAcceptApi(),
    private val bundleApi: PetInviteBundleApi = PetInviteBundleApi(),
) {

    /** 사용자가 붙여넣은 글 그대로. 화면이 그린다. */
    var pasted: String by mutableStateOf("")
        private set

    /** 붙여넣은 글에서 찾아낸 것. 토큰은 [InvitePaste.Result.Found] 안에만 있다. */
    var parsed: InvitePaste.Result by mutableStateOf(InvitePaste.Result.Empty)
        private set

    /** 미리보기 결과. null 이면 아직 안 눌렀다. */
    var preview: PreviewOutcome? by mutableStateOf(null)
        private set

    /**
     * 아이마다 고른 것. **키가 없으면 아직 안 고른 것이다** — [PetChoice.Join] 과 다르다.
     */
    var choices: Map<String, PetChoice> by mutableStateOf(emptyMap())
        private set

    var busy: Boolean by mutableStateOf(false)
        private set

    /** 수락이 끝났으면 그 결과. 성공도 실패도 여기 담긴다. */
    var outcome: AcceptOutcome? by mutableStateOf(null)
        private set

    /** 미리보기로 받은 초대 내용. 아직 안 받았으면 null 이다. */
    val invite: InvitePreview?
        get() = (preview as? PreviewOutcome.Ready)?.preview

    /**
     * 새 계약(연결 선택)을 써도 되나.
     *
     * ⚠️ **구 서버에 `links` 를 보내면 조용히 무시되고 200 이 온다.** 사용자가 고른 연결이
     * 사라진 채 전부 새로 참여해 버린다. 미리보기가 성공했다는 것이 저쪽에 그 경로가
     * 있다는 유일한 증거라, 그때만 선택을 싣는다.
     */
    val previewed: Boolean get() = preview is PreviewOutcome.Ready

    /** 아이마다 하나씩 다 골랐나. 미리보기를 못 받았으면 고를 것 자체가 없다. */
    val allChosen: Boolean
        get() = invite?.let { p -> p.pets.all { it.petId in choices } } ?: false

    /**
     * 지금 수락할 수 있나.
     *
     * 미리보기를 받았으면 **다 골라야** 한다 — 묶음에서 선택이 빠지면 서버가 409 로 막고,
     * 한 마리여도 화면에 고르는 자리를 냈으면 다 고르게 하는 편이 일관된다.
     * 경로가 없는 옛 서버([PreviewOutcome.Unsupported])에서는 링크만 있으면 된다.
     *
     * **미리보기를 아직 못 물어봤거나(로그인·망 문제) 실패·만료·없는 초대면 누를 수 없다.**
     * 예전에는 그때도 버튼이 살아 있어서, 세션을 못 받은 채 누르면 아무 일도 안 일어났다.
     */
    val canAccept: Boolean
        get() = !busy &&
            parsed is InvitePaste.Result.Found &&
            outcome !is AcceptOutcome.Joined &&
            when (preview) {
                is PreviewOutcome.Ready -> allChosen
                PreviewOutcome.Unsupported -> true
                else -> false
            }

    /** 이미 다른 항목이 가져간 기존 아이. 화면이 그 후보를 잠근다. */
    fun takenBy(petId: String): Set<String> =
        choices.filterKeys { it != petId }
            .values
            .filterIsInstance<PetChoice.Link>()
            .map { it.existingPetId }
            .toSet()

    fun paste(text: String) {
        pasted = text
        parsed = InvitePaste.parse(text)
        // 새로 붙여넣으면 앞 시도의 결과도 미리보기도 이 입력의 것이 아니다.
        outcome = null
        preview = null
        choices = emptyMap()
    }

    /**
     * App Links 로 이미 검증된 토큰을 그대로 심는다. **사용자가 붙여넣지 않아도** 미리보기로
     * 이어진다 — 링크를 연 것 자체가 그 토큰을 골랐다는 뜻이기 때문이다.
     *
     * **같은 토큰이 다시 오면 아무것도 안 한다.** 같은 링크가 두 번 전달돼도(연타·재실행)
     * 이미 보고 있는 미리보기·고른 선택을 지우지 않는다. **다른 토큰**이면 [paste] 와 같이
     * 앞 시도를 지운다 — 새 초대가 섞이면 안 된다.
     */
    fun acceptFromLink(token: String) {
        if ((parsed as? InvitePaste.Result.Found)?.token == token) return
        paste("https://${InviteLink.HOST}${InviteLink.PATH}#$token")
    }

    /**
     * 무엇이 든 초대인지 먼저 본다.
     *
     * **미리보기를 성공한 뒤에만 연결 선택을 보낸다** ([previewed]). 실패하면 화면이
     * 이유를 말하고, 경로가 없는 옛 서버면 선택 없이 옛 흐름으로 간다.
     */
    suspend fun loadPreview(accessToken: String): PreviewOutcome? {
        val token = (parsed as? InvitePaste.Result.Found)?.token ?: return null
        if (busy) return null
        busy = true
        outcome = null
        val result = bundleApi.preview(accessToken, token)
        busy = false
        preview = result
        // 이미 구성원인 아이는 고를 것이 없다 — 서버가 그냥 지나가므로 미리 채워 둔다.
        choices = (result as? PreviewOutcome.Ready)?.preview
            ?.pets
            ?.filter { it.alreadyMember }
            ?.associate { it.petId to PetChoice.Join }
            .orEmpty()
        return result
    }

    /**
     * 아이 하나의 선택을 정한다.
     *
     * **같은 기존 아이를 두 항목에 걸 수 없다** — 서버가 422 `duplicate_link_target` 으로
     * 막고, 그때 사용자는 어느 줄을 고쳐야 하는지 모른다.
     *
     * @return 중복이라 못 골랐으면 false.
     */
    fun choose(petId: String, choice: PetChoice): Boolean {
        if (choice is PetChoice.Link && choice.existingPetId in takenBy(petId)) return false
        choices = choices + (petId to choice)
        return true
    }

    /**
     * 「연결 없이 참여」 — 연결이 막힌(`has_other_carers`) 선택을 새로 참여로 바꾸고 안내를 닫는다.
     *
     * 서버가 거절된 초대 강아지 id 를 줬으면 그 줄만, 못 가르면 연결로 고른 줄 전부를 바꾼다.
     * **수락을 다시 부르지 않는다** — 사용자가 바뀐 선택을 보고 최종 수락 버튼을 눌러야 한다.
     */
    fun joinInsteadOfBlockedLink() {
        val blocked = outcome as? AcceptOutcome.Conflict ?: return
        if (!blocked.linkBlockedByOtherCarers) return
        val targets = blocked.petId?.takeIf { it in choices }?.let { setOf(it) }
            ?: choices.filterValues { it is PetChoice.Link }.keys
        choices = choices + targets.associateWith { PetChoice.Join }
        outcome = null
    }

    /** 「확인」 — 연결 차단 안내만 닫는다. 선택은 그대로 둔다. */
    fun dismissBlockedLink() {
        if ((outcome as? AcceptOutcome.Conflict)?.linkBlockedByOtherCarers == true) outcome = null
    }

    /** 화면을 닫거나 로그아웃할 때. **입력과 토큰을 같이 버린다.** */
    fun forget() {
        pasted = ""
        parsed = InvitePaste.Result.Empty
        busy = false
        outcome = null
        preview = null
        choices = emptyMap()
    }

    /**
     * 수락한다. **누른 순간에만 부른다** — 링크를 붙여넣었다는 이유로 미리 보내지 않는다.
     *
     * **묶음 전체가 한 번에 간다.** 하나라도 실패하면 서버가 아무것도 남기지 않으므로,
     * 앱도 일부만 성공한 것처럼 그리지 않는다.
     *
     * @return 성공하면 참여한 아이들. 부르는 쪽이 이것으로 강아지 목록을 다시 받는다.
     */
    suspend fun accept(accessToken: String): AcceptedInvite? {
        val token = (parsed as? InvitePaste.Result.Found)?.token ?: return null
        if (busy) return null // 연타 방지. 같은 초대를 두 번 보내도 서버는 200 이지만 화면이 흔들린다.
        if (previewed && !allChosen) return null
        busy = true
        // 미리보기를 못 받았으면 선택을 싣지 않는다 — 옛 서버가 조용히 무시해 버린다.
        val result = api.accept(accessToken, token, if (previewed) choices.toLinks() else emptyList())
        busy = false
        outcome = result
        return when (result) {
            is AcceptOutcome.Joined -> {
                // 성공했으면 이 토큰은 더 쓸 일이 없다 — 화면에서도 지운다.
                pasted = ""
                parsed = InvitePaste.Result.Empty
                preview = null
                choices = emptyMap()
                result.pet
            }
            // 실패는 입력을 남겨 둔다. 망이 끊긴 것이면 그대로 다시 누르면 된다.
            else -> null
        }
    }
}

@Composable
fun rememberInviteAcceptHolder(): InviteAcceptHolder = remember { InviteAcceptHolder() }
