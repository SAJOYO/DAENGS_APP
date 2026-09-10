package com.daengs.app.territory

import com.daengs.app.auth.Session
import com.daengs.app.walk.TrackingState
import com.daengs.app.walk.WalkTrackingState
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** PHOTO rows share the ordered action journal; binding releases later pause/end operations. */
class ServerTerritoryPhotos(
    private val actions: TerritoryActionSync,
    private val api: TerritoryActionClient,
    private val uploader: TerritoryPhotoUploader,
    private val files: File,
    private val freshSession: suspend () -> Session?,
    private val currentOwner: () -> String?,
    private val tracking: () -> WalkTrackingState,
    private val scope: CoroutineScope,
    private val enqueue: suspend () -> Unit,
) {
    private val delivery = Mutex()
    private val saves = Mutex()
    private val liveCaptures = ConcurrentHashMap.newKeySet<String>()

    suspend fun reserve(expected: WalkTrackingState, mark: TerritoryOperation, capture: String, expectedVersion: Long? = null): String? {
        val startedNanos = System.nanoTime()
        val id = JSONObject(capture).getString("client_capture_id")
        try {
            val saved = actions.mutate { dao ->
                val now = tracking()
                if (currentOwner() != mark.ownerId || now.ownerId != mark.ownerId ||
                    now.activeSessionId != expected.activeSessionId || now.activeDogIds != expected.activeDogIds ||
                    now.trail.state != TrackingState.RECORDING) return@mutate false
                val rows = dao.all()
                val latest = rows.firstOrNull { it.identity == mark.identity && it.state == "CONFIRMED" } ?: return@mutate false
                val claim = parseTerritoryClaim(checkNotNull(latest.response), latest.body)
                if (claim.photoStatus == ClaimPhotoStatus.PENDING) return@mutate false
                if (expectedVersion == null && (claim.resolutionCode != null || claim.photoStatus !in setOf(ClaimPhotoStatus.NOT_SUBMITTED,
                        ClaimPhotoStatus.REJECTED, ClaimPhotoStatus.RETRY_PENDING))) return@mutate false
                if (rows.any { it.kind == "PHOTO" && it.ownerId == mark.ownerId && it.photoMarkIdentity() == mark.identity && it.photoActive() }) return@mutate false
                val body = JSONObject().put("mark_identity", mark.identity).put("claim_id", claim.claimId)
                    .put("capture", capture).toString()
                dao.insert(TerritoryOperation(identity = "photo:$id", ownerId = mark.ownerId, sessionId = mark.sessionId,
                    kind = "PHOTO", body = body, state = "CAPTURING")) > 0
            }
            currentCoroutineContext().ensureActive()
            if (!saved) return null
            if (expectedVersion != null) {
                val auth = freshSession()
                if (auth?.appUserId != mark.ownerId || currentOwner() != mark.ownerId) { cancelSaved(id); return null }
                val claim = parseTerritoryClaim(checkNotNull(mark.response), mark.body)
                try {
                    val response = JSONObject(api.request(auth.accessToken, "PUT", "/claims/${claim.claimId}/challenges/$id",
                        JSONObject().put("expected_site_version", expectedVersion).toString()))
                    require(response.getString("challenge_id") == id)
                    val current = tracking()
                    if (currentOwner() != mark.ownerId || current.ownerId != mark.ownerId ||
                        current.activeSessionId != expected.activeSessionId || current.activeDogIds != expected.activeDogIds ||
                        current.trail.state != TrackingState.RECORDING) { cancelSaved(id); return null }
                    // Capture metadata was sampled before admission. Do not shoot with an old
                    // sample after a slow auth/network roundtrip; server tolerates only 5s.
                    if (System.nanoTime() - startedNanos > 5_000_000_000L) {
                        cancelSaved(id, "challenge_expired")
                        throw TerritoryCaptureBlocked("challenge_expired")
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (blocked: TerritoryCaptureBlocked) { throw blocked }
                catch (error: TerritoryActionException) {
                    cancelSaved(id, error.code ?: "capture_failed")
                    throw TerritoryCaptureBlocked(error.code)
                }
                catch (_: Exception) { cancelSaved(id); return null }
            }
            liveCaptures += id
            return id
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { cancelSaved(id) }
            throw cancelled
        } catch (blocked: TerritoryCaptureBlocked) {
            throw blocked
        } catch (_: Exception) {
            cancelSaved(id)
            return null
        }
    }

    suspend fun checkAccess(mark: TerritoryOperation): Boolean {
        return try {
            val auth = freshSession() ?: return false
            if (auth.appUserId != mark.ownerId || currentOwner() != mark.ownerId) return false
            val claim = parseTerritoryClaim(checkNotNull(mark.response), mark.body)
            val value = JSONObject(api.request(auth.accessToken, "GET", "/claims/${claim.claimId}/photo-access", null))
            if (currentOwner() != mark.ownerId) return false
            if (value.getString("allowed_action") !in setOf("PHOTO_TAKEOVER", "PHOTO_UPGRADE"))
                throw TerritoryCaptureBlocked(value.optString("reason"))
            true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (blocked: TerritoryCaptureBlocked) { throw blocked }
        catch (_: Exception) { false }
    }

    /** Application-owned copy/commit survives a dismissed or recreated camera screen. */
    fun save(captureId: String, source: File): Deferred<Boolean> = scope.async {
        saves.withLock {
            try {
                val row = actions.rows().firstOrNull { it.identity == "photo:$captureId" && it.state == "CAPTURING" }
                    ?: return@withLock false
                withContext(Dispatchers.IO) {
                    require(source.isFile && source.length() in 1..MAX_TERRITORY_PHOTO_BYTES)
                    check(files.isDirectory || files.mkdirs())
                    val destination = file(captureId)
                    val partial = File(files, "$captureId.part")
                    FileOutputStream(partial).use { output ->
                        source.inputStream().use { input -> input.copyTo(output) }
                        output.fd.sync()
                    }
                    check(partial.renameTo(destination))
                }
                actions.mutate { it.update(row.copy(state = "PENDING")) }
                source.delete()
                wake()
                scope.launch {
                    try { actions.deliver(); deliverBound() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { wake() }
                }
                true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                File(files, "$captureId.part").delete()
                cancelSaved(captureId)
                false
            }
        }
    }

    fun cancel(captureId: String) { scope.launch { saves.withLock { cancelSaved(captureId) }; wake() } }
    private suspend fun cancelSaved(captureId: String, reason: String = "capture_failed") = actions.mutate { dao ->
        dao.all().firstOrNull { it.identity == "photo:$captureId" && it.state == "CAPTURING" }?.let {
            dao.update(it.copy(state = "REJECTED", failure = reason))
            file(captureId).delete()
        }
    }

    suspend fun recover() {
        actions.mutate { dao ->
            dao.all().filter { it.kind == "PHOTO" && it.state == "CAPTURING" }.forEach { row ->
                val complete = file(row.captureId()).isFile
                dao.update(row.copy(state = if (complete) "PENDING" else "REJECTED",
                    failure = if (complete) null else "capture_failed"))
            }
        }
        cleanTerminalFiles()
        files.listFiles()?.filter { it.name.matches(Regex("[0-9a-f-]{36}\\.part")) }?.forEach { it.delete() }
    }

    /** Called from the ordered action sender. Returns only after claim-photo binding is confirmed. */
    internal suspend fun bind(row: TerritoryOperation, token: String): String {
        val capture = row.captureBody()
        val ticket = parsePhotoTicket(api.request(token, "POST", "/attempts", capture), capture)
        checkOwner(row)
        val progress = row.photoProgress()
        if (progress.has("photo_id")) require(progress.getString("photo_id") == ticket.photoId)
        progress.put("photo_id", ticket.photoId).put("stage", "TICKET")
        actions.mutate { it.update(row.copy(response = progress.toString(), sent = true)) }
        val claimId = JSONObject(row.body).getString("claim_id")
        val response = api.request(token, "PUT", "/claims/$claimId/photos/${ticket.photoId}", null)
        val mark = markFor(row)
        val claim = parseTerritoryClaim(response, mark.body)
        require(claim.claimId == claimId)
        updateClaim(mark, response)
        val stage = when {
            claim.currentPhotoId != ticket.photoId -> "COMPLETE"
            ticket.status == "PENDING_UPLOAD" -> "BOUND"
            ticket.status == "VISION_PENDING" -> "CONFIRMING"
            else -> "POLLING"
        }
        return progress.put("stage", stage).toString()
    }

    /** BOUND photos can upload and settle after ENDED. No location or capture ID is regenerated. */
    suspend fun deliverBound(): Boolean = delivery.withLock {
        var complete = true
        for (snapshot in actions.rows().filter { it.kind == "PHOTO" && it.state == "CONFIRMED" && it.photoStage() != "COMPLETE" }) {
            if (snapshot.ownerId != currentOwner()) continue
            val auth = freshSession()
            if (auth == null || auth.appUserId != snapshot.ownerId || currentOwner() != snapshot.ownerId) { complete = false; continue }
            var row = snapshot
            try {
                if (row.photoStage() == "BOUND") {
                    val ticket = parsePhotoTicket(api.request(auth.accessToken, "POST", "/attempts", row.captureBody()), row.captureBody())
                    require(ticket.photoId == row.photoProgress().getString("photo_id"))
                    checkOwner(row)
                    if (ticket.status == "PENDING_UPLOAD") {
                        val original = file(row.captureId())
                        if (!original.isFile) throw TerritoryActionException(409, "photo_file_missing")
                        uploader.upload(checkNotNull(ticket.url), ticket.headers, original)
                        row = stage(row, "UPLOADED")
                    } else row = stage(row, if (ticket.status == "VISION_PENDING") "CONFIRMING" else "POLLING")
                }
                if (row.photoStage() in setOf("UPLOADED", "CONFIRMING")) {
                    checkOwner(row)
                    val response = api.request(auth.accessToken, "POST", "/attempts/${row.photoProgress().getString("photo_id")}/confirm", null)
                    val photo = parsePhotoTicket(response, row.captureBody())
                    require(photo.photoId == row.photoProgress().getString("photo_id"))
                    row = stage(row, "POLLING")
                }
                checkOwner(row)
                val mark = markFor(row)
                val claimId = JSONObject(row.body).getString("claim_id")
                val response = api.request(auth.accessToken, "GET", "/claims/$claimId", null)
                val claim = parseTerritoryClaim(response, mark.body)
                require(claim.claimId == claimId)
                updateClaim(mark, response)
                if (claim.currentPhotoId == row.photoProgress().getString("photo_id") && claim.photoStatus == ClaimPhotoStatus.PENDING) {
                    stage(row, "POLLING")
                    complete = false
                } else {
                    require(claim.currentPhotoId != row.photoProgress().getString("photo_id") ||
                        claim.photoStatus in setOf(ClaimPhotoStatus.VERIFIED, ClaimPhotoStatus.REJECTED, ClaimPhotoStatus.RETRY_PENDING))
                    stage(row, "COMPLETE", if (claim.currentPhotoId != row.photoProgress().getString("photo_id")) "superseded" else claim.resolutionCode)
                    file(row.captureId()).delete()
                    if (currentOwner() == row.ownerId) actions.publishPhotoReceipt(TerritoryMarkReceipt(mark, claim,
                        eventId = "$claimId:${row.captureId()}", animate = liveCaptures.remove(row.captureId()) &&
                            claim.currentPhotoId == row.photoProgress().getString("photo_id") && claim.resolutionCode == null && claim.photoStatus == ClaimPhotoStatus.VERIFIED))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (error is TerritoryActionException && error.code == "photo_not_uploaded") {
                    stage(row, "BOUND", "retry"); complete = false
                } else if (error is TerritoryActionException && error.status in setOf(400, 403, 404, 409, 422)) {
                    stage(row, "COMPLETE", error.code ?: "photo_failed")
                    file(row.captureId()).delete()
                } else if (error is TerritoryUploadException && error.status in setOf(400, 413, 415)) {
                    stage(row, "COMPLETE", "upload_rejected")
                    file(row.captureId()).delete()
                } else {
                    actions.mutate { it.update(row.copy(failure = "retry")) }
                    complete = false
                }
            }
        }
        cleanTerminalFiles()
        complete
    }

    private suspend fun stage(row: TerritoryOperation, stage: String, failure: String? = null): TerritoryOperation =
        row.copy(response = row.photoProgress().put("stage", stage).toString(), failure = failure).also { value -> actions.mutate { it.update(value) } }
    private suspend fun markFor(row: TerritoryOperation) = actions.rows().first { it.identity == row.photoMarkIdentity() && it.ownerId == row.ownerId }
    private suspend fun updateClaim(mark: TerritoryOperation, response: String) { actions.mutate { it.update(mark.copy(response = response)) } }
    private fun checkOwner(row: TerritoryOperation) { check(currentOwner() == row.ownerId) }
    private fun file(id: String) = File(files, "${canonicalUuid(id)}.jpg")
    private suspend fun wake() { try { enqueue() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { } }
    private suspend fun cleanTerminalFiles() {
        actions.rows().filter { it.kind == "PHOTO" && !it.photoActive() }.forEach { file(it.captureId()).delete() }
    }
}

internal fun TerritoryOperation.captureBody(): String = JSONObject(body).getString("capture")
internal fun TerritoryOperation.captureId(): String = JSONObject(captureBody()).getString("client_capture_id")
internal fun TerritoryOperation.photoMarkIdentity(): String = JSONObject(body).getString("mark_identity")
internal fun TerritoryOperation.photoProgress(): JSONObject = response?.let(::JSONObject) ?: JSONObject()
internal fun TerritoryOperation.photoStage(): String = photoProgress().optString("stage")
internal fun TerritoryOperation.photoActive(): Boolean = state in setOf("CAPTURING", "PENDING") || (state == "CONFIRMED" && photoStage() != "COMPLETE")
internal fun TerritoryOperation.photoGuidance(): String = when {
    failure == "protected" -> if (state == "CONFIRMED" && photoStage() == "COMPLETE") "사진 인증은 완료됐지만 보호 중이라 점령하지 못했어요 · 보호 종료 후 다시 도전해 주세요"
        else "인증된 영역 보호 중이에요 · 보호 종료 후 새 사진으로 도전해 주세요"
    failure == "already_certified" -> "이미 우리 강아지가 인증한 영역이에요"
    failure == "season_ended" -> "시즌이 끝나 점령하지 못했어요 · 다음 시즌을 확인해 주세요"
    failure == "new_session_required" -> "이 도전은 이전 규칙으로 처리됐어요 · 새 산책에서 도전해 주세요"
    failure == "challenge_expired" -> "촬영 접수 시간이 지났어요 · 현장에서 새 사진으로 도전해 주세요"
    failure == "challenge_required" -> "도전 접수를 다시 확인해 주세요 · 현장에서 새 사진으로 도전해 주세요"
    failure == "site_changed" || failure == "superseded" -> "다른 강아지가 먼저 점령했어요 · 보호 종료 후 다시 도전해 주세요"
    state == "CAPTURING" -> "인증 사진을 저장하고 있어요"
    photoActive() && failure != null -> "사진은 보관 중이에요 · 연결되면 다시 전송해요"
    state == "PENDING" -> "사진 인증을 접수하고 있어요"
    state == "CONFIRMED" && photoStage() == "COMPLETE" && failure != null -> "사진 전송을 복구할 수 없어요 · 새 산책에서 다시 시도해 주세요"
    state == "REJECTED" || failure != null -> "사진을 접수하지 못했어요 · 현장에서 다시 촬영해 주세요"
    photoStage() in setOf("BOUND", "UPLOADED", "CONFIRMING") -> "인증 사진 전송 중 · 산책을 계속해도 돼요"
    else -> "사진 확인 중 · 산책을 계속해도 돼요"
}

/** Only fixed, public policy messages reach the camera; server bodies are never displayed. */
class TerritoryCaptureBlocked(code: String?) : IllegalStateException(when (code) {
    "protected" -> "인증된 영역 보호 중이에요 · 보호가 끝난 뒤 다시 도전해 주세요"
    "already_certified" -> "이미 우리 강아지가 인증한 영역이에요"
    "site_changed" -> "점령 상태가 바뀌었어요 · 지도로 돌아가 다시 확인해 주세요"
    "season_ended", "policy_unavailable" -> "지금은 진행 중인 점령 시즌이 없어요"
    "challenge_expired" -> "촬영 접수 시간이 지났어요 · 다시 도전해 주세요"
    else -> "촬영 가능 상태를 확인하지 못했어요 · 잠시 후 다시 시도해 주세요"
})
