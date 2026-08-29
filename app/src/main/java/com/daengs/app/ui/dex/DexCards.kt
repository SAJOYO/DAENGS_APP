package com.daengs.app.ui.dex

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// 카드 12장 — `assets/neo-hologram/cards.mjs` 를 옮긴 것
//
// **여기 필드가 적은 이유**: 카드 그림(webp)에 프레임·제목·기술명·수치가 전부 구워져
// 있다. 웹판도 그림 위에 포일만 얹지 글자를 그리지 않는다. 그래서 우리가 들고 있어야
// 할 건 그리드 아래 설명과, 어떤 포일을 쓸지뿐이다.
//
// 저쪽이 카드를 늘리면 여기에 줄만 더한다. `cards.mjs` 가 원본이다.
// ---------------------------------------------------------------------------

/**
 * 누끼 팝아웃. 카드를 짚고 있는 동안 주인공이 카드 테두리 밖으로 떠오른다.
 *
 * **이머시브와 다른 것이다.** 이머시브는 배경 원화·틀·곡까지 한 벌이 필요하지만
 * 팝아웃은 누끼 하나와 그 자리([fit])면 된다. 저쪽도 `scene` 이 아니라 `pop` 이라는
 * 별도 필드로 갈라 뒀다 — `scene` 을 주면 원치 않게 이머시브 카드가 되기 때문이다.
 *
 * 이머시브 카드는 장면에서 쓰는 누끼를 그대로 재활용하므로 이 필드가 따로 없어도 된다.
 */
@Immutable
data class CardPop(val subject: String, val fit: ImmersiveScene.Fit)

@Immutable
data class DexCard(
    val no: Int,
    val id: String,
    /** 그리드 설명에 쓰는 이름 */
    val name: String,
    /** 예: `CRUNCH 820` 의 앞부분 */
    val statLabel: String,
    val stat: Int,
    val foil: Foil,
    /** 카드 뒤 글로우 색. 저쪽 `accent` */
    val accent: Color,
    /** 있으면 확대 뷰에서 짚는 동안 주인공이 떠오른다. */
    val pop: CardPop? = null,
) {
    /** `assets/` 안의 그림 경로 */
    val art: String get() = "neo-hologram/art/$id.webp"

    /** 그리드 설명 두 번째 줄. 저쪽은 `statLabel` 이 빈 카드가 하나 있다(토마토). */
    val statLine: String get() = if (statLabel.isBlank()) "$stat" else "$statLabel $stat"
}

/**
 * 카드 순서 = 도감 순서다. No.01 부터.
 *
 * **저쪽에서 `immersive` 인 카드는 여기서도 포일을 하나 골라 둔다.** 이머시브는 꾹
 * 눌러야 들어가는 별개 뷰이고(`IMMERSIVE_SCENES`), 그 전까지 그리드와 확대 뷰에서는
 * 여느 카드처럼 그려져야 하는데 [Foil] 에는 `immersive` 항목이 없기 때문이다.
 * No.01 배추는 [Foil.Prism], No.10 고구마는 원래 값이던 [Foil.Cosmos] 를 그대로 둔다.
 *
 * **포일과 이머시브는 서로 독립이다.** No.12 상추가 [Foil.Metal] 을 유지한 채
 * 이머시브를 갖는다 — 포일은 카드 위에 얹히는 겹이고 이머시브는 별개의 화면이라
 * 하나를 위해 다른 하나를 포기할 이유가 없다. 저쪽도 같은 판단을 했다.
 */
val DEX_CARDS: List<DexCard> = listOf(
    DexCard(1, "cabbage", "Cabbage Neo", "CRUNCH", 820, Foil.Prism, Color(0xFF8FD94A)),
    DexCard(2, "pepper", "Pepper Neo", "CRISP", 860, Foil.Prism, Color(0xFFFFD838)),
    DexCard(3, "eggplant", "Eggplant Neo", "GLOSS", 900, Foil.Crystal, Color(0xFFA86BFF)),
    DexCard(4, "carrot", "Carrot Neo", "SNAP", 830, Foil.Gold, Color(0xFFFF8A2B)),
    DexCard(5, "danhobak", "Danhobak Neo", "CRUNCH", 840, Foil.Oilslick, Color(0xFF7D9B46)),
    DexCard(6, "mushroom", "Mushroom Neo", "MYCELIUM MASH", 820, Foil.Sunburst, Color(0xFFCBB08A)),
    DexCard(7, "broccoli", "Broccoli Neo", "MYCELIUM MASH", 850, Foil.Holo, Color(0xFF7BBF3A)),
    DexCard(8, "cucumber", "Cucumber Neo", "MYCELIUM MASH", 810, Foil.Reverse, Color(0xFF4FAE52)),
    DexCard(9, "spinach", "Spinach Neo", "MYCELIUM MASH", 860, Foil.Aurora, Color(0xFF3F8F3F)),
    DexCard(10, "sweet-potato", "Sweet Potato Neo", "MYCELIUM MASH", 830, Foil.Cosmos, Color(0xFFA0656F)),
    DexCard(
        11, "tomato", "Tomato Neo", "", 840, Foil.Mosaic, Color(0xFFCC351A),
        // 이머시브가 아니다. 누끼 하나로 팝아웃만 한다.
        // fit 은 저쪽이 템플릿 매칭으로 찾은 값이다 — 받은 누끼가 카드 대비 세로로
        // 3.2% 눌려 있어 이미지를 늘려 비율을 맞춘 뒤 다시 쟀다.
        pop = CardPop(
            subject = "neo-hologram/art/tomato-subject.webp",
            fit = ImmersiveScene.Fit(2.21f, 15.64f, 97.18f, 66.93f),
        ),
    ),
    DexCard(12, "lettuce", "Lettuce Neo", "FRESH FLUTTER", 800, Foil.Metal, Color(0xFFB2D121)),
)
