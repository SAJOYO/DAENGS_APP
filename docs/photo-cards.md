# 도감 포토 탭 — 서버가 그린 카드를 달별 칸으로 모은다

2026-09-15 · PR (이 문서와 같은 브랜치 `feat/photo-cards`) · 서버 계약 `SAJOYO/DAENGS_dev` #537 (D-076)

## 목표

백엔드가 연 `/app/ai-cards` 로 **강아지 사진 한 장 → 달별 카드 한 장(994×1582 PNG)** 을 만들고,
도감의 세 번째 탭 **「포토」** 에 모은다. 만드는 길은 다르지만 **만든 뒤에는 야채·과일 카드와 똑같이
작용한다** — 칸·잠금·×N·확대 뷰·포일·설명 시트·저장·공유·방 액자·지우기.

기존 누끼 카드(폰 안에서 얼굴을 따 틀에 끼우는 것)는 그대로 둔다.

## 정한 것 (2026-09-15 사용자 결정)

| # | 항목 | 결정 | 기각한 것 |
| --- | --- | --- | --- |
| 1 | 자리 | 도감에 탭 추가 | 별도 앨범 화면 · 폰 사진첩에만 저장 |
| 2 | 이름 | **포토** (머리말 「포토 속 우리 아이」) | AI 카드 · 열두 달 · 달력 · 화보 · 스페셜 등 |
| 3 | 칸 | **달마다 한 칸, 열두 칸.** 같은 달을 또 만들면 ×N | 만든 순서대로 늘어놓는 목록 |
| 4 | 달 고르기 | **앱에 연 달 목록을 적는다** (지금 4월·9월). 서버가 닫았으면 404 문장을 띄운다 | 이번 달만 · 서버에 달 목록 API 추가 |
| 5 | 동작 | 저장·공유 · 지우기 · 방 액자 · 포일 **전부** | 일부만 |
| 6 | 코드 구조 | **A안** — 칸이 가진 카드를 `OwnedCard`(누끼 \| 포토)로 넓혀 화면 한 벌을 같이 쓴다 | B안(포토 전용 화면) · C안(Room 표에 끼우기 — 누끼 동기화가 `/app/cards` 로 잘못 올린다) |
| 7 | 포일 조정 | 이번엔 기본값만. **포토 포일은 표 한 곳에 모아** 나중에 따로 손본다 | 이번 PR 에서 효과 조정 |

## 1. 서버 계약 (저쪽이 원본)

| 요청 | 응답 |
| --- | --- |
| `POST /app/ai-cards?month=&dog_name=&dog_id=` · 본문 사진 원시 바이트 · `Content-Type: image/jpeg` | `202` · `status: generating` |
| `GET /app/ai-cards` | `{cards: [...]}` 최근 것부터, `image_url` 은 늘 null |
| `GET /app/ai-cards/{id}` | `ready` 면 `image_url` 이 온다 |
| `DELETE /app/ai-cards/{id}` | `204` |

- 오류 본문은 `{"detail": {"code", "message"}}` 이고 **`message` 는 앱이 그대로 띄운다.**
  `409 already_generating` · `429 limit_reached` · `404 month_closed` · `503 unavailable/storage` · `400` 사진.
- 한도(테스트 단계): 사용자별 동시 1장 + KST 하루 완성 1장. 실패는 안 센다.
- 한 장에 30~60초. 서버는 **9분** 지나도 `generating` 이면 조회 때 `failed/interrupted` 로 바꾼다.
- `failed` 의 `error_code`: `upstream` · `no_image` · `unavailable` · `storage` · `internal` · `interrupted`.

## 2. 카탈로그 — 포토 열두 장

`ui/dex/PhotoCards.kt` (새 파일).

- `DexDeck` 에 `Photo("포토")` 를 더한다. 머리말은 `DexDeck` 별 문장 표에 「포토 속 우리 아이」.
- 1~12월을 `DexCard` 열두 장으로 적는다. `id = "photo-01"` … `"photo-12"`, `no = 달`,
  `name` = 서버 카드명(`NEW YEAR` · `LOVE` · `FIRST DAY` · `BLOSSOM` · `HOME TEAM` · `POOL` · `BEACH` ·
  `RAIN` · `CHUSEOK` · `GHOST` · `THANKS` · `SANTA` — 저쪽 `catalog.py`), `ko` = 「4월 벚꽃」 식.
