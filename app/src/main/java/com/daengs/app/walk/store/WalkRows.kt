package com.daengs.app.walk.store

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "walk_session")
data class WalkSessionRow(
    @PrimaryKey val id: String,
    val dogId: String?,
    val startedAtMillis: Long,
    /** null이면 아직 진행 중이거나 명시적인 종료 전에 프로세스가 끝난 세션이다. */
    val endedAtMillis: Long?,
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
