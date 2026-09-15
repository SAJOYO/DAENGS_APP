package com.daengs.app.pet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 링크로 받았는데 아직 화면에 넘기지 못한 초대 토큰 — **프로세스에 하나.**
 *
 * **왜 액티비티(ViewModel)가 아니라 여기인가.** 카카오 로그인 화면(Custom Tab·
 * `AuthCodeHandlerActivity`)처럼 다른 액티비티가 맨 위에 있을 때 링크가 오면 `singleTop`
 * 이 안 먹어서 MainActivity 가 하나 더 생긴다. 토큰을 인스턴스마다 들고 있으면 새로 생긴
 * 쪽(로그인 전)과 로그인 콜백을 받는 원래 쪽이 서로 다른 상태를 갖는다. 그래서 새로 생긴
 * 쪽은 토큰만 여기 두고 곧바로 닫히고([shouldHandOffInvite]), 원래 화면이 다시 앞에 와서
 * 로그인이 끝나면([inviteOpenStep]) 여기서 꺼내 연다. 로그인 화면은 닫지 않는다.
 *
 * ⚠️ **메모리뿐이다.** 디스크·SavedState 에 적지 않는다 — 토큰은 자격증명이다. 프로세스가
 * 죽으면 사라지고 사용자가 링크를 다시 누른다.
 */
class InviteInbox {

    /** 아직 화면에 안 넘긴 토큰. **나중 링크가 이긴다.** */
    var pendingToken: String? by mutableStateOf(null)
        private set

    /** 곧 이어질 [signOut] 이 토큰까지 버리지 않게 하는 표시. [holdForLogin] 만 켠다. */
    private var holdThroughSignOut = false

    /** 살아 있는(onCreate 를 마치고 아직 onDestroy 안 된) MainActivity 수. 메인 스레드에서만 바뀐다. */
    private var liveEntries = 0

    val hasLiveEntry: Boolean get() = liveEntries > 0

    fun receive(token: String) {
        pendingToken = token
    }

    /** 화면이 가져간다. 가져가면 비운다. */
    fun take(): String? = pendingToken.also { pendingToken = null }

    /** 사용자가 「다시 로그인」을 눌렀다. 토큰만 남기고, 이어지는 로그아웃 정리 한 번을 넘긴다. */
    fun holdForLogin(token: String) {
        pendingToken = token
        holdThroughSignOut = true
    }

    /** 로그아웃·탈퇴·계정 전환. [holdForLogin] 바로 뒤가 아니면 토큰까지 버린다. */
    fun signOut() {
        if (!holdThroughSignOut) pendingToken = null
        holdThroughSignOut = false
    }

    fun register() {
        liveEntries += 1
    }

    fun unregister() {
        if (liveEntries > 0) liveEntries -= 1
    }

    companion object {
        /** 앱이 쓰는 하나. 테스트는 새로 만들어 넘긴다. */
        val process = InviteInbox()
    }
}

/**
 * 링크로 **새로** 뜬 MainActivity 가 화면을 그리지 말고 토큰만 넘긴 채 닫혀야 하나.
 *
 * 이미 살아 있는 MainActivity 가 있는데 또 생겼다는 것은 그 위에 다른 액티비티(카카오
 * 로그인 등)가 있어 `singleTop` 이 안 먹었다는 뜻이다. 닫히면 위에 있던 화면이 그대로
 * 보이고, 로그인 진행·콜백은 원래 인스턴스에 남는다. 복원(재생성·프로세스 복원)은 링크를
 * 새로 받은 것이 아니므로 넘기지 않는다.
 */
fun shouldHandOffInvite(restoring: Boolean, hasInviteToken: Boolean, otherEntryAlive: Boolean): Boolean =
    !restoring && hasInviteToken && otherEntryAlive

/** 대기 초대를 지금 어떻게 할지. */
enum class InviteOpenStep {
    /** 보관만 한다 — 로그인 전·로그인 진행/취소/실패 뒤·세션 복원 중·온보딩·산책 중. */
    Wait,

    /** 홈에 있으니 바로 연다. */
    Open,

    /** 다른 화면(챗·도감 등)이면 홈으로 돌아와 연다. */
    GoHomeThenOpen,
}

/**
 * 대기 초대를 열 차례인가. **여는 것은 미리보기까지다** — 수락은 화면의 버튼으로만 한다.
 *
 * @param loggedIn 세션이 있나. 로그인 화면이 떠 있거나 로그인을 취소·실패했으면 거짓이라
 *   토큰은 그대로 남고, 다시 로그인에 성공해 홈에 닿으면 연다
 * @param screenBlocksInvite 로딩·랜딩·닉네임·등록·산책처럼 끊으면 안 되는 화면인가
 */
fun inviteOpenStep(
    hasPending: Boolean,
    sessionRestoring: Boolean,
    loggedIn: Boolean,
    screenBlocksInvite: Boolean,
    onHome: Boolean,
): InviteOpenStep = when {
    !hasPending || sessionRestoring || !loggedIn || screenBlocksInvite -> InviteOpenStep.Wait
    onHome -> InviteOpenStep.Open
    else -> InviteOpenStep.GoHomeThenOpen
}
