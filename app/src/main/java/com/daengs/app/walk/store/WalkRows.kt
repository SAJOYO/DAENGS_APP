package com.daengs.app.walk.store

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "walk_session")
data class WalkSessionRow(
    @PrimaryKey val id: String,
    val startedAtMillis: Long,
    /** null이면 아직 진행 중이거나 명시적인 종료 전에 프로세스가 끝난 세션이다. */
    val endedAtMillis: Long?,
    /**
     * 나갈 때의 날씨. 셋 다 nullable 이다 — 네트워크가 안 되면 못 받고,
     * **못 받은 것을 "맑음"으로 채우면 기록이 거짓말을 한다.**
     */
    val weatherCode: Int? = null,
    val isDay: Boolean? = null,
    val temperatureC: Float? = null,
    /** 서버에 올라간 시각. null 이면 아직 이 기기에만 있다. */
    val syncedAtMillis: Long? = null,
)

/**
 * 그 산책에 누가 나갔나.
 *
 * **한 번에 여러 마리를 데리고 나간다.** `walk_session.dogId` 한 칸이던 것을 표로
 * 옮긴 이유다 — 두 마리를 데리고 나갔는데 한 아이만 남으면, 나중에 챗봇이 "이 아이
 * 이번 주 운동량"을 말할 때 나머지 아이의 산책이 통째로 빈다.
 *
 * 두 칸이 함께 키라서 **같은 아이를 두 번 붙여도 한 줄**이다. 서버의
 * `walk_pets` 와 같은 성질이다.
 */
@Entity(
    tableName = "walk_session_dog",
    primaryKeys = ["sessionId", "dogId"],
    foreignKeys = [
        ForeignKey(
            entity = WalkSessionRow::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("dogId")],
)
data class WalkSessionDogRow(
    val sessionId: String,
    /**
     * 서버의 pet id 다. **기기에서 강아지를 지워도 이 줄은 남는다** — 지난 산책이
     * "누구와 갔는지 모르는 것"이 되면 안 된다. 이름은 여기 안 적는다. 이름은 바뀌는
     * 값이라 박아 두면 개명한 뒤에도 옛 이름이 뜬다.
     */
    val dogId: String,
)

/**
 * clientSeq가 자동 증가 ID 대신 키의 일부다. 같은 fix를 다시 쓰면 중복 행을 만들지 않는다.
 */
@Entity(
    tableName = "walk_fix",
    primaryKeys = ["sessionId", "clientSeq"],
    foreignKeys = [
        ForeignKey(
            entity = WalkSessionRow::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class WalkFixRow(
    val sessionId: String,
    val clientSeq: Int,
    val chainIndex: Int,
    val atMillis: Long,
    val lat: Double,
    val lng: Double,
    val accuracyM: Float?,
    val isMock: Boolean,
)
