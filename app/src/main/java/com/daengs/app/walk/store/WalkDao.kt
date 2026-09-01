package com.daengs.app.walk.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WalkDao {
    /** IGNORE: 같은 세션 시작을 재전송해도 최초 시작 시각을 덮어쓰지 않는다. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(row: WalkSessionRow)

    /** 같은 clientSeq를 다시 받으면 이미 저장한 원본을 유지한다. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFix(row: WalkFixRow)

    /** 열린 세션만 닫는다. 종료 요청을 반복해도 최초 종료 시각은 변하지 않는다. */
    @Query(
        "UPDATE walk_session SET endedAtMillis = :endedAtMillis " +
            "WHERE id = :sessionId AND endedAtMillis IS NULL",
    )
    suspend fun closeSession(sessionId: String, endedAtMillis: Long)

    @Query("DELETE FROM walk_session WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("SELECT * FROM walk_session WHERE id = :sessionId")
    suspend fun session(sessionId: String): WalkSessionRow?

    @Query("SELECT * FROM walk_session WHERE endedAtMillis IS NULL ORDER BY startedAtMillis")
    suspend fun unfinishedSessions(): List<WalkSessionRow>

    /**
     * 끝난 산책만, 최근 것부터.
     *
     * **미종료 세션을 섞지 않는다.** 강제 종료로 열린 채 남은 세션이 목록에 끼면
     * "0m 짜리 산책"이 쌓인다 — 그건 기록이 아니라 사고의 흔적이다.
     */
    @Query(
        "SELECT * FROM walk_session WHERE endedAtMillis IS NOT NULL " +
            "ORDER BY startedAtMillis DESC",
    )
    suspend fun finishedSessions(): List<WalkSessionRow>

    /**
     * 끝났는데 아직 안 올라간 것. **올릴 대상**이다.
     *
     * 오래된 것부터 준다 — 밀린 것이 여럿이면 걸었던 순서대로 올라가는 편이
     * 중간에 실패했을 때 어디까지 됐는지 읽기 쉽다.
     */
    @Query(
        "SELECT * FROM walk_session WHERE endedAtMillis IS NOT NULL " +
            "AND syncedAtMillis IS NULL ORDER BY startedAtMillis",
    )
    suspend fun unsyncedSessions(): List<WalkSessionRow>

    @Query("UPDATE walk_session SET syncedAtMillis = :syncedAtMillis WHERE id = :sessionId")
    suspend fun markSynced(sessionId: String, syncedAtMillis: Long)

    /** 날씨는 세션을 연 뒤 따로 온다. 열린 세션이든 끝난 세션이든 한 번만 쓴다. */
    @Query(
        "UPDATE walk_session SET weatherCode = :weatherCode, isDay = :isDay, " +
            "temperatureC = :temperatureC WHERE id = :sessionId AND weatherCode IS NULL",
    )
    suspend fun stampWeather(
        sessionId: String,
        weatherCode: Int,
        isDay: Boolean,
        temperatureC: Float?,
    )

    @Query("SELECT * FROM walk_fix WHERE sessionId = :sessionId ORDER BY clientSeq")
    suspend fun fixes(sessionId: String): List<WalkFixRow>
}
