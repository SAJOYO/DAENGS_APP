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
    /** Only the visible page. No route reconstruction, photos, edits, or network generation. */
    fun observeTitles(sessionIds: List<String>): Flow<Map<String, String>> {
        if (sessionIds.isEmpty()) return flowOf(emptyMap())
        val expectedOwner = owner()
        val records = combine(sessionIds.distinct().map { id ->
            combine(dao.observeEntries(id), dao.observeSceneAnalysis(id)) { entries, analysis ->
                id to storyboardAnalysisView(analysis, entries).bundle?.takeIf { it.sessionId == id }?.title
            }
        }) { it.toList() }
        return combine(records, dao.observeSessions()) { titles, sessions ->
            val allowed = sessions.filter { it.ownerId == expectedOwner && it.endedAtMillis != null }.map { it.id }.toSet()
            if (owner() != expectedOwner) emptyMap()
            else titles.mapNotNull { (id, title) -> title?.takeIf { id in allowed }?.let { id to it } }.toMap()
        }.flowOn(Dispatchers.IO)
    }

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
