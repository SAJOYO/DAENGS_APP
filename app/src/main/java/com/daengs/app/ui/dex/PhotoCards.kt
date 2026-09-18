package com.daengs.app.ui.dex

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.daengs.app.dogcard.photo.PhotoCardKey

// ---------------------------------------------------------------------------
// 포토 카드 열넷 — 서버가 사진 한 장으로 그려 주는 달 카드 12장 + 종류 카드(딸기·상추)
//
// 원본은 `SAJOYO/DAENGS_dev` 의 `backend/src/daengs_cardimage/catalog.py` 다. 카드명은
// 제목판에 찍히는 영문 그대로다(9월은 원본 HARVEST MOON 이 판에 안 들어가 CHUSEOK,
// 딸기는 STRAWBERRY 가 아니라 BERRY 다).
//
// **그림은 앱에 없다.** 서버가 994×1582 완성 PNG 를 주고, 앱은 받아 둔 파일을 그린다.
// 잠긴 칸은 빈 판에 자물쇠다 (docs/photo-cards.md §2).
// ---------------------------------------------------------------------------

/**
 * 서버가 지금 만들어 주는 달. 저쪽 `DAENGS_CARDIMAGE_MONTHS` 기본값과 같다 —
 * 4·9월로 시작해 `DAENGS_dev` #572(2026-09-17 배포)에서 12달이 됐다.
 *
 * ⚠️ **서버에 달 목록 API 가 없다.** 저쪽이 달을 열거나 닫으면 여기 한 줄을 고친다. 닫힌 달을
 *    보내면 서버가 `404 month_closed` 문장을 주고, 앱은 그걸 그대로 띄운다.
 *
 * 종류 카드(딸기·상추)에는 이런 잠금이 **없다** — 저쪽 카탈로그에 있으면 열린 것이다
 * (D-085). 그래서 짝이 되는 `OPEN_PHOTO_KINDS` 를 두지 않고 [PhotoCardKey.KINDS] 를 그대로 쓴다.
 */
val OPEN_PHOTO_MONTHS: Set<Int> = (1..12).toSet()

/** 서버 카드 비율. 야채(3:4)보다 길쭉하다. */
const val PHOTO_RATIO = 994f / 1582f

/**
 * 포토 카드 한 달의 포일.
 *
 * **효과를 손볼 때 여기만 고친다** (사용자 결정 2026-09-15). 세기는 야채·과일과 같은
 * 포일별 값([webTune])에서 시작한다 — 예전에는 한꺼번에 0.22 로 눌러 두어 밋밋했다
 * (2026-09-18). 어느 달이 사진을 날리면 그 달만 `tune` 을 적어 누른다.
 */
@Immutable
data class PhotoFoil(val foil: Foil, val tune: FoilTune = foil.webTune)

/**
 * 종류 카드의 칸 번호. 달 12장 뒤에 카탈로그 순서대로 붙는다.
 *
 * **번호가 아니라 [PhotoCardKey] 가 정본이다** — 이 번호는 도감 칸 순서와 「No. 13 / 14」
 * 표시에만 쓴다. 저쪽이 종류를 늘리면 여기 한 줄이 늘고 뒷번호가 그만큼 밀린다.
 *
 * ⚠️ **[PHOTO_FOIL] 보다 위에 있어야 한다** — 그 표가 이 번호로 칸을 짓는다. 파일 안의 최상위
 *    값은 적힌 순서대로 초기화돼서, 아래로 내리면 `PHOTO_FOIL` 이 빈 맵을 읽는다.
 */
private val KIND_NO: Map<PhotoCardKey, Int> =
    PhotoCardKey.KINDS.mapIndexed { i, kind -> kind to 12 + i + 1 }.toMap()

/**
 * 칸 번호([DexCard.no])별 포일. 13·14 는 종류 카드다 — 저쪽 프롬프트가 그리는 것을 따라갔다:
 * 딸기는 별하늘에 금색 씨앗이 떠다녀 `Cosmos`, 상추는 무지개 홀로 광선이라 `Holo` 다.
 */
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
    KIND_NO.getValue(PhotoCardKey.Strawberry) to PhotoFoil(Foil.Cosmos),
    KIND_NO.getValue(PhotoCardKey.Lettuce) to PhotoFoil(Foil.Holo),
)

