package com.daengs.app.walk

/**
 * 기기가 산책 중 보고한 원본 위치의 영속 저장 계약.
 *
 * [TrailRecorder]는 화면에 깨끗한 선을 그리기 위해 흔들림과 저정확도 fix를 버리지만,
 * 그 문턱값은 이후 바뀔 수 있다. 그래서 원본은 화면용 필터를 통과하기 전에 이 계약으로
 * 저장하고, 표시용 동선과 복구 가능한 증거를 서로 다른 층으로 둔다.
 */
interface WalkFixLog {
    /** 이미 알려진 ID를 다시 열어도 최초 시작 정보는 바꾸지 않는다. */
    suspend fun openSession(session: RecordedSession)

    suspend fun append(sessionId: String, fix: RecordedFix)

    suspend fun closeSession(sessionId: String, endedAtMillis: Long)

    /**
     * 나갈 때의 날씨. 세션을 연 **뒤에** 따로 온다 — 날씨는 네트워크 왕복이라
     * 기다렸다가 세션을 열면 그동안의 좌표를 놓친다.
     */
    suspend fun stampWeather(sessionId: String, weather: RecordedWeather)

    /** 세션과 그 세션이 소유한 모든 fix를 함께 지운다. */
    suspend fun deleteSession(sessionId: String)

    /** 프로세스 종료나 강제 종료로 명시적인 close를 받지 못한 세션. */
    suspend fun unfinishedSessions(): List<RecordedSession>

    /** 끝난 산책만, 최근 것부터. 목록 화면이 쓴다. */
    suspend fun finishedSessions(): List<RecordedSession>

    /**
     * 끝났는데 아직 서버에 안 올라간 것.
     *
     * **미종료 세션은 안 준다.** 강제 종료로 열린 채 남은 세션은 기록이 아니라
     * 사고의 흔적이라 올릴 것이 아니다.
     */
    suspend fun unsyncedSessions(): List<RecordedSession>

    /** 올라갔다고 표시한다. 다시 올리지 않으려는 표시다. */
    suspend fun markSynced(sessionId: String, syncedAtMillis: Long)

    /**
     * 그 아이를 기록에서 지운다. **강아지를 지울 때 부른다.**
     *
     * 그 아이와만 나간 산책은 통째로 지우고, 다른 아이와 같이 나간 산책은 그 아이만
     * 뗀다 — 그 산책은 남은 아이의 기록이기도 하다. 서버도 같은 규칙이다.
     */
    suspend fun forgetDog(dogId: String)

    suspend fun session(sessionId: String): RecordedSession?

    suspend fun fixes(sessionId: String): List<RecordedFix>
}

data class RecordedSession(
    val id: String,
    /**
     * 데리고 나간 아이들. **여러 마리다.**
     *
     * 비어 있을 수 있다 — 로그인 전이거나 등록한 강아지가 없거나, 고르지 않고 나선
     * 경우다. **그래도 산책은 기록이다.** 사람이 걸은 것은 걸은 것이다.
     */
    val dogIds: List<String> = emptyList(),
    val startedAtMillis: Long,
    val endedAtMillis: Long? = null,
    /** 나갈 때의 날씨. 못 받았으면 null 이고 **"맑음"으로 채우지 않는다.** */
    val weather: RecordedWeather? = null,
    /**
     * 서버에 올라간 시각. null 이면 아직 이 기기에만 있다.
     *
     * **없앨 수 없는 상태다.** 산책은 밖에서 하고 그때 네트워크가 제일 불안하다 —
     * 지하철에 들어가면 업로드가 실패한다. 좌표를 기기에 먼저 쓰는 것은 선택이 아니라
     * 안전장치이고, 그 결과로 "아직 안 올라간" 창이 생긴다. 보통 몇 초다.
     */
    val syncedAtMillis: Long? = null,
)

/**
 * 산책을 시작할 때의 바깥.
 *
 * 접기 전의 WMO 코드를 남긴다 — "비"로만 저장하면 소나기였는지 뇌우였는지 못 되살린다.
 */
data class RecordedWeather(
    val weatherCode: Int,
    val isDay: Boolean,
    val temperatureC: Float?,
)

data class RecordedFix(
    /** 한 세션 안에서 클라이언트가 부여하는 0부터 시작하는 순서. */
    val clientSeq: Int,
    /** 명시적인 pause/resume 뒤 증가한다. 서로 다른 chain은 직선으로 연결하지 않는다. */
    val chainIndex: Int,
    val atMillis: Long,
    val lat: Double,
    val lng: Double,
    val accuracyM: Float?,
    val isMock: Boolean,
)
