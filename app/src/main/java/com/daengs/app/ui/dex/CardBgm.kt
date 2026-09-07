package com.daengs.app.ui.dex

/**
 * 카드마다의 곡. **여기가 곡 목록의 원본이다.**
 *
 * 원래는 이머시브 장면(`ImmersiveScene.bgm`)이 들고 있었다. 그때는 곡이 있는 카드가
 * 곧 무대가 있는 카드라 같은 말이었는데, **이제 아니다** — 시금치·당근은 무대가 없고
 * 곡만 있다. 장면에 매달아 두면 곡을 붙이려고 무대를 만들어야 한다.
 *
 * 그래서 곡을 카드 id 에 직접 건다. 무대는 이 표를 **읽어다 쓴다** — 두 군데 적으면
 * 어긋난다 (턴테이블에는 있는데 무대에서는 조용한 카드가 생긴다).
 *
 * ## 키가 `no` 가 아니라 `id` 인 것이 지금은 필수다
 *
 * 도감이 두 벌이라 **번호가 겹친다** — No.01 이 야채에는 배추, 과일에는 사과다.
 * 번호를 키로 두면 사과 곡이 배추에도 붙는다. id 는 두 벌을 통틀어 유일하다.
 *
 * ## 값이 한 곡이 아니라 목록인 이유
 *
 * 망고에 곡이 둘이고, **뽑는 사람마다 그중 하나가 걸린다** ([bgmFor]). 한 사람에게는
 * 언제나 같은 곡이라 목록 모양은 안 바뀌고, 사람이 다르면 다른 곡이 나온다.
 *
 * 카드 장마다 고르지 않는 것이 요점이다 — 그러면 망고를 세 장 뽑은 사람의 턴테이블에
 * 망고가 세 줄로 뜬다. 사람 기준이면 그 사람에게 망고 곡은 하나뿐이다.
 */
val CARD_BGM: Map<String, List<String>> = mapOf(
    // 야채 — 도감 순서대로
    "cabbage" to listOf("neo-hologram/audio/cabbage.ogg"),
    "carrot" to listOf("neo-hologram/audio/carrot.ogg"),
    "spinach" to listOf("neo-hologram/audio/spinach.ogg"),
    "sweet-potato" to listOf("neo-hologram/audio/sweet-potato.ogg"),
    "tomato" to listOf("neo-hologram/audio/tomato.ogg"),
    "lettuce" to listOf("neo-hologram/audio/lettuce.ogg"),
    // 과일
    "apple" to listOf("neo-hologram/audio/apple.ogg"),
    "banana" to listOf("neo-hologram/audio/banana.ogg"),
    "mango" to listOf(
        "neo-hologram/audio/mango.ogg",
        "neo-hologram/audio/mango-2.ogg",
    ),
    "strawberry" to listOf("neo-hologram/audio/strawberry.ogg"),
    "watermelon" to listOf("neo-hologram/audio/watermelon.ogg"),
)

/**
 * 이 카드의 곡. 없으면 null 이고 턴테이블 목록에도 안 뜬다.
 *
 * 곡이 여럿인 카드는 [appUserId] 로 고른다. **같은 사람에게는 언제나 같은 곡이다** —
 * 열 때마다 굴리면 "내 망고 노래가 바뀌었는데?" 가 된다. 무대가 카드마다 다른 장면을
 * 만들 때 쓰는 [seedOf] 를 그대로 쓴다.
 *
 * @param appUserId 로그인 전이거나 게스트면 `null` 이다. 그때는 첫 곡으로 떨어진다 —
 *   **로그인하면 곡이 바뀔 수 있다는 뜻이다.** 곡을 카드에 저장해 두면 안 바뀌겠지만,
 *   그러려면 컬럼이 하나 늘고 이미 뽑아 둔 카드는 그 값이 비어 있다
 */
fun bgmFor(cardId: String, appUserId: String? = null): String? {
    val tunes = CARD_BGM[cardId] ?: return null
    if (tunes.size == 1 || appUserId == null) return tunes.first()
    // seedOf 는 음수가 나올 수 있다. `%` 만 쓰면 음수 인덱스로 터진다.
    return tunes[seedOf(appUserId).mod(tunes.size)]
}