/**
 * 칸 id 는 카드 키에서 짓는다 — 달은 `photo-04`, 종류는 `photo-strawberry`.
 *
 * **종류를 번호로 짓지 않는다.** 저쪽이 종류를 중간에 끼워 넣어 번호가 밀리면 이미 가진
 * 카드가 다른 칸으로 옮겨 가는데, 키로 지으면 번호가 밀려도 칸은 그대로다.
 */
private fun photoSlotId(key: PhotoCardKey): String =
    key.month?.let { "photo-%02d".format(it) } ?: "photo-${key.raw}"

/** 도감이 아는 포토 카드 — 달 12장 다음에 종류가 카탈로그 순서대로. [PHOTO_CARDS] 와 같은 순서다. */
private val PHOTO_KEYS: List<PhotoCardKey> = (1..12).map(PhotoCardKey::of) + PhotoCardKey.KINDS

private val PHOTO_KEY_BY_ID: Map<String, PhotoCardKey> = PHOTO_KEYS.associateBy(::photoSlotId)

private fun photo(month: Int, name: String, ko: String, tagline: String, accent: Color) = photoCard(
    key = PhotoCardKey.of(month),
    no = month,
    name = name,
    ko = ko,
    tagline = tagline,
    edition = "${MONTH_EN[month - 1]} SPECIAL",
    accent = accent,
)

/** 종류 카드 한 장. `edition` 은 달의 「APRIL SPECIAL」 자리라 카드명을 쓴다. */
private fun kind(key: PhotoCardKey, name: String, ko: String, tagline: String, accent: Color) = photoCard(
    key = key,
    no = KIND_NO.getValue(key),
    name = name,
    ko = ko,
    tagline = tagline,
    edition = "$name SPECIAL",
    accent = accent,
)

private fun photoCard(
    key: PhotoCardKey,
    no: Int,
    name: String,
    ko: String,
    tagline: String,
    edition: String,
    accent: Color,
) = DexCard(
    no = no,
    id = photoSlotId(key),
    name = name,
    ko = ko,
    tagline = tagline,
    type = "PHOTO CARD",
    deck = DexDeck.Photo,
    move = name,
    statLabel = "",
    stat = 0,
    flavor = "",
    edition = edition,
    foil = PHOTO_FOIL.getValue(no).foil,
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
    // 종류 카드 (#593, D-085). 설명은 저쪽 `_KIND_CARDS` 의 프롬프트가 그리는 장면을 옮긴 것이다.
    // **얼굴만 넣는 카드다**(`face_only`) — 몸이 딸기·상추라 옷을 안 입힌다.
    kind(PhotoCardKey.Strawberry, "BERRY", "딸기", "잎사귀 낙하산을 타고 딸기 속을 날아오르는", Color(0xFFEF5A7A)),
    kind(PhotoCardKey.Lettuce, "LETTUCE", "상추", "물방울 맺힌 상춧잎에 폭 안긴", Color(0xFF7FC24A)),
)

fun photoCardFor(card: PhotoCardKey): DexCard? = PHOTO_CARDS.firstOrNull { it.id == photoSlotId(card) }

/** 칸이 가리키는 카드 키. 포토 칸이 아니면 null — 누끼 카드에는 키가 없다. */
val DexCard.photoKey: PhotoCardKey? get() = PHOTO_KEY_BY_ID[id]

val DexCard.isPhoto: Boolean get() = deck == DexDeck.Photo

/** 그리드 캡션 둘째 줄. 포토는 수치가 없다. */
val DexCard.gridCaption: String get() = if (isPhoto) ko else "$ko · $statLine"

/** 포토 설명 시트의 표. 기술·수치 대신 카드명과 닮음 점수(서버 검수 1~5). */
fun DexCard.photoDetailRows(likeness: Int?, total: Int = PHOTO_CARDS.size): List<DetailRow> = listOfNotNull(
    DetailRow("No.", "${no.toString().padStart(2, '0')} / ${total.toString().padStart(2, '0')}"),
    DetailRow("Card", name),
    likeness?.coerceIn(0, 5)?.let { DetailRow("Likeness", "★".repeat(it) + "☆".repeat(5 - it)) },
)