- `OPEN_PHOTO_MONTHS = setOf(4, 9)` — 서버 `DAENGS_CARDIMAGE_MONTHS` 기본값. **서버가 달을 열면 여기 한 줄.**
- **포토 포일 표** `PHOTO_FOIL: Map<Int, PhotoFoil>` — 달 → `Foil` 종류 + `FoilTune` + 강조색.
  이번 값은 임시다. 포토 카드는 전면이 사진 같은 그림이라 `shineOpacity` 를 야채보다 낮게 시작한다.
  **효과를 손볼 때 이 표만 고친다** (결정 7).
- 잠긴 칸 그림: 앱에 달별 틀 그림이 없다(서버에만 있다). 그래서 **5:8 빈 판**(`CardLock` 색 둥근 사각형)에
  자물쇠를 얹는다. 틀 그림 반입은 하지 않는다 — 장당 0.5MB × 12 이고, 잠긴 칸은 어차피 덮인다.
- 설명 시트 줄은 포토 전용: `No. 04 / 12` · `Card BLOSSOM` · `Likeness ★★★★☆` · 만든 날.
  기술·수치·플레이버·에디션 줄은 없다(`detailRows` 를 카드 종류에 따라 가른다).

## 3. 데이터 — `dogcard/photo/`

| 파일 | 책임 |
| --- | --- |
| `PhotoCard.kt` | 서버 한 장의 모양: `id · dogId · month · dogName · title · status · errorCode · likeness · width · height · createdAtMillis` |
| `PhotoCardApi.kt` | `/app/ai-cards` 네 요청 + PNG 받기. `CardApi` 와 같은 배관(`HttpURLConnection`, 오류 `message` 통과) |
| `PhotoCardFiles.kt` | 완성 PNG 보관 `filesDir/photo-cards/{id}.png`. **그림을 받은 뒤에만 칸에 완성으로 뜬다** |
| `PhotoCardHolder.kt` | `mutableStateOf` 홀더 (`CardHolder` 결). 목록·만들기·조회 반복·지우기·탈퇴 정리 |

**정본은 서버다.** Room 표를 만들지 않는다 — 누끼 카드는 오프라인에서 먼저 생겨서 Room 이 필요했지만
포토 카드는 서버에서만 생긴다. 기기에는 **그림 파일만** 둔다(액자·저장·다시 열 때 다시 안 받으려고).

- **목록**: 도감을 열 때와 로그인 직후 `GET /app/ai-cards`. 실패하면 **화면을 비우지 않는다**(`CardHolder.load` 원칙).
- **완성 그림**: `ready` 인데 파일이 없으면 `GET /{id}` → `image_url` 에서 받아 파일로. 서버 목록에 없는
  파일은 지운다(다른 기기에서 지운 카드).
- **만들기**: 사진을 기기에서 긴 변 1600px JPEG(q90)로 줄여 보낸다(서버가 어차피 1600 으로 줄인다 —
  20MB 원본을 올리지 않으려고). 202 를 받으면 목록 맨 앞에 `generating` 으로 넣는다.
- **조회 반복**: `generating` 이 하나라도 있으면 **5초마다** `GET /{id}`. 앱이 앞에 있을 때만 돈다.
  `ready` 가 되면 그림을 받고 멈춘다. 10분이 지나도 안 끝나면 목록을 다시 받는다(서버가 `interrupted` 로 바꿔 둔다).
- **지우기**: 서버 `DELETE` 가 먼저, 그다음 파일. **서버가 실패하면 기기에서도 안 지운다** — 누끼 카드와
  반대다. 포토 카드는 정본이 서버라 기기에서만 지우면 다음 목록에서 되살아난다.
- **로그아웃·탈퇴**: 목록과 `photo-cards/` 폴더를 비운다(서버 행은 저쪽 탈퇴 정리가 지운다).

## 4. 화면 — `OwnedCard` 로 한 벌

