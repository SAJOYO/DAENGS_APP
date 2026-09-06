package com.daengs.app.dogcard

import com.daengs.app.ui.dogcard.CARD_TEMPLATES
import com.daengs.app.ui.dogcard.CardTemplate
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/** 하루에 뽑을 수 있는 장수. */
const val DAILY_DRAWS = 3

/**
 * 카드 한 장을 뽑는다. **스물다섯 종 균등이고 레어도도 천장도 없다.**
 *
 * 레어도를 안 두는 이유는 곡이 있는 카드가 아홉 장뿐이기 때문이다 — 그것들을 레어로
 * 묶으면 턴테이블을 구경도 못 하고 끝나는 사람이 생긴다. 균등이면 세 번 뽑아 74%,
 * 여섯 번에 93% 가 곡을 하나는 갖는다.
 *
 * **[random] 을 주입받는다.** 안 그러면 분포를 단위 테스트로 못 잡는다. 실제로 그
 * 사고가 날 뻔했다 — 자리를 비운 카드는 열두 장인데 코드는 두 장만 알아서, "12종
 * 균등"이라고 적힌 채 배추/고구마 반반이 될 뻔했다.
 */
fun drawTemplate(
    templates: List<CardTemplate> = CARD_TEMPLATES,
    random: Random = Random.Default,
): CardTemplate = templates[random.nextInt(templates.size)]

/**
 * 오늘 몇 번 더 뽑을 수 있나.
 *
 * **따로 카운터를 두지 않는다.** 뽑은 시각이 이미 카드마다 있으므로 오늘 자정 이후의
 * 카드를 세면 된다. SharedPreferences 에 숫자를 따로 얹으면, 뽑다가 프로세스가 죽었을
 * 때 **카운터는 늘었는데 카드는 없는** 상태가 생긴다.
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
