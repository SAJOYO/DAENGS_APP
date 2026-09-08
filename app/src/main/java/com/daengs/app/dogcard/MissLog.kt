package com.daengs.app.dogcard

import android.content.Context
import java.time.Instant
import java.time.ZoneId

/**
 * 꽝이 난 시각. **꽝이 하루 횟수를 쓰게 하려고 있다.**
 *
 * 뽑기 횟수는 저장된 카드의 시각을 세서 구하는데(`drawsLeft`), 꽝은 카드를 안 남기니
 * 그냥 두면 **꽝이 공짜가 된다** — 세 번 다 꽝이어도 여전히 세 번 남아 있어서, 결국
 * 매일 세 장을 다 받게 되고 꽝은 연출로만 남는다.
 *
 * ## 왜 카드 표에 꽝 줄을 넣지 않았나
 *
 * 얼굴 없는 카드 줄은 이미 뜻이 있다 — 예전 디버그 시드가 남긴 줄이고 `CardHolder.load` 가
 * 지운다(`DrawnCard.drawn`). 꽝을 그 모양으로 적으면 **읽을 때 같이 지워진다.**
 *
 * ## 왜 오늘 것만 두나
 *
 * 쓰는 데가 "오늘 몇 번 썼나" 뿐이라 어제 것은 아무도 안 본다. 읽을 때마다 오늘 것만
 * 남기므로 이 값은 최대 [DAILY_DRAWS] 개다 — 늘어나지 않는다.
 */
class MissLog(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("card-miss", Context.MODE_PRIVATE)

    /** 오늘 꽝이 난 시각들. 지난 날짜는 읽으면서 버린다. */
    fun today(now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): List<Long> {
        val kept = stored().filter { sameDay(it, now, zone) }
        if (kept.size != stored().size) write(kept)
        return kept
    }

    /** 꽝 하나를 적는다. */
    fun add(at: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()) {
        write(stored().filter { sameDay(it, at, zone) } + at)
    }

    /** 탈퇴·초기화 때 같이 지운다. */
    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun stored(): List<Long> =
        prefs.getString(KEY_TIMES, null)
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?: emptyList()

    private fun write(times: List<Long>) {
        prefs.edit().putString(KEY_TIMES, times.joinToString(",")).apply()
    }

    private companion object {
        const val KEY_TIMES = "times"

        fun sameDay(a: Long, b: Long, zone: ZoneId): Boolean =
            Instant.ofEpochMilli(a).atZone(zone).toLocalDate() ==
                Instant.ofEpochMilli(b).atZone(zone).toLocalDate()
    }
}
