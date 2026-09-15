package com.daengs.app.dogcard.photo

import android.content.Context

/**
 * **결과를 아직 안 본 완성 카드** 의 id. 만들기 화면을 나가 기다린 사람에게 도감이 「완성됐어요」
 * 를 알리는 데 쓴다 (docs/photo-cards.md §8.3). 방에 있는 동안 완성돼도 다음에 도감을 열 때 알리려고
 * 기기에 남긴다.
 *
 * 홀더는 이 인터페이스만 본다 — JVM 테스트가 [MemoryRevealLog] 로 갈아 끼운다.
 */
interface PhotoRevealLog {
    fun load(): Set<String>
    fun save(ids: Set<String>)
}

class MemoryRevealLog(initial: Set<String> = emptySet()) : PhotoRevealLog {
    private var ids = initial
    override fun load(): Set<String> = ids
    override fun save(ids: Set<String>) { this.ids = ids }
}

/**
 * `SharedPreferences` 에 쉼표로 이어 둔다 (`MissLog` 와 같은 결). 로그아웃하면 홀더가 빈 집합을
 * 저장해 비운다 — 계정별 키를 따로 두지 않는다.
 */
class PrefsRevealLog(context: Context) : PhotoRevealLog {
    private val prefs = context.applicationContext.getSharedPreferences("photo-reveal", Context.MODE_PRIVATE)

    override fun load(): Set<String> =
        prefs.getString(KEY, null)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    override fun save(ids: Set<String>) {
        prefs.edit().putString(KEY, ids.joinToString(",")).apply()
    }

    private companion object { const val KEY = "unrevealed" }
}
