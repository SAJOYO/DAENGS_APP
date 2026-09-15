# 도감 포토 탭 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 서버 `/app/ai-cards` 가 그린 달별 카드를 도감의 세 번째 탭 「포토」에 열두 칸으로 모으고, 만든 뒤에는 야채·과일 카드와 똑같이 작용하게 한다.

**Architecture:** 서버가 정본이다. `dogcard/photo/` 에 API·파일 보관·홀더를 두고, 도감 칸이 가진 카드를 `OwnedCard`(누끼 | 포토)로 넓혀 그리드·확대 뷰·설명 시트·저장·액자·지우기를 한 벌로 쓴다. 포토는 "그림 얻기" 만 다르다 — 조립 대신 받아 둔 PNG 한 장.

**Tech Stack:** Kotlin · Jetpack Compose · `HttpURLConnection` + `org.json` (기존 `CardApi` 와 같은 배관) · JUnit4 + `kotlinx-coroutines-test` (JVM 단위 테스트)

**Spec:** [`docs/photo-cards.md`](photo-cards.md) — 먼저 읽는다.

## Global Constraints

- 커밋 메시지는 **한글 서술형, 접두사 없음.** 제목은 무엇이 어떻게 잘못돼 있었는지(`~던 것`), 본문에 왜·무엇을 재봤는지. 끝에 두 줄:
  `Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>` / `Claude-Session: https://claude.ai/code/session_01Nz4skkb8UjiEWnbWj16iwf`
- 빌드·테스트 (Git Bash, **매 호출마다** export):
  `export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" && ./gradlew.bat :app:testDebugUnitTest --tests '<클래스>'`
  마지막엔 `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest`
- 파일 만들기·고치기는 Read/Edit/Write 도구로만 한다 (heredoc·sed 금지 — 한글이 깨진다).
- 화면 파일에 **새 날것 색(`Color(0x…)`)을 만들지 않는다** — `ui/theme` 의 `CardWhite · CreamBg · DaengPink · PinkFaint · TextDark · TextMuted` 와 `CardDexScreen.kt` 안에 이미 있는 값만 쓴다. 포토 카탈로그의 강조색 12개만 예외(카탈로그 데이터, `DexCards.kt` 의 `accent` 와 같은 자리).
- 새로 만들거나 고친 Composable 에 `@Preview`.
- `DesignLockTest` 가 깨지면 테스트를 고치지 말고 변경을 되돌린다.
- 기존 `DexCardsTest` · `DexSlotsTest` · `CardHolderTest` 는 **그대로 통과**해야 한다 (`DexSlotsTest` 의 `drawnAtMillis` 한 줄만 Task 4 에서 `madeAtMillis` 로 바뀐다).
- 화면 이름은 **포토**다. 코드 식별자는 `PhotoCard*`. 사용자에게 「AI 카드」라는 말을 띄우지 않는다.
- 연 달: `OPEN_PHOTO_MONTHS = setOf(4, 9)`. 조회 간격 5초, 오래된 생성 기준 10분.

## 파일 지도

| 파일 | 새/고침 | 책임 |
| --- | --- | --- |
| `app/src/main/java/com/daengs/app/ui/dex/DexCards.kt` | 고침 | `DexDeck.Photo` |
| `app/src/main/java/com/daengs/app/ui/dex/PhotoCards.kt` | 새 | 포토 열두 장 · 연 달 · 포일 표 · 설명 줄 · 비율 · 캡션 |
| `app/src/main/java/com/daengs/app/dogcard/photo/PhotoCard.kt` | 새 | 모델 · JSON 파싱 · 쿼리 · 오류 문장 · 실패 문장 |
| `app/src/main/java/com/daengs/app/dogcard/photo/PhotoCardApi.kt` | 새 | `PhotoCardRemote` 인터페이스 + HTTP 구현 |
| `app/src/main/java/com/daengs/app/dogcard/photo/PhotoCardFiles.kt` | 새 | 완성 PNG 보관 |
| `app/src/main/java/com/daengs/app/dogcard/photo/PhotoCardHolder.kt` | 새 | 목록 · 만들기 · 조회 반복 · 지우기 · 비우기 |
| `app/src/main/java/com/daengs/app/ui/dex/DexSlots.kt` | 고침 | `OwnedCard` · `dexSlots(…, photos, photoFiles)` |
| `app/src/main/java/com/daengs/app/ui/dex/CardArt.kt` | 고침 | `artSource` nullable · `OwnedCardArt` · `coverOf` |
| `app/src/main/java/com/daengs/app/ui/dex/CardDexScreen.kt` | 고침 | `OwnedCard` 로 한 벌 · 포토 빈 판 · 포토 머리말·만들기·실패 한 줄 |
| `app/src/main/java/com/daengs/app/ui/dex/PhotoCardMakeScreen.kt` | 새 | 달 · 강아지 · 사진 고르기 |
| `app/src/main/java/com/daengs/app/ui/dogcard/ComposedCard.kt` | 고침 | 액자 그림에 포토 파일 |
| `app/src/main/java/com/daengs/app/MainActivity.kt` | 고침 | 홀더 배선 · 조회 반복 · 액자 · 지우기 · 비우기 |
| 테스트 `app/src/test/java/com/daengs/app/ui/dex/PhotoCardsTest.kt` · `dogcard/photo/PhotoCardJsonTest.kt` · `dogcard/photo/PhotoCardHolderTest.kt` · `ui/dex/DexSlotsTest.kt`(더함) · `ui/dex/PhotoMakeDefaultsTest.kt` | | |

---

### Task 1: 포토 카탈로그

**Files:**
- Modify: `app/src/main/java/com/daengs/app/ui/dex/DexCards.kt:38-41`
- Create: `app/src/main/java/com/daengs/app/ui/dex/PhotoCards.kt`
- Test: `app/src/test/java/com/daengs/app/ui/dex/PhotoCardsTest.kt`

**Interfaces:**
- Produces: `DexDeck.Photo` · `PHOTO_CARDS: List<DexCard>` · `OPEN_PHOTO_MONTHS: Set<Int>` · `PhotoFoil(foil, tune)` · `PHOTO_FOIL: Map<Int, PhotoFoil>` · `photoCardFor(month: Int): DexCard?` · `val DexCard.isPhoto: Boolean` · `val DexCard.gridCaption: String` · `fun DexCard.photoDetailRows(likeness: Int?, total: Int = PHOTO_CARDS.size): List<DetailRow>` · `const val PHOTO_RATIO = 994f / 1582f`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```kotlin
package com.daengs.app.ui.dex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 포토 열두 장이 서버 카탈로그(`daengs_cardimage/catalog.py`)와 어긋나지 않게 잡는다. */
class PhotoCardsTest {

    @Test
    fun `열두 달이 한 장씩 순서대로다`() {
        assertEquals((1..12).toList(), PHOTO_CARDS.map { it.no })
        assertTrue(PHOTO_CARDS.all { it.deck == DexDeck.Photo })
        assertEquals("photo-04", PHOTO_CARDS[3].id)
    }

    /** 제목판 카드명은 서버 것 그대로다. 9월은 사용자가 CHUSEOK 으로 정했다. */
    @Test
    fun `카드명이 서버와 같다`() {
        assertEquals(
            listOf("NEW YEAR", "LOVE", "FIRST DAY", "BLOSSOM", "HOME TEAM", "POOL",
                "BEACH", "RAIN", "CHUSEOK", "GHOST", "THANKS", "SANTA"),
            PHOTO_CARDS.map { it.name },
        )
    }

    @Test
    fun `야채 과일 id 와 겹치지 않는다`() {
        val ids = (DEX_CARDS + PHOTO_CARDS).map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `연 달은 4월과 9월이다`() {
        assertEquals(setOf(4, 9), OPEN_PHOTO_MONTHS)
    }

    /** 효과를 나중에 손볼 자리. 한 달이라도 비면 그 달만 포일이 없다. */
    @Test
    fun `포일 표가 열두 달을 다 가진다`() {
        assertEquals((1..12).toSet(), PHOTO_FOIL.keys)
        PHOTO_CARDS.forEach { assertEquals(PHOTO_FOIL.getValue(it.no).foil, it.foil) }
    }

    @Test
    fun `달로 카드를 찾는다`() {
        assertEquals("BLOSSOM", photoCardFor(4)?.name)
        assertNull(photoCardFor(13))
    }

    @Test
    fun `포토 설명 줄은 번호 카드명 닮음이다`() {
        val rows = photoCardFor(4)!!.photoDetailRows(likeness = 4)
        assertEquals(listOf("No.", "Card", "Likeness"), rows.map { it.label })
        assertEquals("04 / 12", rows[0].value)
        assertEquals("BLOSSOM", rows[1].value)
        assertEquals("★★★★☆", rows[2].value)
    }

    @Test
    fun `닮음을 모르면 그 줄이 빠진다`() {
        assertEquals(listOf("No.", "Card"), photoCardFor(9)!!.photoDetailRows(likeness = null).map { it.label })
    }

    @Test
    fun `포토 캡션은 달 이름뿐이고 야채는 수치까지다`() {
        assertEquals("4월 벚꽃", photoCardFor(4)!!.gridCaption)
        assertEquals("배추 · CRUNCH 820", DEX_CARDS.first().gridCaption)
        assertTrue(photoCardFor(4)!!.isPhoto)
    }
}
```

- [ ] **Step 2: 돌려서 실패를 본다**

Run: `export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" && ./gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.ui.dex.PhotoCardsTest'`
Expected: 컴파일 실패 — `PHOTO_CARDS` 없음

- [ ] **Step 3: `DexDeck` 에 포토를 더한다** — `DexCards.kt` 의 enum 을 이렇게:

```kotlin
enum class DexDeck(val label: String) {
    Veggie("야채"),
    Fruit("과일"),
    /** 서버가 사진 한 장으로 그려 준 달별 카드 (`PhotoCards.kt`). */
    Photo("포토"),
}
```

- [ ] **Step 4: `PhotoCards.kt` 를 만든다**

```kotlin
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
```

- [ ] **Step 5: 테스트가 통과하는지 본다**

Run: `export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" && ./gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.ui.dex.PhotoCardsTest' --tests 'com.daengs.app.ui.dex.DexCardsTest'`
Expected: PASS (DexCardsTest 도 — `DexDeck.entries` 루프에서 포토는 `DEX_CARDS` 에 없어 빈 목록이다)

- [ ] **Step 5b: 컴파일을 지킨다** — `CardDexScreen.kt` 의 `DexHeader` 제목 `when (deck)` 은 값으로 쓰여서 `Photo` 갈래가 없으면 **컴파일이 깨진다.** `DexDeck.Fruit -> "과일이 된 우리 아이"` 아래에 `DexDeck.Photo -> "포토 속 우리 아이"` 를 더하고 `./gradlew.bat :app:assembleDebug` 로 확인한다. 다른 `when (deck)` 이 있으면 (`grep -rn "DexDeck\." app/src/main`) 같이 채운다.

- [ ] **Step 6: 커밋**

```bash
git add app/src/main/java/com/daengs/app/ui/dex/DexCards.kt app/src/main/java/com/daengs/app/ui/dex/PhotoCards.kt app/src/main/java/com/daengs/app/ui/dex/CardDexScreen.kt app/src/test/java/com/daengs/app/ui/dex/PhotoCardsTest.kt
git commit -m "도감에 서버가 그린 달별 카드를 둘 벌이 없던 것 — 포토 열두 장과 연 달 표를 적는다 …"
```

---

### Task 2: 포토 카드 모델 · JSON · HTTP

