package com.daengs.app.ui.dex

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// 카드 12장 — `assets/neo-hologram/cards.mjs` 를 옮긴 것
//
// 카드 그림(webp)에 프레임·제목·기술명·수치가 전부 구워져 있다. 그래서 **그리는 데**
// 필요한 건 포일과 색뿐이다. 한동안 필드가 그것뿐이었는데, 그러면서 **설명 시트가 같이
// 빠졌다** — 웹판에는 카드를 두 번 누르면 뜨는 상세가 있었다 (HISTORY 12절).
//
// 그림에 안 구워진 것은 **글**이다. 한글 이름·부제·코드·플레이버·에디션은 그림 어디에도
// 없어서 여기 없으면 화면에 못 띄운다. 그래서 저쪽 필드를 그대로 들고 있는다.
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
    /** 한글 이름. **그림에는 없다** — 설명 시트에서만 쓴다. */
    val ko: String,
    /** 한 줄 부제. 설명 시트 맨 위. */
    val tagline: String,
    val type: String,
    val move: String,
    /** 기술 부연. **없는 카드가 있다** — 저쪽에서 빈 문자열이면 여기서도 비운다. */
    val moveNote: String = "",
    /** 예: `CRUNCH 820` 의 앞부분 */
    val statLabel: String,
    val stat: Int,
    /** 영문 플레이버. 그림에 인쇄된 문장을 저쪽이 옮겨 적은 것이다. */
    val flavor: String,
    val edition: String,
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
 * 설명 시트의 한 줄. [note] 는 작은 글씨로 아래 붙는다 (저쪽 `<small>`).
 */
@Immutable
data class DetailRow(val label: String, val value: String, val note: String = "")

/**
 * 설명 시트의 표.
 *
 * 저쪽 `main.js` 의 `detailMarkup` 과 **같은 순서**다 — No. · Code · Type · Move ·
 * 스탯. 순서를 바꾸면 웹과 앱이 다른 카드처럼 보인다.
 *
 * **스탯 라벨이 빈 카드(No.11 토마토)는 `Stat` 으로 떨어진다.** 그 카드만 스탯 바에
 * 라벨이 안 찍혀 있어서 저쪽이 비워 뒀고, 웹도 같은 자리에 `Stat` 을 쓴다.
 */
fun DexCard.detailRows(
    total: Int = DEX_CARDS.size,
    /** 내 카드의 번호판. null 이면 카탈로그의 것을 쓴다 */
    /**
     * 번호판. **카탈로그에는 없다** — 예전에는 `NEO-0824` 를 박아 뒀는데, 저쪽 카드에
     * 인쇄돼 있던 값이라 우리 화면에 나올 이유가 없었다. 번호는 내 카드에만 있고
     * 아이 생일에서 만든다(`birthCode`). null 이면 그 줄이 아예 안 나온다.
     */
    code: String? = null,
): List<DetailRow> = listOfNotNull(
    DetailRow("No.", "${pad2(no)} / ${pad2(total)}"),
    code?.let { DetailRow("Code", it) },
    DetailRow("Type", type),
    DetailRow("Move", move, moveNote),
    DetailRow(statLabel.ifBlank { "Stat" }, stat.toString()),
)

