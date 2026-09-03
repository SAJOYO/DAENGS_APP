package com.daengs.app.dogcard

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntRect
import com.daengs.app.ui.dogcard.CardTemplate
import java.time.ZoneId
import java.util.UUID
import kotlin.random.Random

/**
 * 뽑아 놓은 카드를 들고 있는 자리. `GaitHolder` 와 같은 결이다 —
 * `ViewModel` 을 안 쓰고 `mutableStateOf` 홀더를 `remember` 로 잡는다 (이 저장소의 패턴).
 *
 * **지금은 기기 안에만 있다.** 서버가 붙으면 [cards] 의 출처만 바뀐다. 화면은 이
 * 홀더만 보고, 카드를 어디서 받아 왔는지는 모른다.
 */
class CardHolder(private val store: CardStore) {

    /** 최근이 앞이다. 도감 칸의 표지가 가장 최근에 뽑은 것이 된다. */
    var cards: List<DrawnCard> by mutableStateOf(emptyList())
        private set

    /** 마지막으로 실패한 이유. 알려 주고 나면 화면이 [clearError] 한다. */
    var error: String? by mutableStateOf(null)
        private set

    fun clearError() {
        error = null
    }

    /** 오늘 몇 번 더 뽑을 수 있나. */
    fun drawsLeft(now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Int =
        drawsLeft(cards.map { it.drawnAtMillis }, now, zone)

    /**
     * 목록을 받아 온다.
     *
     * **실패해도 화면을 비우지 않는다.** 목록이 통째로 사라지면 사용자는 카드가
     * 지워진 줄 안다 (`GaitHolder.load` 와 같은 원칙).
     */
    suspend fun load(appUserId: String?) {
        runCatching { store.all(appUserId) }
            .onSuccess { cards = it }
            .onFailure { error = it.message ?: "카드를 불러오지 못했어요." }
    }

    /**
     * 한 장 뽑아 넣는다.
     *
     * **연출을 시작하기 전에 부른다.** 뒤집기를 먼저 돌리고 저장하면, 그 사이에
     * 프로세스가 죽었을 때 **뽑기 횟수만 쓰고 카드는 없는** 상태가 된다.
     *
     * @return 만들어진 카드. 실패하면 null 이고 이유는 [error] 에 남는다
     */
    suspend fun draw(
        template: CardTemplate,
        face: Bitmap?,
        core: IntRect,
        dogId: String?,
        dogName: String,
        codeText: String,
        appUserId: String?,
        /** 사용자가 원형 틀에 직접 맞췄나. `DrawnCardRow.userFramed` 참고 */
        userFramed: Boolean = false,
        now: Long = System.currentTimeMillis(),
    ): DrawnCard? {
        val card = DrawnCard(
            id = UUID.randomUUID().toString(),
            appUserId = appUserId,
            templateId = template.id,
            dogId = dogId,
            dogName = dogName,
            drawnAtMillis = now,
            codeText = codeText,
            core = core,
            userFramed = userFramed,
        )
        return runCatching { store.add(card, face) }
            .map { cards = listOf(card) + cards; card }
            .onFailure { error = it.message ?: "카드를 저장하지 못했어요." }
            .getOrNull()
    }

    /** 무엇을 뽑을지 고르고 [draw] 로 넘기는 것은 화면이 한다. 여기는 확률만 빌려준다. */
    fun pick(random: Random = Random.Default): CardTemplate = drawTemplate(random = random)

    suspend fun remove(id: String) {
        val before = cards
        cards = cards.filterNot { it.id == id }
        runCatching { store.remove(id) }.onFailure {
            cards = before
            error = it.message ?: "카드를 지우지 못했어요."
        }
    }

    /** 로그인 직후. 둘러보기로 뽑아 둔 카드를 그 계정 것으로 만든다. */
    suspend fun claimOrphans(appUserId: String) {
        runCatching { store.claimOrphans(appUserId) }
        load(appUserId)
    }

    /** 탈퇴. 되돌릴 수 없다. */
    suspend fun forgetEverything() {
        runCatching { store.forgetEverything() }
        cards = emptyList()
    }
}
