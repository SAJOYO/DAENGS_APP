package com.daengs.app.map.features.territory

import com.daengs.app.auth.Session
import com.daengs.app.territory.*
import com.daengs.app.walk.TrackingState
import com.daengs.app.walk.WalkTrackingState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import org.json.JSONObject

private data class SharedBoard(
    val ownerId: String? = null,
    val sites: Map<String, SharedTerritorySite> = emptyMap(),
    val message: String = "점유 정보를 확인하고 있어요",
)

/** Shared read cache with an optional durable action boundary. Failures never become local grants. */
class ServerTerritoryGameProvider(
    private val api: TerritoryOccupancyClient,
    private val freshSession: suspend () -> Session?,
    private val currentOwner: () -> String?,
    private val actions: TerritoryActionSync? = null,
    private val wakeDelivery: suspend () -> Unit = {},
) : TerritoryGameProvider {
    override val onlinePhotos: Boolean get() = actions?.photos != null
    override val refreshesFromServer = true
    private val board = MutableStateFlow(SharedBoard())
    private var generation = 0L
    override val changes = if (actions == null) board.map { Unit } else merge(board.map { Unit },
        actions.operations.map { Unit }, actions.receipt.map { Unit }, actions.storageFailed.map { Unit })
    private val selectedPets = mutableMapOf<String, String>()
    private var acceptingReceipts = false
    private var ignoredReceipt: String? = actions?.receipt?.value?.eventId

    override fun invalidate() {
        acceptingReceipts = false
        generation++
        board.value = SharedBoard()
    }

    override suspend fun refresh(sites: List<TerritorySite>) {
        if (!acceptingReceipts) ignoredReceipt = actions?.receipt?.value?.eventId
        acceptingReceipts = true
        if (actions != null) {
            actions.syncTracking()
            try { wakeDelivery() } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Scheduling failure does not stop occupancy reads. */ }
        }
        val request = ++generation
        val owner = currentOwner()
        val ids = sites.map { it.id }.toSet()
        val previous = board.value.takeIf { it.ownerId == owner }?.sites.orEmpty().filterKeys { it in ids }
        board.value = SharedBoard(ownerId = owner, sites = previous)
        if (sites.isEmpty()) return
        try {
            val session = freshSession()
            currentCoroutineContext().ensureActive()
            if (request != generation || currentOwner() != owner) return
            if (session == null || owner.isNullOrBlank() || session.appUserId != owner) {
                board.value = SharedBoard(message = "로그인하면 점유 정보를 볼 수 있어요")
                return
            }
            val loaded = sites.map { it.id }.distinct().chunked(100).flatMap { ids ->
                api.fetch(session.accessToken, ids)
            }
            currentCoroutineContext().ensureActive()
            if (request != generation || currentOwner() != owner) return
            board.value = SharedBoard(owner, loaded.associateBy { it.siteId }, "점유 정보 · 둘러보기")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (request != generation || currentOwner() != owner) return
            val message = if (error is TerritoryOccupancyApiException && error.status == 401)
                "로그인 상태를 다시 확인해 주세요"
            else "점유 정보를 불러오지 못했어요 · 잠시 후 다시 확인해요"
            board.value = SharedBoard(ownerId = owner, message = message)
        }
        actions?.photos?.deliverBound()
    }

    override fun snapshot(board: TerritoryBoardState, tracking: WalkTrackingState, permitted: Boolean,
                          petNames: Map<String, String>, nowNanos: Long): TerritoryGameState {
        val cached = this.board.value.takeIf { it.ownerId == currentOwner() }
        val live = actions?.receipt?.value?.takeIf { it.operation.ownerId == currentOwner() }
        val sites = board.sites.map { site ->
            val read = cached?.sites?.get(site.id)
            val shared = live?.claim?.site?.takeIf { it.siteId == site.id && read != null && it.version > read.version } ?: read
            val occupied = shared?.occupancy
            val claim = TerritoryClaimSite(site.id, occupied?.let {
                TerritoryOccupancy(it.ownerPetId, null, null, it.certification, it.occupiedAtMillis)
            }, shared?.version ?: 0)
            TerritoryGameSite(site, claim, occupied?.ownerPetName.orEmpty(), null, null, false,
                occupancyKnown = shared != null)
        }
        val base = TerritoryGameState(enabled = true, readOnly = true,
            phase = when {
                tracking.activeSessionId == null || tracking.trail.state == TrackingState.OFF -> TerritoryWalkPhase.BROWSING
                tracking.trail.state == TrackingState.PAUSED -> TerritoryWalkPhase.PAUSED
                else -> TerritoryWalkPhase.WALKING
            },
            sites = sites, targetId = board.selectedSiteId,
            guidance = cached?.message ?: "로그인하면 점유 정보를 볼 수 있어요")
        if (actions == null || base.phase == TerritoryWalkPhase.BROWSING) return base
        return actionSnapshot(base, tracking, permitted, petNames, nowNanos)
    }

    private fun actionSnapshot(base: TerritoryGameState, tracking: WalkTrackingState, permitted: Boolean,
                               petNames: Map<String, String>, nowNanos: Long): TerritoryGameState {
        val owner = currentOwner()
        if (owner == null || tracking.ownerId != owner) return base.copy(guidance = "로그인 상태를 다시 확인해 주세요")
        val rows = actions!!.operations.value.filter { it.ownerId == owner && it.sessionId == tracking.activeSessionId }
        val attempt = rows.firstOrNull { it.kind == "MARK" && JSONObject(it.body).getString("site_id") == base.targetId }
        val lifecycle = rows.filter { it.kind in setOf("REGISTER", "PHASE") }
        val photo = rows.lastOrNull { it.kind == "PHOTO" && it.photoMarkIdentity() == attempt?.identity }
        val remoteClaim = attempt?.takeIf { it.state == "CONFIRMED" }?.let { parseTerritoryClaim(checkNotNull(it.response), it.body) }
        val locked = attempt != null && !attempt.canReplaceRejectedMark()
        val pet = attempt?.takeIf { locked }?.let { JSONObject(it.body).getString("claiming_pet_id") }
            ?: selectedPets["${tracking.activeSessionId}:${base.targetId}"]?.takeIf { it in tracking.activeDogIds }
            ?: tracking.activeDogIds.firstOrNull()
        val fix = tracking.latestMomentFix
        val age = fix?.elapsedRealtimeNanos?.let { nowNanos - it }
        val trusted = permitted && fix != null && !fix.isMock && tracking.lastSample?.isMock != true &&
            age != null && age in 0..10_000_000_000L && tracking.errorMessage == null &&
            tracking.trail.skippedTooFast == 0 && tracking.trail.skippedLowAccuracy == 0
        val sessionReady = rows.any { it.kind == "REGISTER" && it.state == "CONFIRMED" } &&
            lifecycle.none { it.state != "CONFIRMED" } &&
            lifecycle.lastOrNull()?.let {
                if (it.kind == "REGISTER") parseClaimSession(checkNotNull(it.response), it.sessionId, it.body).phase == "RECORDING"
                else JSONObject(it.body).getString("phase") == "RECORDING"
            } == true
        val sites = base.sites.map { target ->
            val distance = fix?.point?.distanceMetersTo(target.site.point)
            target.copy(distanceMeters = distance, attempted = rows.any { it.kind == "MARK" &&
                JSONObject(it.body).getString("site_id") == target.site.id && !it.canReplaceRejectedMark() },
                interaction = tracking.activeSessionId?.let { evaluateClaimAccess(it, target.site.id,
                    base.phase == TerritoryWalkPhase.WALKING, trusted && sessionReady,
                    distance ?: Double.NaN, fix?.accuracyMeters?.toDouble() ?: Double.NaN, 20.0) })
        }
        val target = sites.firstOrNull { it.site.id == base.targetId }
        val disposition = target?.let { unverifiedClaimDisposition(it.claim, pet.orEmpty()) }
        val canMark = base.phase == TerritoryWalkPhase.WALKING && sessionReady && !locked && pet != null &&
            target?.occupancyKnown == true && target.interaction?.access == ClaimAccess.READY && disposition == ClaimDisposition.GRANTED
        val photoRange = target?.distanceMeters?.let { it + (fix?.accuracyMeters?.toDouble() ?: Double.POSITIVE_INFINITY) <= 10.0 } == true
        val photoAllowed = remoteClaim?.resolutionCode == null && (remoteClaim == null || remoteClaim.photoStatus in
            setOf(ClaimPhotoStatus.NOT_SUBMITTED, ClaimPhotoStatus.REJECTED, ClaimPhotoStatus.RETRY_PENDING))
        val canPhotograph = onlinePhotos && base.phase == TerritoryWalkPhase.WALKING && sessionReady && trusted && photoRange &&
            pet != null && target?.occupancyKnown == true && photo?.photoActive() != true && photoAllowed &&
            (attempt == null || attempt.state == "CONFIRMED" || attempt.canReplaceRejectedMark()) &&
            !(disposition == ClaimDisposition.ALREADY_OWNED && target.claim.occupancy?.certification == ClaimCertification.VERIFIED)
        val live = actions.receipt.value?.takeIf { it.operation.ownerId == owner && it.operation.sessionId == tracking.activeSessionId }
        val confirmed = live?.claim?.takeIf { acceptingReceipts && live.animate && live.eventId != ignoredReceipt &&
            (it.disposition == ClaimDisposition.GRANTED || it.photoStatus == ClaimPhotoStatus.VERIFIED) &&
            it.site.occupancy?.isMine == true && it.site.occupancy.ownerPetId == JSONObject(live.operation.body).getString("claiming_pet_id") &&
            sites.any { target -> target.site.id == it.site.siteId && target.claim.version == it.site.version } }
        val guidance = when {
            base.phase == TerritoryWalkPhase.PAUSED -> "산책을 재개하면 영역표시할 수 있어요"
            remoteClaim?.resolutionCode == "site_changed" -> "점유가 바뀌었어요 · 새 산책에서 다시 방문해 주세요"
            photo != null && (photo.photoActive() || photo.state == "REJECTED" || photo.failure != null) -> photo.photoGuidance()
            remoteClaim?.photoStatus == ClaimPhotoStatus.VERIFIED -> "사진 인증이 완료됐어요"
            remoteClaim?.photoStatus == ClaimPhotoStatus.REJECTED -> "사진이 부적합해요 · 현장에서 다시 촬영해 주세요"
            remoteClaim?.photoStatus == ClaimPhotoStatus.RETRY_PENDING -> "사진 판정을 마치지 못했어요 · 현장에서 새 사진을 찍어 주세요"
            canPhotograph && attempt?.state == "CONFIRMED" -> "사진을 찍어 영역표시를 인증할 수 있어요"
            onlinePhotos && attempt?.state == "CONFIRMED" && photoAllowed && !photoRange -> "사진 인증은 GPS 오차를 포함해 10m 안에서 할 수 있어요"
            attempt != null && locked -> attempt.markGuidance()
            actions.storageFailed.value -> "게임 요청을 저장하지 못했어요 · 잠시 후 다시 확인해 주세요"
            pet == null -> "함께 걷는 강아지가 있어야 영역표시할 수 있어요"
            lifecycle.any { it.failure == "auth" } -> "로그인 상태를 다시 확인해 주세요 · 요청은 보관 중이에요"
            lifecycle.any { it.state == "REJECTED" } -> "게임 세션을 연결할 수 없어요 · 산책과 참여견을 확인해 주세요"
            !sessionReady -> "게임 세션을 연결하고 있어요"
            target?.occupancyKnown != true -> base.guidance
            !trusted -> "정확한 현재 위치를 확인하고 있어요"
            target.interaction?.access == ClaimAccess.APPROACHING -> "가까이 가면 영역표시할 수 있어요"
            target.interaction?.access != ClaimAccess.READY -> "GPS 오차가 줄어들면 영역표시할 수 있어요"
            disposition == ClaimDisposition.PHOTO_REQUIRED -> "인증된 영역이에요 · 탈취에는 사진 인증이 필요해요"
            disposition == ClaimDisposition.POLICY_UNDECIDED -> "이미 다른 강아지가 표시한 영역이에요"
            disposition == ClaimDisposition.ALREADY_OWNED -> "이미 우리 강아지의 영역이에요"
            attempt?.canReplaceRejectedMark() == true -> attempt.markGuidance()
            else -> "점령 준비 · 영역표시할 수 있어요"
        }
        return base.copy(readOnly = false, sites = sites, claimingPetId = pet,
            eligiblePets = tracking.activeDogIds.associateWith { petNames[it] ?: "강아지" }, petLocked = locked,
            representativeLabel = pet?.let { petNames[it] ?: "대표 강아지" }, canMark = canMark,
            guidance = guidance, canPhotograph = canPhotograph, onlinePhotos = onlinePhotos,
            photoStatus = if (photo?.state == "REJECTED") ClaimPhotoStatus.REJECTED else remoteClaim?.photoStatus,
            confirmedMarkId = live?.eventId.takeIf { confirmed != null }, confirmedMarkSiteId = confirmed?.site?.siteId,
            confirmedMarkVerified = confirmed?.site?.occupancy?.certification == ClaimCertification.VERIFIED)
    }

    override fun selectPet(petId: String, siteId: String, tracking: WalkTrackingState) {
        if (tracking.trail.state == TrackingState.RECORDING && petId in tracking.activeDogIds)
            selectedPets["${tracking.activeSessionId}:$siteId"] = petId
    }

    override suspend fun submitMark(siteId: String, board: TerritoryBoardState, tracking: WalkTrackingState,
        permitted: Boolean, petNames: Map<String, String>, nowNanos: Long, atMillis: Long): String {
        val current = snapshot(board.copy(selectedSiteId = siteId), tracking, permitted, petNames, nowNanos)
        if (!current.canMark || actions == null) return current.guidance
        val fix = checkNotNull(tracking.latestMomentFix)
        if (atMillis - fix.capturedAtMillis !in -5_000L..30_000L ||
            fix.capturedAtMillis < (tracking.activeSessionStartedAtMillis ?: Long.MAX_VALUE))
            return "정확한 현재 위치를 다시 확인해 주세요"
        return actions.submit(tracking, siteId, checkNotNull(current.claimingPetId),
            markBody(checkNotNull(tracking.activeSessionId), siteId, current.claimingPetId, fix))
    }

    override suspend fun prepareCapture(siteId: String, board: TerritoryBoardState, tracking: WalkTrackingState,
        permitted: Boolean, petNames: Map<String, String>, nowNanos: Long, atMillis: Long): TerritoryCaptureTarget? {
        val current = snapshot(board.copy(selectedSiteId = siteId), tracking, permitted, petNames, nowNanos)
        if (!current.canPhotograph || actions == null) return null
        val fix = tracking.latestMomentFix ?: return null
        if (atMillis - fix.capturedAtMillis !in -5_000L..30_000L || fix.capturedAtMillis < (tracking.activeSessionStartedAtMillis ?: Long.MAX_VALUE)) return null
        val pet = current.claimingPetId ?: return null
        val session = tracking.activeSessionId ?: return null
        actions.submit(tracking, siteId, pet, markBody(session, siteId, pet, fix))
        actions.deliver()
        val mark = actions.rows().firstOrNull { it.kind == "MARK" && it.ownerId == currentOwner() && it.sessionId == session && JSONObject(it.body).getString("site_id") == siteId && it.state == "CONFIRMED" } ?: return null
        val claim = parseTerritoryClaim(checkNotNull(mark.response), mark.body)
        if (claim.resolutionCode != null || claim.photoStatus !in setOf(ClaimPhotoStatus.NOT_SUBMITTED, ClaimPhotoStatus.REJECTED, ClaimPhotoStatus.RETRY_PENDING)) return null
        return TerritoryCaptureTarget(ClaimSession(session, mark.ownerId, JSONObject(mark.body).getString("claiming_pet_id")), siteId, claim.claimId)
    }

    override suspend fun beginCapture(target: TerritoryCaptureTarget, board: TerritoryBoardState, tracking: WalkTrackingState,
        permitted: Boolean, petNames: Map<String, String>, nowNanos: Long, atMillis: Long): String? {
        val current = snapshot(board.copy(selectedSiteId = target.siteId), tracking, permitted, petNames, nowNanos)
        if (!current.canPhotograph || actions?.photos == null || tracking.activeSessionId != target.session.clientSessionId ||
            currentOwner() != target.session.actorUserId || current.claimingPetId != target.session.claimingPetId) return null
        val mark = actions.rows().firstOrNull { it.kind == "MARK" && it.ownerId == target.session.actorUserId &&
            it.sessionId == target.session.clientSessionId && JSONObject(it.body).getString("site_id") == target.siteId && it.state == "CONFIRMED" } ?: return null
        if (parseTerritoryClaim(checkNotNull(mark.response), mark.body).claimId != target.serverClaimId) return null
        val id = java.util.UUID.randomUUID().toString()
        return actions.photos!!.reserve(tracking, mark, photoCaptureBody(id, mark.sessionId, target.siteId,
            target.session.claimingPetId, checkNotNull(tracking.latestMomentFix), atMillis))
    }

    override fun saveCapture(captureId: String, file: java.io.File) = actions?.photos?.save(captureId, file)
    override fun cancelCapture(captureId: String) { actions?.photos?.cancel(captureId) }
}
