package com.daengs.app.pet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/** 초대 미리보기·수락에 쓸 로그인을 못 받은 이유. 화면이 할 일을 다르게 권한다. */
enum class InviteAuthProblem {
    /** 서버에 못 닿았다. 세션은 그대로 두고 다시 시도한다. */
    Unreachable,

    /** 로그인이 만료됐거나 서버가 거절했다. 다시 로그인해야 한다. */
    LoginRequired,
}

/**
 * 초대받기의 **진입 상태** — 초대받기 화면이 떠 있는지와 그 화면의 붙여넣기·미리보기·선택.
 *
 * **왜 ViewModel 인가.** 초대받기 화면 상태를 `remember` 로 두면 미리보기를 보는 사이
 * 액티비티가 다시 만들어질 때(다크 모드·글꼴 크기·언어 변경 — 회전은 `configChanges` 로
 * 막아 두었다) **화면·선택이 통째로 사라진다.** ViewModel 은 재생성을 넘어 메모리에서 이어진다.
 *
 * **아직 화면에 안 넘긴 링크 토큰은 여기가 아니라 [InviteInbox] 에 있다.** 다른 액티비티
 * (카카오 로그인)가 위에 있을 때 링크로 MainActivity 가 하나 더 생겨도 토큰은 프로세스에
 * 하나라서, 원래 인스턴스가 로그인을 마친 뒤 그 토큰을 연다.
 *
 * ⚠️ **메모리뿐이다.** `SavedStateHandle` 도 디스크도 쓰지 않는다 — 토큰은 남의 강아지
 * 초대를 가로챌 수 있는 자격증명이다. **프로세스가 죽으면 사라지고 사용자가 링크를 다시
 * 누르는 것이 맞다.** 그것이 지원 범위다.
 *
 * 로그아웃하면 [signOut] 으로 전부 버린다 — 이전 계정이 받은 초대·미리보기·선택이
 * 다음 계정 화면으로 이어지면 안 된다. 예외는 [holdForLogin] 으로 **토큰만** 들고
 * 다시 로그인하러 가는 경우다.
 */
class InviteEntryViewModel(
    /** 붙여넣기·미리보기·선택·수락. 화면 상태와 같은 생애를 산다. */
    val holder: InviteAcceptHolder = InviteAcceptHolder(),
    /** 아직 안 넘긴 링크 토큰. 앱에서는 프로세스에 하나다. */
    private val inbox: InviteInbox = InviteInbox.process,
) : ViewModel() {

    /**
     * 링크로 받았는데 아직 화면에 넘기지 못한 토큰. 로그인·세션 복원을 마치고 홈에
     * 닿으면 [openFromLink] 가 가져가고 비운다.
     */
    val pendingToken: String? get() = inbox.pendingToken

    /** 초대받기 화면이 떠 있나. */
    var accepting: Boolean by mutableStateOf(false)
        private set

    /**
     * 링크를 눌러서 바로 들어왔나. 참이면 화면이 붙여넣기 칸을 숨긴다. 수동 경로
     * (메뉴의 「받은 초대 링크 넣기」)는 늘 거짓이다.
     */
    var autoEntered: Boolean by mutableStateOf(false)
        private set

    /** 미리보기·수락에 쓸 세션을 못 받은 이유. null 이면 문제가 없다. */
    var authProblem: InviteAuthProblem? by mutableStateOf(null)
        private set

    /**
     * 링크가 왔다. **나중 것이 이긴다** — 로그인을 기다리는 사이 다른 초대를 누르면
     * 그것이 열려야 한다. 같은 링크가 두 번 오면 값이 같아 아무것도 안 바뀐다.
     */
    fun receive(token: String) {
        inbox.receive(token)
    }

    /** 메뉴의 「받은 초대 링크 넣기」. 붙여넣기 칸이 보이는 예전 화면 그대로다. */
    fun openManually() {
        accepting = true
        autoEntered = false
    }

    /**
     * 받아 둔 토큰으로 화면을 연다. 같은 토큰이 다시 오면 [InviteAcceptHolder.acceptFromLink]
     * 가 미리보기·선택을 지우지 않고, 다른 토큰이면 앞 시도를 지운다. **수락은 안 부른다.**
     *
     * @return 넘긴 토큰이 있었나. 없으면 아무것도 안 했다
     */
    fun openFromLink(): Boolean {
        val token = inbox.take() ?: return false
        holder.acceptFromLink(token)
        accepting = true
        autoEntered = true
        authProblem = null
        return true
    }

    /** 세션을 받으려 해 본 결과. 받았으면 null 을 넘겨 안내를 지운다. */
    fun reportAuth(problem: InviteAuthProblem?) {
        authProblem = problem
    }

    /**
     * 로그인이 만료돼 다시 로그인하러 간다. **토큰만 들고 간다** — 미리보기·선택은 만료된
     * 계정의 후보로 만든 것이라 버리고, 로그인해서 홈에 닿으면 링크 진입과 같은 길로
     * [openFromLink] 가 다시 열어 미리보기를 새로 받는다. 수락은 여전히 버튼으로만 한다.
     */
    fun holdForLogin() {
        val token = (holder.parsed as? InvitePaste.Result.Found)?.token
        close()
        if (token != null) inbox.holdForLogin(token)
    }

    /** 화면을 닫거나 수락을 끝냈을 때. 붙여넣은 글과 토큰을 같이 버린다. */
    fun close() {
        holder.forget()
        accepting = false
        autoEntered = false
        authProblem = null
    }

    /**
     * 로그아웃·탈퇴. 아직 못 넘긴 토큰까지 버린다 — 다음 사람 것이 아니다.
     * [holdForLogin] 바로 뒤라면 그 토큰만 남긴다(미리보기·선택은 이미 버렸다).
     */
    fun signOut() {
        close()
        inbox.signOut()
    }
}
