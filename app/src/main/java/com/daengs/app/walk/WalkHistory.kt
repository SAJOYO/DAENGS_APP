package com.daengs.app.walk

import java.time.LocalDate
import java.time.ZoneId
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
    val changes get() = log.historyChanges

    /**
     * 끝난 산책, 최근 것부터.
     *
     * **미종료 세션은 안 준다.** 강제 종료로 열린 채 남은 세션이 목록에 끼면 "0m 짜리
     * 산책"이 쌓이는데, 그건 기록이 아니라 사고의 흔적이다 — 이어 기록할지 버릴지는
     * 아직 정하지 않은 별개 문제다.
     */
    suspend fun finished(): List<WalkSummary> = withContext(Dispatchers.IO) {
        log.finishedSessions()
            .map { session -> summarize(session, log.fixes(session.id)) }
            // **옛 기록도 지금 기준으로 다시 본다.** 이 규칙([countsAsWalk])이 생기기
            // 전에 쌓인 0m 짜리가 목록에 남아 있으면, "너무 짧아서 기록하지 않았어요"
            // 라고 말해 놓고 목록에는 0m 이 보이는 앞뒤 안 맞는 화면이 된다.
            //
            // 요약을 저장하지 않는 것과 같은 원칙이다 — 규칙이 바뀌면 지난 기록도
            // 같이 바뀌는 것이 맞다. **지우지는 않는다.** 원본은 그대로 있어서
            // 문턱값을 낮추면 다시 보인다.
            .filter { it.countsAsWalk || log.hasEntries(it.sessionId) }
    }

    /**
     * 오늘 걸은 것. 홈 카드가 쓴다.
     *
     * 하루의 경계는 **기기 시간대**다 (`ZoneId.systemDefault()`) — 걷는 사람의 하루가
     * 기준이지 UTC 의 하루가 아니다.
     */
    suspend fun todayTotals(today: LocalDate = LocalDate.now()): WalkDayTotals =
        withContext(Dispatchers.IO) {
            val zone = ZoneId.systemDefault()
            finished().totalsFor(
                dayStartMillis = today.atStartOfDay(zone).toInstant().toEpochMilli(),
                dayEndMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            )
        }

    /**
     * 산책으로 칠 만한지 보고, 아니면 **지운다.**
     *
     * 문을 눌렀다 닫은 것이 0m 짜리 기록으로 쌓이면 목록이 지저분해지고 오늘 요약의
     * 횟수가 거짓이 된다. 지우면 서버에도 안 올라간다 — 올리기는 "끝났고 안 올라간
     * 것" 을 훑는데 그 세션이 이미 없다.
     *
     * @return 산책으로 쳤으면 true. false 면 **부르는 쪽이 사용자에게 알려야 한다** —
     *   말없이 사라지면 기록이 유실된 것으로 읽힌다.
     */
    suspend fun keepIfWalk(sessionId: String): Boolean = withContext(Dispatchers.IO) {
        val summary = detail(sessionId) ?: return@withContext false
        if (summary.countsAsWalk || log.hasEntries(sessionId)) return@withContext true
        log.deleteSession(sessionId)
        false
    }

    /**
     * 지운 강아지를 기록에서도 지운다.
     *
     * **그 아이와만 나간 산책만 지운다.** 다른 아이와 같이 나간 산책은 남는다 —
     * 그건 남은 아이의 기록이기도 해서, 지우면 그 아이의 운동량이 통째로 빈다.
     *
     * 서버도 같은 규칙이라(강아지 삭제 API 가 그렇게 한다) 다음 동기화 때 지운 산책이
     * 되돌아오지 않는다.
     */
    suspend fun forgetDog(dogId: String) = withContext(Dispatchers.IO) {
        log.forgetDog(dogId)
    }

    /**
     * 이 기기의 산책을 **전부** 잊는다. **탈퇴할 때** 부른다.
     *
     * 서버는 탈퇴에서 개인정보를 파기하고 산책도 같이 지운다. 그런데 폰의 Room 에는
     * 원본 좌표가 남아 있었다 — **산책 경로는 집과 생활권을 그대로 드러내는 값**이라,
     * 그걸 두고 "계정을 지우면 데이터도 지운다" 고 하면 거짓말이 된다. 다음에 이
     * 폰으로 로그인한 사람이 남의 동선을 물려받기도 한다.
     *
     * 로그아웃에서는 부르지 않는다. 같은 사람이 다시 로그인할 자리라 지우면
     * 자기 기록을 잃는다.
     */
    suspend fun forgetEverything() = withContext(Dispatchers.IO) {
        log.forgetEverything()
    }

    suspend fun forgetOwner(ownerId: String) = withContext(Dispatchers.IO) {
        require(ownerId.isNotBlank())
        log.forgetOwner(ownerId)
    }

    suspend fun detail(sessionId: String): WalkSummary? = withContext(Dispatchers.IO) {
        val session = log.session(sessionId) ?: return@withContext null
        summarize(session, log.fixes(sessionId))
    }

    /** 완료 직후와 지난 기록에서 같은 저장 원본을 읽는다. 프로세스 메모리는 보지 않는다. */
    suspend fun sessionDetail(sessionId: String): WalkSessionDetail? = withContext(Dispatchers.IO) {
        val session = log.session(sessionId) ?: return@withContext null
        // 전체 경로는 사용자가 한 세션을 연 이 자리에서만 만든다. 목록과 오늘 합계까지
        // 모든 과거 좌표를 무제한으로 펼치면 기록이 쌓일수록 읽기 비용이 폭증한다.
        val summary = summarize(session, log.fixes(sessionId), maxRouteSamples = Int.MAX_VALUE)
        WalkSessionDetail(
            summary = summary,
            route = summary.toSessionRoute(),
            moments = log.actions(sessionId).toMomentGroups(),
        )
    }
}
