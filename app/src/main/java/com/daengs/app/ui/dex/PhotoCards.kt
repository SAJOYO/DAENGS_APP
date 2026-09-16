package com.daengs.app.ui.dex

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// 포토 카드 열두 장 — 서버가 사진 한 장으로 그려 주는 달별 카드
//
// 원본은 `SAJOYO/DAENGS_dev` 의 `backend/src/daengs_cardimage/catalog.py` 다. 카드명은
// 제목판에 찍히는 영문 그대로다(9월은 원본 HARVEST MOON 이 판에 안 들어가 CHUSEOK).
//
// **그림은 앱에 없다.** 서버가 994×1582 완성 PNG 를 주고, 앱은 받아 둔 파일을 그린다.
// 잠긴 칸은 빈 판에 자물쇠다 (docs/photo-cards.md §2).
// ---------------------------------------------------------------------------

/**
 * 서버가 지금 만들어 주는 달. 저쪽 `DAENGS_CARDIMAGE_MONTHS` 기본값과 같다.
 *
 * ⚠️ **서버에 달 목록 API 가 없다.** 저쪽이 달을 열면 여기 한 줄을 고친다. 닫힌 달을
 *    보내면 서버가 `404 month_closed` 문장을 주고, 앱은 그걸 그대로 띄운다.
 */
val OPEN_PHOTO_MONTHS: Set<Int> = setOf(4, 9)

/** 서버 카드 비율. 야채(3:4)보다 길쭉하다. */
const val PHOTO_RATIO = 994f / 1582f

/**
 * 포토 카드 한 달의 포일.
 *
 * **효과를 손볼 때 여기만 고친다** (사용자 결정 2026-09-15 — 이번엔 기본값). 포토 카드는
 * 전면이 사진 같은 그림이라 야채 기본값(`shineOpacity` 0.30)보다 약하게 시작한다.
 */
@Immutable
data class PhotoFoil(val foil: Foil, val tune: FoilTune = PHOTO_TUNE)

/** 포토 기본 세기. 얼굴이 날아가지 않게 야채보다 낮다. */
val PHOTO_TUNE = FoilTune(shineOpacity = 0.22f, glareOpacity = 0.28f)

val PHOTO_FOIL: Map<Int, PhotoFoil> = mapOf(
    1 to PhotoFoil(Foil.Gold),
    2 to PhotoFoil(Foil.Prism),
    3 to PhotoFoil(Foil.Holo),
    4 to PhotoFoil(Foil.Prism),
    5 to PhotoFoil(Foil.Sunburst),
    6 to PhotoFoil(Foil.Oilslick),
    7 to PhotoFoil(Foil.Aurora),
    8 to PhotoFoil(Foil.Reverse),
    9 to PhotoFoil(Foil.Gold),
    10 to PhotoFoil(Foil.Cosmos),
    11 to PhotoFoil(Foil.Metal),
    12 to PhotoFoil(Foil.Crystal),
)

private fun photo(month: Int, name: String, ko: String, tagline: String, accent: Color) = DexCard(
    no = month,
    id = "photo-%02d".format(month),
    name = name,
    ko = ko,
    tagline = tagline,
    type = "PHOTO CARD",
    deck = DexDeck.Photo,
    move = name,
    statLabel = "",
    stat = 0,
    flavor = "",
    edition = "${MONTH_EN[month - 1]} SPECIAL",
    foil = PHOTO_FOIL.getValue(month).foil,
    accent = accent,
)

private val MONTH_EN = listOf(
    "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
    "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER",
)

val PHOTO_CARDS: List<DexCard> = listOf(
    photo(1, "NEW YEAR", "1월 새해", "새해 첫날 한복을 차려입은", Color(0xFFE0B04A)),
    photo(2, "LOVE", "2월 사랑", "하트를 한가득 안고 온", Color(0xFFF0607A)),
    photo(3, "FIRST DAY", "3월 첫날", "새 가방 메고 첫걸음을 뗀", Color(0xFF7BBF3A)),
    photo(4, "BLOSSOM", "4월 벚꽃", "벚꽃 아래 매트에 앉아 꽃잎을 올려다보는", Color(0xFFF4A6C0)),
    photo(5, "HOME TEAM", "5월 응원", "우리 팀 유니폼으로 목청을 높이는", Color(0xFF4F7FD9)),
    photo(6, "POOL", "6월 수영장", "튜브에 몸을 맡긴", Color(0xFF3FB8D9)),
    photo(7, "BEACH", "7월 바다", "모래사장에서 파도를 쫓는", Color(0xFFF2C14E)),
    photo(8, "RAIN", "8월 비", "우비를 쓰고 물웅덩이를 건너는", Color(0xFF6F8FAF)),
    photo(9, "CHUSEOK", "9월 추석", "한복 입고 송편 쟁반을 든", Color(0xFFE08A3C)),
    photo(10, "GHOST", "10월 유령", "이불 유령이 되어 사탕을 받으러 온", Color(0xFF8A6BCF)),
    photo(11, "THANKS", "11월 감사", "고마운 마음을 한 상 차린", Color(0xFFB5763A)),
    photo(12, "SANTA", "12월 산타", "산타 모자를 쓰고 선물을 나르는", Color(0xFFD9443A)),
)

fun photoCardFor(month: Int): DexCard? = PHOTO_CARDS.firstOrNull { it.no == month }

val DexCard.isPhoto: Boolean get() = deck == DexDeck.Photo

/** 그리드 캡션 둘째 줄. 포토는 수치가 없다. */
val DexCard.gridCaption: String get() = if (isPhoto) ko else "$ko · $statLine"

/** 포토 설명 시트의 표. 기술·수치 대신 카드명과 닮음 점수(서버 검수 1~5). */
fun DexCard.photoDetailRows(likeness: Int?, total: Int = PHOTO_CARDS.size): List<DetailRow> = listOfNotNull(
    DetailRow("No.", "${no.toString().padStart(2, '0')} / ${total.toString().padStart(2, '0')}"),
    DetailRow("Card", name),
    likeness?.coerceIn(0, 5)?.let { DetailRow("Likeness", "★".repeat(it) + "☆".repeat(5 - it)) },
)