**Files:**
- Create: `app/src/main/java/com/daengs/app/dogcard/photo/PhotoCard.kt`
- Create: `app/src/main/java/com/daengs/app/dogcard/photo/PhotoCardApi.kt`
- Test: `app/src/test/java/com/daengs/app/dogcard/photo/PhotoCardJsonTest.kt`

**Interfaces:**
- Produces:
  - `enum class PhotoCardStatus { Generating, Ready, Failed }` + `PhotoCardStatus.of(raw: String): PhotoCardStatus`
  - `data class PhotoCard(id: String, dogId: String?, month: Int, dogName: String, title: String, status: PhotoCardStatus, errorCode: String?, likeness: Int?, createdAtMillis: Long)`
  - `data class PhotoCardDetail(card: PhotoCard, imageUrl: String?)`
  - `fun parsePhotoCard(json: JSONObject): PhotoCard` · `fun parsePhotoCardDetail(body: String): PhotoCardDetail` · `fun parsePhotoCardList(body: String): List<PhotoCard>`
  - `fun photoCardQuery(month: Int, dogName: String, dogId: String?): String`
  - `fun photoCardErrorMessage(status: Int, body: String?): String`
  - `fun photoFailureText(month: Int, errorCode: String?): String`
  - `interface PhotoCardRemote { suspend fun create(token: String, month: Int, dogName: String, dogId: String?, jpeg: ByteArray): Result<PhotoCard>; suspend fun list(token: String): Result<List<PhotoCard>>; suspend fun get(token: String, id: String): Result<PhotoCardDetail>; suspend fun delete(token: String, id: String): Result<Unit>; suspend fun download(url: String): Result<ByteArray> }`
  - `object HttpPhotoCardRemote : PhotoCardRemote` · `val HttpPhotoCardRemote.configured: Boolean`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```kotlin
package com.daengs.app.dogcard.photo

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** 저쪽 `schemas/ai_card.py` 의 `AiCardResponse` 를 읽는다. */
class PhotoCardJsonTest {

    private val generating = """
        {"id":"8a1c","dog_id":null,"month":9,"dog_name":"콩이","title":"CHUSEOK 콩이",
         "status":"generating","error_code":null,"likeness":null,"attempts":null,
         "width":null,"height":null,"created_at":"2026-09-15T01:02:03.456789Z","image_url":null}
    """.trimIndent()

    @Test
    fun `만드는 중인 카드를 읽는다`() {
        val card = parsePhotoCard(JSONObject(generating))
        assertEquals("8a1c", card.id)
        assertNull(card.dogId)
        assertEquals(9, card.month)
        assertEquals("콩이", card.dogName)
        assertEquals(PhotoCardStatus.Generating, card.status)
        assertNull(card.likeness)
        assertEquals(Instant.parse("2026-09-15T01:02:03.456Z").toEpochMilli(), card.createdAtMillis)
    }

    /** 파이썬 쪽이 `+00:00` 으로 줄 수도 있다. */
    @Test
    fun `오프셋 표기 시각도 읽는다`() {
        val card = parsePhotoCard(JSONObject(generating.replace("Z\"", "+09:00\"")))
        assertEquals(Instant.parse("2026-09-14T16:02:03.456Z").toEpochMilli(), card.createdAtMillis)
    }

    @Test
    fun `완성 단건은 그림 주소를 준다`() {
        val body = generating.replace("\"generating\"", "\"ready\"")
            .replace("\"likeness\":null", "\"likeness\":5")
            .replace("\"image_url\":null", "\"image_url\":\"https://storage.example/a.png?sig=1\"")
        val detail = parsePhotoCardDetail(body)
        assertEquals(PhotoCardStatus.Ready, detail.card.status)
        assertEquals(5, detail.card.likeness)
        assertEquals("https://storage.example/a.png?sig=1", detail.imageUrl)
    }

    @Test
    fun `목록을 읽는다`() {
        val list = parsePhotoCardList("""{"cards":[$generating,$generating]}""")
        assertEquals(2, list.size)
    }

    @Test
    fun `모르는 상태는 만드는 중으로 본다`() {
        assertEquals(PhotoCardStatus.Failed, PhotoCardStatus.of("failed"))
        assertEquals(PhotoCardStatus.Generating, PhotoCardStatus.of("queued"))
    }

    /** 한글 이름이 쿼리에서 깨지면 서버가 엉뚱한 이름으로 카드를 굽는다. */
    @Test
    fun `쿼리는 한글 이름을 인코딩하고 강아지가 없으면 뺀다`() {
        assertEquals("month=4&dog_name=%EC%BD%A9%EC%9D%B4", photoCardQuery(4, "콩이", null))
        assertEquals("month=9&dog_name=a+b&dog_id=d-1", photoCardQuery(9, "a b", "d-1"))
    }

    /** 서버 `message` 는 앱이 그대로 띄우는 문장이다. */
    @Test
    fun `오류 본문의 문장을 통과시킨다`() {
        val body = """{"detail":{"code":"limit_reached","message":"오늘은 카드를 더 만들 수 없어요. 내일 다시 시도해 주세요."}}"""
        assertEquals("오늘은 카드를 더 만들 수 없어요. 내일 다시 시도해 주세요.", photoCardErrorMessage(429, body))
        assertEquals("서버 오류 (500)", photoCardErrorMessage(500, "<html>"))
        assertEquals("서버 오류 (502)", photoCardErrorMessage(502, null))
    }

    @Test
    fun `실패 코드마다 다시 할 일을 말한다`() {
        assertEquals("9월 카드를 만들지 못했어요 · 강아지가 잘 보이는 다른 사진으로 해 주세요", photoFailureText(9, "no_image"))
        assertTrue(photoFailureText(4, "interrupted").endsWith("잠시 뒤 다시 만들어 주세요"))
        assertTrue(photoFailureText(4, null).endsWith("잠시 뒤 다시 만들어 주세요"))
    }
}
```

- [ ] **Step 2: 돌려서 실패를 본다**

Run: `export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" && ./gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.dogcard.photo.PhotoCardJsonTest'`
Expected: 컴파일 실패 — `parsePhotoCard` 없음

- [ ] **Step 3: `PhotoCard.kt`**

```kotlin
package com.daengs.app.dogcard.photo

import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * 서버가 그려 준 포토 카드 한 장. 계약은 저쪽 `routers/ai_card.py` (#537).
 *
 * **Room 에 두지 않는다.** 누끼 카드는 오프라인에서 먼저 생겨 기기 표가 필요했지만,
 * 이 카드는 서버에서만 생긴다 — 정본이 서버다 (docs/photo-cards.md §3).
 */
data class PhotoCard(
    val id: String,
    val dogId: String?,
    val month: Int,
    val dogName: String,
    /** 서버가 제목판에 찍은 글자. 예: `CHUSEOK 콩이` */
    val title: String,
    val status: PhotoCardStatus,
    /** `failed` 일 때만. `upstream · no_image · unavailable · storage · internal · interrupted` */
    val errorCode: String?,
    /** 서버 닮음 검수 1~5. 완성 전에는 null */
    val likeness: Int?,
    val createdAtMillis: Long,
)

enum class PhotoCardStatus {
    Generating, Ready, Failed;

    companion object {
        /** 모르는 값은 만드는 중으로 본다 — 조회를 한 번 더 할 뿐 칸을 잘못 채우지 않는다. */
        fun of(raw: String): PhotoCardStatus = when (raw) {
            "ready" -> Ready
            "failed" -> Failed
            else -> Generating
        }
    }
}

/** 단건 조회. **여기서만 그림 주소가 온다** (목록은 늘 null). 주소는 절대 주소다. */
data class PhotoCardDetail(val card: PhotoCard, val imageUrl: String?)

fun parsePhotoCard(json: JSONObject): PhotoCard = PhotoCard(
    id = json.getString("id"),
    dogId = json.optStringOrNull("dog_id"),
    month = json.getInt("month"),
    dogName = json.optString("dog_name"),
    title = json.optString("title"),
    status = PhotoCardStatus.of(json.optString("status")),
    errorCode = json.optStringOrNull("error_code"),
    likeness = if (json.isNull("likeness")) null else json.optInt("likeness"),
    createdAtMillis = json.optStringOrNull("created_at")?.let(::parseInstantMillis) ?: 0L,
)

fun parsePhotoCardDetail(body: String): PhotoCardDetail {
    val json = JSONObject(body)
    return PhotoCardDetail(parsePhotoCard(json), json.optStringOrNull("image_url"))
}

fun parsePhotoCardList(body: String): List<PhotoCard> {
    val arr = JSONObject(body).getJSONArray("cards")
    return (0 until arr.length()).map { parsePhotoCard(arr.getJSONObject(it)) }
}

/** 메타는 쿼리다 (본문은 사진 원시 바이트). 한글 이름은 UTF-8 로 인코딩한다. */
fun photoCardQuery(month: Int, dogName: String, dogId: String?): String = buildString {
    append("month=").append(month)
    append("&dog_name=").append(URLEncoder.encode(dogName, "UTF-8"))
    if (dogId != null) append("&dog_id=").append(URLEncoder.encode(dogId, "UTF-8"))
}

/** 서버 `{"detail": {"code", "message"}}` 의 문장을 그대로. 못 읽으면 상태 코드. */
fun photoCardErrorMessage(status: Int, body: String?): String {
    val detail = runCatching { JSONObject(body.orEmpty()).opt("detail") }.getOrNull()
    return when (detail) {
        is String -> detail.takeIf(String::isNotBlank)
        is JSONObject -> detail.optString("message").takeIf(String::isNotBlank)
        else -> null
    } ?: "서버 오류 ($status)"
}

/**
 * 뒤에서 실패한 카드를 알리는 한 줄. **실패는 하루 한도에 안 센다** — 그래서 다시 하라고 말한다.
 */
fun photoFailureText(month: Int, errorCode: String?): String {
    val next = when (errorCode) {
        "no_image" -> "강아지가 잘 보이는 다른 사진으로 해 주세요"
        else -> "잠시 뒤 다시 만들어 주세요"
    }
    return "${month}월 카드를 만들지 못했어요 · $next"
}

private fun parseInstantMillis(raw: String): Long? =
    runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()
        // 오프셋이 없으면 서버 UTC 로 본다.
        ?: runCatching { LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()

private fun JSONObject.optStringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
```

- [ ] **Step 4: `PhotoCardApi.kt`**

