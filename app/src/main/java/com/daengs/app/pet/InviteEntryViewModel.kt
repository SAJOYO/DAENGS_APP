package com.daengs.app.pet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/**
 * 초대받기의 **진입 상태** — 링크로 받은 토큰과, 초대받기 화면이 떠 있는지.
 *
 * **왜 ViewModel 인가.** 링크로 받은 토큰을 액티비티 필드에 두고 초대받기 화면 상태를
 * `remember` 로 두면, 로그인을 기다리거나 미리보기를 보는 사이에 액티비티가 다시
 * 만들어질 때(다크 모드·글꼴 크기·언어 변경 — 회전은 `configChanges` 로 막아 두었다)
 * **초대가 통째로 사라진다.** 인텐트에서는 이미 지웠으니 다시 읽을 곳도 없다.
 * ViewModel 은 같은 액티비티가 다시 만들어져도 **메모리에서** 그대로 이어진다.
 *
 * ⚠️ **메모리뿐이다.** `SavedStateHandle` 도 디스크도 쓰지 않는다 — 토큰은 남의 강아지
 * 초대를 가로챌 수 있는 자격증명이라, 되살리려고 어딘가에 적는 순간 그 자리가 새는
 * 곳이 된다. **프로세스가 죽으면 사라지고 사용자가 링크를 다시 누르는 것이 맞다.**
 * 그것이 지원 범위다.
 *
 * 로그아웃하면 [signOut] 으로 전부 버린다 — 이전 계정이 받은 초대·미리보기·선택이
 * 다음 계정 화면으로 이어지면 안 된다.
 */
class InviteEntryViewModel(
    /** 붙여넣기·미리보기·선택·수락. 화면 상태와 같은 생애를 산다. */
    val holder: InviteAcceptHolder = InviteAcceptHolder(),
) : ViewModel() {

    /**
     * 링크로 받았는데 아직 화면에 넘기지 못한 토큰. 로그인·세션 복원을 마치고 홈에
     * 닿으면 [openFromLink] 가 가져가고 비운다.
     */
    var pendingToken: String? by mutableStateOf(null)
        private set

    /** 초대받기 화면이 떠 있나. */
    var accepting: Boolean by mutableStateOf(false)
        private set

    /**
     * 링크를 눌러서 바로 들어왔나. 참이면 화면이 붙여넣기 칸을 숨긴다. 수동 경로
     * (메뉴의 「받은 초대 링크 넣기」)는 늘 거짓이다.
     */
    var autoEntered: Boolean by mutableStateOf(false)
        private set

    /**
     * 링크가 왔다. **나중 것이 이긴다** — 로그인을 기다리는 사이 다른 초대를 누르면
     * 그것이 열려야 한다. 같은 링크가 두 번 오면 값이 같아 아무것도 안 바뀐다.
     */
    fun receive(token: String) {
        pendingToken = token
    }

    /** 메뉴의 「받은 초대 링크 넣기」. 붙여넣기 칸이 보이는 예전 화면 그대로다. */
    fun openManually() {
        accepting = true
        autoEntered = false
    }

    /**
     * 받아 둔 토큰으로 화면을 연다. 같은 토큰이 다시 오면 [InviteAcceptHolder.acceptFromLink]
     * 가 미리보기·선택을 지우지 않고, 다른 토큰이면 앞 시도를 지운다.
     *
     * @return 넘긴 토큰이 있었나. 없으면 아무것도 안 했다
     */
    fun openFromLink(): Boolean {
        val token = pendingToken ?: return false
        holder.acceptFromLink(token)
        accepting = true
        autoEntered = true
        pendingToken = null
        return true
    }

    /** 화면을 닫거나 수락을 끝냈을 때. 붙여넣은 글과 토큰을 같이 버린다. */
    fun close() {
        holder.forget()
        accepting = false
        autoEntered = false
    }

    /** 로그아웃·탈퇴. 아직 못 넘긴 토큰까지 버린다 — 다음 사람 것이 아니다. */
    fun signOut() {
        close()
        pendingToken = null
    }
}
