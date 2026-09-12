package com.daengs.app.ui.game.owned

import com.daengs.app.activity.ActivityAuthenticationRequired
import com.daengs.app.activity.ActivitySessionChanged
import com.daengs.app.map.layers.territory.TerritoryMarkerOccupancy
import com.daengs.app.map.layers.territory.TerritorySiteMarkerState
import com.daengs.app.map.shell.BaseMapStyle
import com.daengs.app.map.shell.MapScene
import com.daengs.app.territory.ClaimCertification
import com.daengs.app.territory.owned.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class OwnedBrowserStatus { LOADING, READY, NO_SEASON, PREPARING, SIGN_IN, ERROR }

data class OwnedBrowserState(
    val status: OwnedBrowserStatus = OwnedBrowserStatus.LOADING,
    val items: List<OwnedTerritory> = emptyList(), val total: Int? = null,
    val seasonId: String? = null, val nextCursor: String? = null,
    val serverNowMillis: Long = 0, val receivedAtNanos: Long = 0,
    val loadingMore: Boolean = false, val message: String? = null,
) {
    fun nowMillis(nowNanos: Long): Long = serverNowMillis + ((nowNanos - receivedAtNanos).coerceAtLeast(0) / 1_000_000)
    fun visibleItems(nowNanos: Long): List<OwnedTerritory> = items.filter {
        it.expiresAtMillis == null || it.expiresAtMillis > nowMillis(nowNanos)
    }
}

/** One screen/filter owns this reader. Late requests cannot repopulate a refreshed collection. */
internal class OwnedTerritoryBrowser(
    private val scope: CoroutineScope, private val repository: OwnedTerritoryRepository,
    private val ownerId: String, private val petId: String?,
    private val nowNanos: () -> Long = System::nanoTime,
) {
    private val mutable = MutableStateFlow(OwnedBrowserState())
    val state = mutable.asStateFlow()
    private var generation = 0
    private var job: Job? = null
    private val seenCursors = mutableSetOf<String>()

    fun stop() { generation++; job?.cancel(); job = null }

    fun refresh(message: String? = null) {
        stop()
        seenCursors.clear()
        mutable.value = OwnedBrowserState(message = message)
        request(null)
    }

    fun loadMore() {
        val value = mutable.value
        if (value.status != OwnedBrowserStatus.READY || value.loadingMore) return
        val cursor = value.nextCursor ?: return
        mutable.value = value.copy(loadingMore = true, message = null)
        request(cursor)
    }

    private fun request(cursor: String?) {
        val version = generation
        job = scope.launch {
            val result = repository.page(ownerId, petId, cursor)
            if (version != generation) return@launch
            val old = mutable.value
            result.fold(onSuccess = { page ->
                if (cursor != null && page.seasonId != old.seasonId) {
                    refresh("시즌이 바뀌어 목록을 새로 불러왔어요.")
                    return@fold
                }
                if (page.nextCursor != null && (page.nextCursor == cursor || page.nextCursor in seenCursors)) {
                    failure(IllegalStateException("Repeated territory cursor"), cursor)
                    return@fold
                }
                cursor?.let(seenCursors::add)
                val merged = if (cursor == null) page.items else
                    (old.items.associateBy { it.siteId } + page.items.associateBy { it.siteId }).values.toList()
                mutable.value = OwnedBrowserState(
                    status = if (page.seasonId == null) OwnedBrowserStatus.NO_SEASON else OwnedBrowserStatus.READY,
                    items = merged, total = page.totalCount, seasonId = page.seasonId, nextCursor = page.nextCursor,
                    serverNowMillis = page.serverNowMillis, receivedAtNanos = nowNanos(), message = old.message,
                )
            }, onFailure = { error ->
                if (cursor != null && error is OwnedTerritoryHttpException && error.status == 409 && error.code == "season_changed") {
                    refresh("시즌이 바뀌어 목록을 새로 불러왔어요.")
                } else failure(error, cursor)
            })
        }
    }

    private fun failure(error: Throwable, cursor: String?) {
        val status = when {
            error is ActivityAuthenticationRequired || error is ActivitySessionChanged ||
                (error is OwnedTerritoryHttpException && error.status == 401) -> OwnedBrowserStatus.SIGN_IN
            error is OwnedTerritoryHttpException && error.code == "activity_disabled" -> OwnedBrowserStatus.PREPARING
            else -> OwnedBrowserStatus.ERROR
        }
        val message = when (status) {
            OwnedBrowserStatus.SIGN_IN -> "로그인 상태를 확인한 뒤 다시 열어 주세요."
            OwnedBrowserStatus.PREPARING -> "점령 게임을 준비하고 있어요."
            else -> "점령지를 불러오지 못했어요. 잠시 후 다시 시도해 주세요."
        }
        mutable.value = if (cursor != null && status == OwnedBrowserStatus.ERROR)
            mutable.value.copy(loadingMore = false, message = message)
        else OwnedBrowserState(status = status, message = message)
    }
}

/** Display-only scene; creating it cannot start tracking or submit a game action. */
internal fun ownedTerritoryScene(items: List<OwnedTerritory>, selectedId: String?): MapScene = MapScene(
    baseMapStyle = BaseMapStyle.TERRITORY_FOCUSED,
    allowRegionalOverview = true,
    territorySites = items.mapNotNull { site -> site.point?.let { point ->
        TerritorySiteMarkerState(site.siteId, point, selected = site.siteId == selectedId,
            occupancy = if (site.certification == ClaimCertification.VERIFIED) TerritoryMarkerOccupancy.VERIFIED
                else TerritoryMarkerOccupancy.UNVERIFIED,
            label = "${site.petName}의 점령지", occupancyKnown = true, isMine = true)
    } },
)

internal fun ownedRemaining(expiresAt: Long?, now: Long): String {
    if (expiresAt == null) return "유지 기한 정보 없음"
    val remaining = expiresAt - now
    if (remaining <= 0) return "유지 기간 종료"
    val minutes = (remaining / 60_000).coerceAtLeast(1)
    val hours = minutes / 60
    return when {
        hours >= 24 -> "${hours / 24}일 ${hours % 24}시간 남음"
        hours > 0 -> "${hours}시간 ${minutes % 60}분 남음"
        else -> "${minutes}분 남음"
    }
}