```kotlin
package com.daengs.app.dogcard.photo

import com.daengs.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 포토 카드 서버 경계. **홀더는 이 인터페이스만 본다** — 테스트가 가짜로 갈아 끼운다.
 */
interface PhotoCardRemote {
    /** 만들기를 **시작한다.** 202 와 `generating` 이 온다. 30~60초 뒤 [get] 이 `ready` 를 준다. */
    suspend fun create(token: String, month: Int, dogName: String, dogId: String?, jpeg: ByteArray): Result<PhotoCard>
    suspend fun list(token: String): Result<List<PhotoCard>>
    suspend fun get(token: String, id: String): Result<PhotoCardDetail>
    /** 없는 카드(404)도 성공으로 본다 — 이미 지워졌다는 뜻이라 기기에서도 지우면 된다. */
    suspend fun delete(token: String, id: String): Result<Unit>
    suspend fun download(url: String): Result<ByteArray>
}

/** `/app/ai-cards`. 배관은 `CardApi` 와 같다 — 저쪽 문장을 그대로 통과시킨다. */
object HttpPhotoCardRemote : PhotoCardRemote {

    val configured: Boolean get() = BuildConfig.API_BASE_URL.isNotBlank()

    override suspend fun create(
        token: String, month: Int, dogName: String, dogId: String?, jpeg: ByteArray,
    ): Result<PhotoCard> = call(token, "?${photoCardQuery(month, dogName, dogId)}", "POST", jpeg) {
        parsePhotoCard(org.json.JSONObject(it))
    }

    override suspend fun list(token: String): Result<List<PhotoCard>> =
        call(token, "", "GET") { parsePhotoCardList(it) }

    override suspend fun get(token: String, id: String): Result<PhotoCardDetail> =
        call(token, "/$id", "GET") { parsePhotoCardDetail(it) }

    override suspend fun delete(token: String, id: String): Result<Unit> =
        call(token, "/$id", "DELETE", notFoundOk = true) { }

    override suspend fun download(url: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = BODY_TIMEOUT_MS
            }
            try {
                if (conn.responseCode !in 200..299) error("그림을 받지 못했어요 (${conn.responseCode})")
                conn.inputStream.use { it.readBytes() }
            } finally {
                conn.disconnect()
            }
        }.recoverCatching { rethrow(it) }
    }

    private suspend fun <T> call(
        token: String,
        path: String,
        method: String,
        jpeg: ByteArray? = null,
        notFoundOk: Boolean = false,
        parse: (String) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            check(configured) { "서버 주소가 없습니다. local.properties 의 daengs.apiBaseUrl 을 채우세요." }
            val conn = (URL("${BuildConfig.API_BASE_URL.trimEnd('/')}/app/ai-cards$path")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = TIMEOUT_MS
                readTimeout = if (jpeg != null) BODY_TIMEOUT_MS else TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
            }
            try {
                if (jpeg != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "image/jpeg")
                    conn.setFixedLengthStreamingMode(jpeg.size)
                    conn.outputStream.use { it.write(jpeg) }
                }
                val code = conn.responseCode
                if (notFoundOk && code == 404) return@runCatching parse("")
                if (code !in 200..299) {
                    error(photoCardErrorMessage(code, conn.errorStream?.bufferedReader()?.use { it.readText() }))
                }
                parse(if (code == 204) "" else conn.inputStream.bufferedReader().use { it.readText() })
            } finally {
                conn.disconnect()
            }
        }.recoverCatching { rethrow(it) }
    }

    private fun rethrow(cause: Throwable): Nothing {
        if (cause is IllegalStateException) throw cause
        throw IllegalStateException("서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.", cause)
    }

    private const val TIMEOUT_MS = 10_000
    private const val BODY_TIMEOUT_MS = 30_000
}
```

- [ ] **Step 5: 테스트 통과를 본다**

Run: `export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" && ./gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.dogcard.photo.PhotoCardJsonTest'`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add app/src/main/java/com/daengs/app/dogcard/photo/ app/src/test/java/com/daengs/app/dogcard/photo/PhotoCardJsonTest.kt
git commit -m "앱이 서버 AI 카드 응답을 읽을 길이 없던 것 — 포토 카드 모델과 /app/ai-cards 배관을 둔다 …"
```

---

### Task 3: 완성 그림 보관 · 포토 카드 홀더

**Files:**
- Create: `app/src/main/java/com/daengs/app/dogcard/photo/PhotoCardFiles.kt`
- Create: `app/src/main/java/com/daengs/app/dogcard/photo/PhotoCardHolder.kt`
- Test: `app/src/test/java/com/daengs/app/dogcard/photo/PhotoCardHolderTest.kt`

**Interfaces:**
- Consumes: Task 2 의 `PhotoCard` · `PhotoCardStatus` · `PhotoCardDetail` · `PhotoCardRemote`
- Produces:
  - `class PhotoCardFiles(dir: File) { fun existing(): Map<String, File>; suspend fun write(id: String, png: ByteArray): File?; suspend fun delete(id: String); suspend fun keepOnly(ids: Set<String>); suspend fun clear() }`
  - `class PhotoCardHolder(remote: PhotoCardRemote, files: PhotoCardFiles, accessToken: suspend () -> String?, now: () -> Long = System::currentTimeMillis)`
    - 상태: `cards: List<PhotoCard>` · `images: Map<String, File>` · `creating: Boolean` · `createError: String?` · `error: String?`
    - 계산: `generating: Boolean` · `latestFailure: PhotoCard?`
    - 동작: `suspend fun load()` · `suspend fun create(month: Int, dogName: String, dogId: String?, jpeg: ByteArray): Boolean` · `suspend fun pollOnce()` · `suspend fun pollWhileGenerating(intervalMs: Long = PHOTO_POLL_MS)` · `suspend fun remove(id: String): Boolean` · `fun clearCreateError()` · `fun clearError()` · `suspend fun forget()`
  - `const val PHOTO_POLL_MS = 5_000L` · `const val PHOTO_STALE_MS = 10 * 60_000L`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```kotlin
package com.daengs.app.dogcard.photo

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PhotoCardHolderTest {

    @get:Rule val tmp = TemporaryFolder()

    private class FakeRemote : PhotoCardRemote {
        val server = mutableListOf<PhotoCard>()
        var failList = false
        var failDelete = false
        var createError: String? = null
        var gets = 0
        val urls = mutableMapOf<String, String>()

        override suspend fun create(token: String, month: Int, dogName: String, dogId: String?, jpeg: ByteArray): Result<PhotoCard> {
            createError?.let { return Result.failure(IllegalStateException(it)) }
            val made = card("new-$month", month, PhotoCardStatus.Generating, at = 1_000L)
            server.add(0, made)
            return Result.success(made)
        }
        override suspend fun list(token: String) =
            if (failList) Result.failure(IllegalStateException("서버에 닿지 못했어요.")) else Result.success(server.toList())
        override suspend fun get(token: String, id: String): Result<PhotoCardDetail> {
            gets++
            val c = server.first { it.id == id }
            return Result.success(PhotoCardDetail(c, urls[id]))
        }
        override suspend fun delete(token: String, id: String): Result<Unit> {
            if (failDelete) return Result.failure(IllegalStateException("카드 보관은 아직 준비 중이에요."))
            server.removeAll { it.id == id }
            return Result.success(Unit)
        }
        override suspend fun download(url: String) = Result.success(byteArrayOf(1, 2, 3))
    }

    companion object {
        fun card(id: String, month: Int, status: PhotoCardStatus, at: Long = 0L) = PhotoCard(
            id = id, dogId = null, month = month, dogName = "콩이", title = "BLOSSOM 콩이",
            status = status, errorCode = if (status == PhotoCardStatus.Failed) "upstream" else null,
            likeness = if (status == PhotoCardStatus.Ready) 5 else null, createdAtMillis = at,
        )
    }

    private fun holder(remote: FakeRemote, token: String? = "t", now: Long = 2_000L) =
        PhotoCardHolder(remote, PhotoCardFiles(tmp.newFolder()), { token }, { now })

    @Test
    fun `완성 카드는 그림을 받아 둔다`() = runTest {
        val remote = FakeRemote().apply {
            server += card("a", 4, PhotoCardStatus.Ready)
            urls["a"] = "https://x/a.png"
        }
        val h = holder(remote)
        h.load()
        assertEquals(listOf("a"), h.cards.map { it.id })
        assertTrue(h.images.getValue("a").exists())
    }

    @Test
    fun `이미 받은 그림은 다시 안 받는다`() = runTest {
        val remote = FakeRemote().apply {
            server += card("a", 4, PhotoCardStatus.Ready)
            urls["a"] = "https://x/a.png"
        }
        val h = holder(remote)
        h.load()
        h.load()
        assertEquals(1, remote.gets)
    }

    /** 목록이 통째로 사라지면 사용자는 카드가 지워진 줄 안다. */
    @Test
    fun `못 불러와도 들고 있던 것을 유지한다`() = runTest {
        val remote = FakeRemote().apply { server += card("a", 4, PhotoCardStatus.Generating) }
        val h = holder(remote)
        h.load()
        remote.failList = true
        h.load()
        assertEquals(1, h.cards.size)
        assertNotNull(h.error)
    }

    @Test
    fun `로그인 전이면 아무것도 안 한다`() = runTest {
        val remote = FakeRemote().apply { server += card("a", 4, PhotoCardStatus.Ready) }
        val h = holder(remote, token = null)
        h.load()
        assertTrue(h.cards.isEmpty())
        assertNull(h.error)
    }

    @Test
    fun `만들면 맨 앞에 만드는 중으로 온다`() = runTest {
        val h = holder(FakeRemote())
        assertTrue(h.create(4, "콩이", null, byteArrayOf(9)))
        assertEquals(PhotoCardStatus.Generating, h.cards.first().status)
        assertTrue(h.generating)
        assertFalse(h.creating)
    }

    /** 서버 문장(한도·중복)은 만들기 화면에 그대로 뜬다. */
    @Test
    fun `만들기가 거절되면 문장을 남기고 목록은 그대로다`() = runTest {
        val remote = FakeRemote().apply { createError = "오늘은 카드를 더 만들 수 없어요. 내일 다시 시도해 주세요." }
        val h = holder(remote)
        assertFalse(h.create(4, "콩이", null, byteArrayOf(9)))
        assertEquals("오늘은 카드를 더 만들 수 없어요. 내일 다시 시도해 주세요.", h.createError)
        assertTrue(h.cards.isEmpty())
        h.clearCreateError()
        assertNull(h.createError)
    }

    @Test
    fun `조회해서 완성되면 바꿔 끼우고 그림을 받는다`() = runTest {
        val remote = FakeRemote()
        val h = holder(remote)
        h.create(4, "콩이", null, byteArrayOf(9))
        remote.server[0] = remote.server[0].copy(status = PhotoCardStatus.Ready, likeness = 4)
        remote.urls["new-4"] = "https://x/n.png"
        h.pollOnce()
        assertEquals(PhotoCardStatus.Ready, h.cards.first().status)
        assertTrue(h.images.containsKey("new-4"))
        assertFalse(h.generating)
    }

    /** 서버는 9분 지나면 조회 때 interrupted 로 바꾼다. 앱은 10분 뒤 목록을 다시 받는다. */
    @Test
    fun `너무 오래 만드는 중이면 목록을 다시 받는다`() = runTest {
        val remote = FakeRemote().apply { server += card("old", 9, PhotoCardStatus.Generating, at = 0L) }
        val h = holder(remote, now = PHOTO_STALE_MS + 1)
        h.load()
        remote.server[0] = remote.server[0].copy(status = PhotoCardStatus.Failed, errorCode = "interrupted")
        h.pollOnce()
        assertEquals(0, remote.gets)
        assertEquals("old", h.latestFailure?.id)
    }

    @Test
    fun `만드는 중이 없으면 조회를 안 돈다`() = runTest {
        val remote = FakeRemote().apply { server += card("a", 4, PhotoCardStatus.Failed) }
        val h = holder(remote)
        h.load()
        h.pollWhileGenerating(intervalMs = 1)
        assertEquals(0, remote.gets)
    }

    @Test
    fun `지우면 서버와 파일에서 다 빠진다`() = runTest {
        val remote = FakeRemote().apply {
            server += card("a", 4, PhotoCardStatus.Ready)
            urls["a"] = "https://x/a.png"
        }
        val h = holder(remote)
        h.load()
        val file = h.images.getValue("a")
        assertTrue(h.remove("a"))
        assertTrue(h.cards.isEmpty())
        assertFalse(file.exists())
    }

    /** 정본이 서버라 기기에서만 지우면 다음 목록에서 되살아난다 — 누끼 카드와 반대다. */
    @Test
    fun `서버가 못 지우면 기기에서도 안 지운다`() = runTest {
        val remote = FakeRemote().apply { server += card("a", 4, PhotoCardStatus.Ready); failDelete = true }
        val h = holder(remote)
        h.load()
        assertFalse(h.remove("a"))
        assertEquals(1, h.cards.size)
        assertEquals("카드 보관은 아직 준비 중이에요.", h.error)
    }

    @Test
    fun `서버 목록에 없는 그림 파일은 치운다`() = runTest {
        val dir = tmp.newFolder()
        val files = PhotoCardFiles(dir)
        files.write("gone", byteArrayOf(1))
        val h = PhotoCardHolder(FakeRemote(), files, { "t" }, { 0L })
        h.load()
        assertFalse(files.existing().containsKey("gone"))
    }

    @Test
    fun `비우면 목록과 파일이 다 사라진다`() = runTest {
        val remote = FakeRemote().apply {
            server += card("a", 4, PhotoCardStatus.Ready)
            urls["a"] = "https://x/a.png"
        }
        val h = holder(remote)
        h.load()
        h.forget()
        assertTrue(h.cards.isEmpty())
        assertTrue(h.images.isEmpty())
    }
}
```

- [ ] **Step 2: 돌려서 실패를 본다**

Run: `export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" && ./gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.dogcard.photo.PhotoCardHolderTest'`
Expected: 컴파일 실패 — `PhotoCardHolder` 없음

