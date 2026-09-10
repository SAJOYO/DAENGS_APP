package com.daengs.app.walk.store

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** Only walks ended by this version opt in. Existing saved boards are never reopened. */
@Entity(tableName = "walk_diary_publication", foreignKeys = [
    ForeignKey(entity = WalkSessionRow::class, parentColumns = ["id"],
        childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE),
])
data class WalkDiaryPublicationRow(
    @PrimaryKey val sessionId: String,
    val startedAtMillis: Long,
    val deadlineAtMillis: Long,
    val baseBundle: String? = null,
    val publishedBundle: String? = null,
    val publishedAtMillis: Long? = null,
)
