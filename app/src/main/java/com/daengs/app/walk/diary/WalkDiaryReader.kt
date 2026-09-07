package com.daengs.app.walk.diary

import com.daengs.app.walk.WalkSummary
import com.daengs.app.walk.store.WalkDao
import com.daengs.app.walk.store.WalkPhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn

class WalkDiaryReader(
    private val dao: WalkDao,
    private val photos: WalkPhotoStore,
    private val owner: () -> String,
) {
    fun observe(walks: List<WalkSummary>): Flow<List<DiaryWalk>> {
        if (walks.isEmpty()) return flowOf(emptyList())
        val expectedOwner = owner()
        return combine(walks.map { walk ->
            combine(dao.observeEntries(walk.sessionId), dao.observeSceneAnalysis(walk.sessionId),
                dao.observeStoryboard(walk.sessionId), photos.observe(walk.sessionId),
                dao.observeSessions()) { entries, analysis, draft, images, sessions ->
                if (owner() != expectedOwner || sessions.none {
                        it.id == walk.sessionId && it.ownerId == expectedOwner && it.endedAtMillis != null
                    }) null
                else diaryWalk(walk, entries.mapNotNull { it.entry() }, images,
                    StoryboardDraft.parse(draft?.payload), storyboardAnalysisView(analysis, entries))
            }
        }) { records -> records.filterNotNull() }.flowOn(Dispatchers.IO)
    }
}