- [ ] **Step 3: `PhotoCardFiles.kt`**

```kotlin
package com.daengs.app.dogcard.photo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 완성된 포토 카드 PNG 를 기기에 둔다. 방 액자·저장·다시 열 때 서버에서 또 안 받으려고.
 *
 * **폴더를 받는다** (`filesDir/photo-cards`). `Context` 를 안 받는 것은 JVM 테스트가 임시
 * 폴더로 돌게 하려는 것이다.
 */
class PhotoCardFiles(private val dir: File) {

    private fun file(id: String) = File(dir.apply { mkdirs() }, "$id.png")

    fun existing(): Map<String, File> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(".png") }
            ?.associateBy { it.name.removeSuffix(".png") }
            .orEmpty()

    /** 다 쓴 뒤에만 제자리 이름으로 옮긴다 — 반쯤 쓴 파일이 칸에 뜨면 깨진 그림이 된다. */
    suspend fun write(id: String, png: ByteArray): File? = withContext(Dispatchers.IO) {
        runCatching {
            val target = file(id)
            val part = File(target.parentFile, "$id.part")
            part.writeBytes(png)
            if (!part.renameTo(target)) {
                target.delete()
                check(part.renameTo(target)) { "그림을 저장하지 못했어요." }
            }
            target
        }.getOrNull()
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) { file(id).delete(); Unit }

    suspend fun keepOnly(ids: Set<String>) = withContext(Dispatchers.IO) {
        existing().filterKeys { it !in ids }.values.forEach { it.delete() }
    }

    suspend fun clear() = withContext(Dispatchers.IO) { dir.deleteRecursively(); Unit }
}
```

- [ ] **Step 4: `PhotoCardHolder.kt`**

```kotlin
package com.daengs.app.dogcard.photo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.io.File

/** 만드는 중일 때 다시 묻는 간격. 한 장에 30~60초라 5초면 끝난 뒤 금방 뜬다. */
const val PHOTO_POLL_MS = 5_000L

/**
 * 이만큼 지나도 만드는 중이면 조회를 멈추고 목록을 다시 받는다. 서버는 9분(엔진·검수
 * 타임아웃 × 재시도 + 60초)이 지나면 조회 때 `interrupted` 로 바꿔 둔다.
 */
const val PHOTO_STALE_MS = 10 * 60_000L

/**
 * 포토 카드를 들고 있는 자리. `CardHolder` 와 같은 결이다 (`mutableStateOf` 홀더).
 *
 * **정본은 서버다.** 기기에는 완성 그림 파일만 있다 — 그래서 지우기는 누끼 카드와 반대로
 * **서버가 먼저**고, 서버가 못 지우면 기기에서도 안 지운다 (docs/photo-cards.md §3).
 */
class PhotoCardHolder(
    private val remote: PhotoCardRemote,
    private val files: PhotoCardFiles,
    private val accessToken: suspend () -> String?,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** 최근이 앞이다. 실패한 카드도 들고 있는다 — 머리말의 실패 한 줄이 쓴다. */
    var cards: List<PhotoCard> by mutableStateOf(emptyList())
        private set

    /** 받아 둔 완성 그림. 여기 있어야 칸에 완성으로 뜬다. */
    var images: Map<String, File> by mutableStateOf(emptyMap())
        private set

    var creating: Boolean by mutableStateOf(false)
        private set

    /** 만들기 화면에 띄울 서버 문장. */
    var createError: String? by mutableStateOf(null)
        private set

    /** 목록·지우기 실패. 도감이 한 줄로 알린다. */
    var error: String? by mutableStateOf(null)
        private set

    val generating: Boolean get() = cards.any { it.status == PhotoCardStatus.Generating }

    val latestFailure: PhotoCard? get() = cards.firstOrNull { it.status == PhotoCardStatus.Failed }

    fun clearCreateError() { createError = null }

    fun clearError() { error = null }

    /** 목록을 받는다. **실패해도 들고 있던 것을 안 비운다** (`CardHolder.load` 원칙). */
    suspend fun load() {
        val token = accessToken() ?: return
        remote.list(token)
            .onSuccess { list ->
                cards = list
                files.keepOnly(list.map { it.id }.toSet())
                images = files.existing()
                fetchImages(token)
            }
            .onFailure { error = it.message ?: "포토 카드를 불러오지 못했어요." }
    }

    suspend fun create(month: Int, dogName: String, dogId: String?, jpeg: ByteArray): Boolean {
        val token = accessToken() ?: run {
            createError = "로그인하면 포토 카드를 만들 수 있어요"
            return false
        }
        creating = true
        createError = null
        val result = remote.create(token, month, dogName, dogId, jpeg)
        creating = false
        return result.fold(
            onSuccess = { made -> cards = listOf(made) + cards.filterNot { it.id == made.id }; true },
            onFailure = { createError = it.message ?: "카드를 만들지 못했어요."; false },
        )
    }

    suspend fun pollOnce() {
        if (!generating) return
        val token = accessToken() ?: return
        val stale = cards.any { it.status == PhotoCardStatus.Generating && now() - it.createdAtMillis > PHOTO_STALE_MS }
        if (stale) {
            load()
            return
        }
        cards.filter { it.status == PhotoCardStatus.Generating }.forEach { waiting ->
            val detail = remote.get(token, waiting.id).getOrNull() ?: return@forEach
            cards = cards.map { if (it.id == detail.card.id) detail.card else it }
            if (detail.card.status == PhotoCardStatus.Ready) store(detail)
        }
    }

    /** 만드는 중이 있는 동안만 돈다. 부르는 쪽이 앱이 앞에 있을 때만 부른다. */
    suspend fun pollWhileGenerating(intervalMs: Long = PHOTO_POLL_MS) {
        while (generating) {
            delay(intervalMs)
            pollOnce()
        }
    }

    suspend fun remove(id: String): Boolean {
        val token = accessToken() ?: run {
            error = "로그인하면 지울 수 있어요"
            return false
        }
        return remote.delete(token, id).fold(
            onSuccess = {
                cards = cards.filterNot { it.id == id }
                images = images - id
                files.delete(id)
                true
            },
            onFailure = { error = it.message ?: "카드를 지우지 못했어요."; false },
        )
    }

    /** 로그아웃·탈퇴. 다음 사람이 남의 카드 그림을 물려받으면 안 된다. */
    suspend fun forget() {
        cards = emptyList()
        images = emptyMap()
        createError = null
        error = null
        files.clear()
    }

    private suspend fun fetchImages(token: String) {
        cards.filter { it.status == PhotoCardStatus.Ready && it.id !in images }.forEach { ready ->
            remote.get(token, ready.id).getOrNull()?.let { store(it) }
        }
    }

    private suspend fun store(detail: PhotoCardDetail) {
        val url = detail.imageUrl ?: return
        val png = remote.download(url).getOrNull() ?: return
        val file = files.write(detail.card.id, png) ?: return
        images = images + (detail.card.id to file)
    }
}
```

- [ ] **Step 5: 테스트 통과를 본다**

Run: `export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" && ./gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.dogcard.photo.*'`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add app/src/main/java/com/daengs/app/dogcard/photo/PhotoCardFiles.kt app/src/main/java/com/daengs/app/dogcard/photo/PhotoCardHolder.kt app/src/test/java/com/daengs/app/dogcard/photo/PhotoCardHolderTest.kt
git commit -m "서버가 카드를 다 그렸는지 앱이 알 길이 없던 것 — 포토 카드 홀더가 목록·조회·지우기를 맡는다 …"
```

---

### Task 4: `OwnedCard` 와 칸 겹치기

**Files:**
- Modify: `app/src/main/java/com/daengs/app/ui/dex/DexSlots.kt` (전체)
- Modify: `app/src/main/java/com/daengs/app/ui/dex/CardArt.kt:40-41` 과 끝에 더함
- Modify: `app/src/test/java/com/daengs/app/ui/dex/DexSlotsTest.kt` (55행 한 줄 + 테스트 추가)

**Interfaces:**
- Consumes: Task 1 `PHOTO_CARDS` · `photoCardFor` · `isPhoto`; Task 2 `PhotoCard` · `PhotoCardStatus`
- Produces:
  - `sealed interface OwnedCard { val id: String; val dogName: String; val madeAtMillis: Long }` · `OwnedCard.Drawn(card: DrawnCard)` · `OwnedCard.Photo(card: PhotoCard, file: File?)` + `val pending: Boolean`
  - `DexSlot(card: DexCard, owned: List<OwnedCard>)` (`drawnCount` 는 누끼 `drawn` + 포토 완성)
  - `fun dexSlots(cards: List<DexCard> = DEX_CARDS, drawn: List<DrawnCard>, photos: List<PhotoCard> = emptyList(), photoFiles: Map<String, File> = emptyMap()): List<DexSlot>`
  - `val DexCard.artSource: CardArt?` (포토면 null)
  - `data class OwnedCardArt(drawn: DrawnCardArt?, photoFile: File?)` · `@Composable fun rememberOwnedCardArt(owned: OwnedCard?): OwnedCardArt?` · `fun coverOf(card: DexCard, art: OwnedCardArt?): CardArt?`

- [ ] **Step 1: 실패하는 테스트를 더한다** — `DexSlotsTest.kt` 55행을 `owned.map { it.madeAtMillis }` 로 바꾸고, 클래스 끝에:

```kotlin
    private fun photo(id: String, month: Int, status: PhotoCardStatus, at: Long) = PhotoCard(
        id = id, dogId = null, month = month, dogName = "콩이", title = "T",
        status = status, errorCode = null, likeness = null, createdAtMillis = at,
    )

    private val all = DEX_CARDS + PHOTO_CARDS

    @Test
    fun `포토는 달 칸에 겹치고 최근이 앞이다`() {
        val slots = dexSlots(all, emptyList(), listOf(photo("a", 4, PhotoCardStatus.Ready, 10), photo("b", 4, PhotoCardStatus.Ready, 20)))
        val april = slots.first { it.card.id == "photo-04" }
        assertEquals(listOf("b", "a"), april.owned.map { it.id })
        assertTrue(slots.first { it.card.id == "photo-09" }.locked)
    }

    /** 실패한 카드는 칸을 열지 않는다 — 머리말의 한 줄이 알린다. */
    @Test
    fun `실패한 포토는 칸에 안 들어간다`() {
        val slots = dexSlots(all, emptyList(), listOf(photo("x", 9, PhotoCardStatus.Failed, 10)))
        assertTrue(slots.first { it.card.id == "photo-09" }.locked)
    }

    /** 만드는 중이면 칸은 열리지만 아직 "만든 장수" 는 아니다. */
    @Test
    fun `만드는 중인 포토는 칸을 열되 장수에는 안 센다`() {
        val slots = dexSlots(all, emptyList(), listOf(photo("g", 9, PhotoCardStatus.Generating, 10)))
        val sep = slots.first { it.card.id == "photo-09" }
        assertFalse(sep.locked)
        assertEquals(0, sep.drawnCount)
        assertTrue((sep.owned.single() as OwnedCard.Photo).pending)
    }

    @Test
    fun `완성인데 그림 파일이 있어야 만든 장으로 센다`() {
        val ready = photo("r", 4, PhotoCardStatus.Ready, 10)
        val noFile = dexSlots(all, emptyList(), listOf(ready)).first { it.card.id == "photo-04" }
        assertEquals(0, noFile.drawnCount)
        val withFile = dexSlots(all, emptyList(), listOf(ready), mapOf("r" to java.io.File("r.png")))
            .first { it.card.id == "photo-04" }
        assertEquals(1, withFile.drawnCount)
        assertFalse((withFile.owned.single() as OwnedCard.Photo).pending)
    }

    @Test
    fun `포토를 넣어도 야채 칸은 그대로다`() {
        val before = dexSlots(drawn = listOf(card("cabbage", 10)))
        val after = dexSlots(all, listOf(card("cabbage", 10)), listOf(photo("a", 4, PhotoCardStatus.Ready, 5)))
        assertEquals(before.map { it.card.id to it.count }, after.take(DEX_CARDS.size).map { it.card.id to it.count })
    }

    @Test
    fun `포토 표지는 파일이고 파일이 없으면 빈 판이다`() {
        val april = photoCardFor(4)!!
        assertEquals(null, coverOf(april, null))
        assertEquals(null, coverOf(april, OwnedCardArt(drawn = null, photoFile = null)))
        val f = java.io.File("a.png")
        assertEquals(CardArt.Local(f), coverOf(april, OwnedCardArt(drawn = null, photoFile = f)))
        assertEquals(CardArt.Asset("neo-hologram/art/cabbage.webp"), coverOf(DEX_CARDS.first(), null))
    }
