package com.daengs.app.walk.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WalkDao {
    /**
     * IGNORE: 같은 세션 시작을 재전송해도 최초 시작 시각을 덮어쓰지 않는다.
     *
     * @return 새로 넣었으면 rowId, 이미 있어서 넘겼으면 -1. **아이를 붙일지 말지가
     *   이 값에 달렸다** — 이미 있는 세션에 나중 목록을 덧붙이면 처음에 데리고 나간
     *   아이가 아닌 아이가 그 산책에 섞인다.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(row: WalkSessionRow): Long

    /** IGNORE: 같은 아이를 두 번 붙여도 한 줄이다. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSessionDog(row: WalkSessionDogRow)

    @Query("SELECT * FROM walk_session_dog WHERE sessionId = :sessionId ORDER BY dogId")
    suspend fun sessionDogs(sessionId: String): List<WalkSessionDogRow>

    /**
     * 여러 산책의 아이들을 **한 번에** 읽는다.
     *
     * 목록을 그릴 때 산책마다 한 번씩 물으면 스무 건이면 스무 번 왕복한다.
     */
    @Query("SELECT * FROM walk_session_dog WHERE sessionId IN (:sessionIds) ORDER BY dogId")
    suspend fun sessionDogs(sessionIds: List<String>): List<WalkSessionDogRow>

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

    /**
     * **그 아이와만** 나간 산책을 지운다. 강아지를 지울 때 부른다.
     *
     * 다른 아이와 같이 나간 산책은 안 지운다 — 그건 남은 아이의 기록이기도 해서,
     * 지우면 그 아이의 운동량이 통째로 빈다. 아무도 안 붙은 산책은 연결 줄이 없어
     * 여기 안 걸린다.
     *
     * 좌표는 외래키가 지운다.
     */
    @Query(
        "DELETE FROM walk_session WHERE id IN (" +
            "SELECT sessionId FROM walk_session_dog WHERE dogId = :dogId " +
            "AND sessionId NOT IN (" +
            "SELECT sessionId FROM walk_session_dog WHERE dogId <> :dogId))",
    )
    suspend fun deleteSessionsOnlyWith(dogId: String)

    /** 남은 산책에서 그 아이만 뗀다. 산책 자체는 남는다. */
    @Query("DELETE FROM walk_session_dog WHERE dogId = :dogId")
    suspend fun unlinkDog(dogId: String)

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
