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
            combine(dao.observeEntries(id), dao.observeSceneAnalysis(id), dao.observePhotoSync(id), dao.observePhotos(id)) { entries, analysis, state, images ->
                id to storyboardAnalysisView(analysis, entries, state, images).bundle?.takeIf { it.sessionId == id }?.title
            }
        }) { it.toList() }
        return combine(records, dao.observeSessions()) { titles, sessions ->
            val allowed = sessions.filter { it.ownerId == expectedOwner && it.endedAtMillis != null }.map { it.id }.toSet()
            if (owner() != expectedOwner) emptyMap()
            else titles.mapNotNull { (id, title) -> title?.takeIf { id in allowed }?.let { id to it } }.toMap()
        }.flowOn(Dispatchers.IO)
    }

    fun observe(walks: List<WalkSummary>, observations: Map<String, List<com.daengs.app.walk.RecordedFix>> = emptyMap()): Flow<List<DiaryWalk>> {
        if (walks.isEmpty()) return flowOf(emptyList())
        val expectedOwner = owner()
        return combine(walks.map { walk ->
            val photoSource = combine(dao.observePhotoSync(walk.sessionId), dao.observePhotos(walk.sessionId), photos.observe(walk.sessionId)) {
                state, rows, images -> Triple(state, rows, images)
            }
            combine(dao.observeEntries(walk.sessionId), dao.observeSceneAnalysis(walk.sessionId),
                dao.observeStoryboard(walk.sessionId), photoSource,
                dao.observeSessions()) { entries, analysis, draft, images, sessions ->
                if (owner() != expectedOwner || sessions.none {
                        it.id == walk.sessionId && it.ownerId == expectedOwner && it.endedAtMillis != null
                    }) null
                else diaryWalk(walk, entries.mapNotNull { it.entry() }, images.third,
                    StoryboardDraft.parse(draft?.payload), storyboardAnalysisView(analysis, entries, images.first, images.second),
                    observations[walk.sessionId].orEmpty())
            }
        }) { records -> records.filterNotNull() }.flowOn(Dispatchers.IO)
    }
}
