package com.daengs.app.walk.store

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.daengs.app.walk.RecordingEpoch

@Entity(tableName = "walk_recording_epoch", foreignKeys = [ForeignKey(
    entity = WalkSessionRow::class, parentColumns = ["id"], childColumns = ["sessionId"],
    onDelete = ForeignKey.CASCADE)], indices = [Index("sessionId")])
data class RecordingEpochRow(
    @PrimaryKey val id: String,
    val sessionId: String,
    val clockEpochId: String,
    val chainIndex: Int,
    val startedAtMillis: Long,
    val startedElapsedNanos: Long,
    val firstIngressSeq: Long,
    val endedAtMillis: Long?,
    val endedElapsedNanos: Long?,
    val endKind: String?,
    val targetIngressSeq: Long?,
    val persistedCount: Long,
    val failureReason: String?,
    val firstFailedSeq: Long?,
    val drained: Boolean,
) {
    fun toModel() = RecordingEpoch(id, sessionId, clockEpochId, chainIndex, startedAtMillis, startedElapsedNanos, firstIngressSeq, endedAtMillis, endedElapsedNanos, endKind, targetIngressSeq, persistedCount, failureReason, firstFailedSeq, drained)

    companion object {
        fun from(value: RecordingEpoch) = RecordingEpochRow(value.id, value.sessionId, value.clockEpochId, value.chainIndex, value.startedAtMillis, value.startedElapsedNanos, value.firstIngressSeq, value.endedAtMillis, value.endedElapsedNanos, value.endKind, value.targetIngressSeq, value.persistedCount, value.failureReason, value.firstFailedSeq, value.drained)
    }
}
