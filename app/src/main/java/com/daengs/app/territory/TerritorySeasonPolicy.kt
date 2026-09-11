package com.daengs.app.territory

const val FIRST_SEASON_POLICY = "first-season-rewards-v1"

internal fun String?.requiresTerritoryChallenge() =
    this == "certified-protection-v2" || this == FIRST_SEASON_POLICY

/** Use the server sample plus a monotonic interval, never the phone's wall clock. */
internal fun SharedTerritorySite.leaseRemainingMillis(nowNanos: Long): Long? {
    val expiry = occupancy?.expiresAtMillis ?: return null
    val sampled = serverNowMillis ?: return null
    val elapsed = ((nowNanos - receivedAtNanos).coerceAtLeast(0) / 1_000_000)
    return (expiry - sampled - elapsed).coerceAtLeast(0)
}

internal fun SharedTerritorySite.leaseLabel(nowNanos: Long): String? {
    val remaining = leaseRemainingMillis(nowNanos) ?: return null
    if (remaining == 0L) return "유지 시간 종료 · 점유 상태 확인 중"
    val minutes = (remaining + 59_999) / 60_000
    val duration = when {
        minutes >= 1440 -> "${minutes / 1440}일 ${(minutes % 1440) / 60}시간"
        minutes >= 60 -> "${minutes / 60}시간 ${minutes % 60}분"
        else -> "${minutes}분"
    }
    return "점령 유지 · $duration 남음"
}
