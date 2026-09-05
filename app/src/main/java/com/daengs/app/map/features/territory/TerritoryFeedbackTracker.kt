package com.daengs.app.map.features.territory

import com.daengs.app.map.layers.territory.TerritoryFeedback
import com.daengs.app.map.layers.territory.TerritoryFeedbackKind
import com.daengs.app.territory.ClaimCertification
import com.daengs.app.territory.TerritoryClaimSite

/** ViewModel 생애 동안 상태 전환만 읽는다. 점령 규칙/거리/재시도 정책에는 쓰지 않는다. */
internal class TerritoryFeedbackTracker {
    private var session: String? = null
    private val previous = mutableMapOf<String, TerritoryClaimSite>()
    private val readySeen = mutableSetOf<String>()
    private var event: TerritoryFeedback? = null
    private var sequence = 0L
    private var observedTarget: String? = null
    private val serverMarksSeen = mutableSetOf<String>()
    private var initialized = false

    fun update(game: TerritoryGameState, sessionId: String?, visible: Boolean, nowNanos: Long): TerritoryFeedback? {
        if (session != sessionId) {
            session = sessionId; previous.clear(); readySeen.clear(); event = null; observedTarget = null
        }
        val target = game.target
        val newServerMark = game.confirmedMarkId?.let { serverMarksSeen.add(it) } == true && initialized
        initialized = true
        val before = target?.let { previous[it.site.id] }
        // 보이지 않거나 일시정지 중에 들어온 결과도 관찰한다. 나중에 성공으로 재생하지 않는다.
        game.sites.forEach { previous[it.site.id] = it.claim }
        if (!visible || !game.enabled || sessionId == null || game.phase != TerritoryWalkPhase.WALKING || target == null) {
            event = null
            observedTarget = null
            return null
        }
        if (event?.siteId != target.site.id || event?.progressAt(nowNanos) == 1f) event = null
        val occupancy = target.claim.occupancy
        val settled = observedTarget == target.site.id && before != null && before.occupancy != occupancy &&
            occupancy?.sourceSessionId == sessionId
        observedTarget = target.site.id
        val kind = when {
            newServerMark && game.confirmedMarkSiteId == target.site.id -> TerritoryFeedbackKind.MARKED
            settled && occupancy?.certification == ClaimCertification.VERIFIED -> TerritoryFeedbackKind.VERIFIED
            settled && occupancy?.certification == ClaimCertification.UNVERIFIED -> TerritoryFeedbackKind.MARKED
            (game.canMark || game.canPhotograph) && readySeen.add(target.site.id) -> TerritoryFeedbackKind.READY
            else -> null
        }
        // GPS 흔들림/일시정지/재선택으로 준비 효과를 반복하지 않는다. 새 산책에서 다시 한 번.
        if (kind != null) {
            readySeen.add(target.site.id)
            event = TerritoryFeedback(++sequence, target.site.id, kind, nowNanos)
        }
        if (event?.kind == TerritoryFeedbackKind.READY && !game.canMark && !game.canPhotograph) event = null
        return event
    }
}