```kotlin
sealed interface OwnedCard {
    val id: String; val dogName: String; val madeAtMillis: Long
    data class Drawn(val card: DrawnCard) : OwnedCard
    data class Photo(val card: PhotoCard, val file: File?) : OwnedCard
}
data class DexSlot(val card: DexCard, val owned: List<OwnedCard>)
```

- `dexSlots(drawn, photos)` 가 누끼는 `templateId`, 포토는 `month` 로 칸에 겹친다. 포토 칸에는
  **`ready` 와 `generating` 만** 들어간다. `failed` 는 칸에 안 넣는다(§5).
- **그림 얻기만 갈린다.** `rememberOwnedCardArt(owned)`:
  누끼 → 지금의 `rememberDrawnCardArt`(틀 + 얼굴 + 글자) / 포토 → 파일 PNG 한 장(합성 없음).
- 그 밖에 `DrawnCard` 를 직접 쥐던 자리를 `OwnedCard` 로 바꾼다 — 그리드 표지 · 확대 뷰 · 사본 줄
  (`CopyStrip`) · 설명 시트 · 저장(`CardShot`, 포토는 `template = null` 로 PNG 그대로) · 액자 ·
  지우기 확인 창. 이머시브·팝아웃은 누끼 카드만 해당이라 포토에서는 안 뜬다(`IMMERSIVE_SCENES` 에 포토 id 가 없다).
- **만드는 중인 장**: 칸 표지·사본 자리에 그 달의 빈 판 + 「만드는 중…」 + 가는 진행 띠. 포일·설명·저장·액자는
  안 열린다. 지우기는 된다(서버가 생성 중 삭제를 받는다).
- **방 액자**: `framePicture` 가 `frameCardId` 로 누끼 목록에서 못 찾으면 포토 목록에서 찾아 파일을 읽는다.
  액자 저장 자리(`roomStore.saveFrameCardId`)는 그대로 id 문자열이다.

## 5. 만들기 흐름

포토 탭 머리말의 「＋ 포토 카드 만들기」 → `PhotoCardMakeScreen`(도감 위에 덮는다, 뽑기와 같은 방식).

```
어느 카드로 만들까요?      [4월] [9월 ●]     ← OPEN_PHOTO_MONTHS
어느 아이인가요?           [콩이 ▾]                           ← 대표가 먼저
[ 사진 고르기 ]            → 시스템 사진 고르기 → 미리보기
안내 한 줄: 얼굴이 정면으로 잘 보이는 사진이 잘 나와요          ← 서버 README 「정면 사진 안내」
[ 이 사진으로 만들기 ]     → POST → 도감 포토 탭으로 돌아가 그 달 칸이 「만드는 중…」
```

- **막는 것** (버튼 자리는 남기고 누르면 이유를 말한다 — `CardDexScreen` 의 `onDrawBlocked` 원칙):
  로그인 전 → 「로그인하면 포토 카드를 만들 수 있어요」 · 강아지 없음 → 기존 `PetNeed.Card` 문 ·
  이미 만드는 중(앱이 안다) → 「만들고 있는 카드가 있어요」.
- **POST 오류**는 서버 `message` 를 만들기 화면에 그대로 띄우고 화면을 닫지 않는다.
- **백그라운드 실패**(`failed`)는 포토 탭 머리말 아래 한 줄: 「9월 카드를 만들지 못했어요 · 다시 만들 수 있어요」
  + 「확인」(= 그 행 `DELETE`). 코드별 문장은 앱 표 하나(`photoFailureText`) — `interrupted`·`upstream` 은
  「잠시 뒤 다시 만들어 주세요」, `no_image` 는 「강아지가 잘 보이는 다른 사진으로 해 주세요」.
- 잠긴 칸 중 **안 연 달**을 누르면 「준비 중인 달이에요」 한 줄(지운 알림과 같은 자리·같은 길이).

## 6. 색 · 잠금

- 새 색은 `ui/theme/Color.kt` 에서만 가져온다(design-locks 0절). `CardDexScreen.kt` 에 이미 있는 날것 색은
  이 PR 에서 늘리지 않는다. 포토 강조색 12개는 카탈로그 데이터라 `DexCards.kt` 의 `accent` 와 같은 자리에 둔다.