/** 도감 번호는 두 자리다. 저쪽 `pad2` 와 같다. */
private fun pad2(value: Int): String = value.toString().padStart(2, '0')

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
    DexCard(
        no = 1, id = "cabbage", name = "Cabbage", ko = "배추",
        tagline = "강아지인지 채소인지 끝내 모를", type = "VEGGIE DOG",
        move = "LEAFY LOOK",
        moveNote = "Opponent stunned by awkward cuteness.",
        statLabel = "CRUNCH", stat = 820,
        flavor = "Part pup. Part produce. All confusion. Handle with salad.",
        edition = "Leafy Look Edition",
        foil = Foil.Prism, accent = Color(0xFF8FD94A),
    ),
    DexCard(
        no = 2, id = "pepper", name = "Pepper", ko = "피망",
        tagline = "노랗고 수상하게 강한", type = "VEGGIE DOG",
        move = "YELLOW SHOCK",
        statLabel = "CRISP", stat = 860,
        flavor = "Sweet face. Zero warning. Maximum pepper.",
        edition = "Prismatic Pepper Edition",
        foil = Foil.Prism, accent = Color(0xFFFFD838),
    ),
    DexCard(
        no = 3, id = "eggplant", name = "Eggplant", ko = "가지",
        tagline = "보라색으로 반들거리며 아무 생각 없는", type = "VEGGIE DOG",
        move = "NIGHT SHADE",
        statLabel = "GLOSS", stat = 900,
        flavor = "Deep purple. Empty thoughts. Unfairly glossy.",
        edition = "Night Shade Edition",
        foil = Foil.Crystal, accent = Color(0xFFA86BFF),
    ),
    DexCard(
        no = 4, id = "carrot", name = "Carrot", ko = "당근",
        tagline = "흙에서 막 나왔는데 과하게 차려입은", type = "VEGGIE DOG",
        move = "ROOT RUSH",
        statLabel = "SNAP", stat = 830,
        flavor = "Straight from the dirt. Still overdressed.",
        edition = "Root Rush Edition",
        foil = Foil.Gold, accent = Color(0xFFFF8A2B),
    ),
    DexCard(
        no = 5, id = "danhobak", name = "Danhobak", ko = "단호박",
        tagline = "껍질만 단단하고 속은 물렁한", type = "VEGGIE DOG",
        move = "SWEET IMPACT",
        statLabel = "CRUNCH", stat = 840,
        flavor = "Hard shell. Soft pup.",
        edition = "Hard Shell Edition",
        foil = Foil.Oilslick, accent = Color(0xFF7D9B46),
    ),
    DexCard(
        no = 6, id = "mushroom", name = "Mushroom", ko = "버섯",
        tagline = "나비넥타이까지 맨 포자 살포자", type = "VEGGIE DOG",
        move = "FUNGAL FACE",
        moveNote = "Mushroom master of confusing cuteness.",
        statLabel = "MYCELIUM MASH", stat = 820,
        flavor = "Part pup. Part fungi. Totally bizarre. Watch for spores.",
        edition = "Spore Bloom Edition",
        foil = Foil.Sunburst, accent = Color(0xFFCBB08A),
    ),
    DexCard(
        no = 7, id = "broccoli", name = "Broccoli", ko = "브로콜리",
        tagline = "왕관은 큰데 판단력은 작은", type = "VEGGIE DOG",
        move = "FLORET FORCE",
        moveNote = "Big crown. Tiny judgment.",
        statLabel = "MYCELIUM MASH", stat = 850,
        flavor = "Big crown. Tiny judgment.",
        edition = "Floret Force Edition",
        foil = Foil.Holo, accent = Color(0xFF7BBF3A),
    ),
    DexCard(
        no = 8, id = "cucumber", name = "Cucumber", ko = "오이",
        tagline = "거의 물인데 태도만은 확실한", type = "VEGGIE DOG",
        move = "COOL CRUNCH",
        moveNote = "Mostly water. Entirely attitude.",
        statLabel = "MYCELIUM MASH", stat = 810,
        flavor = "Mostly water. Entirely attitude.",
        edition = "Cool Crunch Edition",
        foil = Foil.Reverse, accent = Color(0xFF4FAE52),
    ),
    DexCard(
        no = 9, id = "spinach", name = "Spinach", ko = "시금치",
        tagline = "잎은 부드러운데 힘이 말이 안 되는", type = "VEGGIE DOG",
        move = "IRON LEAF",
        moveNote = "Soft leaf. Unreasonable power.",
        statLabel = "MYCELIUM MASH", stat = 860,
        flavor = "Part pup. Part fungi. Totally bizarre. Watch for spores.",
        edition = "Iron Leaf Edition",
        foil = Foil.Aurora, accent = Color(0xFF3F8F3F),
    ),
    DexCard(
        no = 10, id = "sweet-potato", name = "Sweet Potato", ko = "고구마",
        tagline = "깊이 묻혀 있다가 더 깊이 차려입고 나온", type = "VEGGIE DOG",
        move = "ROOT RUMBLE",
        moveNote = "Buried deep. Dressed deeper.",
        statLabel = "MYCELIUM MASH", stat = 830,
        flavor = "Buried deep. Dressed deeper.",
        edition = "Root Rumble Edition",
        foil = Foil.Cosmos, accent = Color(0xFFA0656F),
    ),
    DexCard(
        no = 11, id = "tomato", name = "Tomato", ko = "토마토",
        tagline = "잘 익고 둥글고 준비까지 끝난", type = "VEGGIE DOG",
        move = "JUICY BLAST",
        statLabel = "", stat = 840,
        flavor = "Ripe, round, and ready.",
        edition = "Juicy Blast Edition",
        foil = Foil.Mosaic, accent = Color(0xFFCC351A),
        // 이머시브가 아니다. 누끼 하나로 팝아웃만 한다.
        // fit 은 저쪽이 템플릿 매칭으로 찾은 값이다 — 받은 누끼가 카드 대비 세로로
        // 3.2% 눌려 있어 이미지를 늘려 비율을 맞춘 뒤 다시 쟀다.
        pop = CardPop(
            subject = "neo-hologram/art/tomato-subject.webp",
            fit = ImmersiveScene.Fit(2.21f, 15.64f, 97.18f, 66.93f),
        ),
    ),
    DexCard(
        no = 12, id = "lettuce", name = "Lettuce", ko = "상추",
        tagline = "잎은 제멋대로인데 웃음만 큰", type = "VEGGIE DOG",
        move = "LEAF PARADE",
        moveNote = "Loose leaves strut in a fresh breeze.",
        statLabel = "FRESH FLUTTER", stat = 800,
        flavor = "Loose leaves. Loud smile.",
        edition = "Leaf Parade Edition",
        foil = Foil.Metal, accent = Color(0xFFB2D121),
    ),
)
