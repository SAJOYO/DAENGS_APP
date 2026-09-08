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
class CardHolder(
    private val store: CardStore,
    /**
     * 서버에도 알릴 때 쓰는 토큰. 없으면(로그인 전) **기기에서만** 지운다.
     *
     * ⚠️ **지우는 것은 그 자리에서 서버에도 알려야 한다.** 동기화가 뒤늦게 맞추는
     *    방식으로는 못 한다 — "기기에 없고 서버에 있다" 가 새 폰(받아 와야 함)인지
     *    삭제(지워야 함)인지 구분이 안 되기 때문이다 (`CardSync` 주석).
     */
    private val accessToken: suspend () -> String? = { null },
    /**
     * 꽝이 난 시각을 적어 두는 곳. **없으면(테스트·미리보기) 꽝이 횟수를 안 쓴다.**
     *
     * 카드 표에 안 넣는 이유는 [MissLog] 주석에 있다 — 얼굴 없는 줄은 이미 다른 뜻이라
     * 도감에 빈 카드가 뜬다.
     */
    private val missLog: MissLog? = null,
) {

    /** 최근이 앞이다. 도감 칸의 표지가 가장 최근에 뽑은 것이 된다. */
    var cards: List<DrawnCard> by mutableStateOf(emptyList())
        private set

    /** 마지막으로 실패한 이유. 알려 주고 나면 화면이 [clearError] 한다. */
    var error: String? by mutableStateOf(null)
        private set

    fun clearError() {
        error = null
    }

    /**
     * 오늘 몇 번 더 뽑을 수 있나.
     *
     * **카드 시각에 꽝 시각을 더해서 센다.** 꽝은 카드를 안 남기므로 카드만 세면
     * 꽝이 공짜가 된다 (`MissLog`).
     */
    fun drawsLeft(now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Int =
        drawsLeft(cards.map { it.drawnAtMillis } + misses, now, zone)

    /** 오늘 난 꽝. 화면이 다시 그려질 때 같이 읽히도록 상태로 든다. */
    var misses: List<Long> by mutableStateOf(missLog?.today().orEmpty())
        private set

    /**
     * 꽝 한 판을 적는다. **카드를 저장하는 자리와 같은 무게다** — 이걸 빼먹으면
     * 그 판은 없던 일이 되고 하루 세 번이 도로 살아난다.
     */
    fun recordMiss(at: Long = System.currentTimeMillis()) {
        missLog?.add(at)
        misses = misses + at
    }

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
            return
        }
        // 서버에도 알린다. **못 알려도 기기에서는 지운 채로 둔다** — 사용자가 지우라고
        // 한 것을 망 사정 때문에 되돌리면 안 된다. 서버에 남은 것은 다음에 그 카드를
        // 받아 오게 되는데, 그건 다시 지우면 된다.
        accessToken()?.let { token -> CardApi.delete(token, id) }
    }

    /** 로그인 직후. 둘러보기로 뽑아 둔 카드를 그 계정 것으로 만든다. */
    suspend fun claimOrphans(appUserId: String) {
        runCatching { store.claimOrphans(appUserId) }
        load(appUserId)
    }

    /** 탈퇴. 되돌릴 수 없다. */
    suspend fun forgetEverything() {
        missLog?.clear()
        misses = emptyList()
        runCatching { store.forgetEverything() }
        cards = emptyList()
    }
}