- 잠긴 디자인 1~5절과 겹치는 화면은 없다.

## 7. 테스트

| 무엇 | 파일 |
| --- | --- |
| 포토 열두 장 · id · 달 · 연 달 ⊂ 1..12 · 포일 표가 열두 달을 다 가짐 | `PhotoCardsTest` |
| `dexSlots` 가 포토를 달로 겹침 · `failed` 제외 · 만드는 중은 잠금 아님 · 누끼 칸 결과 불변 | `DexSlotsTest`(기존에 더함) |
| 응답 파싱 · 오류 `message` 통과 · 쿼리 인코딩(한글 이름) | `PhotoCardApiTest` |
| 조회 반복: generating 있을 때만 · ready 에서 멈춤 · 10분 뒤 목록 다시 | `PhotoCardHolderTest`(가짜 API) |
| 지우기: 서버 실패면 기기도 유지 · 성공이면 파일까지 | 〃 |
| 실패 코드 → 문장 | `PhotoFailureTextTest` |
| 포토 설명 줄 · 저장 파일 이름 | `PhotoCardsTest` |

새 Composable(`PhotoCardMakeScreen` · 만드는 중 표지 · 실패 한 줄)에는 `@Preview`.
**실기기**: 개발 서버에 #537 이 배포되고 마이그레이션·키가 들어갔는지 먼저 확인하고, 4월 한 장을 만들어
칸 · 포일 · 저장 · 액자 · 지우기를 화면 크기에서 본다.

## 8. 만든 뒤 보여 주기 (2026-09-15 추가 — 실기기 확인 뒤 사용자 결정)

실기기에서 본 문제 두 가지를 고친다.

1. **만들기를 누르면 결과 없이 도감으로 돌아가 칸에 「만드는 중…」만 뜬다.** 누끼 카드는 뽑으면
   그 자리에서 뒤집히고 결과 화면이 뜨는데, 포토 카드는 아무 일도 안 일어난 것처럼 보인다.
2. **완성돼도 도감을 나갔다 들어와야 칸이 바뀐다.** 조회 반복(§3)이 있는데도 화면이 안 바뀌었다.

### 정한 것

| # | 항목 | 결정 | 기각한 것 |
| --- | --- | --- | --- |
| 8 | 기다리는 자리 | **둘 다** — 만들기 화면에서 기다리면 그 자리에서 뒤집히고, 「다 되면 알려 주세요」로 나가면 도감에서 알린다 | 만들기 화면에서만 · 도감으로 돌아가 칸이 저절로 열림 |
| 9 | 도감에서 완성 알림 | 한 줄 알림 「9월 카드가 완성됐어요 · 보기」. 누르면 뒤집기 → 결과. **화면을 저절로 바꾸지 않는다** | 완성 순간 칸을 자동으로 열기(다른 카드를 보던 중 화면이 튄다) |
| 10 | 연출 | 누끼 뽑기와 **같은 결** — 뒷면 → 0.76초 뒤집기(90도에서 앞면으로) → 결과 화면 | 포토만의 다른 연출 |

### 8.1 먼저 원인 — 완성돼도 칸이 안 바뀐다

코드만 읽어서는 원인이 안 보인다(앞에 있는 동안 5초마다 조회하고 `cards`·`images` 를 바꾼다). **짐작으로 고치지
않는다.** 조회 반복의 시작·멈춤, 한 번 조회할 때마다의 상태, 그림 받기 결과를 로그로 남기고, 가짜 서버를 끼운
화면 테스트로 먼저 재현한다. 재현되면 그 테스트가 원인 수정의 회귀 테스트가 된다. 재현이 안 되면 실기기
logcat 으로 확인한다(카드 생성 비용이 들어 사용자에게 먼저 묻는다).

### 8.2 만들기 화면에서 기다리기

- 「이 사진으로 만들기」가 성공(202)하면 **화면을 닫지 않고 「그리는 중」 단계로 바꾼다.**
  - 서버 카드 비율(994×1582)의 **뒷면**이 은은하게 반짝이고, 아래에 가는 진행 띠와 「안녕의 9월 카드를 그리는 중이에요」.
  - 버튼 하나: 「다 되면 알려 주세요」 — 도감으로 돌아간다(§8.3).
  - 뒤로가기도 같은 뜻이다(요청은 이미 서버에 들어갔다 — 취소가 아니다).