```

import 에 `com.daengs.app.dogcard.photo.PhotoCard` · `com.daengs.app.dogcard.photo.PhotoCardStatus` 추가.

- [ ] **Step 2: 돌려서 실패를 본다**

Run: `export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" && ./gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.ui.dex.DexSlotsTest'`
Expected: 컴파일 실패 — `OwnedCard` 없음

- [ ] **Step 3: `DexSlots.kt` 를 이렇게 바꾼다**

```kotlin
package com.daengs.app.ui.dex

import androidx.compose.runtime.Immutable
import com.daengs.app.dogcard.DrawnCard
import com.daengs.app.dogcard.photo.PhotoCard
import com.daengs.app.dogcard.photo.PhotoCardStatus
import java.io.File

/**
 * 도감 칸이 가진 카드 한 장. **누끼 카드이거나 포토 카드다.**
 *
 * 둘은 만드는 길이 다르다 — 누끼는 틀 + 얼굴 + 글자를 조립하고, 포토는 서버가 다 그린
 * PNG 한 장이다. 그래도 **만든 뒤에는 똑같이 작용해야 해서**(사용자 결정 2026-09-15)
 * 칸·확대 뷰·설명·저장·액자·지우기가 이 한 겹만 보고, 그림 얻기에서만 갈린다
 * ([rememberOwnedCardArt]).
 */
@Immutable
sealed interface OwnedCard {
    val id: String
    val dogName: String
    val madeAtMillis: Long

    data class Drawn(val card: DrawnCard) : OwnedCard {
        override val id get() = card.id
        override val dogName get() = card.dogName
        override val madeAtMillis get() = card.drawnAtMillis
    }

    /** @param file 받아 둔 완성 그림. 없으면 아직 만드는 중이거나 받는 중이다 */
    data class Photo(val card: PhotoCard, val file: File?) : OwnedCard {
        override val id get() = card.id
        override val dogName get() = card.dogName
        override val madeAtMillis get() = card.createdAtMillis

        /** 그림이 아직 없다. 포일·설명·저장·액자를 안 연다 */
        val pending: Boolean get() = card.status != PhotoCardStatus.Ready || file == null
    }
}

/**
 * 도감 한 칸.
 *
 * **칸 수는 카탈로그가 정한다.** 같은 종류를 세 번 뽑아도 칸이 세 개가 되지 않고 [owned] 가
 * 세 장이 된다 — 도감은 "무엇을 가졌나" 를 보여 주는 자리이지 뽑은 순서를 늘어놓는
 * 자리가 아니다.
 *
 * 다만 **뽑은 장은 한 장도 안 버린다.** 같은 아이라도 사진마다 표정이 달라서 두 번째
 * 배추가 첫 번째와 다른 카드다. 칸 안에서 넘겨 볼 수 있어야 한다.
 */
@Immutable
data class DexSlot(
    val card: DexCard,
    /** 최근이 앞이다. 비어 있으면 아직 안 뽑은 칸이다 */
    val owned: List<OwnedCard>,
) {
    val locked: Boolean get() = owned.isEmpty()
    val count: Int get() = owned.size

    /**
     * **뽑아서(만들어서) 다 된 장수.** 화면의 `×N` 은 이 값을 쓴다.
     *
     * [count] 와 다르다 — 얼굴 자리가 빈 줄(예전 디버그 시드)과 **아직 만드는 중인 포토**는
     * 안 센다. 시금치를 한 번 뽑았는데 ×2 로 보이면 "두 번 뽑았다" 는 거짓말이 된다.
     */
    val drawnCount: Int get() = owned.count {
        when (it) {
            is OwnedCard.Drawn -> it.card.drawn
            is OwnedCard.Photo -> !it.pending
        }
    }
}

/**
 * 카탈로그에 내가 가진 것을 겹친다. 누끼는 `templateId`, 포토는 달로 칸을 찾는다.
 *
 * 카탈로그에 없는 `templateId` 는 **조용히 버린다.** 저쪽이 카드를 갈아엎으면 생길
 * 수 있는데, 그릴 칸이 없으니 그릴 수가 없다. 다만 [ownedTotal] 에서도 빼서 "내 카드
 * 7장" 이라고 해 놓고 여섯 장만 보이는 화면이 안 나오게 한다.
 *
 * **실패한 포토는 칸에 안 넣는다** — 도감 머리말의 한 줄이 알린다.
 */
fun dexSlots(
    cards: List<DexCard> = DEX_CARDS,
    drawn: List<DrawnCard>,
    photos: List<PhotoCard> = emptyList(),
    photoFiles: Map<String, File> = emptyMap(),
): List<DexSlot> {
    val byTemplate: Map<String, List<OwnedCard>> = drawn
        .map { OwnedCard.Drawn(it) }
        .groupBy { it.card.templateId }
    val byMonth: Map<String, List<OwnedCard>> = photos
        .filter { it.status != PhotoCardStatus.Failed }
        .mapNotNull { p -> photoCardFor(p.month)?.let { it.id to OwnedCard.Photo(p, photoFiles[p.id]) } }
        .groupBy({ it.first }, { it.second })
    return cards.map { card ->
        val mine = (byTemplate[card.id].orEmpty() + byMonth[card.id].orEmpty())
            .sortedByDescending { it.madeAtMillis }
        DexSlot(card, mine)
    }
}

/** 몇 종을 모았나. 머리글의 분자다. */
fun List<DexSlot>.collectedKinds(): Int = count { !it.locked }

/** 모두 몇 장인가. 같은 종류를 여러 장 뽑았으면 그만큼 는다. */
fun List<DexSlot>.ownedTotal(): Int = sumOf { it.count }
```

- [ ] **Step 4: `CardArt.kt` — `artSource` 를 nullable 로, 끝에 포토 갈래를 더한다**

40–41행을:

```kotlin
/**
 * 카탈로그 카드의 그림 자리. **포토 카드는 null 이다** — 달별 틀 그림은 서버에만 있고,
 * 잠긴 칸은 빈 판에 자물쇠를 얹는다 (docs/photo-cards.md §2).
 */
val DexCard.artSource: CardArt? get() = if (isPhoto) null else CardArt.Asset(art)
```

파일 끝에:

```kotlin
/**
 * 칸이 가진 한 장을 그릴 재료. **여기서만 누끼와 포토가 갈린다.**
 *
 * @param drawn 누끼 카드면 채운다 (틀 + 얼굴 + 글자)
 * @param photoFile 포토 카드의 받아 둔 완성 그림. 만드는 중이면 null
 */
@Immutable
data class OwnedCardArt(val drawn: DrawnCardArt?, val photoFile: File?) {
    val composed: Boolean get() = drawn?.composed == true
}

@Composable
fun rememberOwnedCardArt(owned: OwnedCard?): OwnedCardArt? = when (owned) {
    null -> null
    is OwnedCard.Drawn -> OwnedCardArt(rememberDrawnCardArt(owned.card), null)
    is OwnedCard.Photo -> OwnedCardArt(null, owned.file)
}

/**
 * 칸 표지·확대 뷰에 깔 판. **null 이면 빈 판**(포토의 잠긴 칸 · 만드는 중)을 그린다.
 */
fun coverOf(card: DexCard, art: OwnedCardArt?): CardArt? = when {
    art == null -> card.artSource
    art.photoFile != null -> CardArt.Local(art.photoFile)
    art.drawn == null -> null
    art.drawn.composed -> CardArt.Asset(art.drawn.template!!.art)
    else -> art.drawn.fallback
}
```

`CardArt.kt` import 에 이미 `java.io.File` 가 있다. `OwnedCard` 는 같은 패키지다.

- [ ] **Step 5: 테스트 통과를 본다**

Run: `export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" && ./gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.ui.dex.DexSlotsTest'`
Expected: 이 태스크 테스트는 PASS 여야 하지만 **`CardDexScreen.kt` 가 컴파일 안 될 수 있다** (`slot.owned` 가 `OwnedCard` 가 됨). 그러면 이 태스크 커밋은 Task 5 와 **한 커밋으로 묶는다** — 깨진 빌드를 커밋하지 않는다. 테스트는 Task 5 Step 끝에서 함께 돌린다.

---

### Task 5: 도감 화면을 `OwnedCard` 한 벌로

**Files:**
- Modify: `app/src/main/java/com/daengs/app/ui/dex/CardDexScreen.kt`
- Modify: `app/src/main/java/com/daengs/app/ui/dogcard/ComposedCard.kt`
- Modify: `app/src/main/java/com/daengs/app/MainActivity.kt:1285-1352` (호출부 타입만)

**Interfaces:**
- Consumes: Task 1 · Task 4 전부
- Produces: `CardDexScreen(…, drawn: List<DrawnCard>, photos: List<PhotoCard> = emptyList(), photoFiles: Map<String, File> = emptyMap(), onFrame: ((OwnedCard?) -> Unit)?, onDelete: ((OwnedCard) -> Unit)?, …)` · `@Composable fun rememberFramePicture(drawn: DrawnCard?, photoFile: File?): ImageBitmap?` (ComposedCard.kt)

이 태스크는 **동작을 늘리지 않는다.** 포토 탭이 뜨고, 포토 카드가 있으면 그려지고, 저장·액자·지우기가 타입만 넓어진다. 만들기 버튼·실패 한 줄은 Task 6.

- [ ] **Step 1: `CardDexScreen` 매개변수와 칸 목록**

`CardDexScreen` 시그니처에서 `onFrame`·`onDelete` 타입과 새 인자:

