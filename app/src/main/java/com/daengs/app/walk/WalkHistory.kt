package com.daengs.app.walk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 지난 산책을 읽는 자리.
 *
 * 쓰는 쪽([WalkFixWriter])과 갈라 둔다 — 쓰기는 산책 중에 한 줄씩 밀어 넣는 큐이고,
 * 읽기는 화면이 열릴 때 한 번에 가져오는 일이라 성질이 다르다.
 *
 * **서버에 안 보낸다.** 산책은 기기에만 있다 (HISTORY 21절). 나중에 올리게 되면
 * 여기에 원본이 그대로 있으므로 이 클래스 안만 바뀐다.
 */
class WalkHistory(private val log: WalkFixLog) {

    /**
     * 끝난 산책, 최근 것부터.
     *
     * **미종료 세션은 안 준다.** 강제 종료로 열린 채 남은 세션이 목록에 끼면 "0m 짜리
     * 산책"이 쌓이는데, 그건 기록이 아니라 사고의 흔적이다 — 이어 기록할지 버릴지는
     * 아직 정하지 않은 별개 문제다.
     */
    suspend fun finished(): List<WalkSummary> = withContext(Dispatchers.IO) {
        log.finishedSessions().map { session -> summarize(session, log.fixes(session.id)) }
    }

    suspend fun detail(sessionId: String): WalkSummary? = withContext(Dispatchers.IO) {
        val session = log.session(sessionId) ?: return@withContext null
        summarize(session, log.fixes(sessionId))
    }
}
