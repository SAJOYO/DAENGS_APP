package com.daengs.app.walk.store

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** Frozen wire snapshot and receipt survive worker/process restarts. No separate account credentials. */
@Entity(tableName = "walk_motion_backup", foreignKeys = [ForeignKey(entity = WalkSessionRow::class,
    parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)])
data class WalkMotionBackupRow(
    @PrimaryKey val sessionId: String,
    val manifestJson: String,
    val manifestFingerprint: String,
    val evidenceFingerprint: String,
    val completedAtMillis: Long? = null,
    val lastError: String? = null,
)
