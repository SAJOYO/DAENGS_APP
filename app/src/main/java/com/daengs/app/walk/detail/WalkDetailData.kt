package com.daengs.app.walk.detail

import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.WalkSessionDetail
import com.daengs.app.walk.diary.DiaryWalk
import com.daengs.app.walk.diary.StoryboardScene
import kotlinx.coroutines.flow.Flow

/** One session in one login generation. Route preparation and selection remain UI responsibilities. */
internal interface WalkDetailSource {
    val changes: Flow<Unit>
    val entries: Flow<List<WalkEntry>>
    fun isCurrentAccount(): Boolean
    suspend fun load(): WalkSessionDetail?
    fun observeDiary(detail: WalkSessionDetail): Flow<DiaryWalk?>
}

/** Caller owns the coroutine and error presentation; existing stores own durable writes. */
internal interface WalkDetailActions {
    suspend fun open()
    fun prepareDiary()
    suspend fun generateDiary()
    suspend fun saveEntry(entry: WalkEntry)
    suspend fun deleteEntry(id: String)
    suspend fun saveScene(scene: StoryboardScene, title: String, body: String)
    suspend fun deletePhoto(id: String)
}
