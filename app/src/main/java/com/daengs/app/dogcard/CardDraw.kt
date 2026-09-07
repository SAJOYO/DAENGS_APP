package com.daengs.app.dogcard

import com.daengs.app.ui.dex.CARD_BGM
import com.daengs.app.ui.dex.IMMERSIVE_SCENES
import com.daengs.app.ui.dogcard.CARD_TEMPLATES
import com.daengs.app.ui.dogcard.CardTemplate
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/** 하루에 뽑을 수 있는 장수. */
const val DAILY_DRAWS = 3

/**
 * 한 판이 꽝일 확률 (백분율).
 *
 * **꽝도 하루 횟수를 쓴다.** 안 쓰면 결국 매일 세 장을 다 받게 되어 연출만 남는다.
 * 하루 평균 2.4장이고, 세 번이 다 꽝인 날은 0.8% 다.
 */
const val MISS_PERCENT = 20

/**
 * 뽑기 한 판의 결과.
 *
 * **[Miss] 가 있는 것이 요점이다.** 예전에는 이 자리가 그냥 [CardTemplate] 이라
 * "카드가 안 나왔다" 를 말할 수가 없었다. `null` 로 두면 실패(저장 오류)와 꽝이
 * 같은 값이 되어 화면이 둘을 못 가른다 — 하나는 사과할 일이고 하나는 놀릴 일이다.
 */
sealed interface DrawOutcome {
    /** 꽝. 카드는 안 생기고 하루 횟수만 줄어든다. */
    data object Miss : DrawOutcome

    /** 카드가 나왔다. */
    data class Got(val template: CardTemplate) : DrawOutcome
}

/**
 * 이 카드가 뽑기에서 갖는 무게. **클수록 자주 나온다.**
 *
 * 목록을 따로 적지 않고 곡·무대 표에 **물어본다.** 곡을 하나 붙이면 그 카드는 그날로
 * 레어가 된다 — 두 군데 적어 두면 곡은 늘렸는데 확률은 그대로인 채로 지나간다.
 */
fun weightOf(templateId: String): Int = when {
    templateId in IMMERSIVE_SCENES -> 1   // 무대까지 있는 세 장
    templateId in CARD_BGM -> 2           // 곡만 있는 여덟 장
    else -> 4                             // 나머지 열네 장
}

/**
 * 카드 한 판. **꽝 20% 이고, 나머지는 무게에 따라 갈린다.**
 *
 * ## 균등이 아니게 된 내력
 *
 * 예전에는 레어도를 **일부러 안 뒀다.** 그때 카드는 열두 장이고 곡이 아홉 장이라,
 * 곡 있는 카드를 레어로 묶으면 턴테이블을 구경도 못 하고 끝나는 사람이 생겼다.
 *
 * **스물다섯 장이 되면서 사정이 뒤집혔다.** 곡 있는 카드는 열한 장으로 비율이 절반
 * 아래로 내려갔고, 균등이면 무대까지 있는 카드도 그냥 카드와 똑같은 4% 라 뽑아도
 * 특별하지가 않다. 이제는 무게를 준다 — 무대 카드는 한 판에 1.1%, 세 번 뽑아
 * 곡을 하나라도 얻을 확률은 49% 다.
 *
 * **[random] 을 주입받는다.** 안 그러면 분포를 단위 테스트로 못 잡는다. 실제로 그
 * 사고가 날 뻔했다 — 자리를 비운 카드는 열두 장인데 코드는 두 장만 알아서, "12종
 * 균등"이라고 적힌 채 배추/고구마 반반이 될 뻔했다.
 */
fun drawOutcome(
    templates: List<CardTemplate> = CARD_TEMPLATES,
    random: Random = Random.Default,
): DrawOutcome =
    if (random.nextInt(100) < MISS_PERCENT) DrawOutcome.Miss
    else DrawOutcome.Got(drawTemplate(templates, random))

/**
 * 카드가 나왔을 때 **어느 카드인가.** 꽝은 여기서 다루지 않는다 ([drawOutcome]).
 *
 * 무게의 합만큼의 눈금에서 하나를 집고, 앞에서부터 무게를 빼며 걸리는 칸을 고른다.
 */
fun drawTemplate(
    templates: List<CardTemplate> = CARD_TEMPLATES,
    random: Random = Random.Default,
): CardTemplate {
    val weights = templates.map { weightOf(it.id) }
    var ticket = random.nextInt(weights.sum())
    templates.forEachIndexed { i, template ->
        ticket -= weights[i]
        if (ticket < 0) return template
    }
    // 눈금이 합보다 작으므로 여기까지 오지 않는다. 목록이 비면 위에서 이미 터진다.
    return templates.last()
}

/**
 * 오늘 몇 번 더 뽑을 수 있나.
 *
 * **따로 카운터를 두지 않는다.** 뽑은 시각이 이미 카드마다 있으므로 오늘 자정 이후의
 * 카드를 세면 된다. SharedPreferences 에 숫자를 따로 얹으면, 뽑다가 프로세스가 죽었을
 * 때 **카운터는 늘었는데 카드는 없는** 상태가 생긴다.
 *
 * ⚠️ **꽝은 카드가 없어서 여기 안 걸린다.** 그래서 부르는 쪽이 꽝의 시각까지 합쳐서
 *    넘긴다 (`CardHolder.drawsLeft`, `MissLog`). 이 함수는 "시각 목록"만 세는 것이지
 *    카드를 세는 것이 아니다 — 그 구분이 사라지면 꽝이 공짜가 된다.
 *
 * 하루의 경계는 **기기 시간대**다. `WalkHistory.todayTotals` 와 같은 규칙이다 —
 * 뽑는 사람의 하루가 기준이지 UTC 의 하루가 아니다.
 *
 * ⚠️ **기기 시계를 돌리면 뚫린다.** 이건 방어가 아니라 예의다. 진짜 제한은 서버가
 * 붙을 때 `POST /app/cards` 가 거절하는 것이고, 여기는 그 자리를 미리 만들어 둔 것이다.
 */
fun drawsLeft(
    drawnAtMillis: List<Long>,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
    limit: Int = DAILY_DRAWS,
): Int {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val used = drawnAtMillis.count { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == today }
    return (limit - used).coerceAtLeast(0)
}
