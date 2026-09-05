package com.daengs.app.walk.store

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** Server source facts only; user edits and last reviewed snapshot stay in walk_storyboard. */
@Entity(tableName = "walk_scene_analysis", foreignKeys = [ForeignKey(
    entity = WalkSessionRow::class, parentColumns = ["id"], childColumns = ["sessionId"],
    onDelete = ForeignKey.CASCADE,
)])
data class WalkSceneAnalysisRow(
    @PrimaryKey val sessionId: String,
    val generation: Long,
    val entryStamp: String,
    val inputRevision: String,
    val status: String,
    val bundle: String?,
    val error: String?,
)
