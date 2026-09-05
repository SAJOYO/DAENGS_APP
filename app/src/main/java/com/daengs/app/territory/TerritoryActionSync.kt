package com.daengs.app.territory

import com.daengs.app.auth.Session
import com.daengs.app.walk.TrackingState
import com.daengs.app.walk.WalkTrackingState
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class TerritoryMarkReceipt(val operation: TerritoryOperation, val claim: RemoteTerritoryClaim,
    val eventId: String = claim.claimId, val animate: Boolean = true)

/** App-owned durable journal, separate from both the ViewModel and completed-walk upload. */
class TerritoryActionSync(
    private val dao: TerritoryActionDao,
    private val api: TerritoryActionClient,
    private val freshSession: suspend () -> Session?,
    private val currentOwner: () -> String?,
    private val tracking: () -> WalkTrackingState,
    scope: CoroutineScope,
    private val enqueue: suspend () -> Unit,
) {
    private val writes = Mutex()
    private val delivery = Mutex()
    val operations = dao.observe().stateIn(scope, SharingStarted.Eagerly, emptyList())
    private val _receipt = MutableStateFlow<TerritoryMarkReceipt?>(null)
    val receipt = _receipt.asStateFlow()
    private val _storageFailed = MutableStateFlow(false)
    val storageFailed = _storageFailed.asStateFlow()
    var photos: ServerTerritoryPhotos? = null
        internal set
    internal suspend fun rows() = dao.all()
    internal suspend fun <T> mutate(block: suspend (TerritoryActionDao) -> T): T = writes.withLock { block(dao) }
    internal fun publishPhotoReceipt(receipt: TerritoryMarkReceipt) { _receipt.value = receipt }

    /** START_NOT_STICKY walks are not resumed by a new process. Close abandoned game sessions. */
    suspend fun recover() {
        try {
            photos?.recover()
            writes.withLock {
                val active = tracking().activeSessionId
                dao.all().groupBy { it.ownerId to it.sessionId }.values.forEach { rows ->
                    if (rows.first().sessionId != active) endIfOpen(rows)
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { _storageFailed.value = true }
        schedule()
    }

    suspend fun syncTracking() {
        try {
            val changed = writes.withLock {
                val before = dao.all().size
                recordTracking(tracking())
                dao.all().size != before
            }
            _storageFailed.value = false
            if (changed) schedule()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { _storageFailed.value = true }
    }

    private suspend fun recordTracking(state: WalkTrackingState) {
        val owner = currentOwner() ?: return
        val rows = dao.all().filter { it.ownerId == owner }
        rows.groupBy { it.sessionId }.values.filter { it.first().sessionId != state.activeSessionId }
            .forEach { endIfOpen(it) }
        val id = state.activeSessionId ?: return
        if (state.ownerId != owner || state.activeDogIds.isEmpty() || state.trail.state == TrackingState.OFF) return
        canonicalUuid(id)
        val startedAt = state.activeSessionStartedAtMillis ?: return
        var sessionRows = rows.filter { it.sessionId == id }
        if (sessionRows.isEmpty()) {
            val row = TerritoryOperation(identity = "$owner:$id:register", ownerId = owner, sessionId = id,
                kind = "REGISTER", body = sessionStartBody(startedAt, state.activeDogIds))
            dao.insert(row)
            sessionRows = listOf(row)
        }
        val desired = if (state.trail.state == TrackingState.PAUSED) "PAUSED" else "RECORDING"
        val last = lastPhase(sessionRows)
        if (last != desired && last != "ENDED") addPhase(owner, id, desired)
    }

    private fun lastPhase(rows: List<TerritoryOperation>) = rows.lastOrNull { it.kind == "PHASE" }
        ?.let { JSONObject(it.body).getString("phase") } ?: "RECORDING"

    private suspend fun endIfOpen(rows: List<TerritoryOperation>) {
        if (lastPhase(rows) != "ENDED") addPhase(rows.first().ownerId, rows.first().sessionId, "ENDED")
    }

    private suspend fun addPhase(owner: String, session: String, phase: String) = dao.insert(
        TerritoryOperation(identity = UUID.randomUUID().toString(), ownerId = owner, sessionId = session,
            kind = "PHASE", body = JSONObject().put("phase", phase).toString()))

    /** Returns after local commit. WorkManager owns network retries even if this caller disappears. */
    suspend fun submit(expected: WalkTrackingState, siteId: String, petId: String, body: String): String {
        val message = try {
            writes.withLock {
                val now = tracking()
                val owner = currentOwner()
                if (owner == null || now.ownerId != owner || expected.ownerId != owner ||
                    now.activeSessionId != expected.activeSessionId || now.trail.state != TrackingState.RECORDING ||
                    now.activeDogIds != expected.activeDogIds || petId !in now.activeDogIds)
                    return@withLock "산책 상태가 바뀌었어요 · 다시 확인해 주세요"
                recordTracking(now)
                val rows = dao.all().filter { it.ownerId == owner && it.sessionId == now.activeSessionId }
                if (rows.none { it.kind == "REGISTER" } || lastPhase(rows) == "ENDED")
                    return@withLock "게임 세션을 준비하고 있어요"
                val key = "$owner:${now.activeSessionId}:$siteId"
                val previous = rows.firstOrNull { it.identity == key }
                if (previous != null && !previous.canReplaceRejectedMark())
                    return@withLock if (previous.state == "CONFIRMED") "이번 산책에서 이미 영역표시한 장소예요" else "저장한 영역표시 결과를 확인하고 있어요"
                val row = TerritoryOperation(identity = key, ownerId = owner, sessionId = now.activeSessionId!!,
                    kind = "MARK", body = body)
                if (previous == null) dao.insert(row) else dao.replaceRejected(row.copy(sequence = previous.sequence))
                _storageFailed.value = false
                "영역표시를 저장했어요 · 서버 결과를 확인하고 있어요"
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { _storageFailed.value = true; "영역표시를 저장하지 못했어요 · 다시 시도해 주세요" }
        schedule()
        return message
    }

    private suspend fun schedule() {
        // A scheduling failure leaves committed rows for the next startup/foreground reconciliation.
        try { enqueue() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { }
    }

    /** False means pending transient work. Never send another account's outbox with the current token. */
    suspend fun deliver(): Boolean = delivery.withLock {
        val owner = currentOwner() ?: return@withLock true
        val blocked = mutableSetOf<String>()
        repeat(100) {
            if (currentOwner() != owner) return@withLock true
            val all = dao.all().filter { it.ownerId == owner }
            val row = all.firstOrNull { it.state in setOf("PENDING", "CAPTURING") && it.sessionId !in blocked }
                ?: return@withLock all.none { it.state in setOf("PENDING", "CAPTURING") }
            if (row.state == "CAPTURING") { blocked += row.sessionId; return@repeat }
            val registration = all.first { it.sessionId == row.sessionId && it.kind == "REGISTER" }
            if (registration.state == "REJECTED") {
                writes.withLock { dao.update(row.copy(state = "REJECTED", failure = "session_unavailable")) }
                return@repeat
            }
            val auth = freshSession()
            if (auth == null || auth.appUserId != owner || currentOwner() != owner) return@withLock false
            try {
                var sentRow = row
                val path = "/claim-sessions/${row.sessionId}"
                if (row.kind == "PHASE" && !JSONObject(row.body).has("expected_version")) {
                    val remote = parseClaimSession(api.request(auth.accessToken, "GET", path, null), row.sessionId, registration.body)
                    if (currentOwner() != owner) return@withLock true
                    val desired = JSONObject(row.body).getString("phase")
                    if (remote.phase == desired || remote.phase == "ENDED") {
                        writes.withLock { dao.update(row.copy(
                            state = if (remote.phase == desired) "CONFIRMED" else "REJECTED",
                            failure = if (remote.phase == desired) null else "session_ended")) }
                        return@repeat
                    }
                    sentRow = row.copy(body = JSONObject(row.body).put("expected_version", remote.version).toString())
                }
                // Commit before HTTP. Crash at either side of this line replays the same operation.
                writes.withLock { dao.update(sentRow.copy(sent = true, failure = null)) }
                if (currentOwner() != owner) return@withLock true
                if (row.kind == "PHOTO") {
                    val progress = checkNotNull(photos).bind(sentRow, auth.accessToken)
                    writes.withLock { dao.update(sentRow.copy(sent = true, state = "CONFIRMED", response = progress)) }
                    return@repeat
                }
                val response = api.request(auth.accessToken, when (row.kind) {
                    "REGISTER" -> "PUT"; "PHASE" -> "PATCH"; else -> "POST"
                }, if (row.kind == "MARK") "/claims" else path, sentRow.body)
                val claim = if (row.kind == "MARK") parseTerritoryClaim(response, sentRow.body) else {
                    val remote = parseClaimSession(response, row.sessionId, registration.body)
                    if (row.kind == "PHASE") require(remote.phase == JSONObject(sentRow.body).getString("phase"))
                    null
                }
                writes.withLock { dao.update(sentRow.copy(sent = true, state = "CONFIRMED", response = response)) }
                if (!row.sent && claim != null && currentOwner() == owner) _receipt.value = TerritoryMarkReceipt(sentRow, claim)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                writes.withLock {
                    val latest = dao.all().first { it.sequence == row.sequence }
                    when {
                        row.kind == "PHASE" && error is TerritoryActionException && error.code == "session_changed" -> {
                            // A rejected CAS is safe to replace after GET; never replace a mark's original evidence.
                            val body = JSONObject(latest.body).apply { remove("expected_version") }.toString()
                            dao.update(latest.copy(body = body, failure = "retry"))
                            blocked += row.sessionId
                        }
                        error is TerritoryActionException && error.status in listOf(400, 403, 404, 409, 422) ->
                            dao.update(latest.copy(state = "REJECTED", failure = error.code ?: "http_${error.status}"))
                        else -> {
                            dao.update(latest.copy(failure = if (error is TerritoryActionException && error.status == 401) "auth" else "retry"))
                            blocked += row.sessionId
                        }
                    }
                }
            }
        }
        false
    }
}

internal fun TerritoryOperation.canReplaceRejectedMark(): Boolean = kind == "MARK" && state == "REJECTED" &&
    failure in setOf("stale_location", "OUT_OF_RANGE", "UNTRUSTED_LOCATION", "site_not_nearby", "NOT_RECORDING")

internal fun TerritoryOperation.markGuidance(): String = when (state) {
    "CONFIRMED" -> when (parseTerritoryClaim(checkNotNull(response), body).disposition) {
        ClaimDisposition.GRANTED -> "영역표시가 접수됐어요 · 현재 점유는 지도에서 확인해요"
        ClaimDisposition.PHOTO_REQUIRED -> "인증된 영역이에요 · 탈취에는 사진 인증이 필요해요"
        ClaimDisposition.POLICY_UNDECIDED -> "이미 다른 강아지가 표시한 영역이에요"
        ClaimDisposition.ALREADY_OWNED -> "이미 우리 강아지의 영역이에요"
    }
    "REJECTED" -> if (canReplaceRejectedMark()) "접촉이 인정되지 않았어요 · 현재 위치에서 다시 시도해 주세요"
        else "영역표시를 진행할 수 없어요 · 산책과 참여견을 확인해 주세요"
    else -> if (failure == "auth") "로그인 상태를 다시 확인해 주세요 · 요청은 보관 중이에요"
        else if (failure != null) "연결되면 저장한 영역표시를 다시 확인해요" else "영역표시 확인 중 · 산책을 계속해도 돼요"
}
