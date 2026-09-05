package com.daengs.app.walk.store

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** User edits and the explicitly reviewed snapshot; removed with the owning walk. */
@Entity(tableName = "walk_storyboard", foreignKeys = [ForeignKey(
    entity = WalkSessionRow::class, parentColumns = ["id"], childColumns = ["sessionId"],
    onDelete = ForeignKey.CASCADE,
)])
data class WalkStoryboardRow(@PrimaryKey val sessionId: String, val payload: String)
