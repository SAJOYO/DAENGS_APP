package com.daengs.app.walk.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WalkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRecordingEpoch(row: RecordingEpochRow)

    @Query("SELECT * FROM walk_recording_epoch WHERE sessionId = :sessionId ORDER BY firstIngressSeq, chainIndex")
    suspend fun recordingEpochs(sessionId: String): List<RecordingEpochRow>

    @Query("SELECT * FROM walk_fix WHERE sessionId = :sessionId AND ingressSeq > :afterSeq ORDER BY ingressSeq LIMIT :limit")
    suspend fun observationsAfter(sessionId: String, afterSeq: Long, limit: Int): List<WalkFixRow>

    @Query("SELECT * FROM walk_fix WHERE sessionId = :sessionId AND clientSeq = :clientSeq")
    suspend fun observation(sessionId: String, clientSeq: Int): WalkFixRow?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertObservation(row: WalkFixRow)

    @Query("UPDATE walk_recording_epoch SET persistedCount = persistedCount + 1 WHERE id = :epochId AND sessionId = :sessionId AND chainIndex = :chainIndex AND clockEpochId = :clockEpochId AND drained = 0")
    suspend fun advanceRecordingEpoch(epochId: String, sessionId: String, chainIndex: Int, clockEpochId: String): Int

    @androidx.room.Transaction
    suspend fun appendObservation(row: WalkFixRow) {
        val existing = observation(row.sessionId, row.clientSeq)
        if (existing != null) { check(existing == row) { "Conflicting observation identity" }; return }
        check(row.ingressSeq == row.clientSeq.toLong()) { "Observation sequence changed" }
        check(session(row.sessionId)?.endedAtMillis == null) { "Recording session is already closed" }
        check(advanceRecordingEpoch(requireNotNull(row.sourceEpoch), row.sessionId, row.chainIndex,
            requireNotNull(row.clockEpochId)) == 1) { "Recording epoch is unavailable" }
        insertObservation(row)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDiaryPublication(row: WalkDiaryPublicationRow): Long

    @Query("SELECT * FROM walk_diary_publication WHERE sessionId = :id")
    suspend fun diaryPublication(id: String): WalkDiaryPublicationRow?

    @Query("SELECT * FROM walk_diary_publication WHERE sessionId = :id")
    fun observeDiaryPublication(id: String): kotlinx.coroutines.flow.Flow<WalkDiaryPublicationRow?>

    @Query("SELECT sessionId FROM walk_diary_publication WHERE publishedBundle IS NULL")
    suspend fun pendingDiaryPublications(): List<String>

    @Query("SELECT COUNT(publishedBundle) FROM walk_diary_publication")
    fun observeDiaryPublicationCount(): kotlinx.coroutines.flow.Flow<Int>

    @Query("UPDATE walk_diary_publication SET baseBundle = :bundle WHERE sessionId = :id AND baseBundle IS NULL")
    suspend fun freezeDiaryBase(id: String, bundle: String)

    @Query("UPDATE walk_diary_publication SET publishedBundle = :bundle, publishedAtMillis = :now " +
        "WHERE sessionId = :id AND publishedBundle IS NULL AND baseBundle IS NOT NULL AND :now < deadlineAtMillis")
    suspend fun publishDiaryCandidate(id: String, bundle: String, now: Long): Int

    @Query("UPDATE walk_diary_publication SET publishedBundle = baseBundle, publishedAtMillis = :now " +
        "WHERE sessionId = :id AND publishedBundle IS NULL AND baseBundle IS NOT NULL AND :now >= deadlineAtMillis")
    suspend fun publishDiaryBase(id: String, now: Long): Int

    @androidx.room.Transaction
    suspend fun closeAndPrepareDiary(id: String, endedAt: Long) {
        val current = session(id) ?: return
        if (current.endedAtMillis != null) return
        val epochs = recordingEpochs(id)
        if (epochs.isNotEmpty()) com.daengs.app.walk.checkRecordingComplete(epochs.map { it.toModel() })
        closeSession(id, endedAt)
        insertDiaryPublication(WalkDiaryPublicationRow(id, endedAt,
            endedAt + com.daengs.app.walk.diary.DIARY_PREPARATION_BUDGET_MS))
    }

    @androidx.room.Transaction
    suspend fun prepareLocalDiary(id: String, ownerId: String): WalkDiaryPublicationRow? {
        val row = diaryPublication(id) ?: return null
        val walk = session(id)?.takeIf { it.ownerId == ownerId && it.endedAtMillis != null } ?: return null
        if (row.baseBundle == null) {
            val source = fixes(id).filter { it.recordingEligible != false }.map {
                com.daengs.app.walk.RecordedFix(it.clientSeq, it.chainIndex, it.atMillis, it.lat, it.lng, it.accuracyM, it.isMock)
            }
            val summary = com.daengs.app.walk.summarize(walk.toModel(), source, Int.MAX_VALUE)
            freezeDiaryBase(id, com.daengs.app.walk.diary.LocalDiaryBoard.build(summary, source,
                entries(id).mapNotNull { it.entry() }, photos(id)))
        }
        return diaryPublication(id)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPhotoSync(row: WalkPhotoSyncRow): Long

    @Query("SELECT * FROM walk_photo_sync WHERE sessionId = :sessionId")
    suspend fun photoSync(sessionId: String): WalkPhotoSyncRow?

    @Query("SELECT * FROM walk_photo_sync WHERE sessionId = :sessionId")
    fun observePhotoSync(sessionId: String): kotlinx.coroutines.flow.Flow<WalkPhotoSyncRow?>

    @Query("SELECT * FROM walk_photo WHERE sessionId = :sessionId ORDER BY id")
    suspend fun photos(sessionId: String): List<WalkPhotoRow>

    @Query("SELECT sessionId FROM walk_photo_sync WHERE revision > acknowledgedRevision OR pendingPayload IS NOT NULL")
    suspend fun dirtyPhotoSessions(): List<String>

    @Query("UPDATE walk_photo_sync SET revision = revision + 1 WHERE sessionId = :sessionId AND ownerId = :ownerId")
    suspend fun touchPhotoSync(sessionId: String, ownerId: String)

    @androidx.room.Transaction
    suspend fun savePhotoAndQueue(row: WalkPhotoRow) {
        check(session(row.sessionId)?.ownerId == row.ownerId)
        insertPhoto(row)
        insertPhotoSync(WalkPhotoSyncRow(row.sessionId, row.ownerId, java.util.UUID.randomUUID().toString()))
        touchPhotoSync(row.sessionId, row.ownerId)
    }

    @androidx.room.Transaction
    suspend fun deletePhotoAndQueue(id: String, ownerId: String) {
        val row = photo(id) ?: return
        check(row.ownerId == ownerId && session(row.sessionId)?.ownerId == ownerId)
        // Also covers photos present before migration or saved by an earlier app version.
        insertPhotoSync(WalkPhotoSyncRow(row.sessionId, ownerId, java.util.UUID.randomUUID().toString()))
        deletePhoto(id)
        touchPhotoSync(row.sessionId, ownerId)
    }

    @androidx.room.Transaction
    suspend fun photoUploadSnapshot(sessionId: String, ownerId: String, walkId: String): WalkPhotoUploadSnapshot? {
        val walk = session(sessionId) ?: return null
        if (walk.ownerId != ownerId || walk.endedAtMillis == null || walk.serverWalkId != walkId) return null
        val state = photoSync(sessionId) ?: return null
        if (state.ownerId != ownerId) return null
        val photos = photos(sessionId)
        check(photos.all { it.ownerId == ownerId })
        return WalkPhotoUploadSnapshot(state, photos)
    }

    @Query("UPDATE walk_photo_sync SET pendingPayload = :payload WHERE sessionId = :sessionId " +
        "AND ownerId = :ownerId AND revision = :revision AND pendingPayload IS NULL")
    suspend fun freezePhotoUpload(sessionId: String, ownerId: String, revision: Long, payload: String): Int

    @Query("UPDATE walk_photo_sync SET acknowledgedRevision = :revision, pendingPayload = NULL " +
        "WHERE sessionId = :sessionId AND ownerId = :ownerId AND pendingPayload = :payload " +
        "AND acknowledgedRevision < :revision")
    suspend fun acknowledgePhotoUpload(sessionId: String, ownerId: String, revision: Long, payload: String): Int

    // A definitive 422 did not write a server revision. Preserve edits and the last ACK.
    @Query("UPDATE walk_photo_sync SET pendingPayload = NULL WHERE sessionId = :sessionId " +
        "AND ownerId = :ownerId AND pendingPayload = :payload AND acknowledgedRevision = :expectedRevision")
    suspend fun rejectPhotoUpload(sessionId: String, ownerId: String, expectedRevision: Long, payload: String): Int

    @androidx.room.Transaction
    suspend fun rejectPinRequest(id: String, sent: String, ownerId: String, message: String) {
        val row = entry(id) ?: return
        if (row.pendingRequest != sent || session(row.sessionId)?.ownerId != ownerId) return
        updatePinRow(row.copy(pendingRequest = null, syncError = message))
    }

    @androidx.room.Transaction
    suspend fun rebaseLegacyEntry(id: String, response: String, ownerId: String, requiresV2: Boolean = false) {
        val row = entry(id) ?: return
        if (session(row.sessionId)?.ownerId != ownerId) return
        val remote = org.json.JSONObject(response)
        if (remote.optBoolean("deleted")) { acceptDeletedEntry(id, remote.getInt("revision")); return }
        val pin = remote.optJSONObject("pin")
        // A null note pin cannot identify its storage version; a v1 426 can.
        val upgraded = row.isV2 || requiresV2 || (pin != null && pin.optString("policy_version") != "legacy-v1")
        updatePinRow(row.copy(revision = maxOf(row.revision, remote.getInt("revision")),
            mutationId = java.util.UUID.randomUUID().toString(), isV2 = upgraded,
            pinPayload = if (row.payload != null) pin?.toString() else null,
            pinRevision = if (row.payload != null) remote.getInt("pin_revision") else 0,
            syncError = if (row.payload == null) null else "다른 기기에서 바뀐 기록이에요. 내용을 확인하고 저장해 주세요."))
    }

    @androidx.room.Transaction
    suspend fun preparePinRequest(id: String, ownerId: String, cutoffSupported: Boolean = true, recordingEvidence: String? = null): String? {
        val row = entry(id) ?: return null
        if (session(row.sessionId)?.ownerId != ownerId) return null
        row.pendingRequest?.let { return it }
        if (!row.dirty && !row.pinDirty) return null
        val pending = com.daengs.app.walk.sync.PinPending.from(row, cutoffSupported, recordingEvidence).json.toString()
        updatePinRow(row.copy(pendingRequest = pending))
        return pending
    }

    @androidx.room.Transaction
    suspend fun retryLegacyPinSourceErrors(sessionId: String, ownerId: String) {
        if (session(sessionId)?.ownerId != ownerId) return
        for (row in entries(sessionId)) {
            if (row.isV2 && row.revision == 0 && row.payload != null && row.pendingRequest == null &&
                row.syncError == com.daengs.app.walk.sync.LEGACY_PIN_SOURCE_ERROR &&
                row.pinPayload?.let { org.json.JSONObject(it).optString("state") } == "unlocated") {
                // The new rejection text differs, so an unrelated 422 is not retried forever.
                updatePinRow(row.copy(syncError = null))
            }
        }
    }

    @androidx.room.Transaction
    suspend fun ackPinRequest(id: String, sent: String, response: String, ownerId: String) {
        val row = entry(id) ?: return
        if (row.pendingRequest != sent || session(row.sessionId)?.ownerId != ownerId) return
        val remote = org.json.JSONObject(response)
        if (remote.optBoolean("deleted")) {
            acceptDeletedEntry(id, maxOf(row.revision, remote.getInt("revision")))
            return
        }
        val pending = com.daengs.app.walk.sync.PinPending(org.json.JSONObject(sent))
        // An old successful receipt advances acknowledgement, never overwrites current content/pin.
        updatePinRow(row.copy(revision = maxOf(row.revision, remote.getInt("revision")),
            pinRevision = maxOf(row.pinRevision, remote.getInt("pin_revision")), pendingRequest = null,
            dirty = if (pending.kind in listOf("create", "content")) row.payload != pending.snapshot else row.dirty,
            pinDirty = if (pending.kind in listOf("create", "pin")) row.pinPayload != pending.pin else row.pinDirty,
            mutationId = if (row.mutationId == pending.localVersion) remote.getString("mutation_id") else row.mutationId))
    }

    @androidx.room.Transaction
    suspend fun conflictPinRequest(id: String, sent: String, response: String, ownerId: String) {
        val row = entry(id) ?: return
        if (row.pendingRequest != sent || session(row.sessionId)?.ownerId != ownerId) return
        val remote = org.json.JSONObject(response)
        if (remote.optBoolean("deleted")) {
            acceptDeletedEntry(id, maxOf(row.revision, remote.getInt("revision")))
            return
        }
        val pending = com.daengs.app.walk.sync.PinPending(org.json.JSONObject(sent))
        val remotePin = remote.optJSONObject("pin")
        val terminal = remotePin?.optString("state") != "provisional"
        // A local edit may have arrived after this pin request was frozen. Compare semantic
        // content, since server normalization changes JSON field order and timestamp spelling.
        fun content(payload: String) = com.daengs.app.walk.WalkEntry.parse(id, row.sessionId, org.json.JSONObject(payload))
        val contentConflict = pending.kind in listOf("create", "content") ||
            (row.dirty && pending.kind == "pin" &&
                pending.snapshot?.let(::content) != content(remote.getJSONObject("content").toString()))
        updatePinRow(row.copy(revision = maxOf(row.revision, remote.getInt("revision")),
            pendingRequest = null, mutationId = java.util.UUID.randomUUID().toString(),
            pinRevision = remote.getInt("pin_revision"),
            pinPayload = if (row.payload == null) null else if (terminal) remotePin?.toString() else row.pinPayload,
            pinDirty = row.payload != null && !terminal && row.pinDirty,
            syncError = if (row.payload != null && contentConflict)
                "다른 기기에서 바뀐 기록이에요. 내용을 확인하고 저장해 주세요." else null))
    }

    @androidx.room.Transaction
    suspend fun acceptPinRemote(sessionId: String, response: String, ownerId: String) {
        if (session(sessionId)?.ownerId != ownerId) return
        val remote = org.json.JSONObject(response)
        val id = remote.getString("id")
        val row = entry(id)
        if (row != null && row.sessionId != sessionId) return
        val revision = remote.getInt("revision")
        if (remote.optBoolean("deleted")) {
            if (row == null) insertEntry(WalkEntryRow(id, sessionId, null, revision,
                remote.getString("mutation_id"), false, isV2 = true))
            else acceptDeletedEntry(id, maxOf(row.revision, revision))
            return
        }
        if (row != null && (revision < row.revision || row.pendingRequest != null || row.payload == null)) return
        val pin = remote.optJSONObject("pin")
        val preserveLocalPin = row?.pinDirty == true && pin?.optString("state") == "provisional"
        val fresh = WalkEntryRow(id, sessionId, remote.getJSONObject("content").toString(), revision,
            remote.getString("mutation_id"), false, pinPayload = pin?.toString(),
            pinRevision = remote.getInt("pin_revision"),
            pinChainIndex = -1,
            isV2 = row?.isV2 == true || (pin != null && pin.optString("policy_version") != "legacy-v1"))
        if (row == null) insertEntry(fresh) else updatePinRow(fresh.copy(
            payload = if (row.dirty) row.payload else fresh.payload, dirty = row.dirty,
            syncError = if (row.dirty && revision > row.revision)
                "다른 기기에서 바뀐 기록이에요. 내용을 확인하고 저장해 주세요." else row.syncError,
            pinPayload = if (preserveLocalPin) row.pinPayload else fresh.pinPayload,
            pinDirty = preserveLocalPin, pinChainIndex = row.pinChainIndex,
            mutationId = if (row.dirty || preserveLocalPin) row.mutationId else fresh.mutationId))
    }

    @androidx.room.Update
    suspend fun updatePinRow(row: WalkEntryRow)

    @Query("SELECT * FROM walk_entry WHERE pinPayload IS NOT NULL AND payload IS NOT NULL")
    suspend fun pendingPinEntries(): List<WalkEntryRow>

    @androidx.room.Transaction
    suspend fun createPinEntry(row: WalkEntryRow, ownerId: String): Boolean {
        if (session(row.sessionId)?.ownerId != ownerId || entry(row.id) != null) return false
        insertEntry(row)
        return true
    }

    @androidx.room.Transaction
    suspend fun finishPinEntry(id: String, previous: String, pin: String, mutation: String, ownerId: String) {
        val row = entry(id) ?: return
        if (row.payload == null || row.pinPayload != previous || session(row.sessionId)?.ownerId != ownerId) return
        updatePinRow(row.copy(pinPayload = pin, pinDirty = true, mutationId = mutation))
    }

    @androidx.room.Transaction
    suspend fun deletePinAwareEntry(id: String, mutation: String) {
        val row = entry(id) ?: return
        if (row.payload == null) return
        // A late ACK cannot restore these coordinates or the request's content snapshot.
        updatePinRow(row.copy(payload = null, pinPayload = null, pinRevision = 0, pinDirty = false,
            pendingRequest = null, mutationId = mutation, dirty = true, syncError = null))
    }

    @Query("SELECT sessionId FROM walk_scene_analysis")
    fun observeAnalysisChanges(): kotlinx.coroutines.flow.Flow<List<String>>

    @Query("SELECT * FROM walk_entry WHERE sessionId IN (:ids) ORDER BY id")
    suspend fun historySearchEntries(ids: List<String>): List<WalkEntryRow>

    @Query("SELECT * FROM walk_scene_analysis WHERE sessionId IN (:ids)")
    suspend fun historySearchAnalyses(ids: List<String>): List<WalkSceneAnalysisRow>

    /** A fresh, bounded projection instead of a second mutable search copy of private notes. */
    @androidx.room.Transaction
    suspend fun historySearchText(ids: List<String>, ownerId: String): Map<String, List<String>> {
        val allowed = ids.filter { session(it)?.let { s -> s.ownerId == ownerId && s.endedAtMillis != null } == true }
        if (allowed.isEmpty()) return emptyMap()
        val entries = historySearchEntries(allowed).groupBy { it.sessionId }
        val analyses = historySearchAnalyses(allowed).associateBy { it.sessionId }
        return allowed.associateWith { id ->
            val rows = entries[id].orEmpty()
            val publication = diaryPublication(id)
            val board = if (publication != null) publication.publishedBundle?.let {
                com.daengs.app.walk.diary.GeoStoryboardBundle.parse(it)
            } else com.daengs.app.walk.diary.storyboardAnalysisView(analyses[id], rows).bundle
            val title = board?.takeIf { it.sessionId == id }?.title
            listOfNotNull(title) + rows.mapNotNull { runCatching { it.entry()?.note }.getOrNull() }
        }
    }

    @Query("SELECT * FROM walk_scene_analysis WHERE sessionId = :sessionId")
    fun observeSceneAnalysis(sessionId: String): kotlinx.coroutines.flow.Flow<WalkSceneAnalysisRow?>
    @Query("SELECT * FROM walk_scene_analysis WHERE sessionId = :sessionId")
    suspend fun sceneAnalysis(sessionId: String): WalkSceneAnalysisRow?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSceneAnalysis(row: WalkSceneAnalysisRow)
    @Query("UPDATE walk_scene_analysis SET status = 'failed', error = :error WHERE sessionId = :sessionId AND entryStamp = :stamp AND status != 'ready'")
    suspend fun failSceneAnalysis(sessionId: String, stamp: String, error: String)
    @androidx.room.Transaction
    suspend fun acceptSceneAnalysis(row: WalkSceneAnalysisRow, ownerId: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (session(row.sessionId)?.ownerId != ownerId) return false
        val stamp = if (row.entryStamp.startsWith("diary:"))
            com.daengs.app.walk.sync.diaryInputStamp(entries(row.sessionId), photoSync(row.sessionId), photos(row.sessionId))
            else com.daengs.app.walk.sync.storyboardEntryStamp(entries(row.sessionId))
        if (stamp != row.entryStamp) return false
        val current = sceneAnalysis(row.sessionId)
        if (current != null && current.generation > row.generation) return false
        // A pending/failed/stale response must not erase the last successful source or relabel it
        // as belonging to the new input. Acceptance of that input is still checked above.
        saveSceneAnalysis(if (row.status == "ready") row.copy(bundleEntryStamp = row.entryStamp)
            else row.copy(bundle = current?.bundle, bundleEntryStamp = current?.bundleEntryStamp))
        if (row.status == "ready" && row.bundle != null && diaryPublication(row.sessionId) != null) {
            val parsed = com.daengs.app.walk.diary.GeoStoryboardBundle.parse(row.bundle)
            check(parsed.sessionId == row.sessionId)
            publishDiaryCandidate(row.sessionId, row.bundle, nowMillis)
        }
        return true
    }

    @Query("SELECT * FROM walk_photo WHERE sessionId = :sessionId ORDER BY capturedAtMillis, id")
    fun observePhotos(sessionId: String): kotlinx.coroutines.flow.Flow<List<WalkPhotoRow>>
    @Query("SELECT * FROM walk_photo WHERE id = :id")
    suspend fun photo(id: String): WalkPhotoRow?
    @Query("SELECT id FROM walk_photo")
    suspend fun photoIds(): List<String>
    @Query("SELECT id FROM walk_photo ORDER BY id")
    fun observePhotoIds(): kotlinx.coroutines.flow.Flow<List<String>>
    @Query("SELECT EXISTS(SELECT 1 FROM walk_photo WHERE sessionId = :sessionId)")
    suspend fun hasPhotos(sessionId: String): Boolean
    @Insert
    suspend fun insertPhoto(row: WalkPhotoRow)
    @Query("DELETE FROM walk_photo WHERE id = :id")
    suspend fun deletePhoto(id: String)

    @Query("SELECT * FROM walk_storyboard WHERE sessionId = :sessionId")
    suspend fun storyboard(sessionId: String): WalkStoryboardRow?

    @Query("SELECT * FROM walk_storyboard WHERE sessionId = :sessionId")
    fun observeStoryboard(sessionId: String): kotlinx.coroutines.flow.Flow<WalkStoryboardRow?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveStoryboard(row: WalkStoryboardRow)

    /** Merge one inline edit into the latest draft, keeping other scenes and original records. */
    @androidx.room.Transaction
    suspend fun saveDiarySceneEdit(sessionId: String, ownerId: String,
        scene: com.daengs.app.walk.diary.StoryboardScene, title: String, body: String) {
        check(ownerId.isNotBlank() && session(sessionId)?.let {
            it.ownerId == ownerId && it.endedAtMillis != null
        } == true) { "현재 계정의 완료된 산책이 아닙니다." }
        require(title.isNotBlank() && title.length <= 80 &&
            body.length <= com.daengs.app.walk.diary.MAX_DIARY_SCENE_BODY_LENGTH)
        check(diaryPublication(sessionId)?.let { it.publishedBundle != null } != false) { "산책을 정리하고 있어요." }
        val draft = com.daengs.app.walk.diary.StoryboardDraft.parse(storyboard(sessionId)?.payload)
        saveStoryboard(WalkStoryboardRow(sessionId,
            draft.edit(scene, title = title, body = body, acknowledge = true,
                bodyScope = com.daengs.app.walk.diary.SceneBodyScope.SCENE).toJson()))
    }

    @Query("SELECT * FROM walk_entry WHERE sessionId = :sessionId ORDER BY id")
    fun observeEntries(sessionId: String): kotlinx.coroutines.flow.Flow<List<WalkEntryRow>>

    @Query("SELECT * FROM walk_entry WHERE sessionId = :sessionId ORDER BY id")
    suspend fun entries(sessionId: String): List<WalkEntryRow>

    @Query("SELECT * FROM walk_entry WHERE id = :id")
    suspend fun entry(id: String): WalkEntryRow?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntry(row: WalkEntryRow)

    @Query("UPDATE walk_entry SET payload = :payload, mutationId = :mutationId, dirty = 1, syncError = NULL WHERE id = :id AND payload IS NOT NULL")
    suspend fun editEntry(id: String, payload: String?, mutationId: String): Int

    @Query("UPDATE walk_entry SET payload = :payload, mutationId = :mutationId, dirty = 1, syncError = NULL WHERE id = :id AND sessionId = :sessionId AND payload IS NOT NULL AND revision = :baseRevision AND mutationId = :baseMutationId")
    suspend fun editEntryIfUnchanged(id: String, sessionId: String, payload: String, mutationId: String,
        baseRevision: Int?, baseMutationId: String?): Int

    /** 검사와 쓰기를 한 트랜잭션으로 묶어 그 사이의 GET/ACK/삭제도 막는다. */
    @androidx.room.Transaction
    suspend fun saveEntryChecked(id: String, sessionId: String, payload: String, mutationId: String,
        baseRevision: Int?, baseMutationId: String?, ownerId: String?) {
        val session = session(sessionId)
        check(session != null && (ownerId == null || session.ownerId == ownerId)) {
            "현재 계정의 산책 기록이 아닙니다."
        }
        val existing = entry(id)
        if (existing == null) {
            check(baseRevision == null && baseMutationId == null) { "이미 삭제된 기록입니다." }
            insertEntry(WalkEntryRow(id, sessionId, payload, 0, mutationId, true))
        } else {
            check(existing.sessionId == sessionId && existing.payload != null) { "이미 삭제된 기록입니다." }
            check(editEntryIfUnchanged(id, sessionId, payload, mutationId, baseRevision, baseMutationId) == 1) {
                "편집 중 기록이 변경됐어요. 최신 내용을 확인해 주세요. 작성 중인 내용은 유지돼요."
            }
        }
    }

    @Query("UPDATE walk_entry SET revision = :revision, dirty = CASE WHEN mutationId = :mutationId THEN 0 ELSE 1 END WHERE id = :id")
    suspend fun acknowledgeEntry(id: String, revision: Int, mutationId: String)

    @Query("UPDATE walk_entry SET payload = :payload, revision = :revision, mutationId = :mutationId WHERE id = :id AND dirty = 0")
    suspend fun acceptEntry(id: String, payload: String?, revision: Int, mutationId: String)

    @Query("SELECT DISTINCT sessionId FROM walk_entry WHERE dirty = 1 OR pinDirty = 1 OR pendingRequest IS NOT NULL")
    suspend fun dirtyEntrySessions(): List<String>

    @Query("UPDATE walk_entry SET revision = :revision, syncError = :message WHERE id = :id AND mutationId = :mutationId")
    suspend fun conflictEntry(id: String, revision: Int, mutationId: String, message: String)

    @Query("UPDATE walk_entry SET payload = NULL, pinPayload = NULL, pendingRequest = NULL, pinDirty = 0, pinRevision = 0, revision = :revision, dirty = 0, syncError = NULL WHERE id = :id")
    suspend fun acceptDeletedEntry(id: String, revision: Int)

    @Query("SELECT * FROM walk_session ORDER BY startedAtMillis DESC")
    fun observeSessions(): kotlinx.coroutines.flow.Flow<List<WalkSessionRow>>

    @Query("SELECT mutationId FROM walk_entry ORDER BY id")
    fun observeEntryRevisions(): kotlinx.coroutines.flow.Flow<List<String>>

    @Query("DELETE FROM walk_session WHERE ownerId = :ownerId")
    suspend fun deleteOwnerSessions(ownerId: String)

    @Query("UPDATE walk_session SET ownerId = :ownerId, serverWalkId = :serverWalkId WHERE id = :id AND ownerId = ''")
    suspend fun restoreOwner(id: String, ownerId: String, serverWalkId: String)

    /**
     * IGNORE: 같은 세션 시작을 재전송해도 최초 시작 시각을 덮어쓰지 않는다.
     *
     * @return 새로 넣었으면 rowId, 이미 있어서 넘겼으면 -1. **아이를 붙일지 말지가
     *   이 값에 달렸다** — 이미 있는 세션에 나중 목록을 덧붙이면 처음에 데리고 나간
     *   아이가 아닌 아이가 그 산책에 섞인다.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(row: WalkSessionRow): Long

    /** IGNORE: 같은 아이를 두 번 붙여도 한 줄이다. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSessionDog(row: WalkSessionDogRow)

    @Query("SELECT * FROM walk_session_dog WHERE sessionId = :sessionId ORDER BY dogId")
    suspend fun sessionDogs(sessionId: String): List<WalkSessionDogRow>

    /**
     * 여러 산책의 아이들을 **한 번에** 읽는다.
     *
     * 목록을 그릴 때 산책마다 한 번씩 물으면 스무 건이면 스무 번 왕복한다.
     */
    @Query("SELECT * FROM walk_session_dog WHERE sessionId IN (:sessionIds) ORDER BY dogId")
    suspend fun sessionDogs(sessionIds: List<String>): List<WalkSessionDogRow>

    /** 같은 clientSeq를 다시 받으면 이미 저장한 원본을 유지한다. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFix(row: WalkFixRow)

    /** 같은 id를 다시 받아도 버튼 기록 하나만 유지한다. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAction(row: WalkActionRow)

    /** 열린 세션만 닫는다. 종료 요청을 반복해도 최초 종료 시각은 변하지 않는다. */
    @Query(
        "UPDATE walk_session SET endedAtMillis = :endedAtMillis " +
            "WHERE id = :sessionId AND endedAtMillis IS NULL",
    )
    suspend fun closeSession(sessionId: String, endedAtMillis: Long)

    @Query("DELETE FROM walk_session WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    /**
     * **그 아이와만** 나간 산책을 지운다. 강아지를 지울 때 부른다.
     *
     * 다른 아이와 같이 나간 산책은 안 지운다 — 그건 남은 아이의 기록이기도 해서,
     * 지우면 그 아이의 운동량이 통째로 빈다. 아무도 안 붙은 산책은 연결 줄이 없어
     * 여기 안 걸린다.
     *
     * 좌표는 외래키가 지운다.
     */
    @Query(
        "DELETE FROM walk_session WHERE id IN (" +
            "SELECT sessionId FROM walk_session_dog WHERE dogId = :dogId " +
            "AND sessionId NOT IN (" +
            "SELECT sessionId FROM walk_session_dog WHERE dogId <> :dogId))",
    )
    suspend fun deleteSessionsOnlyWith(dogId: String)

    /** 남은 산책에서 그 아이만 뗀다. 산책 자체는 남는다. */
    @Query("DELETE FROM walk_session_dog WHERE dogId = :dogId")
    suspend fun unlinkDog(dogId: String)

    /**
     * 이 기기의 산책을 **전부** 지운다. 탈퇴할 때 부른다.
     *
     * 서버는 탈퇴에서 개인정보를 파기하고 산책도 `ON DELETE CASCADE` 로 지우는데,
     * 폰의 Room 에는 원본 좌표가 그대로 남아 있었다. **산책 경로는 집과 생활권을
     * 그대로 드러낸다** — 그걸 두고 "계정을 지우면 데이터도 지운다" 고 할 수는 없다.
     *
     * 좌표와 강아지 연결은 외래키가 같이 지운다.
     */
    @Query("DELETE FROM walk_session")
    suspend fun deleteAllSessions()

    @Query("SELECT * FROM walk_session WHERE id = :sessionId")
    suspend fun session(sessionId: String): WalkSessionRow?

    @Query("SELECT * FROM walk_session WHERE endedAtMillis IS NULL ORDER BY startedAtMillis")
    suspend fun unfinishedSessions(): List<WalkSessionRow>

    /**
     * 끝난 산책만, 최근 것부터.
     *
     * **미종료 세션을 섞지 않는다.** 강제 종료로 열린 채 남은 세션이 목록에 끼면
     * "0m 짜리 산책"이 쌓인다 — 그건 기록이 아니라 사고의 흔적이다.
     */
    @Query(
        "SELECT * FROM walk_session WHERE endedAtMillis IS NOT NULL " +
            "ORDER BY startedAtMillis DESC",
    )
    suspend fun finishedSessions(): List<WalkSessionRow>

    /** Complete account-owned selection for records exploration, before UI pagination. */
    @Query("SELECT * FROM walk_session WHERE ownerId = :ownerId AND endedAtMillis IS NOT NULL " +
        "AND (:dogId IS NULL OR EXISTS (SELECT 1 FROM walk_session_dog d WHERE d.sessionId = walk_session.id AND d.dogId = :dogId)) " +
        "ORDER BY startedAtMillis DESC, id DESC")
    suspend fun finishedRecordSessions(ownerId: String, dogId: String?): List<WalkSessionRow>

    @Query("SELECT * FROM walk_session WHERE ownerId = :ownerId AND endedAtMillis IS NOT NULL " +
        "AND (:dogId IS NULL OR EXISTS (SELECT 1 FROM walk_session_dog d WHERE d.sessionId = walk_session.id AND d.dogId = :dogId)) " +
        "AND (:beforeAt IS NULL OR startedAtMillis < :beforeAt OR (startedAtMillis = :beforeAt AND id < :beforeId)) " +
        "ORDER BY startedAtMillis DESC, id DESC LIMIT :limit")
    suspend fun finishedSessionsPage(ownerId: String, dogId: String?, beforeAt: Long?, beforeId: String?, limit: Int): List<WalkSessionRow>

    /** 끝났지만 아직 계산 완료되지 않은 것. 오래된 것부터 이어서 처리한다. */
    @Query(
        "SELECT * FROM walk_session WHERE endedAtMillis IS NOT NULL " +
            "AND syncState <> 'derived' ORDER BY startedAtMillis",
    )
    suspend fun sessionsPendingAnalysis(): List<WalkSessionRow>

    @Query(
        "UPDATE walk_session SET syncState = 'raw_uploaded', serverWalkId = :serverWalkId, " +
            "syncedAtMillis = :changedAtMillis WHERE id = :sessionId",
    )
    suspend fun markRawUploaded(sessionId: String, serverWalkId: String, changedAtMillis: Long)

    @Query(
        "UPDATE walk_session SET syncState = 'derived', syncedAtMillis = :changedAtMillis " +
            "WHERE id = :sessionId AND syncState = 'raw_uploaded'",
    )
    suspend fun markDerived(sessionId: String, changedAtMillis: Long)

    /** 날씨는 세션을 연 뒤 따로 온다. 열린 세션이든 끝난 세션이든 한 번만 쓴다. */
    @Query(
        "UPDATE walk_session SET weatherCode = :weatherCode, isDay = :isDay, " +
            "temperatureC = :temperatureC WHERE id = :sessionId AND weatherCode IS NULL",
    )
    suspend fun stampWeather(
        sessionId: String,
        weatherCode: Int,
        isDay: Boolean,
        temperatureC: Float?,
    )

    @Query("SELECT * FROM walk_fix WHERE sessionId = :sessionId ORDER BY clientSeq")
    suspend fun fixes(sessionId: String): List<WalkFixRow>

    @Query(
        "SELECT * FROM walk_action WHERE sessionId = :sessionId " +
            "ORDER BY recordedAtMillis, id",
    )
    suspend fun actions(sessionId: String): List<WalkActionRow>
}
