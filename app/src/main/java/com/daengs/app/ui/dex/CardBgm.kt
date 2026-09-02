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
 */
val CARD_BGM: Map<String, String> = mapOf(
    "cabbage" to "neo-hologram/audio/cabbage.ogg",
    "carrot" to "neo-hologram/audio/carrot.ogg",
    "spinach" to "neo-hologram/audio/spinach.ogg",
    "sweet-potato" to "neo-hologram/audio/sweet-potato.ogg",
    "lettuce" to "neo-hologram/audio/lettuce.ogg",
)

/** 이 카드의 곡. 없으면 null 이고 턴테이블 목록에도 안 뜬다. */
fun bgmFor(cardId: String): String? = CARD_BGM[cardId]