- 그 카드가 `ready` 이고 **그림을 받은 뒤** 뒤집기 → **결과 화면**.
  - 결과: 카드 · 「9월 추석」 · 「도감에서 보기」 · 갤러리에 저장 · 공유 (누끼 `ResultBody` 와 같은 배치).
- 그 카드가 `failed` 면 그 자리에서 `photoFailureText` 한 줄 + 「다시 만들기」(사진 고르기 단계로).
  실패 행은 「다시 만들기」·나가기 때 지운다(도감 머리말의 실패 한 줄과 같은 처리).
- POST 자체가 거절되면(409·429·404·400) 지금처럼 만들기 화면에 서버 문장을 띄우고 단계를 안 바꾼다.

### 8.3 나가면 도감에서

- 만드는 중인 칸의 표지는 어두운 빈 판 대신 **§8.2 와 같은 반짝이는 뒷면** + 「만드는 중…」.
- 사용자가 결과를 아직 안 본 완성 카드가 생기면 도감 아래에 한 줄 알림 **「9월 카드가 완성됐어요 · 보기」**.
  누르면 §8.2 와 같은 뒤집기 → 결과가 도감 위에 뜬다. 안 눌러도 칸은 완성 카드로 바뀐다.
- **「결과를 안 본 완성 카드」 는 기기에 기억한다** (`unrevealed` id 집합). 만든 직후 그 id 를 넣고, 결과 화면을
  보면(만들기 화면이든 도감이든) 뺀다. 방에 있는 동안 완성되면 다음에 도감을 열 때 알림이 뜬다.
  로그아웃하면 비운다. 목록에 없는 id 는 버린다(다른 기기에서 지운 카드).

### 8.4 부품

| 파일 | 책임 |
| --- | --- |
| `ui/dex/PhotoReveal.kt` (새) | `PhotoCardBack`(반짝이는 뒷면) · `PhotoFlip`(뒤집기) · `PhotoResult`(결과 화면). 만들기 화면과 도감이 같이 쓴다 |
| `dogcard/photo/PhotoCardHolder.kt` | `unrevealed: Set<String>` · `markRevealed(id)` · 만든 직후 기록 · `forget()` 에서 비움 |
| `dogcard/photo/PhotoRevealStore.kt` (새) | `unrevealed` 를 기기에 남기는 작은 저장소 (SharedPreferences, 계정별 키) |
| `ui/dex/PhotoCardMakeScreen.kt` | 사진 고르기 → 그리는 중 → 뒤집기 → 결과 / 실패 단계 |
| `ui/dex/CardDexScreen.kt` | 만드는 중 칸 뒷면 · 완성 알림 한 줄 · 알림에서 여는 결과 오버레이 |

- 누끼 뽑기의 `FlipToCard`·`ResultBody` 는 누끼 틀(`CardTemplate`)에 묶여 있어 **합치지 않는다.** 연출 길이·배치
  값만 같게 둔다.
- 새 Composable 에 `@Preview`, 새 색은 테마에서만.

### 8.5 테스트

| 무엇 | 파일 |
| --- | --- |
| 조회로 완성되면 칸이 바뀐다 (§8.1 재현 → 회귀) | 원인에 따라 `PhotoCardHolderTest` 또는 화면 테스트 |
| 만든 직후 `unrevealed` 에 들어가고, 결과를 보면 빠지고, 로그아웃이면 비고, 목록에 없는 id 는 버려진다 | `PhotoCardHolderTest` |
| 만들기 화면 단계 전이: 제출 성공 → 그리는 중 → (ready+그림) 뒤집기 → 결과 / failed → 실패 | 단계 판정을 순수 함수로 빼서 단위 테스트 |


- 포일·기울기 효과 조정 (결정 7 — 표만 마련)
- 서버 달 목록 API · 자동 재시도 · 알림(푸시)으로 완성 알리기
- 새 기기 오프라인 열람 (목록은 서버에서 받는다)
- 제품 규칙(몇 장·어떤 조건) — 서버 `check_quota` 가 정한다
