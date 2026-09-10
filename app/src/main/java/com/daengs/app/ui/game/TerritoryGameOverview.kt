package com.daengs.app.ui.game

import com.daengs.app.activity.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class GameOverviewStatus { LOADING, PREPARING, NO_SEASON, READY, ERROR, SIGN_IN, NO_PET, PETS_LOADING, PETS_ERROR }

data class TerritoryGameOverview(
    val status: GameOverviewStatus = GameOverviewStatus.LOADING,
    val season: ActivitySeason? = null,
    val summary: ActivityTerritorySummary? = null,
    val receivedAtNanos: Long = 0,
) {
    val aggregating: Boolean get() = summary?.status?.let { it != ActivityTerritoryStatus.READY } == true
    val points: String? get() = summary?.score?.let {
        gamePoints(holdingPoints(it).add(BigDecimal.valueOf(it.bonus)))
    } ?: if (summary?.status == ActivityTerritoryStatus.READY && summary.statistics?.acquisitionCount == 0L) "0" else null
    val owned: Long? get() = summary?.score?.currentCount ?: summary?.statistics?.ownedSiteCount
    val takeovers: Long? get() = summary?.statistics?.takeoverCount ?: summary?.score?.takeovers
    val message: String get() = when (status) {
        GameOverviewStatus.LOADING -> "시즌 성적을 불러오고 있어요"
        GameOverviewStatus.PREPARING -> "시즌 게임을 준비하고 있어요"
        GameOverviewStatus.NO_SEASON -> "진행 중인 시즌이 없어요"
        GameOverviewStatus.ERROR -> "시즌 성적을 불러오지 못했어요"
        GameOverviewStatus.SIGN_IN -> "로그인하면 우리 강아지의 성적을 볼 수 있어요"
        GameOverviewStatus.NO_PET -> "강아지를 등록하면 시즌 성적을 볼 수 있어요"
        GameOverviewStatus.PETS_LOADING -> "강아지 정보를 불러오고 있어요"
        GameOverviewStatus.PETS_ERROR -> "강아지 정보를 불러오지 못했어요"
        GameOverviewStatus.READY -> if (aggregating) "집계 중 · 확인된 성적부터 보여드려요" else "이번 시즌에 쌓은 점수예요"
    }
}

/** A fresh read replaces all fields together. No stale score is carried across dogs or seasons. */
suspend fun loadGameOverview(repository: ActivityRepository, petId: String,
                             nowNanos: () -> Long = System::nanoTime): TerritoryGameOverview {
    val seasonResult = repository.currentSeason()
    val sampled = nowNanos()
    seasonResult.exceptionOrNull()?.let { return TerritoryGameOverview(gameReadError(it)) }
    val season = seasonResult.getOrNull() ?: return TerritoryGameOverview(GameOverviewStatus.NO_SEASON)
    val result = repository.territorySummary(season.id, petId)
    return if (result.isSuccess) TerritoryGameOverview(GameOverviewStatus.READY, season, result.getOrThrow(), sampled)
    else TerritoryGameOverview(gameReadError(checkNotNull(result.exceptionOrNull())), season, receivedAtNanos = sampled)
}

private fun gameReadError(error: Throwable) = when {
    error is ActivityHttpException && error.code == "activity_disabled" -> GameOverviewStatus.PREPARING
    error is ActivityAuthenticationRequired || error is ActivitySessionChanged ||
        error is ActivityHttpException && error.statusCode == 401 -> GameOverviewStatus.SIGN_IN
    else -> GameOverviewStatus.ERROR
}

internal fun holdingPoints(score: ActivityScore): BigDecimal =
    BigDecimal(score.holdingUnits).divide(BigDecimal("36000000000"), 1, RoundingMode.DOWN)

internal fun gamePoints(value: BigDecimal): String = NumberFormat.getNumberInstance(Locale.KOREA).apply {
    maximumFractionDigits = 1
    roundingMode = RoundingMode.DOWN
}.format(value)

internal fun TerritoryGameOverview.seasonTitle(): String = season?.let {
    val month = Instant.ofEpochMilli(it.endsMs - 1).atZone(ZoneId.of("Asia/Seoul")).monthValue
    "${month}월 시즌"
} ?: "이번 시즌"

internal fun TerritoryGameOverview.seasonTime(nowNanos: Long): String {
    val season = season ?: return when (status) {
        GameOverviewStatus.PREPARING, GameOverviewStatus.NO_SEASON -> "다음 산책을 준비해요"
        else -> "시즌 정보를 확인해요"
    }
    val elapsed = (nowNanos - receivedAtNanos).coerceAtLeast(0) / 1_000_000
    val remaining = (season.endsMs - season.serverNowMs - elapsed).coerceAtLeast(0)
    if (remaining == 0L) return "시즌 종료 · 결산을 확인하고 있어요"
    val minutes = (remaining + 59_999) / 60_000
    val time = when {
        minutes >= 1440 -> "${minutes / 1440}일 ${(minutes % 1440) / 60}시간"
        minutes >= 60 -> "${minutes / 60}시간 ${minutes % 60}분"
        else -> "${minutes}분"
    }
    return "종료까지 $time"
}

internal fun scoreAsOf(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.of("Asia/Seoul"))
    .format(DateTimeFormatter.ofPattern("M.d HH:mm", Locale.KOREA))
