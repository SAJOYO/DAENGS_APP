package com.daengs.app.gait

import androidx.compose.runtime.mutableStateListOf

/**
 * 끝난 줄 아는 보행 기록 하나.
 *
 * **`recordId` 만 들지 않는다.** 알림을 누른 시점에 대표 강아지가 바뀌어 있을 수 있고,
 * 그러면 엉뚱한 아이의 대화에 카드가 붙는다. 어느 아이의 것인지를 같이 들고 다녀야
 * 붙일 자리를 가릴 수 있다.
 *
 * 대화(conversation) 식별자는 안 쓴다 — 서버 대화 구조를 건드리지 않기로 했고,
 * 강아지 단위면 "남의 아이 카드가 붙는" 일은 막힌다.
 */
data class GaitCompletion(val petId: String?, val recordId: String)

/**
 * 앱이 켜져 있는 동안만 기억하는, **끝났다고 들은 보행 기록들**.
 *
 * ### 왜 필요한가
 *
 * 챗을 나갔다 들어오면 대화는 **서버 이력에서 다시 그려진다**
 * (`ChatScreen` 의 `restoredChatEntries`). 그런데 그 이력에는 보행 카드가 없다 —
 * 서버 대화에는 말풍선만 있고 결과 카드는 앱이 만든 것이라서다. 그래서 분석을 걸고
 * 챗을 나갔다가 알림을 받고 돌아오면, **완료된 결과를 다시 붙일 근거가 없었다.**
 *
 * 여기 담는 것은 `(petId, recordId)` 뿐이다. 대화 전체를 들고 있는 게 아니다 —
 * 말풍선은 어차피 서버가 주고, 카드를 그리는 데 필요한 기록 본문은
 * [GaitHolder.records] 가 서버에서 받아 온다. 이 목록은 **"무엇을 붙일지"** 만 말한다.
 *
 * ### 앱을 완전히 끄면 사라진다
 *
 * 일부러 그렇게 뒀다. 영속화하면 며칠 전 기록이 오늘 대화에 되살아난다. 프로세스가
 * 죽은 뒤의 복원은 이번 범위가 아니고, 그때도 **목록 화면에는 기록이 그대로 있다.**
 */
class GaitCompletions {
    private val items = mutableStateListOf<GaitCompletion>()

    /** 이 강아지 것만. 대표가 바뀌면 붙일 것도 달라진다. */
    fun forPet(petId: String?): List<GaitCompletion> =
        items.filter { it.petId == null || it.petId == petId }

    /**
     * 하나 기억한다. **같은 기록을 두 번 넣지 않는다** — 알림을 여러 번 누르거나
     * 화면이 다시 조합돼도 카드가 겹치면 안 된다.
     */
    fun remember(petId: String?, recordId: String) {
        if (items.none { it.recordId == recordId }) {
            items += GaitCompletion(petId, recordId)
        }
    }

    /**
     * 붙였으면 지운다.
     *
     * **안 지우면 다음에 챗에 들어갈 때마다 같은 카드가 또 붙는다.** 한 번 대화에
     * 올라간 뒤로는 그 대화가 들고 있으면 되고, 여기 남아 있을 이유가 없다.
     */
    fun consume(recordId: String) {
        items.removeAll { it.recordId == recordId }
    }
}
