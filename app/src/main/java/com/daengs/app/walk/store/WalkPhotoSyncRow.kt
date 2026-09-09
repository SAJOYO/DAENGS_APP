package com.daengs.app.walk.store

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** Exists only for a locally originated photo collection, never an empty server restore. */
@Entity(tableName = "walk_photo_sync", foreignKeys = [ForeignKey(entity = WalkSessionRow::class,
    parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)])
data class WalkPhotoSyncRow(
    @PrimaryKey val sessionId: String,
    val ownerId: String,
    val publisherId: String,
    val revision: Long = 1,
    val acknowledgedRevision: Long = 0,
    /** Frozen request survives lost ACKs, process death and edits while uploading. */
    val pendingPayload: String? = null,
)

data class WalkPhotoUploadSnapshot(val state: WalkPhotoSyncRow, val photos: List<WalkPhotoRow>)
