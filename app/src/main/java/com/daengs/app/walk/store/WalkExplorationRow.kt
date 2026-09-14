package com.daengs.app.walk.store

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** Small local reading address. Session deletion/logout cleanup cascades through the parent. */
@Entity(tableName = "walk_exploration", foreignKeys = [ForeignKey(entity = WalkSessionRow::class,
    parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)])
data class WalkExplorationRow(@PrimaryKey val sessionId: String, val ownerId: String, val payload: String)
