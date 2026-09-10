package com.daengs.app.walk

/**
 * 기기가 산책 중 보고한 원본 위치의 영속 저장 계약.
 *
 * [TrailRecorder]는 화면에 깨끗한 선을 그리기 위해 흔들림과 저정확도 fix를 버리지만,
 * 그 문턱값은 이후 바뀔 수 있다. 그래서 원본은 화면용 필터를 통과하기 전에 이 계약으로
 * 저장하고, 표시용 동선과 복구 가능한 증거를 서로 다른 층으로 둔다.
 */
interface WalkFixLog {
    val ownerId: String? get() = null
    suspend fun saveRecordingEpoch(epoch: RecordingEpoch) { error("Recording journal unavailable") }
    suspend fun recordingEpochs(sessionId: String): List<RecordingEpoch> = emptyList()
    suspend fun observationsAfter(sessionId: String, afterSeq: Long, limit: Int): List<RecordedFix> =
        fixes(sessionId).filter { (it.ingressSeq ?: it.clientSeq.toLong()) > afterSeq }.take(limit)
    val historyChanges: kotlinx.coroutines.flow.Flow<Unit> get() = kotlinx.coroutines.flow.flowOf(Unit)
    suspend fun restoreSession(session: RecordedSession) = openSession(session)
    suspend fun hasEntries(sessionId: String): Boolean = actions(sessionId).isNotEmpty()
    /** Searchable visible text only, without GPS, photos or a generation request. */
    suspend fun historySearchText(sessionIds: List<String>): Map<String, List<String>> = emptyMap()

    /** 이미 알려진 ID를 다시 열어도 최초 시작 정보는 바꾸지 않는다. */
    suspend fun openSession(session: RecordedSession)

    suspend fun append(sessionId: String, fix: RecordedFix)

    /** 사용자가 버튼으로 남긴 행동. GPS 자동 판정이나 서버 attestation이 아니다. */
    suspend fun appendAction(action: RecordedWalkAction)

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

    /** Stable keyset order. Room overrides this to page before fetching any GPS. */
    suspend fun finishedSessionsPage(before: WalkHistoryCursor?, dogId: String?, limit: Int): List<RecordedSession> =
        finishedSessions().filter { (dogId == null || dogId in it.dogIds) &&
            (before == null || it.startedAtMillis < before.startedAtMillis ||
                (it.startedAtMillis == before.startedAtMillis && it.id < before.sessionId)) }
            .sortedWith(compareByDescending<RecordedSession> { it.startedAtMillis }.thenByDescending { it.id })
            .take(limit)

    /**
     * 끝났지만 아직 계산 완료되지 않은 것.
     *
     * [WalkSyncState.LOCAL_ONLY]는 원본부터 올리고, [WalkSyncState.RAW_UPLOADED]는
     * finalize만 다시 부른다. **미종료 세션은 안 준다.**
     */
    suspend fun sessionsPendingAnalysis(): List<RecordedSession>

    /** 모든 원본 좌표가 서버에 들어갔다. 이 뒤에는 finalize만 재시도한다. */
    suspend fun markRawUploaded(
        sessionId: String,
        serverWalkId: String,
        changedAtMillis: Long,
    )

    /** 서버가 계산 결과를 원자적으로 저장했다. */
    suspend fun markDerived(sessionId: String, changedAtMillis: Long)

    /**
     * 그 아이를 기록에서 지운다. **강아지를 지울 때 부른다.**
     *
     * 그 아이와만 나간 산책은 통째로 지우고, 다른 아이와 같이 나간 산책은 그 아이만
     * 뗀다 — 그 산책은 남은 아이의 기록이기도 하다. 서버도 같은 규칙이다.
     */
    suspend fun forgetDog(dogId: String)

    /**
     * 이 기기의 산책을 **전부** 잊는다. 탈퇴할 때 부른다.
     *
     * 산책 경로는 집과 생활권을 드러내는 값이라, 계정을 지웠는데 폰에 남아 있으면
     * 다음에 이 폰으로 로그인한 사람이 남의 동선을 물려받는다.
     */
    suspend fun forgetEverything()

    /** 탈퇴 요청 시작 때 고정한 계정만 삭제한다. 현재 로그인 상태를 다시 읽지 않는다. */
    suspend fun forgetOwner(ownerId: String) {
        error("계정별 산책 삭제를 지원하지 않는 저장소입니다.")
    }

    suspend fun session(sessionId: String): RecordedSession?

    suspend fun fixes(sessionId: String): List<RecordedFix>

    suspend fun actions(sessionId: String): List<RecordedWalkAction>

    suspend fun moments(sessionId: String): List<WalkMoment> = actions(sessionId).toMomentGroups()
}

data class RecordedSession(
    val id: String,
    val ownerId: String? = null,
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
    /** 원본 업로드와 계산 완료를 구분한다. 둘 사이에서 앱이 종료돼도 이어갈 수 있다. */
    val syncState: WalkSyncState = WalkSyncState.LOCAL_ONLY,
    /** 서버가 부여한 walk id. [WalkSyncState.RAW_UPLOADED]부터 finalize 재시도에 쓴다. */
    val serverWalkId: String? = null,
    /** 마지막 동기화 상태 전이 시각. [WalkSyncState.LOCAL_ONLY]이면 null이다. */
    val syncedAtMillis: Long? = null,
)

enum class WalkSyncState(val storedValue: String) {
    LOCAL_ONLY("local_only"),
    RAW_UPLOADED("raw_uploaded"),
    DERIVED("derived"),
    ;

    companion object {
        fun fromStored(value: String): WalkSyncState =
            entries.firstOrNull { it.storedValue == value }
                ?: error("모르는 산책 동기화 상태입니다: $value")
    }
}

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
    val ingressSeq: Long? = null,
    val sourceEpoch: String? = null,
    val clockEpochId: String? = null,
    val elapsedRealtimeNanos: Long? = null,
    val receivedElapsedNanos: Long? = null,
    val receivedAtMillis: Long? = null,
    val speedMps: Float? = null,
    val speedAccuracyMps: Float? = null,
    val bearingDegrees: Float? = null,
    val bearingAccuracyDegrees: Float? = null,
    val provider: String? = null,
    val recordingEligible: Boolean? = null,
)