```kotlin
    /** 내가 뽑은 카드. 비어 있으면 열두 칸이 다 잠긴다 */
    drawn: List<DrawnCard> = emptyList(),
    /** 서버 포토 카드. 실패한 것도 들어온다 — 칸에는 안 넣는다 (`dexSlots`) */
    photos: List<PhotoCard> = emptyList(),
    /** 받아 둔 포토 그림. 없으면 그 장은 만드는 중으로 그린다 */
    photoFiles: Map<String, File> = emptyMap(),
```

```kotlin
    onFrame: ((OwnedCard?) -> Unit)? = null,
    ...
    onDelete: ((OwnedCard) -> Unit)? = null,
```

`deleteAndTell` 을 `((OwnedCard) -> Unit)?` / `{ card: OwnedCard -> … }` 로. 칸 목록:

```kotlin
    val all = remember(drawn, photos, photoFiles) {
        dexSlots(cards = DEX_CARDS + PHOTO_CARDS, drawn = drawn, photos = photos, photoFiles = photoFiles)
    }
```

`CardViewer` 의 `onFrame`·`onDelete` 매개변수 타입도 같이 `OwnedCard` 로.

- [ ] **Step 2: 머리말 제목에 포토**

`DexHeader` 의 제목 `when` 과 빈 문장:

```kotlin
            when (deck) {
                DexDeck.Veggie -> "채소가 된 우리 아이"
                DexDeck.Fruit -> "과일이 된 우리 아이"
                DexDeck.Photo -> "포토 속 우리 아이"
            },
```

```kotlin
            if (kinds == 0) {
                if (deck == DexDeck.Photo) "포토 카드를 만들어 도감을 채워 보세요" else "카드를 뽑아 도감을 채워 보세요"
            } else {
                "$kinds / $of 수집 · 내 카드 ${total}장"
            },
```

그리고 `DexHeader` 의 「＋ 카드 뽑기」는 **포토 탭에서는 안 띄운다** (Task 6 이 만들기 버튼을 단다): `DexGrid` 에서 `onDraw = onDraw.takeIf { deck != DexDeck.Photo }` 로 넘긴다.

- [ ] **Step 3: 포토 빈 판 Composable** — `drawLock()` 아래에 더한다

```kotlin
/**
 * 그림이 없는 포토 칸. **잠긴 칸이거나 서버가 아직 그리는 중이다.**
 *
 * 달별 틀 그림은 앱에 없다(서버에만 있다). 그래서 카드 비율의 어두운 판을 깔고, 잠겼으면
 * 자물쇠를, 만드는 중이면 한 줄을 얹는다. 높이를 먼저 맞추는 것은 [HoloCard] 와 같다 —
 * 옆 칸과 줄이 안 어긋난다.
 */
@Composable
private fun PhotoBlank(locked: Boolean, label: String?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .aspectRatio(PHOTO_RATIO, matchHeightConstraintsFirst = true)
            .clip(RoundedCornerShape(8.dp))
            .background(CardLock),
        contentAlignment = Alignment.Center,
    ) {
        if (locked) Canvas(Modifier.matchParentSize()) { drawLock() }
        label?.let {
            Text(it, color = CardWhite, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6E9E3)
@Composable
private fun PhotoBlankPreview() {
    Row(Modifier.height(220.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PhotoBlank(locked = true, label = null)
        PhotoBlank(locked = false, label = "만드는 중…")
    }
}
```

- [ ] **Step 4: `GridCard` 를 `OwnedCard` 로**

`val mine = …` 부터 `val art = …` 까지를:

```kotlin
    val mine = slot.owned.firstOrNull()
    // **그림 얻기만 누끼와 포토가 갈린다** (`rememberOwnedCardArt`). 누끼는 자리를 비운 원화 위에
    // 얼굴을 깔고 글자를 얹어야 카드가 되고, 포토는 서버가 다 그린 한 장이다.
    val owned = rememberOwnedCardArt(mine)
    val drawn = owned?.drawn
    val cover = coverOf(card, owned)
    val art = rememberCardImage(cover, sample = 2)
    val pending = (mine as? OwnedCard.Photo)?.pending == true
```

`HoloCard(…)` 호출을 `if (cover == null) { PhotoBlank(locked = slot.locked, label = if (pending) "만드는 중…" else null, modifier = Modifier.height(maxWidth * SLOT_RATIO)) } else { HoloCard(…) ; if (slot.locked) Canvas(…) { drawLock() } }` 로 감싼다. `HoloCard` 의 `foil = card.foil` 은 그대로, 인자 하나를 더한다: `tune = if (card.isPhoto) PHOTO_FOIL.getValue(card.no).tune else FoilTune()`.

캡션: `"${card.ko} · ${card.statLine}"` → `card.gridCaption`.

`onImmersive` 람다 안 `slot.owned.firstOrNull()?.dogName` 은 `OwnedCard.dogName` 이라 그대로 컴파일된다.

- [ ] **Step 5: `CardViewer` 를 `OwnedCard` 로**

`val mine = slot.owned.getOrNull(copy)` 아래 세 줄을:

```kotlin
    val owned = rememberOwnedCardArt(mine)
    val drawn = owned?.drawn
    val cover = coverOf(card, owned)
    val art = rememberCardImage(cover)
    val pending = (mine as? OwnedCard.Photo)?.pending == true
```

`HoloCard` 도 Step 4 와 같이 `cover == null` 이면 `PhotoBlank(locked = slot.locked, label = if (pending) "만드는 중이에요. 잠시 뒤 다시 열어 보세요" else null, modifier = Modifier.fillMaxWidth())` 로, 아니면 `HoloCard(…, tune = …)`.

`rub` 의 `onTap`: `{ if (!slot.locked) showDetail = !showDetail }` 그대로 둔다 — 만드는 중인 장도 설명 시트를 열어 **지우기**를 쓸 수 있다 (spec §4).

`shot`:

```kotlin
                val shot = if (mine != null && art != null && !pending) {
                    CardShot(
                        fileName = cardFileName(
                            // 포토는 칸 id(`photo-04`)를 틀 자리에 쓴다 — 파일 이름에 달이 남는다.
                            templateId = when (mine) {
                                is OwnedCard.Drawn -> mine.card.templateId
                                is OwnedCard.Photo -> card.id
                            },
                            cardId = mine.id,
                            at = mine.madeAtMillis,
                        ),
                        art = art,
                        template = if (drawn?.composed == true) drawn.template else null,
                        face = drawn?.face,
                        name = mine.dogName,
                        code = (mine as? OwnedCard.Drawn)?.card?.codeText.orEmpty(),
                    )
                } else {
                    null
                }
```

`CardDetailSheet(…)` 호출: `onFrame` 은 `if (mine != null && onFrame != null && !pending)`, `onDelete` 는 그대로 `mine != null && onDelete != null`.

사본 줄 아래 문장:

```kotlin
                        if (card.isPhoto) {
                            "${copy + 1} / ${slot.count} · 이 달로 ${slot.drawnCount}장 만들었어요"
                        } else {
                            "${copy + 1} / ${slot.count} · 이 ${slot.card.deck.label}로 ${slot.drawnCount}장 뽑았어요"
                        },
```

확대 뷰 아래 캡션 `"${card.ko} · ${card.statLine}"` → `card.gridCaption`.

- [ ] **Step 6: `CardDetailSheet` 를 `OwnedCard` 로**

매개변수 `mine: DrawnCard? = null` → `mine: OwnedCard? = null`. 본문:

```kotlin
            mine?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    "${drawnOn(it.madeAtMillis)}에 ${if (card.isPhoto) "만들었어요" else "뽑았어요"}",
                    color = Color(0xFF9E8B84),
                    fontSize = 12.sp,
                )
            }
```

```kotlin
            val rows = if (card.isPhoto) {
                card.photoDetailRows(likeness = (mine as? OwnedCard.Photo)?.card?.likeness, total = of)
            } else {
                card.detailRows(total = of, code = (mine as? OwnedCard.Drawn)?.card?.codeText)
            }
            rows.forEach { row -> /* 기존 Row 그대로 */ }
```

플레이버 `Text(card.flavor …)` 앞의 `Spacer` 와 함께 `if (card.flavor.isNotBlank()) { … }` 로 감싼다.

- [ ] **Step 7: `CopyStrip` 을 `OwnedCard` 로**

```kotlin
@Composable
private fun CopyStrip(
    owned: List<OwnedCard>,
    picked: Int,
    onPick: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        owned.forEachIndexed { i, card ->
            // 누끼는 조립해서 굽고, 포토는 받아 둔 그림을 작게 읽는다.
            val thumb = when (card) {
                is OwnedCard.Drawn -> rememberComposedCard(card.card, width = COPY_THUMB_PX)
                is OwnedCard.Photo -> rememberCardImage(card.file?.let { CardArt.Local(it) }, sample = 8)
            }
            Box(
                Modifier
                    .height(52.dp)
                    .aspectRatio(if (card is OwnedCard.Photo) PHOTO_RATIO else COPY_THUMB_RATIO)
                    // … 나머지 그대로
```

- [ ] **Step 8: 액자 그림 — `ComposedCard.kt` 끝에 더한다**

```kotlin
/**
 * 방 액자에 넣을 그림 한 장. 누끼 카드면 조립하고, **포토 카드면 받아 둔 파일을 그대로** 읽는다.
 *
 * 둘 다 null 이면 null — 액자는 발자국으로 돌아간다.
 */
@Composable
fun rememberFramePicture(drawn: DrawnCard?, photoFile: java.io.File?): ImageBitmap? {
    val composed = rememberComposedCard(drawn)
    val photo = rememberCardImage(photoFile?.let { CardArt.Local(it) }, sample = 2)
    return if (drawn != null) composed else photo
}
```

- [ ] **Step 9: `MainActivity.kt` 호출부 타입만 맞춘다** (포토 배선은 Task 7)

```kotlin
                        onFrame = { card ->
                            frameCardId = card?.id
                            roomStore.saveFrameCardId(card?.id)
                        },
                        onDelete = { card ->
                            scope.launch {
                                if (frameCardId == card.id) {
                                    frameCardId = null
                                    roomStore.saveFrameCardId(null)
                                }
                                when (card) {
                                    is OwnedCard.Drawn -> cards.remove(card.id)
                                    is OwnedCard.Photo -> Unit // Task 7
                                }
                            }
                        },
```

import `com.daengs.app.ui.dex.OwnedCard`.

- [ ] **Step 10: 빌드와 전체 단위 테스트**

