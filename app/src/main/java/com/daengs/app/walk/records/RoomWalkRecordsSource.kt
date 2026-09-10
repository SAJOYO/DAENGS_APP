package com.daengs.app.walk.records

import androidx.room.withTransaction
import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.countsAsWalk
import com.daengs.app.walk.diary.storyboardAnalysisView
import com.daengs.app.walk.forHistoryThumbnail
import com.daengs.app.walk.store.WalkDatabase
import com.daengs.app.walk.store.WalkEntryRow
import com.daengs.app.walk.store.WalkFixRow
import com.daengs.app.walk.store.WalkSessionRow
import com.daengs.app.walk.store.toModel
import com.daengs.app.walk.summarize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.ZoneId

/** Reads the complete local record selection for one captured account; never creates map traces. */
class RoomWalkRecordsSource(
    private val database: WalkDatabase,
    private val expectedOwner: String,
    private val currentOwner: () -> String,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : WalkRecordsSource {
    override val changes: Flow<Unit> = database.invalidationTracker.createFlow(
        "walk_session", "walk_session_dog", "walk_fix", "walk_entry",
        "walk_scene_analysis", "walk_photo", "walk_photo_sync", emitInitialState = true,
    ).map { Unit }

    override suspend fun select(query: WalkRecordsQuery): WalkRecordsSelection {
        checkOwner()
        // Detach caller-owned condition sets before any suspension.
        val selectedQuery = WalkRecordsSelection(query, emptyList()).query
        return withContext(Dispatchers.Default) {
            val snapshot = readSnapshot(selectedQuery)
            checkOwner()
            val records = snapshot.mapNotNull { input ->
                currentCoroutineContext().ensureActive()
                val summary = summarize(input.session.toModel(input.dogIds), input.fixes.map(WalkFixRow::toModel),
                    maxRouteSamples = Int.MAX_VALUE)
                // Match the existing history's accepted records, including short action/photo walks.
                if (!summary.countsAsWalk && input.entries.isEmpty() && !input.hasPhotos) return@mapNotNull null
                WalkRecord(summary.forHistoryThumbnail(), title = input.title, notes = input.notes, entries = input.entries,
                    serverWalkId = input.session.serverWalkId,
                    traceState = if (input.session.serverWalkId == null) WalkTraceState.NOT_UPLOADED
                        else WalkTraceState.NOT_REQUESTED)
            }
            val selected = selectWalkRecords(records, selectedQuery, zone)
            currentCoroutineContext().ensureActive()
            checkOwner()
            selected
        }
    }

    private suspend fun readSnapshot(query: WalkRecordsQuery): List<RecordSnapshot> = database.withTransaction {
        checkOwner()
        val dao = database.walkDao()
        // Reuse the history's date/weather rules before loading a walk's heavier input rows.
        val sessions = dao.finishedRecordSessions(expectedOwner, query.dogId)
            .filter { query.filter.matches(it.toModel(), zone) }
        val records = mutableListOf<RecordSnapshot>()
        for (batch in sessions.chunked(QUERY_BATCH_SIZE)) {
            currentCoroutineContext().ensureActive()
            val ids = batch.map { it.id }
            val dogs = dao.sessionDogs(ids).groupBy({ it.sessionId }, { it.dogId })
            val entries = dao.historySearchEntries(ids).groupBy { it.sessionId }
            val analyses = dao.historySearchAnalyses(ids).associateBy { it.sessionId }
            for (session in batch) {
                currentCoroutineContext().ensureActive()
                val rows = entries[session.id].orEmpty()
                val photoSync = dao.photoSync(session.id)
                val photos = dao.photos(session.id)
                val visibleEntries = rows.mapNotNull(WalkEntryRow::entry)
                // Deleted rows still belong to the source stamp, but never return as visible entries.
                val title = storyboardAnalysisView(analyses[session.id], rows, photoSync, photos)
                    .bundle?.takeIf { it.sessionId == session.id }?.title
                val notes = visibleEntries.mapNotNull { it.note }
                // Read GPS only after the current title/notes match, within the same DB snapshot.
                if (!query.filter.matchesText(listOfNotNull(title) + notes)) continue
                records += RecordSnapshot(session, dogs[session.id].orEmpty(), visibleEntries, title, notes,
                    photos.isNotEmpty(), dao.fixes(session.id))
            }
        }
        records
    }

    private fun checkOwner() {
        check(expectedOwner.isNotBlank() && currentOwner() == expectedOwner) {
            "산책을 읽는 동안 계정이 변경됐어요."
        }
    }

    private data class RecordSnapshot(
        val session: WalkSessionRow,
        val dogIds: List<String>,
        val entries: List<WalkEntry>,
        val title: String?,
        val notes: List<String>,
        val hasPhotos: Boolean,
        val fixes: List<WalkFixRow>,
    )

    private companion object { const val QUERY_BATCH_SIZE = 900 }
}
