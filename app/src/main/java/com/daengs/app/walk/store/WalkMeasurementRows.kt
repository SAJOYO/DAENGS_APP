package com.daengs.app.walk.store

import androidx.room.Entity
import androidx.room.ForeignKey

/** Only a fully verified generation is published; individual pages stay below CursorWindow limits. */
@Entity(tableName = "walk_measurement", primaryKeys = ["sessionId"], foreignKeys = [ForeignKey(
    entity = WalkSessionRow::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)])
data class WalkMeasurementRow(val sessionId: String, val measurementId: String, val summaryJson: String,
    val summaryHash: String)

@Entity(tableName = "walk_measurement_chunk", primaryKeys = ["sessionId", "chunkIndex"], foreignKeys = [ForeignKey(
    entity = WalkMeasurementRow::class, parentColumns = ["sessionId"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)])
data class WalkMeasurementChunkRow(val sessionId: String, val chunkIndex: Int, val payload: String)
