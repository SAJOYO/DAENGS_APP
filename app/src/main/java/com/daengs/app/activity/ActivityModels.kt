package com.daengs.app.activity

import java.math.BigInteger
import java.util.UUID

/** DEV #281. 기간은 산책 종료 시각 기준 [fromMs, toMs), 단위는 epoch milliseconds. */
data class ActivityWalkWindow(val fromMs: Long, val toMs: Long, val petId: String? = null) {
    init {
        require(fromMs in 0..253402300799000L && toMs in 0..253402300799000L)
        require(toMs > fromMs && toMs - fromMs <= 366L * 86_400_000)
        petId?.let(::requireActivityUuid)
    }
}

internal fun requireActivityUuid(value: String) {
    require(UUID.fromString(value).toString() == value) { "Expected a canonical UUID" }
}

enum class ActivityLinkStatus { WAITING_FOR_WALK, WALK_ONLY, LINKED, CONFLICT }
enum class ActivityWalkStatus { PENDING, READY }
enum class ActivityTerritoryStatus { PENDING, READY, STALE }

data class ActivitySessionLink(
    val clientSessionId: String, val walkId: String?, val gameSessionId: String?,
    val status: ActivityLinkStatus, val conflicts: List<String>,
)
data class ActivityProjectionIdentity(val generationId: String, val statisticsVersion: String)
data class ActivityAnalysisVersions(val facts: Long, val calculation: Long, val receipt: Long, val capsule: Long)
data class ActivityWalkSource(val walkId: String, val analysisId: String, val revision: Long)
data class ActivityExclusion(val reason: String, val count: Long)

/** null 측정값, 제외 이유, 대기 건수, 원본 revision을 보존한다. PENDING도 부분 결과일 수 있다. */
data class ActivityWalkSummary(
    val identity: ActivityProjectionIdentity, val expectedVersions: ActivityAnalysisVersions,
    val ownerId: String, val petId: String?, val fromMs: Long, val toMs: Long,
    val recordedWalkCount: Long, val observedWalkCount: Long,
    val movingDistanceM: Long?, val movingS: Long?, val stopCount: Long?, val stopS: Long?,
    val avgSpeedMps: Double?, val exclusions: List<ActivityExclusion>,
    val sources: List<ActivityWalkSource>, val windowBasis: String,
    val pendingWalkCount: Long, val status: ActivityWalkStatus,
)
data class ActivityTerritoryStatistics(
    val statisticsVersion: String, val generationId: String,
    val coverageStartMs: Long, val confirmedThroughMs: Long,
    val acquisitionCount: Long, val takeoverCount: Long,
    val heldSiteMs: Long, val verifiedHeldSiteMs: Long,
    val ownedSiteCount: Long, val peakOwnedSiteCount: Long,
)

/** holdingUnits는 서버의 정수 누적 단위다. 부동소수점 점수로 바꾸거나 사실 통계를 역산하지 않는다. */
data class ActivityScore(
    val bonus: Long, val holdingUnits: BigInteger, val heldSiteMs: Long,
    val currentCount: Long, val scoringCount: Long, val peak: Long,
    val claims: Long, val takeovers: Long, val lastMs: Long,
)
data class ActivityHoldingSource(
    val periodId: String, val siteId: String, val claimId: String?, val gameSessionId: String?,
)
data class ActivityTerritorySummary(
    val seasonId: String, val petId: String, val status: ActivityTerritoryStatus,
    val sourceRevision: Long, val processedRevision: Long,
    val statistics: ActivityTerritoryStatistics?, val score: ActivityScore?, val scoreAsOfMs: Long?,
    val sources: List<ActivityHoldingSource>,
)