Run: `export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" && ./gradlew.bat :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. `DexCardsTest` · `DexSlotsTest` · `CardHolderTest` · `DesignLockTest` · `PhotoCardsTest` 통과.

- [ ] **Step 11: 커밋** (Task 4 와 함께)

```bash
git add app/src/main/java/com/daengs/app/ui/dex/DexSlots.kt app/src/main/java/com/daengs/app/ui/dex/CardArt.kt app/src/main/java/com/daengs/app/ui/dex/CardDexScreen.kt app/src/main/java/com/daengs/app/ui/dogcard/ComposedCard.kt app/src/main/java/com/daengs/app/MainActivity.kt app/src/test/java/com/daengs/app/ui/dex/DexSlotsTest.kt
git commit -m "도감 칸이 누끼 카드만 받을 수 있던 것 — 칸이 가진 카드를 누끼|포토로 넓혀 화면 한 벌을 같이 쓴다 …"
```

---

### Task 6: 포토 만들기 화면 · 머리말 버튼 · 실패 한 줄

**Files:**
- Create: `app/src/main/java/com/daengs/app/ui/dex/PhotoCardMakeScreen.kt`
- Modify: `app/src/main/java/com/daengs/app/ui/dex/CardDexScreen.kt`
- Test: `app/src/test/java/com/daengs/app/ui/dex/PhotoMakeDefaultsTest.kt`

**Interfaces:**
- Consumes: Task 1 `OPEN_PHOTO_MONTHS` · `photoCardFor`; `com.daengs.app.screening.Photo.prepare(context, uri): Result<PreparedPhoto>` (`thumbnail: Bitmap`, `jpeg: ByteArray`, 긴 변 1600 · q90); `DaengsWideButton(label, onClick, modifier, enabled, busy, accent)` · `DaengsTextAction(label, onClick, modifier, tint)`
- Produces:
  - `data class PhotoDog(val id: String, val name: String, val isPrimary: Boolean)`
  - `fun defaultPhotoMonth(start: Int?, today: Int, open: Set<Int> = OPEN_PHOTO_MONTHS): Int`
  - `fun defaultPhotoDog(dogs: List<PhotoDog>): PhotoDog?`
  - `@Composable fun PhotoCardMakeScreen(startMonth: Int?, dogs: List<PhotoDog>, busy: Boolean, error: String?, onSubmit: (month: Int, dog: PhotoDog, jpeg: ByteArray) -> Unit, onCancel: () -> Unit)`
  - `CardDexScreen` 새 인자: `makePhoto: (@Composable (startMonth: Int?, onDone: () -> Unit) -> Unit)? = null` · `onMakePhotoBlocked: (() -> Unit)? = null` · `photoFailure: PhotoCard? = null` · `onDismissPhotoFailure: ((PhotoCard) -> Unit)? = null`

- [ ] **Step 1: 실패하는 테스트**

```kotlin
package com.daengs.app.ui.dex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoMakeDefaultsTest {

    @Test
    fun `잠긴 칸을 눌러 왔으면 그 달이다`() {
        assertEquals(4, defaultPhotoMonth(start = 4, today = 9))
    }

    @Test
    fun `이번 달이 열려 있으면 이번 달이다`() {
        assertEquals(9, defaultPhotoMonth(start = null, today = 9))
    }

    /** 닫힌 달을 기본으로 고르면 누르자마자 404 가 난다. */
    @Test
    fun `이번 달이 닫혔으면 열린 첫 달이다`() {
        assertEquals(4, defaultPhotoMonth(start = null, today = 1))
        assertEquals(4, defaultPhotoMonth(start = 12, today = 1))
    }

    @Test
    fun `대표 강아지가 먼저다`() {
        val dogs = listOf(PhotoDog("a", "콩이", false), PhotoDog("b", "보리", true))
        assertEquals("b", defaultPhotoDog(dogs)?.id)
        assertEquals("a", defaultPhotoDog(dogs.take(1))?.id)
        assertNull(defaultPhotoDog(emptyList()))
    }
}
```

Run: `export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" && ./gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.ui.dex.PhotoMakeDefaultsTest'` → 컴파일 실패를 본다.

- [ ] **Step 2: `PhotoCardMakeScreen.kt`**

```kotlin
package com.daengs.app.ui.dex

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.screening.Photo
import com.daengs.app.screening.PreparedPhoto
import com.daengs.app.ui.common.DaengsTextAction
import com.daengs.app.ui.common.DaengsWideButton
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 만들기 화면이 고르는 아이. 이름이 제목판에 찍힌다. */
data class PhotoDog(val id: String, val name: String, val isPrimary: Boolean)

/**
 * 처음 골라 둘 달. **닫힌 달을 기본으로 두지 않는다** — 누르자마자 서버가 404 를 준다.
 * 잠긴 칸을 눌러 왔으면 그 달, 아니면 이번 달, 그것도 닫혔으면 열린 첫 달.
 */
fun defaultPhotoMonth(start: Int?, today: Int, open: Set<Int> = OPEN_PHOTO_MONTHS): Int = when {
    start != null && start in open -> start
    today in open -> today
    else -> open.min()
}

fun defaultPhotoDog(dogs: List<PhotoDog>): PhotoDog? = dogs.firstOrNull { it.isPrimary } ?: dogs.firstOrNull()

/**
 * 포토 카드 만들기 — 달 · 아이 · 사진 한 장.
 *
 * **사진을 기기에서 긴 변 1600 JPEG 로 줄여 보낸다** (`Photo.prepare`). 서버도 어차피 그만큼
 * 줄이므로 20MB 원본을 올릴 이유가 없다. 원형 틀에 맞추기(누끼 뽑기)는 없다 — 서버가
 * 사진 전체를 보고 강아지를 찾는다.
 */
@Composable
fun PhotoCardMakeScreen(
    startMonth: Int?,
    dogs: List<PhotoDog>,
    busy: Boolean,
    error: String?,
    onSubmit: (month: Int, dog: PhotoDog, jpeg: ByteArray) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var month by remember { mutableStateOf(defaultPhotoMonth(startMonth, LocalDate.now().monthValue)) }
    var dog by remember(dogs) { mutableStateOf(defaultPhotoDog(dogs)) }
    var picked by remember { mutableStateOf<PreparedPhoto?>(null) }
    var reading by remember { mutableStateOf(false) }
    var pickError by remember { mutableStateOf<String?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        reading = true
        scope.launch {
            Photo.prepare(context, uri)
                .onSuccess { picked = it; pickError = null }
                .onFailure { pickError = it.message ?: "사진을 읽지 못했어요." }
            reading = false
        }
    }
    BackHandler(enabled = !busy) { onCancel() }

    PhotoCardMakeContent(
        month = month,
        onMonth = { month = it },
        dogs = dogs,
        dog = dog,
        onDog = { dog = it },
        preview = picked?.thumbnail?.asImageBitmap(),
        busy = busy || reading,
        error = error ?: pickError,
        onPick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onSubmit = {
            val chosen = dog
            val photo = picked
            if (chosen != null && photo != null) onSubmit(month, chosen, photo.jpeg)
        },
        onCancel = onCancel,
    )
}

@Composable
private fun PhotoCardMakeContent(
    month: Int,
    onMonth: (Int) -> Unit,
    dogs: List<PhotoDog>,
    dog: PhotoDog?,
    onDog: (PhotoDog) -> Unit,
    preview: ImageBitmap?,
    busy: Boolean,
    error: String?,
    onPick: () -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(CreamBg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text("포토 카드 만들기", color = TextDark, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("사진 한 장으로 그 달의 카드를 그려 드려요. 1분쯤 걸려요.", color = TextMuted, fontSize = 13.sp)

        Spacer(Modifier.height(20.dp))
        Text("어느 카드로 만들까요?", color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OPEN_PHOTO_MONTHS.sorted().forEach { m ->
                val card = photoCardFor(m) ?: return@forEach
                Choice("${m}월\n${card.name}", on = m == month) { onMonth(m) }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("어느 아이인가요?", color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            dogs.forEach { d -> Choice(d.name, on = d.id == dog?.id) { onDog(d) } }
        }

        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CardWhite)
                .clickable(enabled = !busy, onClick = onPick),
            contentAlignment = Alignment.Center,
        ) {
            if (preview != null) {
                Image(preview, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text("＋ 사진 고르기", color = DaengPink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        // 서버 README 「정면 사진 안내」 — 엎드린 옆모습 사진에서 닮음이 떨어졌다.
        Text("얼굴이 정면으로 잘 보이는 사진이 잘 나와요", color = TextMuted, fontSize = 12.sp)

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = TextDark, fontSize = 13.sp)
        }

        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth()) {
            DaengsWideButton("다른 사진", onPick, Modifier.weight(1f), enabled = !busy)
            Spacer(Modifier.size(10.dp))
            DaengsWideButton(
                "이 사진으로 만들기",
                onSubmit,
                Modifier.weight(1f),
                enabled = preview != null && dog != null,
                busy = busy,
                accent = true,
            )
        }
        Spacer(Modifier.height(6.dp))
        DaengsTextAction("그만두기", onCancel, tint = TextMuted)
    }
}

@Composable
private fun Choice(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (on) CardWhite else TextDark,
        fontSize = 13.sp,
        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) DaengPink else CardWhite)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Preview(showBackground = true, heightDp = 760)
@Composable
private fun PhotoCardMakeContentPreview() {
    PhotoCardMakeContent(
        month = 9, onMonth = {},
        dogs = listOf(PhotoDog("a", "콩이", true), PhotoDog("b", "보리", false)),
        dog = PhotoDog("a", "콩이", true), onDog = {},
        preview = null, busy = false,
        error = "오늘은 카드를 더 만들 수 없어요. 내일 다시 시도해 주세요.",
        onPick = {}, onSubmit = {}, onCancel = {},
    )
}
```

(`Bitmap` import 는 쓰지 않으면 지운다.)

- [ ] **Step 3: `CardDexScreen` 에 만들기·막기·실패 한 줄**

시그니처에 Interfaces 의 네 인자를 더한다. 상태:

```kotlin
    // 포토 만들기도 뽑기처럼 **도감 위에 덮는다.** 잠긴 칸에서 왔으면 그 달을 들고 간다.
    var makingPhoto by remember { mutableStateOf(false) }
    var makingMonth by remember { mutableStateOf<Int?>(null) }
    val startMake: (Int?) -> Unit = { month ->
        onMakePhotoBlocked?.invoke() ?: run {
            makingMonth = month
            makingPhoto = true
        }
    }
```

`BackHandler` 의 `when` 맨 앞에 `makingPhoto -> makingPhoto = false`. `if (drawing && draw != null) { … }` 바로 아래:

```kotlin
    if (makingPhoto && makePhoto != null) {
        makePhoto(makingMonth) { makingPhoto = false }
        return
    }
```

`DexGrid` 에 인자 `onMakePhoto: (() -> Unit)?` · `onLockedPhoto: (DexCard) -> Unit` · `photoFailure: PhotoCard?` · `onDismissPhotoFailure: ((PhotoCard) -> Unit)?` 를 더하고 `CardDexScreen` 에서:

```kotlin
            onMakePhoto = makePhoto?.let { { startMake(null) } },
            // **안 연 달은 막는다** — 누르면 서버가 404 를 준다. 연 달이면 그 달로 만들러 간다.
            onLockedPhoto = { card ->
                if (card.no in OPEN_PHOTO_MONTHS && makePhoto != null) startMake(card.no)
                else removedNote = "준비 중인 달이에요"
            },
            photoFailure = photoFailure,
            onDismissPhotoFailure = onDismissPhotoFailure,
```

`DexGrid` 의 `GridCard(onOpen = …)`:

```kotlin
                onOpen = { if (slot.locked && slot.card.isPhoto) onLockedPhoto(slot.card) else onOpen(index) },
```

`DexHeader` 에 인자 `onMakePhoto: (() -> Unit)?` · `failure: PhotoCard?` · `onDismissFailure: ((PhotoCard) -> Unit)?` 를 넘기고(`DexGrid` 가 `deck == DexDeck.Photo` 일 때만 넘긴다), 「＋ 카드 뽑기」 블록 아래에:

```kotlin
        onMakePhoto?.let { go ->
            Spacer(Modifier.height(12.dp))
            Text(
                "＋ 포토 카드 만들기",
                color = DaengPink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardWhite)
                    .clickable(onClick = go)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        // **뒤에서 실패한 카드는 칸에 안 넣고 여기 한 줄로 알린다.** 실패는 하루 한도에 안 센다.
        failure?.let { failed ->
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    photoFailureText(failed.month, failed.errorCode),
                    color = TextDark,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                onDismissFailure?.let { dismiss ->
                    Text(
                        "확인",
                        color = DaengPink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { dismiss(failed) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
```

`@Preview` — `DexHeader` 가 private 이면 같은 파일에 `DexHeaderPhotoPreview` 를 더한다:

