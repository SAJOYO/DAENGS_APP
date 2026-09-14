package com.daengs.app.walk.records

import androidx.room.withTransaction
import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.countsAsWalk
import com.daengs.app.walk.store.storedStoryboardAnalysisView
import com.daengs.app.walk.diary.GeoStoryboardBundle
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
        "walk_scene_analysis", "walk_photo", "walk_photo_sync", "walk_diary_publication", "walk_recording_epoch", emitInitialState = true,
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
                    maxRouteSamples = Int.MAX_VALUE, epochs = input.epochs)
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

    override suspend fun loadRoute(record: WalkRecord): com.daengs.app.walk.WalkSummary = withContext(Dispatchers.Default) {
        checkOwner()
        val summary = database.withTransaction {
            checkOwner()
            val dao = database.walkDao()
            val session = checkNotNull(dao.session(record.summary.sessionId)) { "산책 기록이 없어졌어요." }
            check(session.ownerId == expectedOwner && session.endedAtMillis != null) { "이 산책을 읽을 수 없어요." }
            val dogs = dao.sessionDogs(listOf(session.id)).map { it.dogId }
            summarize(session.toModel(dogs), dao.fixes(session.id).map(WalkFixRow::toModel),
                maxRouteSamples = Int.MAX_VALUE, epochs = dao.recordingEpochs(session.id).map { it.toModel() })
        }
        currentCoroutineContext().ensureActive()
        checkOwner()
        check(summary.forHistoryThumbnail() == record.summary) { "산책 기록이 변경됐어요. 다시 선택해 주세요." }
        summary
    }

    private suspend fun readSnapshot(query: WalkRecordsQuery): List<RecordSnapshot> = database.withTransaction {
        checkOwner()
        val dao = database.walkDao()
        // Reuse the history's date/weather rules before loading a walk's heavier input rows.
        val sessions = dao.finishedRecordSessions(expectedOwner, query.dogIds?.singleOrNull())
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
                if (!query.includesDogs(dogs[session.id].orEmpty())) continue
                val rows = entries[session.id].orEmpty()
                val photoSync = dao.photoSync(session.id)
                val photos = dao.photos(session.id)
                val visibleEntries = rows.mapNotNull(WalkEntryRow::entry)
                // Deleted rows still belong to the source stamp, but never return as visible entries.
                val publication = dao.diaryPublication(session.id)
                val board = if (publication != null) publication.publishedBundle?.let(GeoStoryboardBundle::parse)
                    else storedStoryboardAnalysisView(analyses[session.id], rows, photoSync, photos).bundle
                val title = board?.takeIf { it.sessionId == session.id }?.title
                val notes = visibleEntries.mapNotNull { it.note }
                // Read GPS only after the current title/notes match, within the same DB snapshot.
                if (!query.filter.matchesText(listOfNotNull(title) + notes)) continue
                records += RecordSnapshot(session, dogs[session.id].orEmpty(), visibleEntries, title, notes,
                    photos.isNotEmpty(), dao.fixes(session.id), dao.recordingEpochs(session.id).map { it.toModel() })
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
        val epochs: List<com.daengs.app.walk.RecordingEpoch>,
    )

    private companion object { const val QUERY_BATCH_SIZE = 900 }
}
