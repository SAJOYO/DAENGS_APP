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

    /** 세션과 그 세션이 소유한 모든 fix를 함께 지운다. */
    suspend fun deleteSession(sessionId: String)

    /** 프로세스 종료나 강제 종료로 명시적인 close를 받지 못한 세션. */
    suspend fun unfinishedSessions(): List<RecordedSession>

    suspend fun session(sessionId: String): RecordedSession?

    suspend fun fixes(sessionId: String): List<RecordedFix>
}

data class RecordedSession(
    val id: String,
    /** 실제 반려견 선택이 연결되기 전에는 null이다. */
    val dogId: String?,
    val startedAtMillis: Long,
    val endedAtMillis: Long? = null,
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