```kotlin
@Preview(showBackground = true, backgroundColor = 0xFFF6E9E3)
@Composable
private fun DexHeaderPhotoPreview() {
    DexHeader(
        deck = DexDeck.Photo, onDeck = {}, kinds = 1, of = 12, total = 2, onClose = {},
        onMakePhoto = {},
        failure = com.daengs.app.dogcard.photo.PhotoCard(
            "x", null, 9, "콩이", "CHUSEOK 콩이",
            com.daengs.app.dogcard.photo.PhotoCardStatus.Failed, "no_image", null, 0L,
        ),
        onDismissFailure = {},
    )
}
```

- [ ] **Step 4: 빌드·테스트**

Run: `export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" && ./gradlew.bat :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 전체 통과

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/daengs/app/ui/dex/PhotoCardMakeScreen.kt app/src/main/java/com/daengs/app/ui/dex/CardDexScreen.kt app/src/test/java/com/daengs/app/ui/dex/PhotoMakeDefaultsTest.kt
git commit -m "포토 탭에 카드를 만들 입구가 없던 것 — 달·아이·사진을 고르는 화면과 실패 한 줄을 단다 …"
```

---

### Task 7: `MainActivity` 배선

**Files:**
- Modify: `app/src/main/java/com/daengs/app/MainActivity.kt`

**Interfaces:**
- Consumes: Task 3 `PhotoCardHolder` · `PhotoCardFiles` · `HttpPhotoCardRemote`; Task 5 `rememberFramePicture` · `OwnedCard`; Task 6 `PhotoCardMakeScreen` · `PhotoDog` · `CardDexScreen` 새 인자

- [ ] **Step 1: 홀더를 `cards` 옆에 둔다** (359행 아래)

```kotlin
                // 서버가 그려 준 포토 카드. **정본은 서버라** 기기에는 완성 그림만 둔다
                // (`PhotoCardHolder`). `freshToken` 뒤여야 잡힌다 — 위 `cards` 와 같은 이유.
                val photos = remember {
                    PhotoCardHolder(
                        remote = HttpPhotoCardRemote,
                        files = PhotoCardFiles(java.io.File(context.filesDir, "photo-cards")),
                        accessToken = freshToken,
                    )
                }
```

- [ ] **Step 2: 로그인 직후 받고, 로그아웃이면 비운다** — `LaunchedEffect(session)` 안

`session == null` 갈래(`ocrConsent = false` 다음 줄):

```kotlin
                        // 남의 포토 카드 그림이 다음 사람에게 남으면 안 된다 (로그아웃·탈퇴 모두 여기).
                        photos.forget()
```

로그인 갈래 끝 `cards.load(session?.appUserId)` 다음:

```kotlin
                    // 포토 카드. **실패해도 조용하다** — 도감을 열 때 다시 받는다.
                    photos.load()
```

- [ ] **Step 3: 도감을 열 때 다시 받고, 만드는 중이면 앞에 있는 동안 조회한다**

`LaunchedEffect(session)` 블록 아래에:

```kotlin
                // 도감을 열 때마다 목록을 맞춘다 — 다른 기기에서 만든 카드가 여기서 들어온다.
                LaunchedEffect(screen == Screen.Dex, session?.appUserId) {
                    if (screen == Screen.Dex && session != null) photos.load()
                }
                // **앱이 앞에 있는 동안만** 다시 묻는다. 뒤로 가면 멈추고, 돌아오면 이어서 묻는다.
                val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
                LaunchedEffect(photos.generating) {
                    if (!photos.generating) return@LaunchedEffect
                    lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                        photos.pollWhileGenerating()
                    }
                }
```

`LocalLifecycleOwner` 가 `androidx.lifecycle.compose` 에 없으면(의존성 확인: `grep -r "lifecycle.compose\|LocalLifecycleOwner" app/src/main`) 이 저장소가 쓰는 쪽(`androidx.compose.ui.platform.LocalLifecycleOwner`)으로 바꾼다. `repeatOnLifecycle` 은 `androidx.lifecycle.repeatOnLifecycle` import.

- [ ] **Step 4: 액자** (905행)

```kotlin
                        // 액자 그림. 고른 카드가 지워졌으면 못 찾고, 그때는 발자국이다.
                        // 누끼 카드에서 못 찾으면 포토 카드의 받아 둔 그림을 본다.
                        framePicture = rememberFramePicture(
                            drawn = cards.cards.firstOrNull { it.id == frameCardId },
                            photoFile = frameCardId?.let { photos.images[it] },
                        ),
```

import `com.daengs.app.ui.dogcard.rememberFramePicture`.

- [ ] **Step 5: 도감 호출부** (1285행 `CardDexScreen(`)

인자 더하기:

```kotlin
                        photos = photos.cards,
                        photoFiles = photos.images,
                        photoFailure = photos.latestFailure,
                        // 「확인」 = 서버 행을 지운다. 실패 행이 남아 있으면 다음에도 같은 줄이 뜬다.
                        onDismissPhotoFailure = { failed -> scope.launch { photos.remove(failed.id) } },
                        // 강아지가 없으면 뽑기와 같은 문을 연다. **로그인 전은 여기서 안 막는다** —
                        // `PetNeed` 에 로그인 갈래가 없어서, 만들기 화면에서 「로그인하면 포토 카드를
                        // 만들 수 있어요」(`PhotoCardHolder.create`)가 뜨게 둔다.
                        onMakePhotoBlocked = if (waitsForPet && session != null) {
                            { petNeed = PetNeed.Card }
                        } else {
                            null
                        },
                        makePhoto = { startMonth, done ->
                            LaunchedEffect(Unit) { photos.clearCreateError() }
                            PhotoCardMakeScreen(
                                startMonth = startMonth,
                                dogs = pets.pets.orEmpty().map { PhotoDog(it.id, it.name, it.isPrimary) },
                                busy = photos.creating,
                                error = photos.createError,
                                onSubmit = { month, dog, jpeg ->
                                    scope.launch {
                                        if (photos.create(month, dog.name, dog.id, jpeg)) done()
                                    }
                                },
                                onCancel = done,
                            )
                        },
```

`PetNeed` 는 `ui/home/PetGate.kt` 에 있고 `Walk · Chat · Card …` 뿐이다(로그인 갈래 없음 — 2026-09-15 확인).
`LocalLifecycleOwner` 는 `androidx.lifecycle.compose.LocalLifecycleOwner`, `repeatOnLifecycle` 은
`androidx.lifecycle.repeatOnLifecycle` 이다 (`NaverTravelHeadingLayer.kt` 가 같은 조합을 쓴다).

`onDelete` 의 포토 갈래를 채운다 — **서버가 지운 뒤에만 액자를 비운다**:

```kotlin
                        onDelete = { card ->
                            scope.launch {
                                val gone = when (card) {
                                    is OwnedCard.Drawn -> { cards.remove(card.id); true }
                                    is OwnedCard.Photo -> photos.remove(card.id)
                                }
                                // **액자를 먼저 비우던 것을 지운 뒤로 옮겼다.** 포토는 서버가 못 지우면
                                // 카드가 남으므로, 그때 액자만 비면 걸려 있던 그림이 사라진다.
                                if (gone && frameCardId == card.id) {
                                    frameCardId = null
                                    roomStore.saveFrameCardId(null)
                                }
                            }
                        },
```

포토 지우기 실패(`photos.error`)는 도감에 한 줄로 떠야 한다. `CardDexScreen` 의 `removedNote` 가 성공 후에만 뜨므로, 이 호출부에서 `LaunchedEffect(photos.error) { photos.error?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); photos.clearError() } }` 를 `Screen.Dex` 갈래 앞에 둔다 (이 저장소에 이미 Toast 를 쓰는 자리가 있는지 `grep -n "Toast" MainActivity.kt` 로 보고 같은 방식을 따른다).

⚠️ `CardDexScreen` 의 `deleteAndTell` 은 `go(card)` 직후 「삭제되었습니다」를 띄운다. 포토는 비동기라 실패해도 그 문장이 먼저 뜬다 — 이 PR 에서는 받아들이고 **PR 「남은 것」에 적는다** (서버 실패는 드물고, 실패 문장이 바로 뒤따른다).

- [ ] **Step 6: 빌드·테스트**

Run: `export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" && ./gradlew.bat :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 전체 통과

- [ ] **Step 7: 커밋**

```bash
git add app/src/main/java/com/daengs/app/MainActivity.kt
git commit -m "포토 카드 부품이 앱에 이어져 있지 않던 것 — 로그인·도감·액자·지우기·로그아웃에 홀더를 잇는다 …"
```

---

### Task 8: 실기기 확인 · 문서

**Files:**
- Modify: `STATUS.md` (지금 되는 것 목록에 한 줄)
- PR #414 본문 (`gh pr edit 414 --repo SAJOYO/DAENGS_APP --body-file <파일>`)

- [ ] **Step 1: 설치하고 `Success` 를 직접 본다**

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" && ./gradlew.bat :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
출력에 `Success` 가 없으면 멈추고 adb 인증부터 본다 (메모리 `phone-wireless-adb`).

- [ ] **Step 2: 앱이 앞에 떠 있는지 보고 찍는다**

`adb shell dumpsys window | grep mCurrentFocus` 에 `com.daengs.app` 이 보일 때만 스크린샷.

- [ ] **Step 3: 흐름을 한 바퀴** — 로그인된 계정, 개발 서버

1. 방 → 액자 → 도감 → 「포토」 탭: 열두 칸 잠김, 머리말 「포토 속 우리 아이」
2. 잠긴 1월 칸 → 「준비 중인 달이에요」 / 잠긴 4월 칸 → 만들기 화면이 4월로 열림
3. 사진 고르기 → 「이 사진으로 만들기」 → 도감으로 돌아와 4월 칸 「만드는 중…」
4. 1분 안에 완성 그림으로 바뀜 → 칸 표지 · 확대 뷰 문지르기/기울이기 포일 · 설명 시트 줄
5. 갤러리에 저장 → 사진첩 `Pictures/댕스` 에 5:8 그림 / 공유 시트
6. 방 액자에 걸기 → 방에서 액자 그림이 포토 카드 (**화면 크기에서** 본다)
7. 다시 만들기 → 하루 한도 문장이 만들기 화면에 뜸 (한도 1)
8. 지우기 → 칸 다시 잠김, 액자 발자국
9. 야채·과일 탭이 전과 같은지 (뽑기 · 확대 · 저장 · 액자)

- [ ] **Step 4: `STATUS.md` 한 줄 · PR 본문 갱신 · draft 해제 여부는 사용자에게 묻는다**

- [ ] **Step 5: 커밋**

```bash
git add STATUS.md
git commit -m "포토 탭이 지금 어디까지 되는지 적혀 있지 않던 것 — STATUS 에 한 줄 …"
```

---

## Self-review 메모 (계획 작성자)

- spec §2 카탈로그 → Task 1 · §3 데이터 → Task 2·3 · §4 화면 → Task 4·5 · §5 만들기 → Task 6·7 · §6 색 → Global Constraints · §7 테스트 → 각 태스크 · 실기기 → Task 8.
- spec §5 「이미 만드는 중(앱이 안다) → 막는다」: 서버 409 문장이 만들기 화면에 뜨는 것으로 대신한다(`create` 실패 문장). 따로 막지 않는다 — 앱이 아는 상태가 늦을 수 있어 서버 판단이 정확하다.
- 이름 일관성: `OwnedCard.Photo.pending` · `PhotoCardHolder.images` · `photoFiles` 인자 · `rememberFramePicture` · `PHOTO_FOIL` · `PHOTO_TUNE` · `PHOTO_RATIO` 가 태스크 사이에서 같다.
