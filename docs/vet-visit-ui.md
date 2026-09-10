# 저장소 탭 진료비 — 구현 계획 (APP#258)

> **작업자에게:** 이 계획은 태스크 단위로 실행한다. 단계는 체크박스(`- [ ]`)다.
> superpowers:subagent-driven-development 또는 superpowers:executing-plans 를 쓴다.

**목표:** 영수증을 찍으면 진료비가 저장소 탭에 남는다 — 기계가 읽고, 사람이 확정한다.

**구조:** `care/` 에 API·모델·코디네이터(서버 상태), `ui/storage/` 에 화면.
`CareApi`·`CareLogCoordinator`·`CareLogSection` 셋의 모양을 그대로 따른다 — 같은 탭에
나란히 서는 기능이라 배관이 다르면 읽는 사람이 두 규칙을 외워야 한다. 다른 점은 **사진
바이트를 올리는 걸음이 하나 더 있다**는 것뿐이고, 그 걸음은 `ScreeningRecordApi.upload`
가 이미 푼 문제라 그쪽을 본뜬다.

**기술:** Kotlin · Jetpack Compose · `HttpURLConnection` + `org.json` · JUnit4 · Robolectric.
새 의존성을 더하지 않는다.

**스펙:** PR [SAJOYO/DAENGS_APP#258](https://github.com/SAJOYO/DAENGS_APP/pull/258) 본문 ·
저쪽 `docs/vet-visits.md` · `schemas/vet_visit.py` · `routers/vet_visit.py` (SAJOYO/DAENGS_dev#353,
`dev` 에 배포됨 2026-09-10).

---

## 전역 제약

이 절은 **모든 태스크의 요구사항에 묵시적으로 들어간다.**

- **베이스 주소는 `BuildConfig.API_BASE_URL`.** 비어 있으면 호출 전에
  `check(configured)` 로 막는다 — 문구는 `CareApi` 와 **한 글자도 다르지 않게**:
  `"서버 주소가 없습니다. local.properties 의 daengs.apiBaseUrl 을 채우세요."`
- **토큰을 들고 있지 않는다.** 매 호출에 인자로 받는다 (`MainActivity` 의 `freshToken`).
- **실패는 `ChatApiError`** — 상태 코드를 든다. 닿지 못하면 `status = 0`.
- **한글 사유 표시명 17개를 앱에 하드코딩하지 않는다.** 코드→표시명은 서버가 준
  `reason_options` 가 유일한 출처다 (저쪽 docs §1 이 닫힌 목록으로 막으려던 드리프트가
  다시 새는 자리).
- **업로드는 `image/jpeg` 로 고정.** 상한 12 MiB. Content-Type 이 티켓과 다르면 저쪽이
  415 로 막는다. **이 숫자는 Task 1 이 `MAX_RECEIPT_BYTES` 상수로 두고 Task 2 의 `upload`
  가 올리기 전에 잰다** — 다 보내고 나서 413 을 받으면 대역폭이 이미 나갔다.

- **확정 본문의 글자 수 상한도 저쪽과 같은 숫자로 앱이 막는다** — `hospital_name` 60 ·
  `hospital_address` 200 · `reason_detail` 60 · `total_krw` ≤ 100,000,000. Task 1 이
  상수로 두고 Task 4 의 입력칸이 쓴다. 넘겨 보내면 FastAPI 검증 422 가 오는데 그 `detail`
  은 **배열**이라 `ChatApiError.from` 이 "요청 형식이 맞지 않아요. **앱을 업데이트해
  주세요.**" 로 떨어진다 — OCR 이 읽어 온 긴 병원 주소 하나 때문에 유저에게 앱을
  업데이트하라고 말하게 된다.
- **전화번호 모양은 `^[0-9]{2,4}(-[0-9]{3,4}){1,2}$`.** 서버 DB CHECK 와 같은 정규식이고,
  앱도 같은 모양으로 막는다 — 이 칸은 `tel:` 링크가 되어 사람이 눌러 전화를 건다.
- **Composable 을 새로 만들거나 고치면 `@Preview` 를 붙인다** (CLAUDE.md).
- **커밋 메시지는 한글 서술형, 접두사 없음.** 제목은 무엇이 어떻게 잘못돼 있었는지(`~던 것`),
  본문에 왜 그렇게 고쳤는지와 무엇을 재봤는지.
- 검증 명령: `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest`

---

## 이 계획이 정한 것 (PR 본문에 없어서 여기서 정한다)

작업자가 태스크 안에서 다시 고민하지 않도록 먼저 못박는다.

1. **확인 화면의 영수증 그림은 방금 찍은 로컬 비트맵이다.** 응답의 `receipt_image_url`
   을 안 쓴다. 그 주소를 쓰려면 이미지 로더(Coil 등)를 새로 들이거나 바이트를 직접
   받아 와야 하는데, **그 그림은 이미 우리 손에 있다** — 확인 화면은 촬영 직후에만
   열리기 때문이다. 확정된 기록을 나중에 다시 열어 사진을 보는 화면은 이 카드에 없다.

2. **목록의 사유 표시명은 `GET /app/vet-visits/reason-options` 로 그린다.**
   `VetVisitResponse` 에는 `reason_code` 만 실려 오고 표시명이 없다. 강아지를 고를 때
   한 번 받아 `Map<String, String>` 으로 들고, 목록이 그것으로 라벨을 찾는다. 모르는
   코드가 오면 코드를 그대로 보여 준다(서버가 사유를 하나 더 만드는 날 크래시가 아니라
   못생긴 라벨이어야 한다).

3. **`client_event_id` 하나가 초안과 확정을 관통한다.** 저쪽 `confirm_draft` 가
   `get_by_client_event` 를 **제일 먼저** 보므로, 같은 키로 확정을 두 번 눌러도 있던
   기록이 온다. 키는 **촬영이 끝난 순간** 만들고, 그 영수증 흐름이 끝날 때까지 안 바꾼다.

4. **bridge PUT 의 "이미 있다" 는 실패가 아니라 성공으로 읽고 추출로 넘어간다.**
   저쪽이 create-only 라 같은 키로 두 번 쓰면 거절인데, 그 뜻은 그 자리에 이미 온전한
   바이트가 있다는 것이다(반쯤 쓰다 끊긴 파일은 `open_write` 가 지운다). 여기서 새 초안을
   만들면 Gemini 를 한 번 더 부르고 요금이 는다. 그 밖의 실패(연결 끊김·타임아웃)는 PR
   본문대로 새 `client_event_id` 로 새 초안을 만든다.

   ⚠️ **거절 코드가 저장소마다 다르다** — LocalBridge 는 `FileExistsError` → **409**,
   GCS 는 티켓에 실려 오는 `x-goog-if-generation-match: 0` 이 깨져 **412** 다
   (저쪽 `core/storage.py:459`). 둘 다 접지 않으면 저장소를 GCS 로 바꾸는 날 이 판단이
   아무 소리 없이 죽는다.

5. **영수증은 긴 변 2400px 로 굽는다.** `Photo.MAX_EDGE` 는 1600 인데 그건 피부 병변용이고,
   영수증은 항목명이 잔글씨라 1600 에서 뭉갠다. `Photo.prepare` 에 `edge` 인자를 더해
   기존 호출부는 그대로 두고 영수증만 2400 을 넘긴다. q90 JPEG 이면 12 MiB 에 한참 못 미친다.

6. **동의 토글은 만들되 안 보인다.** 저쪽 `OCR_CONSENT_VERSION` 이 `"unset"` 이라 지금
   동의를 받아도 근거가 안 선다. `MyScreen` 에 상수 하나(`OCR_CONSENT_VISIBLE = false`)로
   가려 두고, 판 번호가 정해지면 그 한 줄만 바꾼다.

---

## 파일 구조

| 파일 | 책임 |
| --- | --- |
| `care/VetVisit.kt` (신규) | 도메인 값과 파서만. 네트워크도 상태도 없다 (`CareEvent.kt` 자리) |
| `care/VetVisitApi.kt` (신규) | 여섯 엔드포인트 + bridge PUT. 판단 없음 |
| `care/VetVisitCoordinator.kt` (신규) | 촬영→초안→업로드→추출→확정의 생애, 목록·삭제 |
| `ui/storage/VetVisitSection.kt` (신규) | 저장소 탭의 진료비 칸 (`CareLogSection` 옆) |
| `ui/storage/ReceiptConfirmScreen.kt` (신규) | 확인 화면 — 이 카드의 핵심 |
| `ui/storage/ReceiptPicker.kt` (신규) | 카메라·갤러리에서 집어 JPEG 으로 굽는 자리 |
| `screening/Photo.kt` (수정) | `prepare` 에 `edge` 인자 |
| `auth/AuthApi.kt` · `auth/Session.kt` (수정) | `setOcrConsent` · `AppMe.ocrConsent` |
| `ui/my/MyScreen.kt` (수정) | 동의 토글 (숨김) |
| `ui/storage/ChatSummaryRoute.kt` · `MainActivity.kt` (수정) | 배선 |

---

### Task 1: 도메인 값과 파서

**파일**
- 신규: `app/src/main/java/com/daengs/app/care/VetVisit.kt`
- 테스트: `app/src/test/java/com/daengs/app/care/VetVisitTest.kt`

**인터페이스**
- 쓰는 것: 없다 (첫 태스크)
- 내는 것: `VetReasonOption(code, label)` · `ReceiptItem(name, amountKrw)` ·
  `VetVisitTicket(draftId, petId, storageKey, uploadUrl, uploadHeaders, created)` ·
  `ExtractionStatus{OK,UNREADABLE,FAILED}` · `UnreadableReason{BLURRY,NOT_A_RECEIPT,NO_AMOUNT}` ·
  `VetVisitDraft` · `VetVisit` · `VetVisitConfirmation.toJson()` · `PHONE_PATTERN` ·
  `phoneLooksValid(String): Boolean`. Task 2 이후 전부가 이 이름들을 쓴다.

**계약에서 일부러 안 담는 칸** — 담을 자리를 만들면 다음 사람이 채우려 든다.
`receipt_image_url`(위 "정한 것" 1), 확정 응답의 `suggested_reason_code` · `created_at`,
목록 응답의 `start` · `end` (조회 창을 화면에 안 보여 준다).

- [ ] **Step 1: 실패하는 테스트를 쓴다** — `app/src/test/java/com/daengs/app/care/VetVisitTest.kt`

```kotlin
package com.daengs.app.care

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 저쪽 `schemas/vet_visit.py` 가 못박은 모양. 여기서 잡는 것은 **합계 줄만 없는 응답**
 * (`no_amount`) 이 나머지 칸을 그대로 싣고 온다는 것과, 전화번호 모양이다.
 */
class VetVisitTest {

    @Test
    fun `no_amount 는 총액만 비고 병원·날짜·항목은 채워져 온다`() {
        val draft = VetVisitDraft.parse(JSONObject(NO_AMOUNT_JSON))

        assertEquals(ExtractionStatus.UNREADABLE, draft.status)
        assertEquals(UnreadableReason.NO_AMOUNT, draft.unreadableReason)
        assertNull("합계 줄이 없다", draft.totalKrw)
        assertEquals("압구정동물병원", draft.hospitalName)
        assertEquals("02-543-0075", draft.hospitalPhone)
        assertEquals(LocalDate.of(2019, 5, 17), draft.visitedOn)
        assertEquals(3, draft.items.size)
        assertEquals(46_200, draft.items[1].amountKrw)
    }

    @Test
    fun `blurry 는 전부 비어 있다`() {
        val draft = VetVisitDraft.parse(JSONObject(BLURRY_JSON))

        assertEquals(UnreadableReason.BLURRY, draft.unreadableReason)
        assertNull(draft.hospitalName)
        assertTrue(draft.items.isEmpty())
        assertNull("제안이 없으면 아무것도 미리 안 고른다", draft.suggestedReasonCode)
    }

    @Test
    fun `사유 표시명은 응답에서 온다 — 앱이 한글을 안 적는다`() {
        val draft = VetVisitDraft.parse(JSONObject(NO_AMOUNT_JSON))

        assertEquals(listOf("skin", "vaccination"), draft.reasonOptions.map { it.code })
        assertEquals("피부", draft.reasonOptions[0].label)
    }

    @Test
    fun `티켓은 200 이면 있던 초안이고 201 이면 새 초안이다 — 둘 다 성공`() {
        assertFalse(VetVisitTicket.parse(JSONObject(TICKET_JSON), status = 200).created)
        assertTrue(VetVisitTicket.parse(JSONObject(TICKET_JSON), status = 201).created)
        assertEquals(
            mapOf("Content-Type" to "image/jpeg"),
            VetVisitTicket.parse(JSONObject(TICKET_JSON), 201).uploadHeaders,
        )
    }

    @Test
    fun `전화번호는 서버 CHECK 과 같은 모양만 통과한다`() {
        assertTrue(phoneLooksValid("02-543-0075"))
        assertTrue(phoneLooksValid("031-1234-5678"))
        assertTrue("빈 칸은 안 보낼 값이라 통과다", phoneLooksValid(""))
        assertFalse("카드번호 네 묶음", phoneLooksValid("5432-1234-5678-9012"))
        assertFalse("하이픈이 없다", phoneLooksValid("025430075"))
    }

    @Test
    fun `확정 본문은 채워진 칸만 싣는다 — 빈 병원 칸은 아예 안 간다`() {
        val body = VetVisitConfirmation(
            clientEventId = "10000000-0000-4000-8000-000000000001",
            reasonCode = "skin",
            reasonDetail = null,
            visitedOn = LocalDate.of(2026, 9, 10),
            totalKrw = 61_700,
            hospitalName = "압구정동물병원",
            hospitalAddress = null,
            hospitalPhone = "",
            isEmergency = false,
            isOncology = true,
        ).toJson()

        assertEquals("2026-09-10", body.getString("visited_on"))
        assertEquals(61_700, body.getInt("total_krw"))
        assertEquals("압구정동물병원", body.getString("hospital_name"))
        assertFalse("빈 전화는 칸째로 안 보낸다 — 빈 문자열은 422 다", body.has("hospital_phone"))
        assertFalse(body.has("hospital_address"))
        assertFalse(body.has("reason_detail"))
        assertTrue(body.getBoolean("is_oncology"))
    }

    @Test
    fun `확정된 기록은 코드만 들고 온다 — 표시명은 목록이 따로 붙인다`() {
        val visit = VetVisit.parse(JSONObject(VISIT_JSON))

        assertEquals("skin", visit.reasonCode)
        assertEquals(LocalDate.of(2026, 9, 2), visit.visitedOn)
        assertEquals(80_000, visit.totalKrw)
        assertNull(visit.hospitalAddress)
    }

    private companion object {
        const val NO_AMOUNT_JSON = """
            {"draft_id": "d1", "pet_id": "p1", "receipt_image_url": "http://x/y",
             "extraction_status": "unreadable", "unreadable_reason": "no_amount",
             "visited_on": "2019-05-17", "total_krw": null,
             "hospital_name": "압구정동물병원", "hospital_address": "서울 강남구",
             "hospital_phone": "02-543-0075",
             "items": [{"name": "초진료", "amount_krw": 5500},
                       {"name": "주사-비오칸엠", "amount_krw": 46200},
                       {"name": "처치료", "amount_krw": 10000}],
             "suggested_reason_code": "vaccination", "is_emergency": false,
             "possible_duplicate": false,
             "reason_options": [{"code": "skin", "label": "피부"},
                                {"code": "vaccination", "label": "예방접종"}]}
        """

        const val BLURRY_JSON = """
            {"draft_id": "d2", "pet_id": "p1", "receipt_image_url": "http://x/y",
             "extraction_status": "unreadable", "unreadable_reason": "blurry",
             "visited_on": null, "total_krw": null, "hospital_name": null,
             "hospital_address": null, "hospital_phone": null, "items": [],
             "suggested_reason_code": null, "is_emergency": false,
             "possible_duplicate": false, "reason_options": []}
        """

        const val TICKET_JSON = """
            {"draft_id": "d1", "pet_id": "p1", "storage_key": "vet/d1.jpg",
             "upload_url": "http://server:8000/app/vet-visits/_bridge/upload/vet/d1.jpg",
             "upload_headers": {"Content-Type": "image/jpeg"}, "expires_in_seconds": 600}
        """

        const val VISIT_JSON = """
            {"id": "v1", "pet_id": "p1", "visited_on": "2026-09-02", "total_krw": 80000,
             "hospital_name": "○○동물병원", "hospital_address": null,
             "hospital_phone": "02-123-4567", "reason_code": "skin",
             "reason_detail": null, "suggested_reason_code": "skin",
             "is_emergency": false, "is_oncology": false,
             "client_event_id": "10000000-0000-4000-8000-000000000001",
             "created_at": "2026-09-02T11:00:00+09:00"}
        """
    }
}
```

- [ ] **Step 2: 돌려서 깨지는 걸 본다**

실행: `./gradlew.bat :app:testDebugUnitTest --tests '*VetVisitTest'`
기대: 컴파일 실패 — `Unresolved reference: VetVisitDraft`.

- [ ] **Step 3: `care/VetVisit.kt` 를 쓴다**

```kotlin
package com.daengs.app.care

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * 진료비 기록의 값들. 저쪽 `schemas/vet_visit.py` (SAJOYO/DAENGS_dev#353) 가 원본이다 —
 * **한쪽만 고치지 말 것** (`CareEvent.kt` 와 같은 규칙).
 *
 * **여기 없는 칸이 몇 개 있고, 빠뜨린 게 아니다.** `receipt_image_url` 은 확인 화면이
 * 방금 찍은 로컬 비트맵을 쓰기 때문에 안 담고, 확정 응답의 `suggested_reason_code` ·
 * `created_at` 과 목록 응답의 조회 창은 그리는 자리가 없다. 칸을 만들어 두면 다음 사람이
 * 채우려 들고, 안 쓰는 값이 화면까지 따라다닌다.
 */

/** 사유 드롭다운 한 줄. **표시명이 같이 온다** — 17개 한글을 앱에 적지 않는다. */
data class VetReasonOption(val code: String, val label: String) {
    companion object {
        fun parse(json: JSONObject) = VetReasonOption(json.getString("code"), json.getString("label"))
    }
}

/** 영수증에서 읽은 진료 항목 한 줄. 화면에 보이기만 하고 확정 본문으로 안 간다. */
data class ReceiptItem(val name: String, val amountKrw: Int) {
    companion object {
        fun parse(json: JSONObject) = ReceiptItem(json.getString("name"), json.getInt("amount_krw"))
    }
}

/**
 * 초안 하나와, 그 영수증 사진을 올릴 자리.
 *
 * ⚠️ [uploadUrl] 과 [uploadHeaders] 를 **해석하지 않고 그대로 쓴다.** 지금은 저쪽이 제
 *    주소를 주지만(LocalBridge) 곧 GCS Signed URL 이 온다 — `GaitApi` 와 같은 계약이다.
 */
data class VetVisitTicket(
    val draftId: String,
    val petId: String,
    val storageKey: String,
    val uploadUrl: String,
    val uploadHeaders: Map<String, String>,
    /** 201 이면 새로 만든 것, 200 이면 같은 `client_event_id` 로 있던 것. **둘 다 성공이다.** */
    val created: Boolean,
) {
    companion object {
        fun parse(json: JSONObject, status: Int): VetVisitTicket {
            val headers = json.optJSONObject("upload_headers")
            return VetVisitTicket(
                draftId = json.getString("draft_id"),
                petId = json.getString("pet_id"),
                storageKey = json.getString("storage_key"),
                uploadUrl = json.getString("upload_url"),
                uploadHeaders = headers?.keys()?.asSequence()
                    ?.associateWith { headers.getString(it) }.orEmpty(),
                created = status == 201,
            )
        }
    }
}

/** 추출이 어떻게 끝났나. **셋 다 200 이고 셋 다 손으로 채워 확정할 수 있다.** */
enum class ExtractionStatus { OK, UNREADABLE, FAILED }

/**
 * 왜 못 읽었나.
 *
 * ⚠️ [NO_AMOUNT] 는 **"못 읽었다" 가 아니라 "읽었는데 합계 줄이 없다"** 다. 병원·주소·
 * 전화·날짜·항목이 응답에 그대로 실려 온다 — 화면은 **총액 한 칸만 비우고 나머지를 미리
 * 채운다.** 사진 아래가 잘리는 것이 이 기능에서 제일 흔한 실패라 이 경로가 자주 온다.
 * [BLURRY] · [NOT_A_RECEIPT] 는 정말로 전부 비어 있다.
 */
enum class UnreadableReason { BLURRY, NOT_A_RECEIPT, NO_AMOUNT }

/** 확인 화면 하나를 채우는 값 전부 (`VetVisitDraftResponse`). */
data class VetVisitDraft(
    val draftId: String,
    val petId: String,
    val status: ExtractionStatus,
    val unreadableReason: UnreadableReason?,
    val visitedOn: LocalDate?,
    val totalKrw: Int?,
    val hospitalName: String?,
    val hospitalAddress: String?,
    val hospitalPhone: String?,
    val items: List<ReceiptItem>,
    /**
     * 기계의 제안. **믿을 만하지 않다** — 같은 영수증 3회에 `vaccination` 1 / `skin` 2 가
     * 나왔다(정답은 `vaccination`). 미리 고르기만 하고, 드롭다운은 장식이 아니어야 한다.
     * `null` 이면 아무것도 미리 안 고른다.
     */
    val suggestedReasonCode: String?,
    val isEmergency: Boolean,
    /** 같은 날 같은 금액의 **확정된** 기록이 이미 있다. 막지 않고 한 줄 되묻는다. */
    val possibleDuplicate: Boolean,
    val reasonOptions: List<VetReasonOption>,
) {
    companion object {
        fun parse(json: JSONObject): VetVisitDraft = VetVisitDraft(
            draftId = json.getString("draft_id"),
            petId = json.getString("pet_id"),
            status = when (json.optString("extraction_status")) {
                "ok" -> ExtractionStatus.OK
                "unreadable" -> ExtractionStatus.UNREADABLE
                else -> ExtractionStatus.FAILED
            },
            unreadableReason = when (json.optStringOrNull("unreadable_reason")) {
                "blurry" -> UnreadableReason.BLURRY
                "not_a_receipt" -> UnreadableReason.NOT_A_RECEIPT
                "no_amount" -> UnreadableReason.NO_AMOUNT
                else -> null
            },
            visitedOn = json.optStringOrNull("visited_on")
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            totalKrw = if (json.isNull("total_krw")) null else json.optInt("total_krw"),
            hospitalName = json.optStringOrNull("hospital_name"),
            hospitalAddress = json.optStringOrNull("hospital_address"),
            hospitalPhone = json.optStringOrNull("hospital_phone"),
            items = json.optJSONArray("items").toObjectList(ReceiptItem::parse),
            suggestedReasonCode = json.optStringOrNull("suggested_reason_code"),
            isEmergency = json.optBoolean("is_emergency"),
            possibleDuplicate = json.optBoolean("possible_duplicate"),
            reasonOptions = json.optJSONArray("reason_options").toObjectList(VetReasonOption::parse),
        )
    }
}

/** 확정된 기록 한 건 (`VetVisitResponse`). 표시명은 없다 — 목록이 사유 목록으로 붙인다. */
data class VetVisit(
    val id: String,
    val petId: String,
    val visitedOn: LocalDate,
    val totalKrw: Int,
    val hospitalName: String?,
    val hospitalAddress: String?,
    val hospitalPhone: String?,
    val reasonCode: String,
    val reasonDetail: String?,
    val isEmergency: Boolean,
    val isOncology: Boolean,
    val clientEventId: String,
) {
    companion object {
        fun parse(json: JSONObject): VetVisit = VetVisit(
            id = json.getString("id"),
            petId = json.getString("pet_id"),
            visitedOn = LocalDate.parse(json.getString("visited_on")),
            totalKrw = json.getInt("total_krw"),
            hospitalName = json.optStringOrNull("hospital_name"),
            hospitalAddress = json.optStringOrNull("hospital_address"),
            hospitalPhone = json.optStringOrNull("hospital_phone"),
            reasonCode = json.getString("reason_code"),
            reasonDetail = json.optStringOrNull("reason_detail"),
            isEmergency = json.optBoolean("is_emergency"),
            isOncology = json.optBoolean("is_oncology"),
            clientEventId = json.getString("client_event_id"),
        )
    }
}

/**
 * 유저가 [확인] 을 누른 값. **여기 실린 것만 기록이 된다** — 항목(`raw_ocr_items`)은
 * 저쪽이 초안에서만 읽으므로 이 본문에 자리가 없다 (docs §3).
 */
data class VetVisitConfirmation(
    val clientEventId: String,
    val reasonCode: String,
    val reasonDetail: String?,
    val visitedOn: LocalDate,
    val totalKrw: Int,
    val hospitalName: String?,
    val hospitalAddress: String?,
    val hospitalPhone: String?,
    val isEmergency: Boolean,
    val isOncology: Boolean,
) {
    /** **빈 칸은 아예 안 보낸다.** 빈 문자열을 보내면 저쪽 패턴 검사가 422 를 낸다. */
    fun toJson(): JSONObject = JSONObject()
        .put("client_event_id", clientEventId)
        .put("reason_code", reasonCode)
        .put("visited_on", visitedOn.toString())
        .put("total_krw", totalKrw)
        .put("is_emergency", isEmergency)
        .put("is_oncology", isOncology)
        .putIfPresent("reason_detail", reasonDetail)
        .putIfPresent("hospital_name", hospitalName)
        .putIfPresent("hospital_address", hospitalAddress)
        .putIfPresent("hospital_phone", hospitalPhone)
}

/**
 * 서버 DB CHECK(`vet_visits_hospital_phone_check`)과 **같은 정규식**이다.
 *
 * 앱도 막는 이유는 왕복을 줄이려는 것만이 아니다 — 이 칸은 확인 화면을 지나면 `tel:`
 * 링크가 되어 사람이 눌러 전화를 건다. OCR 이 숫자를 뒤집으면 **모르는 사람에게 전화가
 * 걸리고**, 그건 금액 오류보다 조용히 틀리는 실패다.
 */
val PHONE_PATTERN = Regex("^[0-9]{2,4}(-[0-9]{3,4}){1,2}$")

/** 빈 칸은 통과다 — 안 보낼 값이라 모양을 볼 것이 없다. */
fun phoneLooksValid(value: String): Boolean =
    value.isBlank() || PHONE_PATTERN.matches(value.trim())

// -- 아래는 배관 -------------------------------------------------------------

private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).ifBlank { null }

private fun <T> JSONArray?.toObjectList(parse: (JSONObject) -> T): List<T> =
    List(this?.length() ?: 0) { parse(this!!.getJSONObject(it)) }

private fun JSONObject.putIfPresent(key: String, value: String?): JSONObject =
    if (value.isNullOrBlank()) this else put(key, value.trim())
```

- [ ] **Step 4: 돌려서 통과를 본다**

실행: `./gradlew.bat :app:testDebugUnitTest --tests '*VetVisitTest'`
기대: 7 tests, 0 failures.

- [ ] **Step 5: 커밋**

제목: `진료비 응답을 담을 값이 없던 것`
본문 요지: 저쪽 #353 이 서 있는데 앱에 그 모양이 하나도 없었다 · 안 쓰는 칸은 일부러 뺐다
(자리를 만들면 다음 사람이 채운다) · `no_amount` 가 총액만 비운다는 것을 테스트로 박았다.

---

### Task 2: 여섯 엔드포인트와 bridge 업로드

**파일**
- 신규: `app/src/main/java/com/daengs/app/care/VetVisitApi.kt`
- 테스트: `app/src/test/java/com/daengs/app/care/VetVisitApiTest.kt`

**인터페이스**
- 쓰는 것: Task 1 의 값 전부. `com.daengs.app.chat` 의 `HttpTransport` · `HttpCall` ·
  `HttpReply` · `UrlConnectionTransport` · `ChatApiError` (전부 `internal`, 같은 모듈이다).
- 내는 것:
  - `internal fun interface BridgeUploader { fun put(url: String, headers: Map<String,String>, bytes: ByteArray): Int }`
  - `class VetVisitApi` — `startDraft(token, petId, clientEventId): Result<VetVisitTicket>` ·
    `upload(ticket, jpeg): Result<Unit>` · `extract(token, draftId): Result<VetVisitDraft>` ·
    `confirm(token, draftId, confirmation): Result<VetVisit>` ·
    `list(token, petId): Result<List<VetVisit>>` ·
    `reasonOptions(token, petId): Result<List<VetReasonOption>>` ·
    `delete(token, visitId): Result<Unit>` · `val configured: Boolean`

**이 태스크가 지켜야 하는 것**

- `POST /app/vet-visits` 는 **200 도 성공**이다 (있던 초안). `VetVisitTicket.created` 로만 갈린다.
- bridge PUT 은 **우리 API 가 아니다** — 주소·헤더를 티켓 그대로 쓰고 **토큰을 안 붙인다.**
  `409` 는 "이미 올라가 있다" 라 **성공으로 접는다** (위 "정한 것" 4).
- `extract` 는 Gemini 를 태우므로 read 타임아웃이 길다 (**60초**). 나머지는 30초.
- 409 응답의 `detail` 은 `{"code": ..., "message": ...}` 다 (`photo_not_uploaded` ·
  `invalid_photo_size`). `ChatApiError.from` 은 모르는 코드의 `message` 를 버리고 기본
  문장으로 떨어지므로, **서버 문장을 살려 주는 얇은 껍질**을 여기서 씌운다.

- [ ] **Step 1: 실패하는 테스트를 쓴다** — `app/src/test/java/com/daengs/app/care/VetVisitApiTest.kt`

```kotlin
package com.daengs.app.care

import com.daengs.app.chat.ChatApiError
import com.daengs.app.chat.HttpCall
import com.daengs.app.chat.HttpReply
import com.daengs.app.chat.HttpTransport
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

/**
 * `/app/vet-visits` 로 **무엇을 어디에 어떻게 보내는가.** 저쪽 `routers/vet_visit.py`
 * (SAJOYO/DAENGS_dev#353) 가 못박은 것 — 초안은 200 도 성공이다, bridge PUT 은 토큰을
 * 안 붙이고 티켓의 헤더를 그대로 쓴다, 그 409 는 "이미 올라가 있다" 다, 추출은 못 읽어도
 * 200 이다, 확정에는 항목을 안 싣는다.
 */
class VetVisitApiTest {

    private val transport = FakeTransport()
    private val uploader = FakeUploader()
    private val api = VetVisitApi({ "http://server:8000/" }, transport, uploader)

    private val token = "acc-token-secret-xyz"
    private val pet = "3f2504e0-4f89-11d3-9a0c-0305e82c3301"
    private val draftId = "6ba7b811-9dad-11d1-80b4-00c04fd430c8"
    private val clientEventId = "10000000-0000-4000-8000-000000000001"

    @Test
    fun `초안은 POST 바디에 pet_id·content_type·client_event_id 를 싣는다`() = runBlocking {
        transport.reply = HttpReply(201, TICKET_JSON)
        val ticket = api.startDraft(token, pet, clientEventId).getOrThrow()

        val call = transport.only()
        assertEquals("POST", call.method)
        assertEquals("http://server:8000/app/vet-visits", call.url)
        assertEquals("Bearer $token", call.headers["Authorization"])
        val body = JSONObject(call.body!!)
        assertEquals(pet, body.getString("pet_id"))
        assertEquals("image/jpeg", body.getString("content_type"))
        assertEquals(clientEventId, body.getString("client_event_id"))
        assertTrue(ticket.created)
    }

    @Test
    fun `같은 client_event_id 의 200 도 성공이다 — 있던 초안이 온다`() = runBlocking {
        transport.reply = HttpReply(200, TICKET_JSON)
        val ticket = api.startDraft(token, pet, clientEventId).getOrThrow()

        assertFalse("새로 만든 게 아니다", ticket.created)
        assertEquals(draftId, ticket.draftId)
    }

    @Test
    fun `업로드는 티켓의 주소·헤더를 그대로 쓰고 토큰을 안 붙인다`() = runBlocking {
        uploader.status = 200
        api.upload(ticket(), byteArrayOf(1, 2, 3)).getOrThrow()

        assertEquals("http://bridge/vet/d1.jpg", uploader.url)
        assertEquals(mapOf("Content-Type" to "image/jpeg"), uploader.headers)
        assertFalse("bridge 는 인증 헤더를 안 받는다", uploader.headers.containsKey("Authorization"))
        assertEquals(3, uploader.bytes?.size)
    }

    @Test
    fun `업로드 409 는 이미 올라가 있다는 뜻이라 성공으로 접는다`() = runBlocking {
        uploader.status = 409
        assertTrue(api.upload(ticket(), byteArrayOf(1)).isSuccess)
    }

    @Test
    fun `업로드 415 는 실패다 — 상태 코드를 든 오류가 온다`() = runBlocking {
        uploader.status = 415
        val error = api.upload(ticket(), byteArrayOf(1)).exceptionOrNull() as ChatApiError
        assertEquals(415, error.status)
    }

    @Test
    fun `업로드가 닿지 못하면 status 0 이다`() = runBlocking {
        uploader.failWith = IOException("no route")
        val error = api.upload(ticket(), byteArrayOf(1)).exceptionOrNull() as ChatApiError
        assertEquals(0, error.status)
    }

    @Test
    fun `추출은 POST extract 이고 못 읽어도 200 이라 성공이다`() = runBlocking {
        transport.reply = HttpReply(200, UNREADABLE_JSON)
        val draft = api.extract(token, draftId).getOrThrow()

        val call = transport.only()
        assertEquals("POST", call.method)
        assertEquals("http://server:8000/app/vet-visits/$draftId/extract", call.url)
        assertEquals(ExtractionStatus.UNREADABLE, draft.status)
        assertEquals(UnreadableReason.NO_AMOUNT, draft.unreadableReason)
    }

    @Test
    fun `추출 409 는 서버가 준 문장을 그대로 보여 준다`() = runBlocking {
        transport.reply = HttpReply(409, CONFLICT_JSON)
        val error = api.extract(token, draftId).exceptionOrNull() as ChatApiError

        assertEquals(409, error.status)
        assertEquals("photo_not_uploaded", error.code)
        assertEquals("업로드된 영수증 사진을 찾을 수 없습니다.", error.message)
    }

    @Test
    fun `확정은 POST confirm 이고 항목을 안 싣는다`() = runBlocking {
        transport.reply = HttpReply(200, VISIT_JSON)
        api.confirm(token, draftId, confirmation()).getOrThrow()

        val call = transport.only()
        assertEquals("http://server:8000/app/vet-visits/$draftId/confirm", call.url)
        val body = JSONObject(call.body!!)
        assertFalse("항목은 초안에서만 읽는다 — 본문에 자리가 없다", body.has("items"))
        assertFalse(body.has("raw_ocr_items"))
        assertEquals(clientEventId, body.getString("client_event_id"))
    }

    @Test
    fun `목록은 pet_id 쿼리이고 최근 먼저 온다`() = runBlocking {
        transport.reply = HttpReply(200, LIST_JSON)
        val visits = api.list(token, pet).getOrThrow()

        assertEquals("http://server:8000/app/vet-visits?pet_id=$pet", transport.only().url)
        assertEquals(listOf("v1", "v2"), visits.map { it.id })
    }

    @Test
    fun `사유 목록은 배열로 온다 — 최근 사유가 앞이다`() = runBlocking {
        transport.reply = HttpReply(200, OPTIONS_JSON)
        val options = api.reasonOptions(token, pet).getOrThrow()

        assertEquals(
            "http://server:8000/app/vet-visits/reason-options?pet_id=$pet",
            transport.only().url,
        )
        assertEquals(listOf("skin", "vaccination"), options.map { it.code })
        assertEquals("피부", options[0].label)
    }

    @Test
    fun `삭제는 DELETE 이고 204 라 본문이 없다`() = runBlocking {
        transport.reply = HttpReply(204, "")
        api.delete(token, "v1").getOrThrow()

        val call = transport.only()
        assertEquals("DELETE", call.method)
        assertEquals("http://server:8000/app/vet-visits/v1", call.url)
    }

    @Test
    fun `서버 주소가 비면 부르기 전에 막는다`() = runBlocking {
        val blind = VetVisitApi({ "" }, transport, uploader)
        assertTrue(blind.startDraft(token, pet, clientEventId).exceptionOrNull() is IllegalStateException)
        assertEquals("서버를 아예 안 두드린다", 0, transport.calls.size)
    }

    private fun ticket() = VetVisitTicket(
        draftId = draftId,
        petId = pet,
        storageKey = "vet/d1.jpg",
        uploadUrl = "http://bridge/vet/d1.jpg",
        uploadHeaders = mapOf("Content-Type" to "image/jpeg"),
        created = true,
    )

    private fun confirmation() = VetVisitConfirmation(
        clientEventId = clientEventId,
        reasonCode = "skin",
        reasonDetail = null,
        visitedOn = LocalDate.of(2026, 9, 10),
        totalKrw = 61_700,
        hospitalName = null,
        hospitalAddress = null,
        hospitalPhone = null,
        isEmergency = false,
        isOncology = false,
    )

    private class FakeTransport : HttpTransport {
        val calls = mutableListOf<HttpCall>()
        var reply = HttpReply(200, "{}")
        override fun exchange(call: HttpCall): HttpReply {
            calls += call
            return reply
        }
        fun only(): HttpCall = calls.single()
    }

    private class FakeUploader : BridgeUploader {
        var url: String? = null
        var headers: Map<String, String> = emptyMap()
        var bytes: ByteArray? = null
        var status = 200
        var failWith: Throwable? = null
        override fun put(url: String, headers: Map<String, String>, bytes: ByteArray): Int {
            this.url = url
            this.headers = headers
            this.bytes = bytes
            failWith?.let { throw it }
            return status
        }
    }

    private companion object {
        const val TICKET_JSON = """
            {"draft_id": "6ba7b811-9dad-11d1-80b4-00c04fd430c8",
             "pet_id": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
             "storage_key": "vet/d1.jpg", "upload_url": "http://bridge/vet/d1.jpg",
             "upload_headers": {"Content-Type": "image/jpeg"}, "expires_in_seconds": 600}
        """

        const val UNREADABLE_JSON = """
            {"draft_id": "d1", "pet_id": "p1", "receipt_image_url": "http://x/y",
             "extraction_status": "unreadable", "unreadable_reason": "no_amount",
             "visited_on": "2019-05-17", "total_krw": null,
             "hospital_name": "압구정동물병원", "hospital_address": null,
             "hospital_phone": null, "items": [], "suggested_reason_code": null,
             "is_emergency": false, "possible_duplicate": false, "reason_options": []}
        """

        const val CONFLICT_JSON = """
            {"detail": {"code": "photo_not_uploaded",
                        "message": "업로드된 영수증 사진을 찾을 수 없습니다."}}
        """

        const val VISIT_JSON = """
            {"id": "v1", "pet_id": "p1", "visited_on": "2026-09-10", "total_krw": 61700,
             "hospital_name": null, "hospital_address": null, "hospital_phone": null,
             "reason_code": "skin", "reason_detail": null, "suggested_reason_code": null,
             "is_emergency": false, "is_oncology": false,
             "client_event_id": "10000000-0000-4000-8000-000000000001",
             "created_at": "2026-09-10T11:00:00+09:00"}
        """

        const val LIST_JSON = """
            {"pet_id": "p1", "start": "2025-09-10", "end": "2026-09-10",
             "visits": [
               {"id": "v1", "pet_id": "p1", "visited_on": "2026-09-10", "total_krw": 61700,
                "hospital_name": null, "hospital_address": null, "hospital_phone": null,
                "reason_code": "skin", "reason_detail": null, "suggested_reason_code": null,
                "is_emergency": false, "is_oncology": false, "client_event_id": "c1",
                "created_at": "2026-09-10T11:00:00+09:00"},
               {"id": "v2", "pet_id": "p1", "visited_on": "2026-09-02", "total_krw": 80000,
                "hospital_name": null, "hospital_address": null, "hospital_phone": null,
                "reason_code": "ear", "reason_detail": null, "suggested_reason_code": null,
                "is_emergency": false, "is_oncology": false, "client_event_id": "c2",
                "created_at": "2026-09-02T11:00:00+09:00"}]}
        """

        const val OPTIONS_JSON = """
            [{"code": "skin", "label": "피부"}, {"code": "vaccination", "label": "예방접종"}]
        """
    }
}
```

- [ ] **Step 2: 돌려서 깨지는 걸 본다**

실행: `./gradlew.bat :app:testDebugUnitTest --tests '*VetVisitApiTest'`
기대: 컴파일 실패 — `Unresolved reference: VetVisitApi`.

- [ ] **Step 3: `care/VetVisitApi.kt` 를 쓴다**

```kotlin
package com.daengs.app.care

import com.daengs.app.BuildConfig
import com.daengs.app.chat.ChatApiError
import com.daengs.app.chat.HttpCall
import com.daengs.app.chat.HttpTransport
import com.daengs.app.chat.UrlConnectionTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 진료비 기록 API (`/app/vet-visits`). 계약은 저쪽 SAJOYO/DAENGS_dev#353 의
 * `routers/vet_visit.py` · `schemas/vet_visit.py` 와 `docs/vet-visits.md` 다 —
 * **한쪽만 고치지 말 것** (`CareApi` 머리말과 같은 규칙).
 *
 * 배관은 [CareApi] 와 같다: `org.json`, 전송은 [HttpTransport] 로 떼어 테스트가 요청을
 * 통째로 보고, 실패는 상태 코드를 든 [ChatApiError] 이며, **토큰은 매 호출에 받는다.**
 *
 * 다른 점은 **걸음이 넷**이라는 것이다.
 *
 * ```
 * ① POST /app/vet-visits            초안 + 업로드 티켓 (201 새로, 200 있던 것 — 둘 다 성공)
 * ② PUT  <티켓의 upload_url>        사진 바이트. **우리 API 가 아니다**
 * ③ POST /{draft_id}/extract        Gemini 추출. 못 읽어도 200
 * ④ POST /{draft_id}/confirm        **여기서만 기록이 생긴다**
 * ```
 *
 * ⚠️ **②는 [transport] 를 안 탄다.** [HttpCall] 의 본문이 `String` 이고 Content-Type 을
 *    `application/json` 으로 고정하기 때문이다. 사진은 바이트이고 헤더도 티켓이 준
 *    것을 그대로 써야 해서, 그 한 걸음만 [BridgeUploader] 로 따로 떼었다 — 테스트가
 *    무엇을 어디에 올렸는지 볼 수 있어야 하는 것은 같다.
 */
class VetVisitApi internal constructor(
    private val baseUrl: () -> String,
    private val transport: HttpTransport,
    private val uploader: BridgeUploader,
) {
    constructor() : this({ BuildConfig.API_BASE_URL }, UrlConnectionTransport, UrlConnectionUploader)

    val configured: Boolean
        get() = baseUrl().isNotBlank()

    /**
     * ① 초안을 열고 사진 올릴 자리를 받는다.
     *
     * **[clientEventId] 는 촬영 시점에 만든 것을 그대로 넘긴다.** 업로드 버튼을 누를 때
     * 새로 만들면, 화면이 얼어 보여 두 번 눌린 순간 초안이 둘 생기고 **Gemini 가 두 번
     * 불린다**(요금이 는다). 같은 키면 저쪽이 200 으로 있던 초안을 그대로 준다.
     */
    suspend fun startDraft(
        accessToken: String,
        petId: String,
        clientEventId: String,
    ): Result<VetVisitTicket> = call(
        accessToken,
        "POST",
        "",
        body = JSONObject()
            .put("pet_id", petId)
            .put("content_type", JPEG)
            .put("client_event_id", clientEventId),
    ) { status, body -> VetVisitTicket.parse(JSONObject(body), status) }

    /**
     * ② 티켓이 준 자리에 사진을 올린다.
     *
     * **여기는 우리 API 가 아니다.** 주소도 헤더도 티켓이 준 것을 그대로 쓰고 토큰을
     * 안 붙인다 — 저쪽 bridge 는 인증 헤더를 안 받고 **키가 자격**이다.
     *
     * ⚠️ **409 를 성공으로 접는다.** 저쪽이 `open_write(exclusive=True)` 라 같은 키에
     *    두 번 쓰면 409 인데, 그 뜻은 **그 자리에 이미 바이트가 있다**는 것이다 (반쯤
     *    쓰다 끊긴 파일은 저쪽이 지운다). 여기서 실패로 보고 새 초안을 만들면 같은
     *    영수증에 Gemini 를 한 번 더 태운다. 진짜로 사진이 없으면 ③이 409
     *    `photo_not_uploaded` 로 말해 준다.
     */
    suspend fun upload(ticket: VetVisitTicket, jpeg: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val status = uploader.put(ticket.uploadUrl, ticket.uploadHeaders, jpeg)
                if (status == HttpURLConnection.HTTP_CONFLICT) return@runCatching
                if (status !in 200..299) {
                    throw ChatApiError(status, code = null, message = uploadSentence(status))
                }
            }.recoverCatching { cause ->
                if (cause is ChatApiError) throw cause
                throw ChatApiError.unreachable("영수증 사진을 올리지 못했어요. 잠시 뒤 다시 시도해 주세요.", cause)
            }
        }

    /**
     * ③ 올라온 영수증을 읽는다. **`ok`·`unreadable`·`failed` 모두 200 이고 셋 다 성공이다** —
     * 유저는 셋 모두에서 손으로 채워 확정할 수 있다.
     *
     * ⚠️ **이 화면을 다시 불러 복구하려 들지 마라.** 이미 추출된 초안을 다시 부르면
     *    저장된 결과가 오는데, 유저가 **미동의** 였다면 항목이 애초에 저장되지 않아
     *    그때도 안 온다. 화면 상태는 앱이 들고 있어야 한다 (저쪽 docs §3).
     */
    suspend fun extract(accessToken: String, draftId: String): Result<VetVisitDraft> =
        call(accessToken, "POST", "/${encode(draftId)}/extract", readTimeoutMs = EXTRACT_TIMEOUT_MS) { _, body ->
            VetVisitDraft.parse(JSONObject(body))
        }

    /** ④ 확정. **`vet_visits` 에 행이 생기는 유일한 자리다.** */
    suspend fun confirm(
        accessToken: String,
        draftId: String,
        confirmation: VetVisitConfirmation,
    ): Result<VetVisit> =
        call(accessToken, "POST", "/${encode(draftId)}/confirm", body = confirmation.toJson()) { _, body ->
            VetVisit.parse(JSONObject(body))
        }

    /** 목록, 최근 먼저. 창을 안 보내면 저쪽 기본값(최근 1년)이다. */
    suspend fun list(accessToken: String, petId: String): Result<List<VetVisit>> =
        call(accessToken, "GET", "?pet_id=${encode(petId)}") { _, body ->
            JSONObject(body).optJSONArray("visits").toObjectList(VetVisit::parse)
        }

    /**
     * 사유 드롭다운의 목록. 이 강아지가 최근 쓴 사유가 앞이다.
     *
     * **목록 화면도 이걸 쓴다** — 확정 응답에는 `reason_code` 만 있고 표시명이 없어서,
     * 코드→표시명 지도가 여기서 온다. 17개 한글을 앱에 적으면 닫힌 목록을 서버가
     * 지키는 이유가 그 자리에서 다시 샌다.
     */
    suspend fun reasonOptions(accessToken: String, petId: String): Result<List<VetReasonOption>> =
        call(accessToken, "GET", "/reason-options?pet_id=${encode(petId)}") { _, body ->
            JSONArray(body).toObjectList(VetReasonOption::parse)
        }

    /** 지운다. `DELETE /app/vet-visits/{id}` → 204. 내 기록이 아니면 404. */
    suspend fun delete(accessToken: String, visitId: String): Result<Unit> =
        call(accessToken, "DELETE", "/${encode(visitId)}") { _, _ -> }

    // -- 아래는 배관 -------------------------------------------------------

    private suspend fun <T> call(
        accessToken: String,
        method: String,
        path: String,
        body: JSONObject? = null,
        readTimeoutMs: Int = READ_TIMEOUT_MS,
        parse: (Int, String) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            check(configured) { "서버 주소가 없습니다. local.properties 의 daengs.apiBaseUrl 을 채우세요." }
            val reply = transport.exchange(
                HttpCall(
                    method = method,
                    url = "${baseUrl().trimEnd('/')}/app/vet-visits$path",
                    headers = mapOf(
                        "Accept" to "application/json",
                        "Authorization" to "Bearer $accessToken",
                    ),
                    body = body?.toString(),
                    connectTimeoutMs = CONNECT_TIMEOUT_MS,
                    readTimeoutMs = readTimeoutMs,
                ),
            )
            if (reply.status !in 200..299) throw vetError(reply.status, reply.body)
            parse(reply.status, reply.body)
        }.recoverCatching { cause ->
            if (cause is IllegalStateException) throw cause
            throw ChatApiError.unreachable("서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.", cause)
        }
    }

    /**
     * 저쪽 409 는 `{"code": ..., "message": ...}` 다 (`photo_not_uploaded` ·
     * `invalid_photo_size`). [ChatApiError.from] 은 **모르는 코드의 문장을 버리고**
     * 기본 문구로 떨어지므로 — 대화 API 에는 코드별 문장이 앱에 있어서 맞는 규칙이다 —
     * 여기서 서버가 준 문장을 다시 얹는다. 진료비 쪽 코드를 앱에 또 적지 않으려는 것이다.
     */
    private fun vetError(status: Int, body: String): ChatApiError {
        val base = ChatApiError.from(status, body) { "서버 오류 ($it)" }
        val sentence = base.data["message"]?.takeIf { it.isNotBlank() && it != "null" } ?: return base
        return ChatApiError(status, base.code, sentence, base.data)
    }

    private fun uploadSentence(status: Int): String = when (status) {
        HttpURLConnection.HTTP_ENTITY_TOO_LARGE -> "영수증 사진이 너무 커요. 다시 찍어 주세요."
        else -> "영수증 사진을 올리지 못했어요 ($status)."
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun <T> JSONArray?.toObjectList(parse: (JSONObject) -> T): List<T> =
        List(this?.length() ?: 0) { parse(this!!.getJSONObject(it)) }

    private companion object {
        const val JPEG = "image/jpeg"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 30_000

        /** ③은 Gemini 를 태운다. 30초로는 짧다 (`ScreeningRecordApi` 의 confirm 과 같은 이유). */
        const val EXTRACT_TIMEOUT_MS = 60_000
    }
}

/**
 * bridge 로 바이트 한 덩이를 올리는 한 번. **상태 코드만 돌려준다** — 본문에 볼 것이 없다.
 * 닿지 못하면 예외를 던진다 ([HttpTransport] 와 같은 규칙).
 */
internal fun interface BridgeUploader {
    fun put(url: String, headers: Map<String, String>, bytes: ByteArray): Int
}

/** 진짜 업로드. `ScreeningRecordApi.upload` 의 배관을 그대로 옮긴 것이라 동작이 다르지 않다. */
internal object UrlConnectionUploader : BridgeUploader {
    override fun put(url: String, headers: Map<String, String>, bytes: ByteArray): Int {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            connectTimeout = 10_000
            readTimeout = 30_000
            doOutput = true
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            // 사진 하나가 메모리에 두 번 앉지 않게 길이를 먼저 알려 준다.
            setFixedLengthStreamingMode(bytes.size)
        }
        try {
            conn.outputStream.use { it.write(bytes) }
            return conn.responseCode
        } finally {
            conn.disconnect()
        }
    }
}
```

- [ ] **Step 4: 돌려서 통과를 본다**

실행: `./gradlew.bat :app:testDebugUnitTest --tests '*VetVisitApiTest'`
기대: 13 tests, 0 failures.

- [ ] **Step 5: 커밋**

제목: `영수증을 올릴 길이 앱에 없던 것`
본문 요지: 네 걸음(초안·업로드·추출·확정)을 `CareApi` 배관으로 놓았다 · 사진 걸음만
`BridgeUploader` 로 뗀 이유(HttpCall 의 본문이 String 이고 Content-Type 이 json 고정) ·
업로드 409 를 성공으로 접은 이유(이미 올라가 있다 / Gemini 재호출 방지).

---

### Task 3: 영수증 한 장의 생애 — 코디네이터

**파일**
- 신규: `app/src/main/java/com/daengs/app/care/VetVisitCoordinator.kt`
- 테스트: `app/src/test/java/com/daengs/app/care/VetVisitCoordinatorTest.kt`

**인터페이스**
- 쓰는 것: Task 1·2 의 전부. `com.daengs.app.chat.ChatLoadState`.
- 내는 것:
  - `data class VetVisitState(selectedPetId, visits, reasonLabels, reasonOptions, receipt, deletingVisitId, deleteError)`
  - `data class ReceiptFlow(clientEventId, step, draft, error)` · `enum ReceiptStep { UPLOADING, EXTRACTING, READY, CONFIRMING }`
  - `data class ReceiptEdits(reasonCode, reasonDetail, visitedOn, totalKrw, hospitalName, hospitalAddress, hospitalPhone, isEmergency, isOncology)`
  - `interface VetVisitGateway` (일곱 함수) · `class VetVisitCoordinator(scope, gateway, newId, )`
    — `selectPet` · `load` · `beginReceipt(token, jpeg)` · `retryReceipt(token)` ·
    `confirm(token, edits)` · `dismissReceipt()` · `delete(token, visitId)` · `clearErrors()` ·
    `cancelPending()` · `forget()`

**이 태스크가 지켜야 하는 것 — 재시도가 무엇을 다시 하느냐가 전부다**

**어느 함수를 부르는지가 행마다 다르다** — 화면을 배선하는 사람이 이 표를 읽고 잇는다.

| 어디서 실패했나 | 무엇을 부르나 | 무엇을 다시 하나 |
| --- | --- | --- |
| ① 초안 · ② 업로드 | `retryReceipt` | **새 `client_event_id` 로 새 초안.** 그 사진이 어디까지 갔는지 모른다 |
| ③ 추출 (연결 실패) | `retryReceipt` | **같은 초안으로 추출만.** 저쪽이 저장된 결과를 준다 — Gemini 재호출 없음 |
| ③ 추출 (409 `photo_not_uploaded`) | `retryReceipt` | **새 `client_event_id` 로 새 초안.** 사진이 그 자리에 없다 |
| ③ 추출 (200 `extraction_status="failed"`) | `retryReceipt` | **같은 초안으로 추출만.** 저쪽이 `extracted_at` 을 안 남겨 Gemini 가 다시 돌고 성공할 수 있다 |
| ④ 확정 | **`confirm`** (`retryReceipt` 가 아니다) | **같은 키로 확정만.** 저쪽이 그 키로 먼저 조회해 있던 기록을 준다 |

⚠️ **`failed` 행이 이 표에서 제일 놓치기 쉽다.** 저쪽은 우리 쪽 장애(Gemini 타임아웃·API
오류)를 500 이 아니라 **200 + `extraction_status="failed"`** 로 준다. 그러면 앱에서는
`Result.success` 라 오류 객체가 없는데, 저쪽 `extract_draft` 의 `except
ReceiptExtractionFailed` 는 `extracted_at` 커밋 **앞에서** 빠져나가므로 같은 초안으로 다시
부르면 Gemini 가 다시 돈다 — 저쪽 docs §2 의 표도 `failed` 에만 "다시 시도" 를 안내한다.
오류가 없다고 재시도를 막으면 그 안내가 **눌리지 않는 버튼**이 된다.

⚠️ **④의 실패를 `retryReceipt` 로 이으면 안 된다.** 그러면 재추출이 돌아 유저가 손으로
고친 값이 초안 미리 채움으로 되돌아가고, 유저가 **미동의** 였다면 저쪽이 항목을 저장
자체를 안 해서 항목 목록이 통째로 빈다.

`client_event_id` 는 **[beginReceipt] 가 불릴 때 = 촬영이 끝난 순간** 만든다. 업로드 버튼을
누를 때 만들면, 화면이 얼어 보여 두 번 눌린 순간 초안이 둘 생기고 Gemini 가 두 번 불린다.

**기기에 저장하지 않는다.** `CareLogCoordinator` 와 같은 판단이다 — 키가 의미 있는 구간은
한 영수증을 처리하는 몇십 초뿐이고, 앱이 죽었다 돌아오면 목록을 다시 읽는다.

- [ ] **Step 1: 실패하는 테스트를 쓴다** — `app/src/test/java/com/daengs/app/care/VetVisitCoordinatorTest.kt`

```kotlin
package com.daengs.app.care

import com.daengs.app.chat.ChatApiError
import com.daengs.app.chat.ChatLoadState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

/**
 * 영수증 한 장의 생애. 여기서 잡는 것은 **재시도가 무엇을 다시 하느냐** 하나다 —
 * 업로드까지 못 간 실패는 새 초안이어야 하고, 추출·확정의 실패는 **같은 키로** 다시
 * 가야 한다 (저쪽이 멱등이라 Gemini 도 요금도 다시 안 든다).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VetVisitCoordinatorTest {

    private val gateway = FakeGateway()
    private val token = "acc"
    private val jpeg = byteArrayOf(1, 2, 3)

    @Test
    fun `촬영에서 만든 키가 초안과 확정에 같이 간다`() = runTest {
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.beginReceipt(token, jpeg)
        advanceUntilIdle()

        assertEquals(ReceiptStep.READY, state(coordinator).receipt?.step)
        coordinator.confirm(token, edits())
        advanceUntilIdle()

        assertEquals("id-1", gateway.startedWith.single())
        assertEquals("id-1", gateway.confirmedWith.single())
    }

    @Test
    fun `업로드가 실패하면 다시 누를 때 새 키로 새 초안을 만든다`() = runTest {
        gateway.uploadResult = Result.failure(ChatApiError.unreachable("못 올렸어요", IOException()))
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.beginReceipt(token, jpeg)
        advanceUntilIdle()

        assertNotNull(state(coordinator).receipt?.error)
        gateway.uploadResult = Result.success(Unit)
        coordinator.retryReceipt(token)
        advanceUntilIdle()

        assertEquals(listOf("id-1", "id-2"), gateway.startedWith)
        assertEquals(ReceiptStep.READY, state(coordinator).receipt?.step)
    }

    @Test
    fun `추출이 연결 실패면 같은 초안으로 추출만 다시 부른다`() = runTest {
        gateway.extractResult = Result.failure(ChatApiError.unreachable("못 읽었어요", IOException()))
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.beginReceipt(token, jpeg)
        advanceUntilIdle()

        gateway.extractResult = Result.success(draft())
        coordinator.retryReceipt(token)
        advanceUntilIdle()

        assertEquals("초안을 새로 만들지 않는다", listOf("id-1"), gateway.startedWith)
        assertEquals(listOf("draft-1", "draft-1"), gateway.extractedWith)
    }

    @Test
    fun `사진이 그 자리에 없다는 409 면 새 초안을 만든다`() = runTest {
        gateway.extractResult = Result.failure(
            ChatApiError(409, "photo_not_uploaded", "업로드된 영수증 사진을 찾을 수 없습니다."),
        )
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.beginReceipt(token, jpeg)
        advanceUntilIdle()

        gateway.extractResult = Result.success(draft())
        coordinator.retryReceipt(token)
        advanceUntilIdle()

        assertEquals(listOf("id-1", "id-2"), gateway.startedWith)
    }

    @Test
    fun `확정이 실패하면 같은 키로 확정만 다시 보낸다`() = runTest {
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.beginReceipt(token, jpeg)
        advanceUntilIdle()

        gateway.confirmResult = Result.failure(ChatApiError.unreachable("못 저장했어요", IOException()))
        coordinator.confirm(token, edits())
        advanceUntilIdle()
        assertEquals(ReceiptStep.READY, state(coordinator).receipt?.step)

        gateway.confirmResult = Result.success(visit())
        coordinator.confirm(token, edits())
        advanceUntilIdle()

        assertEquals(listOf("id-1", "id-1"), gateway.confirmedWith)
        assertEquals(listOf("id-1"), gateway.startedWith)
    }

    @Test
    fun `확정에 성공하면 목록 맨 앞에 붙고 흐름이 닫힌다`() = runTest {
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.load(token)
        advanceUntilIdle()
        coordinator.beginReceipt(token, jpeg)
        advanceUntilIdle()
        coordinator.confirm(token, edits())
        advanceUntilIdle()

        assertNull("확인 화면이 닫힌다", state(coordinator).receipt)
        assertEquals(listOf("v-new", "v1"), visits(coordinator).map { it.id })
    }

    @Test
    fun `사유 표시명은 목록과 같이 받아 둔다 — 앱이 한글을 안 적는다`() = runTest {
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.load(token)
        advanceUntilIdle()

        assertEquals("피부", state(coordinator).reasonLabels["skin"])
        assertEquals(listOf("skin", "ear"), state(coordinator).reasonOptions.map { it.code })
    }

    @Test
    fun `사유 목록을 못 받아도 기록 목록은 보인다`() = runTest {
        gateway.optionsResult = Result.failure(ChatApiError.unreachable("못 받았어요", IOException()))
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.load(token)
        advanceUntilIdle()

        assertTrue(state(coordinator).reasonLabels.isEmpty())
        assertEquals(listOf("v1"), visits(coordinator).map { it.id })
    }

    @Test
    fun `강아지를 바꾸면 처리 중이던 영수증이 사라진다`() = runTest {
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.beginReceipt(token, jpeg)
        advanceUntilIdle()

        coordinator.selectPet("other")

        assertNull(state(coordinator).receipt)
        assertEquals(ChatLoadState.Idle, state(coordinator).visits)
    }

    @Test
    fun `삭제는 목록에서 뺀다`() = runTest {
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.load(token)
        advanceUntilIdle()

        coordinator.delete(token, "v1")
        advanceUntilIdle()

        assertTrue(visits(coordinator).isEmpty())
        assertNull(state(coordinator).deleteError)
    }

    // -- 배관 -----------------------------------------------------------

    private fun coordinator(scope: TestScope) =
        VetVisitCoordinator(scope, gateway, newId = sequenceIds())

    private fun sequenceIds(): () -> String {
        var n = 0
        return { "id-${++n}" }
    }

    private fun state(c: VetVisitCoordinator) = c.state.value

    private fun visits(c: VetVisitCoordinator): List<VetVisit> =
        (c.state.value.visits as ChatLoadState.Ready).value

    private fun edits() = ReceiptEdits(
        reasonCode = "skin",
        reasonDetail = null,
        visitedOn = LocalDate.of(2026, 9, 10),
        totalKrw = 61_700,
        hospitalName = null,
        hospitalAddress = null,
        hospitalPhone = null,
        isEmergency = false,
        isOncology = false,
    )

    private fun draft() = VetVisitDraft(
        draftId = "draft-1", petId = "pet", status = ExtractionStatus.OK,
        unreadableReason = null, visitedOn = LocalDate.of(2026, 9, 10), totalKrw = 61_700,
        hospitalName = "압구정동물병원", hospitalAddress = null, hospitalPhone = "02-543-0075",
        items = emptyList(), suggestedReasonCode = "vaccination", isEmergency = false,
        possibleDuplicate = false,
        reasonOptions = listOf(VetReasonOption("skin", "피부"), VetReasonOption("ear", "귀")),
    )

    private fun visit(id: String = "v-new") = VetVisit(
        id = id, petId = "pet", visitedOn = LocalDate.of(2026, 9, 10), totalKrw = 61_700,
        hospitalName = null, hospitalAddress = null, hospitalPhone = null,
        reasonCode = "skin", reasonDetail = null, isEmergency = false, isOncology = false,
        clientEventId = "id-1",
    )

    private inner class FakeGateway : VetVisitGateway {
        val startedWith = mutableListOf<String>()
        val extractedWith = mutableListOf<String>()
        val confirmedWith = mutableListOf<String>()

        var uploadResult: Result<Unit> = Result.success(Unit)
        var extractResult: Result<VetVisitDraft> = Result.success(draft())
        var confirmResult: Result<VetVisit> = Result.success(visit())
        var optionsResult: Result<List<VetReasonOption>> =
            Result.success(listOf(VetReasonOption("skin", "피부"), VetReasonOption("ear", "귀")))

        override suspend fun startDraft(accessToken: String, petId: String, clientEventId: String) =
            Result.success(
                VetVisitTicket("draft-1", petId, "k", "http://bridge/k", emptyMap(), created = true),
            ).also { startedWith += clientEventId }

        override suspend fun upload(ticket: VetVisitTicket, jpeg: ByteArray) = uploadResult

        override suspend fun extract(accessToken: String, draftId: String) =
            extractResult.also { extractedWith += draftId }

        override suspend fun confirm(
            accessToken: String,
            draftId: String,
            confirmation: VetVisitConfirmation,
        ) = confirmResult.also { confirmedWith += confirmation.clientEventId }

        override suspend fun list(accessToken: String, petId: String) =
            Result.success(listOf(visit("v1")))

        override suspend fun reasonOptions(accessToken: String, petId: String) = optionsResult

        override suspend fun delete(accessToken: String, visitId: String) = Result.success(Unit)
    }
}
```

> `advanceUntilIdle()` 은 `kotlinx.coroutines.test` 의 것이다 —
> `CareLogCoordinatorTest` 가 이미 그것으로 돈다 (`runCurrent` 이 아니다). 그 파일의 import 를 그대로 맞춘다.

- [ ] **Step 2: 돌려서 깨지는 걸 본다**

실행: `./gradlew.bat :app:testDebugUnitTest --tests '*VetVisitCoordinatorTest'`
기대: 컴파일 실패 — `Unresolved reference: VetVisitCoordinator`.

- [ ] **Step 3: `care/VetVisitCoordinator.kt` 를 쓴다**

```kotlin
package com.daengs.app.care

import com.daengs.app.chat.ChatApiError
import com.daengs.app.chat.ChatLoadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/** 영수증 한 장이 지금 어디까지 갔나. */
enum class ReceiptStep {
    /** ① 초안 + ② 사진 PUT */
    UPLOADING,

    /** ③ Gemini 추출 */
    EXTRACTING,

    /** 확인 화면이 떠 있다 */
    READY,

    /** ④ 확정을 보내는 중 */
    CONFIRMING,
}

/**
 * 처리 중인 영수증 하나.
 *
 * [clientEventId] 는 **촬영이 끝난 순간** 만들어 이 흐름이 끝날 때까지 안 바뀐다 —
 * 초안(①)과 확정(④)이 같은 키를 쓰므로, 두 번 눌려도 초안도 기록도 하나다.
 */
data class ReceiptFlow(
    val clientEventId: String,
    val step: ReceiptStep,
    val draftId: String? = null,
    val draft: VetVisitDraft? = null,
    val error: ChatApiError? = null,
)

/** 유저가 확인 화면에서 고친 값. `client_event_id` 는 화면이 모른다 — 흐름이 들고 있다. */
data class ReceiptEdits(
    val reasonCode: String,
    val reasonDetail: String?,
    val visitedOn: LocalDate,
    val totalKrw: Int,
    val hospitalName: String?,
    val hospitalAddress: String?,
    val hospitalPhone: String?,
    val isEmergency: Boolean,
    val isOncology: Boolean,
)

data class VetVisitState(
    val selectedPetId: String? = null,
    val visits: ChatLoadState<List<VetVisit>> = ChatLoadState.Idle,
    /** 코드 → 표시명. **목록도 이걸로 라벨을 그린다** — 확정 응답에는 코드만 온다. */
    val reasonLabels: Map<String, String> = emptyMap(),
    /** 드롭다운 순서 그대로 (이 강아지가 최근 쓴 사유가 앞). */
    val reasonOptions: List<VetReasonOption> = emptyList(),
    val receipt: ReceiptFlow? = null,
    val deletingVisitId: String? = null,
    val deleteError: ChatApiError? = null,
)

interface VetVisitGateway {
    suspend fun startDraft(accessToken: String, petId: String, clientEventId: String): Result<VetVisitTicket>
    suspend fun upload(ticket: VetVisitTicket, jpeg: ByteArray): Result<Unit>
    suspend fun extract(accessToken: String, draftId: String): Result<VetVisitDraft>
    suspend fun confirm(accessToken: String, draftId: String, confirmation: VetVisitConfirmation): Result<VetVisit>
    suspend fun list(accessToken: String, petId: String): Result<List<VetVisit>>
    suspend fun reasonOptions(accessToken: String, petId: String): Result<List<VetReasonOption>>
    suspend fun delete(accessToken: String, visitId: String): Result<Unit>
}

private class RemoteVetVisitGateway(private val api: VetVisitApi = VetVisitApi()) : VetVisitGateway {
    override suspend fun startDraft(accessToken: String, petId: String, clientEventId: String) =
        api.startDraft(accessToken, petId, clientEventId)
    override suspend fun upload(ticket: VetVisitTicket, jpeg: ByteArray) = api.upload(ticket, jpeg)
    override suspend fun extract(accessToken: String, draftId: String) = api.extract(accessToken, draftId)
    override suspend fun confirm(accessToken: String, draftId: String, confirmation: VetVisitConfirmation) =
        api.confirm(accessToken, draftId, confirmation)
    override suspend fun list(accessToken: String, petId: String) = api.list(accessToken, petId)
    override suspend fun reasonOptions(accessToken: String, petId: String) = api.reasonOptions(accessToken, petId)
    override suspend fun delete(accessToken: String, visitId: String) = api.delete(accessToken, visitId)
}

/**
 * 저장소 탭 "진료비" 의 상태. `CareLogCoordinator` 와 같은 꼴이다 — 값은 서버 사본이고
 * 기기에 저장하지 않는다.
 *
 * 이 클래스의 일은 **재시도가 무엇을 다시 하느냐** 하나다:
 *
 * | 어디서 실패했나 | 다시 누르면 |
 * | --- | --- |
 * | ① 초안 · ② 업로드 | **새 키로 새 초안.** 그 사진이 어디까지 갔는지 모른다 |
 * | ③ 추출 (연결) | **같은 초안으로 추출만.** 저쪽이 저장된 결과를 준다 — Gemini 재호출 없음 |
 * | ③ 추출 (409 `photo_not_uploaded`) | **새 키로 새 초안.** 사진이 그 자리에 없다 |
 * | ④ 확정 | **같은 키로 확정만.** 저쪽이 그 키로 먼저 조회해 있던 기록을 준다 |
 *
 * ⚠️ **추출을 다시 불러 화면을 복구하려 들지 않는다.** 이미 추출된 초안을 다시 부르면
 *    저장된 결과가 오는데, 유저가 미동의였다면 항목이 애초에 저장되지 않아 그때도 안
 *    온다 (저쪽 docs §3). 확인 화면이 들고 있는 값이 원본이다.
 */
class VetVisitCoordinator(
    private val scope: CoroutineScope,
    private val gateway: VetVisitGateway = RemoteVetVisitGateway(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val mutableState = MutableStateFlow(VetVisitState())
    val state: StateFlow<VetVisitState> = mutableState.asStateFlow()

    /** 재시도가 다시 올릴 바이트. 흐름이 끝나면 놓는다 — 사진 하나가 계속 앉아 있지 않게. */
    private var pendingJpeg: ByteArray? = null

    private var petGeneration = 0L
    private var loadJob: Job? = null
    private var receiptJob: Job? = null
    private var deleteJob: Job? = null

    fun selectPet(petId: String?) {
        require(petId == null || petId.isNotBlank()) { "pet_id 가 비어 있습니다" }
        if (petId == mutableState.value.selectedPetId) return
        cancelAll()
        petGeneration++
        pendingJpeg = null
        mutableState.value = VetVisitState(selectedPetId = petId)
    }

    /**
     * 목록과 사유 표시명을 같이 받는다.
     *
     * **사유 목록이 실패해도 기록 목록은 보인다.** 라벨이 없으면 코드가 그대로 보일 뿐,
     * 기록을 못 보여 줄 이유가 아니다.
     */
    fun load(accessToken: String): Boolean {
        val petId = mutableState.value.selectedPetId ?: return false
        val generation = petGeneration
        loadJob?.cancel()
        mutableState.update { it.copy(visits = ChatLoadState.Loading) }
        loadJob = scope.launch {
            val options = gateway.reasonOptions(accessToken, petId).getOrNull()
            val visits = gateway.list(accessToken, petId)
            if (!isCurrentPet(petId, generation)) return@launch
            mutableState.update { state ->
                state.copy(
                    visits = visits.fold(
                        onSuccess = { ChatLoadState.Ready(it) },
                        onFailure = { ChatLoadState.Failed(it.asVetError()) },
                    ),
                    reasonOptions = options ?: state.reasonOptions,
                    reasonLabels = options?.associate { it.code to it.label } ?: state.reasonLabels,
                )
            }
        }
        return true
    }

    /**
     * 사진을 찍었다. **여기서 `client_event_id` 가 생긴다** — 업로드 버튼을 누를 때가
     * 아니다. 화면이 얼어 보여 두 번 눌려도 같은 키여야 초안이 하나다.
     */
    fun beginReceipt(accessToken: String, jpeg: ByteArray): Boolean {
        val petId = mutableState.value.selectedPetId ?: return false
        if (mutableState.value.receipt != null) return false
        pendingJpeg = jpeg
        val flow = ReceiptFlow(clientEventId = newId(), step = ReceiptStep.UPLOADING)
        mutableState.update { it.copy(receipt = flow) }
        runReceipt(accessToken, petId, flow, fromStart = true)
        return true
    }

    /** 실패한 자리에서 다시. 무엇을 다시 하는지는 클래스 머리말의 표대로다. */
    fun retryReceipt(accessToken: String): Boolean {
        val petId = mutableState.value.selectedPetId ?: return false
        val flow = mutableState.value.receipt ?: return false
        if (flow.error == null) return false

        // 사진이 어디까지 갔는지 모르는 실패는 **새 키로 새 초안**이다.
        val restart = flow.draftId == null || flow.error.code == PHOTO_NOT_UPLOADED
        val next = if (restart) {
            ReceiptFlow(clientEventId = newId(), step = ReceiptStep.UPLOADING)
        } else {
            flow.copy(step = ReceiptStep.EXTRACTING, error = null)
        }
        mutableState.update { it.copy(receipt = next) }
        runReceipt(accessToken, petId, next, fromStart = restart)
        return true
    }

    /**
     * ④ 확정. 실패하면 확인 화면으로 돌아가고, 같은 키로 다시 보낼 수 있다 — 저쪽이
     * 그 키로 먼저 조회하므로 이미 저장됐다면 그 기록이 온다.
     */
    fun confirm(accessToken: String, edits: ReceiptEdits): Boolean {
        val petId = mutableState.value.selectedPetId ?: return false
        val flow = mutableState.value.receipt ?: return false
        val draftId = flow.draftId ?: return false
        if (flow.step == ReceiptStep.CONFIRMING) return false

        val generation = petGeneration
        mutableState.update { it.copy(receipt = flow.copy(step = ReceiptStep.CONFIRMING, error = null)) }
        receiptJob = scope.launch {
            val result = runSafely {
                gateway.confirm(accessToken, draftId, edits.toConfirmation(flow.clientEventId))
            }
            if (!isCurrentPet(petId, generation)) return@launch
            result.fold(
                onSuccess = { visit ->
                    pendingJpeg = null
                    mutableState.update { it.copy(receipt = null, visits = it.visits.withVisit(visit)) }
                },
                onFailure = { error ->
                    mutableState.update {
                        it.copy(receipt = flow.copy(step = ReceiptStep.READY, error = error.asVetError()))
                    }
                },
            )
        }
        return true
    }

    /** 확인 화면을 닫는다. **확정 안 한 초안은 저쪽이 24시간 뒤 사진째 지운다.** */
    fun dismissReceipt() {
        receiptJob?.cancel()
        pendingJpeg = null
        mutableState.update { it.copy(receipt = null) }
    }

    fun delete(accessToken: String, visitId: String): Boolean {
        val petId = mutableState.value.selectedPetId ?: return false
        if (mutableState.value.deletingVisitId != null) return false
        val generation = petGeneration
        mutableState.update { it.copy(deletingVisitId = visitId, deleteError = null) }
        deleteJob = scope.launch {
            val result = gateway.delete(accessToken, visitId)
            if (!isCurrentPet(petId, generation)) return@launch
            result.fold(
                onSuccess = {
                    mutableState.update {
                        it.copy(deletingVisitId = null, visits = it.visits.without(visitId))
                    }
                },
                onFailure = { error ->
                    mutableState.update {
                        it.copy(deletingVisitId = null, deleteError = error.asVetError())
                    }
                },
            )
        }
        return true
    }

    fun clearErrors() {
        mutableState.update { it.copy(deleteError = null) }
    }

    fun cancelPending() {
        cancelAll()
        petGeneration++
        mutableState.update { it.copy(deletingVisitId = null) }
    }

    fun forget() = selectPet(null)

    // -- 아래는 배관 -------------------------------------------------------

    /**
     * ①②③ 을 한 코루틴에서 잇는다. [fromStart] 가 false 면 ③만 다시 부른다.
     *
     * 세 걸음을 갈라 함수를 셋 두지 않는 이유는 **중간 상태가 화면에 없어서**다 —
     * 유저에게는 "읽는 중" 하나이고, 어디서 끊겼는지는 [ReceiptFlow.draftId] 가 안다.
     */
    private fun runReceipt(
        accessToken: String,
        petId: String,
        flow: ReceiptFlow,
        fromStart: Boolean,
    ) {
        val generation = petGeneration
        receiptJob?.cancel()
        receiptJob = scope.launch {
            var draftId = flow.draftId
            if (fromStart) {
                val jpeg = pendingJpeg ?: return@launch
                val ticket = runSafely { gateway.startDraft(accessToken, petId, flow.clientEventId) }
                    .getOrElse { return@launch failReceipt(petId, generation, flow, it) }
                runSafely { gateway.upload(ticket, jpeg) }
                    .getOrElse { return@launch failReceipt(petId, generation, flow, it) }
                draftId = ticket.draftId
                if (!isCurrentPet(petId, generation)) return@launch
                mutableState.update {
                    it.copy(receipt = flow.copy(step = ReceiptStep.EXTRACTING, draftId = draftId))
                }
            }
            val id = draftId ?: return@launch
            val draft = runSafely { gateway.extract(accessToken, id) }
                .getOrElse { return@launch failReceipt(petId, generation, flow.copy(draftId = id), it) }
            if (!isCurrentPet(petId, generation)) return@launch
            mutableState.update {
                it.copy(
                    receipt = flow.copy(
                        step = ReceiptStep.READY,
                        draftId = id,
                        draft = draft,
                        error = null,
                    ),
                    // 초안이 실어 온 사유 목록이 더 최신이다 (이 강아지의 최근 사유가 앞).
                    reasonOptions = draft.reasonOptions.ifEmpty { it.reasonOptions },
                    reasonLabels = draft.reasonOptions
                        .takeIf { opts -> opts.isNotEmpty() }
                        ?.associate { opt -> opt.code to opt.label }
                        ?: it.reasonLabels,
                )
            }
        }
    }

    private fun failReceipt(petId: String, generation: Long, flow: ReceiptFlow, error: Throwable) {
        if (!isCurrentPet(petId, generation)) return
        mutableState.update { it.copy(receipt = flow.copy(error = error.asVetError())) }
    }

    private suspend fun <T> runSafely(block: suspend () -> Result<T>): Result<T> = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private fun isCurrentPet(petId: String, generation: Long): Boolean =
        generation == petGeneration && mutableState.value.selectedPetId == petId

    private fun cancelAll() {
        loadJob?.cancel()
        receiptJob?.cancel()
        deleteJob?.cancel()
    }

    private companion object {
        const val PHOTO_NOT_UPLOADED = "photo_not_uploaded"
    }
}

private fun ReceiptEdits.toConfirmation(clientEventId: String) = VetVisitConfirmation(
    clientEventId = clientEventId,
    reasonCode = reasonCode,
    reasonDetail = reasonDetail,
    visitedOn = visitedOn,
    totalKrw = totalKrw,
    hospitalName = hospitalName,
    hospitalAddress = hospitalAddress,
    hospitalPhone = hospitalPhone,
    isEmergency = isEmergency,
    isOncology = isOncology,
)

/** 방금 확정한 기록을 맨 앞에 넣는다. 아직 목록을 못 읽었으면 그대로 둔다. */
private fun ChatLoadState<List<VetVisit>>.withVisit(visit: VetVisit): ChatLoadState<List<VetVisit>> {
    val visits = (this as? ChatLoadState.Ready)?.value ?: return this
    if (visits.any { it.id == visit.id }) return this
    return ChatLoadState.Ready(listOf(visit) + visits)
}

private fun ChatLoadState<List<VetVisit>>.without(visitId: String): ChatLoadState<List<VetVisit>> {
    val visits = (this as? ChatLoadState.Ready)?.value ?: return this
    return ChatLoadState.Ready(visits.filterNot { it.id == visitId })
}

private fun Throwable.asVetError(): ChatApiError =
    this as? ChatApiError ?: ChatApiError.unreachable("서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.", this)
```

- [ ] **Step 4: 돌려서 통과를 본다**

실행: `./gradlew.bat :app:testDebugUnitTest --tests '*VetVisitCoordinatorTest'`
기대: 10 tests, 0 failures.

- [ ] **Step 5: 커밋**

제목: `영수증이 실패한 자리를 몰라 매번 처음부터 다시 가던 것`
본문 요지: 네 걸음의 재시도 규칙을 표로 못박고 코드로 옮겼다 · 업로드 전 실패만 새
`client_event_id` 이고 추출·확정 실패는 같은 키로 간다(저쪽이 멱등이라 Gemini 도 요금도
다시 안 든다) · 키는 촬영 시점에 만든다.

---

### Task 4: 확인 화면 — 이 카드의 핵심

**파일**
- 신규: `app/src/main/java/com/daengs/app/ui/storage/ReceiptConfirmScreen.kt`
- 테스트: `app/src/test/java/com/daengs/app/ui/storage/ReceiptConfirmScreenTest.kt`

**인터페이스**
- 쓰는 것: `VetVisitDraft` · `VetReasonOption` · `ReceiptItem` · `ReceiptStep` ·
  `ReceiptEdits` · `phoneLooksValid` · `ChatApiError` · `DaengsWideButton` ·
  `DaengsTextAction` · `DateWheel` · 테마 색(`CardWhite` · `CreamBg` · `TextDark` ·
  `TextMuted` · `DaengsColors`).
- 내는 것:
  ```kotlin
  @Composable
  fun ReceiptConfirmScreen(
      photo: android.graphics.Bitmap?,
      draft: VetVisitDraft?,
      options: List<VetReasonOption>,
      step: ReceiptStep,
      error: ChatApiError?,
      modifier: Modifier = Modifier,
      onConfirm: (ReceiptEdits) -> Unit = {},
      onRetry: () -> Unit = {},
      onDismiss: () -> Unit = {},
      today: LocalDate = LocalDate.now(),
  )
  ```

**이 화면이 지켜야 하는 것**

1. **`no_amount` 에서 화면을 비우지 않는다.** 총액 한 칸만 비고 병원·주소·전화·날짜·항목은
   미리 채워져 있어야 한다. `blurry` · `not_a_receipt` 는 진짜로 비어 있다.
2. **제안을 자동 수용하지 않는다.** `suggestedReasonCode` 를 미리 골라 주기는 하되
   드롭다운은 **실제로 쓰이는 UI** 여야 한다. `null` 이면 아무것도 안 고르고, 그때는
   유저가 고르기 전까지 [확인] 이 안 눌린다.
3. **병원 세 칸도 편집 가능하다.** 라벨만이 아니다.
4. **전화번호가 `phoneLooksValid` 를 통과하지 못하면 [확인] 이 막힌다.** 서버가 422 를
   내기 전에 여기서 막아 왕복을 줄이고, 무엇보다 `tel:` 링크가 모르는 번호를 걸지 않게 한다.
5. **`possibleDuplicate` 면 막지 말고 한 줄 경고**한다 — 같은 날 두 번 갈 수 있다.
6. 항목(`items`)은 **읽기 전용으로 보여 준다.** 확정 본문으로 안 간다 (저쪽이 초안에서만 읽는다).
7. 상태가 `UPLOADING`·`EXTRACTING` 이면 "영수증을 읽고 있어요" 와 함께 폼을 감춘다.
   `error` 가 있고 `draft` 가 없으면 "다시 시도" 만 보인다.

- [ ] **Step 1: 실패하는 테스트를 쓴다** — `app/src/test/java/com/daengs/app/ui/storage/ReceiptConfirmScreenTest.kt`

```kotlin
package com.daengs.app.ui.storage

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.daengs.app.care.ExtractionStatus
import com.daengs.app.care.ReceiptEdits
import com.daengs.app.care.ReceiptItem
import com.daengs.app.care.ReceiptStep
import com.daengs.app.care.UnreadableReason
import com.daengs.app.care.VetReasonOption
import com.daengs.app.care.VetVisitDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * 확인 화면이 지켜야 하는 것 — **총액 줄만 잘린 영수증을 빈 폼으로 만들지 않는다**,
 * 제안을 자동으로 받아들이지 않는다, 잘못 읽힌 전화번호를 통과시키지 않는다.
 */
@RunWith(RobolectricTestRunner::class)
class ReceiptConfirmScreenTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun `no_amount 는 총액만 비고 병원과 항목은 채워져 있다`() {
        compose.setContent {
            ReceiptConfirmScreen(
                photo = null,
                draft = noAmountDraft(),
                options = OPTIONS,
                step = ReceiptStep.READY,
                error = null,
            )
        }

        compose.onNodeWithText("압구정동물병원").assertExists()
        compose.onNodeWithText("02-543-0075").assertExists()
        compose.onNodeWithText("초진료").assertExists()
        compose.onNodeWithText("합계 줄을 못 읽었어요. 총액만 적어 주세요.").assertExists()
    }

    @Test
    fun `제안이 없으면 사유를 고르기 전까지 확인이 안 눌린다`() {
        compose.setContent {
            ReceiptConfirmScreen(
                photo = null,
                draft = okDraft(suggested = null),
                options = OPTIONS,
                step = ReceiptStep.READY,
                error = null,
            )
        }

        compose.onNodeWithText("확인").assertIsNotEnabled()
    }

    @Test
    fun `제안이 있으면 미리 골라 두되 확정된 값은 유저가 고른 것이다`() {
        var confirmed: ReceiptEdits? = null
        compose.setContent {
            ReceiptConfirmScreen(
                photo = null,
                draft = okDraft(suggested = "vaccination"),
                options = OPTIONS,
                step = ReceiptStep.READY,
                error = null,
                onConfirm = { confirmed = it },
            )
        }

        compose.onNodeWithText("예방접종").assertExists()
        compose.onNodeWithText("확인").performClick()

        assertEquals("vaccination", confirmed?.reasonCode)
        assertEquals(61_700, confirmed?.totalKrw)
        assertEquals(LocalDate.of(2026, 9, 10), confirmed?.visitedOn)
    }

    @Test
    fun `전화번호 모양이 틀리면 확인이 막힌다`() {
        compose.setContent {
            ReceiptConfirmScreen(
                photo = null,
                draft = okDraft(suggested = "skin"),
                options = OPTIONS,
                step = ReceiptStep.READY,
                error = null,
            )
        }

        compose.onNodeWithText("02-543-0075").performTextReplacement("5432-1234-5678-9012")

        compose.onNodeWithText("전화번호 모양이 올바르지 않아요.").assertExists()
        compose.onNodeWithText("확인").assertIsNotEnabled()
    }

    @Test
    fun `빈 전화번호는 통과하고 칸째로 안 보낸다`() {
        var confirmed: ReceiptEdits? = null
        compose.setContent {
            ReceiptConfirmScreen(
                photo = null,
                draft = okDraft(suggested = "skin"),
                options = OPTIONS,
                step = ReceiptStep.READY,
                error = null,
                onConfirm = { confirmed = it },
            )
        }

        compose.onNodeWithText("02-543-0075").performTextReplacement("")
        compose.onNodeWithText("확인").performClick()

        assertNull(confirmed?.hospitalPhone)
    }

    @Test
    fun `같은 날 같은 금액이 이미 있으면 막지 않고 한 줄 알려 준다`() {
        compose.setContent {
            ReceiptConfirmScreen(
                photo = null,
                draft = okDraft(suggested = "skin").copy(possibleDuplicate = true),
                options = OPTIONS,
                step = ReceiptStep.READY,
                error = null,
            )
        }

        compose.onNodeWithText("같은 날 같은 금액의 기록이 이미 있어요.").assertExists()
        compose.onNodeWithText("확인").assertExists()
    }

    @Test
    fun `읽는 중에는 폼 대신 안내가 보인다`() {
        compose.setContent {
            ReceiptConfirmScreen(
                photo = null,
                draft = null,
                options = emptyList(),
                step = ReceiptStep.EXTRACTING,
                error = null,
            )
        }

        compose.onNodeWithText("영수증을 읽고 있어요").assertExists()
    }

    private fun okDraft(suggested: String?) = VetVisitDraft(
        draftId = "d1", petId = "p1", status = ExtractionStatus.OK, unreadableReason = null,
        visitedOn = LocalDate.of(2026, 9, 10), totalKrw = 61_700,
        hospitalName = "압구정동물병원", hospitalAddress = "서울 강남구",
        hospitalPhone = "02-543-0075",
        items = listOf(ReceiptItem("초진료", 5_500)),
        suggestedReasonCode = suggested, isEmergency = false, possibleDuplicate = false,
        reasonOptions = OPTIONS,
    )

    private fun noAmountDraft() = okDraft(suggested = "vaccination").copy(
        status = ExtractionStatus.UNREADABLE,
        unreadableReason = UnreadableReason.NO_AMOUNT,
        totalKrw = null,
    )

    private companion object {
        val OPTIONS = listOf(
            VetReasonOption("skin", "피부"),
            VetReasonOption("vaccination", "예방접종"),
        )
    }
}
```

- [ ] **Step 2: 돌려서 깨지는 걸 본다**

실행: `./gradlew.bat :app:testDebugUnitTest --tests '*ReceiptConfirmScreenTest'`
기대: 컴파일 실패 — `Unresolved reference: ReceiptConfirmScreen`.

- [ ] **Step 3: `ui/storage/ReceiptConfirmScreen.kt` 를 쓴다**

구현 지침 (`CareLogSection` 의 결을 따른다 — 스크롤 하나, 카드 하나, 색은 테마에서):

```kotlin
/**
 * 영수증을 읽은 결과를 **사람이 확정하는** 화면. 기계가 채우고 사람이 고친다.
 *
 * ⚠️ **`no_amount` 에서 화면을 비우지 않는다.** `extraction_status="unreadable"` +
 *    `unreadable_reason="no_amount"` 는 "못 읽었다" 가 아니라 **"읽었는데 합계 줄이
 *    없다"** 다 — 병원·주소·전화·날짜·항목이 다 실려 온다. 사진 아래가 잘리는 것이
 *    이 기능에서 제일 흔한 실패라, 그때 빈 폼을 주면 거의 다 읽은 영수증을 통째로 버린다.
 *
 * ⚠️ **제안을 자동 수용하지 않는다.** `suggestedReasonCode` 는 믿을 만하지 않다 — 실제
 *    영수증 하나로 3회 돌렸을 때 `vaccination` 1 / `skin` 2 가 나왔다(정답은
 *    `vaccination`). 미리 골라 두기만 하고, 드롭다운이 장식이 아니라 실제로 쓰이는 UI 여야
 *    한다. `null` 이면 아무것도 안 고르고 [확인] 이 잠긴다.
 *
 * ⚠️ **병원 전화는 `tel:` 이 된다.** OCR 이 숫자를 뒤집으면 모르는 사람에게 전화가 걸린다 —
 *    금액 오류보다 조용히 틀리는 실패라 서버와 같은 모양([phoneLooksValid])으로 막는다.
 *
 * 영수증 그림은 **방금 찍은 로컬 비트맵**이다. 응답의 `receipt_image_url` 을 안 쓴다 —
 * 그 그림이 이미 우리 손에 있어서 받아 올 이유가 없다.
 */
```

- 상태는 `remember(draft)` 로 초기값을 draft 에서 뽑아 `mutableStateOf` 에 담는다.
  `draft` 가 바뀌면(재추출) 다시 채워진다.
  - `visitedOn: LocalDate` — `draft.visitedOn ?: today`
  - `totalText: String` — `draft.totalKrw?.toString() ?: ""` (숫자만 받는다: `filter { it.isDigit() }`)
  - `hospitalName` · `hospitalAddress` · `hospitalPhone` — `draft.….orEmpty()`
  - `reasonCode: String?` — `draft.suggestedReasonCode?.takeIf { code -> options.any { it.code == code } }`
  - `reasonDetail: String` — 빈 문자열, `take(60)`
  - `isEmergency` — `draft.isEmergency`, `isOncology` — `false` (**유저만 켠다**)
- 안내 문구(정확히 이 문자열이어야 테스트가 통과한다):
  - `NO_AMOUNT` → `"합계 줄을 못 읽었어요. 총액만 적어 주세요."`
  - `BLURRY` → `"사진이 흐려서 못 읽었어요. 손으로 적거나 다시 찍어 주세요."`
  - `NOT_A_RECEIPT` → `"영수증이 아닌 것 같아요. 손으로 적거나 다시 찍어 주세요."`
  - `ExtractionStatus.FAILED` → `"영수증을 읽지 못했어요. 손으로 적거나 다시 시도해 주세요."`
  - `possibleDuplicate` → `"같은 날 같은 금액의 기록이 이미 있어요."`
  - 전화 모양 오류 → `"전화번호 모양이 올바르지 않아요."`
  - `UPLOADING`·`EXTRACTING` → `"영수증을 읽고 있어요"`
- 사유는 **접힌 드롭다운이 아니라 칩**으로 편다 (구현 중 바꾼 것). 제안이 못 믿을 값이라
  유저가 실제로 다시 고르는 것이 이 화면의 목적인데, 접어 두면 "이미 골라져 있다" 로
  읽혀 그냥 넘어간다.

  ⚠️ **다만 전부 펼치지 않는다.** 실제 사유는 17개라 411dp 폭에서 다섯 줄이 되고 그만큼
  [확인] 이 아래로 밀린다 — `PetFormScreen` 의 `BreedGrid` 가 견종 28종에서 이미 같은
  결론을 내고 **"일곱 줄이면 폼의 절반이 견종이 된다"** 고 적어 뒀다. 그쪽처럼 높이를
  묶고 안에서 스크롤하며, 안쪽 스크롤이 바깥 폼을 밀지 않게 `ui/common/KeepScrollInside`
  를 붙인다 (그 파일은 원래 `PetFormScreen` 의 private 였고 두 번째 쓰임이 생겨 올렸다).

  ⚠️ **테스트는 칩을 좌표로 누르지 못한다.** 안쪽 스크롤이 있으면 `performScrollTo` 가
  바깥 폼을 안 움직여서 칩이 화면 밖에 있는 채로 눌린다 — 클릭이 조용히 빗나간다.
  `performSemanticsAction(SemanticsActions.OnClick)` 으로 누른다.
- [확인] 버튼은 `DaengsWideButton(label = "확인", accent = true, busy = step == ReceiptStep.CONFIRMING, enabled = valid)`.
  `valid = reasonCode != null && totalText.isNotBlank() && phoneLooksValid(hospitalPhone)`
- `onConfirm` 에 넘길 때 병원 세 칸과 `reasonDetail` 은 **`trim().takeIf { it.isNotEmpty() }`**
  로 비면 `null` 이 되게 한다 — `VetVisitConfirmation.toJson` 이 null 칸을 안 싣는다.
- `@Preview` 를 셋 붙인다: 정상(`ok`) · 총액만 빈 것(`no_amount`) · 읽는 중.

- [ ] **Step 4: 돌려서 통과를 본다**

실행: `./gradlew.bat :app:testDebugUnitTest --tests '*ReceiptConfirmScreenTest'`
기대: 7 tests, 0 failures.

- [ ] **Step 5: 커밋**

제목: `읽힌 값이 그대로 기록이 되던 것 — 확인 화면을 세운다`

---

### Task 5: 저장소 탭의 진료비 칸

**파일**
- 신규: `app/src/main/java/com/daengs/app/ui/storage/VetVisitSection.kt`
- 테스트: `app/src/test/java/com/daengs/app/ui/storage/VetVisitSectionTest.kt`

**인터페이스**
- 쓰는 것: `VetVisitState` · `VetVisit` · `ChatLoadState` · `CareLogSection` 의 구성 규칙.
- 내는 것:
  ```kotlin
  @Composable
  fun VetVisitSection(
      state: VetVisitState,
      modifier: Modifier = Modifier,
      onPickReceipt: () -> Unit = {},
      onRetryLoad: () -> Unit = {},
      onConfirmDelete: (VetVisit) -> Unit = {},
      onDismissError: () -> Unit = {},
      onCallHospital: (String) -> Unit = {},
  )
  ```

**이 칸이 지켜야 하는 것**

- **스크롤하지 않는 `Column`** 이다 — 저장소 탭의 `LazyColumn` 안에 꽂힌다.
  스크롤 안에 스크롤을 두지 않는다 (`CareLogSection` 과 같은 규칙).
- **사유 표시명은 `state.reasonLabels[visit.reasonCode]` 로 그린다.** 없으면 코드를 그대로
  보여 준다 — 저쪽이 사유를 하나 더 만드는 날 크래시가 아니라 못생긴 라벨이어야 한다.
- 한 줄: `9월 10일 · 피부 · 61,700원` + 병원 이름. 전화가 있으면 그 줄에 **누르면 걸리는**
  전화 표시를 둔다 (`onCallHospital`).
- 삭제는 `CareLogSection` 과 같은 `AlertDialog` 확인을 거친다.
- 금액은 `NumberFormat.getIntegerInstance(Locale.KOREA)` 로 쉼표를 찍는다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```kotlin
@RunWith(RobolectricTestRunner::class)
class VetVisitSectionTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun `사유 표시명은 서버가 준 지도에서 온다`() {
        compose.setContent {
            VetVisitSection(state = ready(listOf(visit(reason = "skin"))))
        }
        compose.onNodeWithText("9월 10일 · 피부 · 61,700원").assertExists()
    }

    @Test
    fun `모르는 코드는 코드를 그대로 보여 준다 — 크래시가 아니다`() {
        compose.setContent {
            VetVisitSection(state = ready(listOf(visit(reason = "dermatology"))))
        }
        compose.onNodeWithText("9월 10일 · dermatology · 61,700원").assertExists()
    }

    @Test
    fun `기록이 없으면 그렇게 말한다`() {
        compose.setContent { VetVisitSection(state = ready(emptyList())) }
        compose.onNodeWithText("아직 남긴 진료비 기록이 없어요").assertExists()
    }

    @Test
    fun `영수증 찍기를 누르면 알려 준다`() {
        var picked = false
        compose.setContent {
            VetVisitSection(state = ready(emptyList()), onPickReceipt = { picked = true })
        }
        compose.onNodeWithText("영수증 찍기").performClick()
        assertTrue(picked)
    }

    @Test
    fun `병원 전화를 누르면 그 번호를 넘긴다`() {
        var called: String? = null
        compose.setContent {
            VetVisitSection(
                state = ready(listOf(visit(reason = "skin", phone = "02-543-0075"))),
                onCallHospital = { called = it },
            )
        }
        compose.onNodeWithText("02-543-0075").performClick()
        assertEquals("02-543-0075", called)
    }

    private fun ready(visits: List<VetVisit>) = VetVisitState(
        selectedPetId = "pet",
        visits = ChatLoadState.Ready(visits),
        reasonLabels = mapOf("skin" to "피부"),
    )

    private fun visit(reason: String, phone: String? = null) = VetVisit(
        id = "v1", petId = "pet", visitedOn = LocalDate.of(2026, 9, 10), totalKrw = 61_700,
        hospitalName = "압구정동물병원", hospitalAddress = null, hospitalPhone = phone,
        reasonCode = reason, reasonDetail = null, isEmergency = false, isOncology = false,
        clientEventId = "c1",
    )
}
```

- [ ] **Step 2: 돌려서 깨지는 걸 본다** — `Unresolved reference: VetVisitSection`
- [ ] **Step 3: `ui/storage/VetVisitSection.kt` 를 쓴다.** `CareLogSection.kt` 를 옆에 두고
  같은 뼈대로 쓴다 (머리 줄 → 버튼 → 오류 줄 → `when (state.visits)`). `@Preview` 셋:
  기록 있음 · 빈 목록 · 읽기 실패.
- [ ] **Step 4: 돌려서 통과를 본다** — 5 tests, 0 failures.
- [ ] **Step 5: 커밋** — 제목: `병원비만 저장소 탭에 없던 것`

---

### Task 6: 영수증 집기와 배선

**파일**
- 신규: `app/src/main/java/com/daengs/app/ui/storage/ReceiptPicker.kt`
- 수정: `app/src/main/java/com/daengs/app/screening/Photo.kt` (`prepare` 에 `edge` 인자)
- 수정: `app/src/main/java/com/daengs/app/ui/storage/ChatSummaryRoute.kt`
- 수정: `app/src/main/java/com/daengs/app/MainActivity.kt`
- 테스트: `app/src/test/java/com/daengs/app/screening/PhotoEdgeTest.kt`

**인터페이스**
- 내는 것:
  ```kotlin
  /** 영수증의 긴 변. 항목명이 잔글씨라 피부용 1600 으로는 뭉갠다. */
  const val RECEIPT_EDGE = 2400

  @Composable
  fun ReceiptPicker(onDone: (PreparedPhoto?) -> Unit)
  ```
  `Photo.prepare(context, uri, edge: Int = MAX_EDGE)` — 기본값이 있어 **기존 호출부는 안 바뀐다.**

**이 태스크가 지켜야 하는 것**

- 카메라 권한이 없으면 **시스템 카메라로 못 떨어진다** (매니페스트에 `CAMERA` 가 있어서
  안드로이드가 막는다 — `ChatScreen` 의 `CAMERA_DENIED` 주석). 거부하면 갤러리로 안내한다.
- **PNG 가 와도 JPEG 으로 굽는다.** 저쪽 키 빌더가 `jpeg`/`webp` 만 받고, 티켓의
  Content-Type 과 다르면 415 다. `Photo.prepare` 가 항상 JPEG 을 낸다.
- `onDone(null)` 은 그만둔 것이다 (`PetPhotoPicker` 와 같은 규칙).
- **`beginReceipt` 는 사진이 손에 들어온 직후에 부른다** — 그 순간이 `client_event_id` 가
  생기는 시점이다.

- [ ] **Step 1: `Photo.prepare` 의 edge 인자에 실패하는 테스트를 쓴다**

```kotlin
@RunWith(RobolectricTestRunner::class)
class PhotoEdgeTest {

    @Test
    fun `영수증은 피부보다 크게 굽는다 — 항목명이 잔글씨다`() {
        assertEquals(1600, Photo.MAX_EDGE)
        assertEquals(2400, RECEIPT_EDGE)
        assertTrue("영수증이 더 커야 한다", RECEIPT_EDGE > Photo.MAX_EDGE)
    }
}
```

> 비트맵을 실제로 굽는 것은 Robolectric 에서 값이 안 나온다. 여기서 잡는 것은 **두 숫자가
> 갈라져 있다는 사실** 하나이고, 잔글씨가 실제로 읽히는지는 실기기 검증(Task 8)이 본다.

- [ ] **Step 2: 돌려서 깨지는 걸 본다** — `Unresolved reference: RECEIPT_EDGE`
- [ ] **Step 3: `Photo.prepare` 에 `edge` 인자를 더하고 `ReceiptPicker.kt` 를 쓴다**

`Photo.kt` 수정 — 한 줄짜리 변경이고 기존 호출부는 그대로다:

```kotlin
suspend fun prepare(context: Context, uri: Uri, edge: Int = MAX_EDGE): Result<PreparedPhoto> =
    withContext(Dispatchers.IO) {
        runCatching {
            val scaled = decodeUpright(context, uri, edge)
            ...
        }
    }
```

`ReceiptPicker.kt` 는 `PetPhotoPicker` 의 뼈대에서 **원형 틀을 뺀 것**이다 — 영수증은
잘라 넣을 자리가 없다. 카메라/갤러리 두 길을 시트로 고르게 하고, 고른 뒤 바로
`Photo.prepare(context, uri, RECEIPT_EDGE)` 로 구워 `onDone` 한다.

- [ ] **Step 4: 돌려서 통과를 본다**
- [ ] **Step 5: `ChatSummaryRoute` 와 `MainActivity` 에 잇는다**

`MainActivity.kt` — `careLog` 바로 아래:

```kotlin
// 저장소 탭의 진료비 (#258). 케어 기록과 같은 생애 — 서버 사본이고 기기에 안 남긴다.
val vetVisits = remember(scope) { VetVisitCoordinator(scope) }
```
`LaunchedEffect(session?.appUserId, pets.primary?.id)` 안의 `chatSummaries.selectPet(petId)`
옆에 `vetVisits.selectPet(petId)` 를 더하고, `ChatSummaryRoute(...)` 호출에
`vetCoordinator = vetVisits` 를 넘긴다.

`ChatSummaryRoute.kt`:
- 인자에 `vetCoordinator: VetVisitCoordinator` 를 더한다.
- `LaunchedEffect` 와 `DisposableEffect` 의 대상에 같이 넣는다 (`selectPet` · `load` ·
  `cancelPending`).
- `item(key = "care")` **뒤에** `item(key = "vet")` 을 놓는다.
- ⚠️ **`HEADER_ITEMS` 를 2 → 3 으로 올린다.** 그 상수는 요약 목록 앞의 항목 수이고,
  선택된 요약으로 스크롤할 때 더하는 값이다 — 안 올리면 스크롤이 한 칸 어긋난다.
  (그 파일에 이미 경고 주석이 있다.)
- 영수증을 고르는 중이면 `ReceiptPicker` 를, `vetState.receipt != null` 이면
  `ReceiptConfirmScreen` 을 목록 **위에 덮는다.**

  ⚠️ **"사진 바꾸기와 같은 방식" 이 아니다** (계획을 쓸 때 잘못 적었다). `PetPhotoPicker`
  는 `MainActivity` 에서 홈을 **교체**하지만 여기는 `Box` 안에서 **겹친다** — 아래 목록이
  컴포지션에 남아 히트테스트를 받는다. 그래서 두 오버레이의 루트에 **터치를 먹는 계층**을
  둬야 하고(`pointerInput` 으로 이벤트를 소비), 인셋 패딩보다 앞에 둬야 상태바 자리까지
  덮는다. 안 두면 빈 자리를 눌렀을 때 그 좌표의 [삭제]나 전화번호가 눌린다.

  ⚠️ **둘 다 `BackHandler` 를 단다.** 이 저장소에서 전면을 덮는 화면은 예외 없이 back 을
  잡는다 (`PetPhotoPicker`·`SkinCaptureScreen`·`ImmersiveScreen`). 안 잡으면 홈이 받아서
  오버레이는 그대로인 채 뒤에서 화면이 바뀐다.

  ⚠️ **사진은 화면이 아니라 `ReceiptFlow` 가 든다.** 저장소 탭을 잠깐 벗어나면 route 의
  `remember` 가 날아가서, 돌아왔을 때 대조할 원본이 없는 확인 화면이 뜬다.

- [ ] **Step 6: 배선이 깨지지 않았는지 본다**

실행: `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest`
기대: BUILD SUCCESSFUL. `ChatSummaryRouteTest` 가 새 인자 때문에 깨지면 그 테스트도 고친다
(`vetCoordinator = VetVisitCoordinator(scope, fakeGateway)`).

- [ ] **Step 7: 커밋** — 제목: `영수증을 집을 길이 없던 것`

---

### Task 7: OCR 학습 이용 동의 (만들되 안 보인다)

**파일**
- 수정: `app/src/main/java/com/daengs/app/auth/Session.kt` (`AppMe` 에 `ocrConsent`)
- 수정: `app/src/main/java/com/daengs/app/auth/AuthApi.kt` (`setOcrConsent` · 파서)
- 수정: `app/src/main/java/com/daengs/app/ui/my/MyScreen.kt` (숨긴 토글)
- 테스트: `app/src/test/java/com/daengs/app/auth/AuthApiOcrConsentTest.kt`

**이 태스크가 지켜야 하는 것**

- **`PATCH /auth/app/me` 는 보낸 칸만 바꾼다.** 닉네임을 고칠 때 `ocr_consent` 를 같이
  보내면 그 값으로 덮이고, 반대도 마찬가지다. `setNickname`·`setRoomName` 이 이미
  **한 칸만 싣는 규칙**을 지키고 있으니 그대로 따른다.
- 응답의 `ocr_consent` 는 **불리언**이다 (원본은 `ocr_consent_at` 시각이지만 앱에는
  불리언으로 온다). `ocr_consent_version` 은 앱이 안 읽는다 — 서버가 정한다.
- ⚠️ **토글을 내보내지 않는다.** 저쪽 `OCR_CONSENT_VERSION` 이 `"unset"` 이라 지금 동의를
  받아도 실제 개인정보처리방침 판을 가리키지 않는다. 상수 한 줄로 가려 두고, 판 번호가
  정해지면 그 줄만 바꾼다.
- **동의는 진료비 기록을 안 막는다.** 미동의여도 금액·병원·사유가 저장되고 비서도 읽는다.
  갈리는 것은 `raw_ocr_items` 를 학습용으로 남길지 하나뿐이고, 화면에는 두 경우 모두
  항목이 보인다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```kotlin
class AuthApiOcrConsentTest {

    @Test
    fun `동의 토글은 ocr_consent 칸만 싣는다 — 닉네임을 같이 보내지 않는다`() {
        val body = JSONObject(AuthApi.ocrConsentBody(true).toString())

        assertTrue(body.getBoolean("ocr_consent"))
        assertFalse("같이 보내면 그 값으로 덮인다", body.has("nickname"))
        assertFalse(body.has("room_name"))
        assertFalse("판 번호는 서버가 정한다", body.has("ocr_consent_version"))
        assertEquals(1, body.length())
    }

    @Test
    fun `me 응답의 ocr_consent 는 불리언이고 없으면 미동의다`() {
        assertTrue(AuthApi.parseMe(JSONObject(ME_CONSENTED)).ocrConsent)
        assertFalse(AuthApi.parseMe(JSONObject(ME_PLAIN)).ocrConsent)
    }
}
```

> `ocrConsentBody` · `parseMe` 를 테스트가 부를 수 있게 `internal` 로 연다.
> `AuthApi.kt` 의 파서가 지금 private 이면 그 한 줄만 바꾼다 — 배관을 새로 만들지 않는다.

- [ ] **Step 2: 돌려서 깨지는 걸 본다**
- [ ] **Step 3: 세 파일을 고친다**
  - `Session.kt` 의 `AppMe` 에 `val ocrConsent: Boolean = false` 를 더한다.
  - `AuthApi.kt` — 파서에 `ocrConsent = optBoolean("ocr_consent")`, 그리고
    `setNickname` 과 **같은 모양**의 `suspend fun setOcrConsent(accessToken: String, on: Boolean): Result<AppMe>`.
  - `MyScreen.kt` — `SettingSection` 안에 스위치 한 줄을 만들되
    ```kotlin
    /**
     * ⚠️ **아직 내보내지 않는다.** 저쪽 `OCR_CONSENT_VERSION` 이 `"unset"` 이라 지금
     *    동의를 받아도 가리킬 판이 없다 (저쪽 docs/vet-visits.md "열린 것"). 개인정보
     *    처리방침에 목적 외 이용 조항이 실리고 판 번호가 정해지면 이 줄을 true 로 바꾼다.
     *    **진료비 기능은 그와 무관하게 지금 돈다** — 동의는 학습 데이터를 남길지만 가른다.
     */
    private const val OCR_CONSENT_VISIBLE = false
    ```
    로 가린다.
- [ ] **Step 4: 돌려서 통과를 본다**
- [ ] **Step 5: 커밋** — 제목: `동의 칸이 서버에만 있고 앱에는 없던 것`

---

### Task 8: 문서와 실기기 검증

- [ ] **Step 1: `HISTORY.md` 에 한 절을 더한다**

되돌리기 번거로운 결정만 적는다:
- 확인 화면이 **방금 찍은 로컬 비트맵**을 쓰고 `receipt_image_url` 을 안 쓴다 (이미지
  로더를 안 들였다). 확정된 기록의 사진을 나중에 다시 보는 화면을 만들려면 그때 로더가 든다.
- 목록의 사유 표시명이 **`reason-options` 응답**에서 온다. 강아지를 고를 때 한 번 더
  두드리는 대신 17개 한글을 앱에 안 적는다.
- bridge PUT 의 **409 를 성공으로 접는다**. 실패로 보면 같은 영수증에 Gemini 가 한 번 더 붙는다.

- [ ] **Step 2: 빌드와 단위 테스트 전체**

```
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```
**출력을 그대로 읽는다.** BUILD SUCCESSFUL 과 실패 0 을 눈으로 본다.

- [ ] **Step 3: 설치하고 `Success` 를 눈으로 본다**

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
⚠️ 출력을 버리지 않는다 — 이 폰은 `adb` 인증이 수시로 풀려 **조용히 실패한다** (CLAUDE.md).

- [ ] **Step 4: 실기기 검증** — PR 본문 "확인한 것" 을 그대로 짚는다

1. 에뮬레이터 `pixel7_api37`, 카카오 로그인된 실계정
2. 영수증 실물 한 장으로 ①~④ 전체 흐름
3. **합계가 잘린 영수증으로 `no_amount` 경로 — 총액만 비고 나머지가 채워지는지**
4. 같은 화면 두 번 탭 → 초안이 하나인지 (서버 로그 또는 Gemini 호출 수로)
5. `tel:` 링크가 실제로 전화 앱을 여는지
6. 닉네임만 바꿨을 때 동의가 안 꺼지는지

스크린샷을 찍기 전에 **앱이 앞에 떠 있는지** 본다:
`adb shell dumpsys window | grep mCurrentFocus` (CLAUDE.md — 남의 대화창이 찍힌다).

- [ ] **Step 5: PR 본문의 체크박스를 채우고 draft 를 푼다**

---

## 자기 점검 (계획을 쓴 뒤 한 번)

**스펙 덮개** — PR 작업 목록 여섯 줄이 전부 태스크에 있다:
`VetVisitApi.kt`(T2) · `VetVisitSection.kt`(T5) · `ReceiptConfirmScreen.kt`(T4) ·
카메라·갤러리→JPEG→업로드(T6) · 동의 토글(T7) · `HISTORY.md`(T8).
PR "컨텍스트 메모" 의 아홉 가지 함정도 전부 태스크 안에 문장으로 들어가 있다.

**빈칸** — 없다. 모든 코드 단계에 실제 코드나, 문자열까지 지정한 지침이 있다.

**이름 일관성** — `VetVisitDraft` · `VetVisit` · `VetVisitTicket` · `ReceiptItem` ·
`VetReasonOption` · `VetVisitConfirmation` · `ReceiptFlow` · `ReceiptStep` · `ReceiptEdits` ·
`VetVisitGateway` · `phoneLooksValid` · `RECEIPT_EDGE` 가 Task 1→8 에서 같은 철자로 쓰인다.

## 이 계획이 안 하는 것

- **확정된 기록의 영수증 사진 다시 보기.** 초안 행이 확정과 함께 지워져 bridge download 도
  404 다. 필요해지면 저쪽에 조회 경로부터 나야 한다.
- **기간 조회 UI** (`from`/`to`). 서버 기본값(최근 1년)만 쓴다.
- **동의 토글 노출.** 판 번호가 정해질 때까지 숨긴다.
- **`suggested_reason_code` 적중률 측정.** 영수증이 쌓인 뒤의 일이다.
