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

/**
 * 영수증에서 읽은 진료 항목 한 줄. 항목 자체는 확정 본문으로 안 간다 — 저쪽이 초안에서만
 * 읽는다.
 *
 * ⚠️ [patientIndex] 는 **몇 번째 `동물명` 블록에서 나온 항목인가**다. 아이별 분할이
 *    제안하는 금액이 이 값으로 묶어 더한 합이라, 여기가 비면 분할이 아무것도 제안하지
 *    못한다. **모르는 것(`null`)을 0 으로 접지 말 것** — 어느 아이 것인지 모르는 항목을
 *    첫째 아이에게 붙이는 셈이 된다.
 */
data class ReceiptItem(val name: String, val amountKrw: Int, val patientIndex: Int? = null) {
    companion object {
        fun parse(json: JSONObject) = ReceiptItem(
            json.getString("name"),
            json.getInt("amount_krw"),
            if (json.isNull("patient_index")) null else json.optInt("patient_index"),
        )
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
    val uploadUrl: String,
    val uploadHeaders: Map<String, String>,
    /**
     * 201 이면 새로 만든 것, 200 이면 같은 `client_event_id` 로 있던 것. **둘 다 성공이다.**
     *
     * 화면이 안 쓰는데 남긴 유일한 칸이다 — 이 계약이 이 클래스에 적혀 있지 않으면 다음
     * 사람이 200 을 실패로 볼 자리다. 응답의 `pet_id`·`storage_key`·`expires_in_seconds`
     * 는 그런 근거가 없어 안 담았다.
     */
    val created: Boolean,
) {
    companion object {
        fun parse(json: JSONObject, status: Int): VetVisitTicket {
            val headers = json.optJSONObject("upload_headers")
            return VetVisitTicket(
                draftId = json.getString("draft_id"),
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
     * 영수증에서 센 `동물명` 블록의 수. **1 이면 분할을 아예 묻지 않는다.**
     *
     * ⚠️ **칸이 없으면 1 이다.** 저쪽 DAENGS_dev#562 전의 서버는 이 칸을 안 싣는데,
     *    그때 0 이나 null 로 두면 분할 자리가 깨진 모양으로 뜬다 — 안 묻는 쪽이 맞다.
     *
     * ⚠️ **"의심되면 높은 쪽"으로 온다** (저쪽 docs §2). 보호자명·수의사명을 동물명으로
     *    잘못 읽으면 2 가 오고 실제로는 한 마리다. 그래서 유저가 분할을 **끄고** 한
     *    아이로 확정할 수 있어야 한다.
     */
    val patientCount: Int,
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
            patientCount = json.optInt("patient_count", 1).coerceAtLeast(1),
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
 * 확정될 기록 한 줄 = **아이 하나** (`VetVisitSplit`).
 *
 * ⚠️ **[clientEventId] 는 행마다 하나씩이고, 재시도 때 같은 값을 보낸다.** 저쪽의 멱등
 *    키가 행 단위라(`UNIQUE (app_user_id, client_event_id)`), 재시도에 새 uuid 를 만들면
 *    **기록이 두 벌 생긴다.** 반대로 같은 값이면 먼저 저장된 것이 그대로 돌아온다 —
 *    내용이 달라도 먼저 온 것이 남는다.
 *
 * ⚠️ **[patientIndex] 를 접지 말 것.** `null` 이면 저쪽이 이 행에 항목을 하나도 안 넣는데,
 *    그게 일부러 그렇게 한 것이다 — 어느 아이 것인지 모르는 항목을 아무 아이에게나 붙이면
 *    OCR 학습 데이터가 틀린다. 그러니 **모를 때는 0 이 아니라 `null` 이 맞다.**
 *
 * [petId] 를 안 주면 저쪽이 초안을 만들 때 고른 강아지를 쓴다.
 */
data class VetVisitSplit(
    val clientEventId: String,
    val petId: String?,
    val reasonCode: String,
    val reasonDetail: String?,
    /** 이 아이 몫. **행들의 합이 영수증 총액과 다르면 422 다.** */
    val totalKrw: Int,
    val isEmergency: Boolean,
    val isOncology: Boolean,
    val patientIndex: Int?,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("client_event_id", clientEventId)
        .put("reason_code", reasonCode)
        .put("total_krw", totalKrw)
        .put("is_emergency", isEmergency)
        .put("is_oncology", isOncology)
        .putIfPresent("pet_id", petId)
        .putIfPresent("reason_detail", reasonDetail)
        .putIfPresent("patient_index", patientIndex)
}

/**
 * 유저가 [확인] 을 누른 값. **여기 실린 것만 기록이 된다** — 항목(`raw_ocr_items`)은
 * 저쪽이 초안에서만 읽으므로 이 본문에 자리가 없다 (docs §3).
 *
 * 날짜·총액·병원은 **영수증 단위**라 아이마다 같고, 나머지는 [splits] 안에 아이마다 있다.
 * [totalKrw] 는 영수증에 인쇄된 총액이고 저쪽이 `splits` 의 합과 대조한다.
 *
 * ⚠️ **[splits] 는 언제나 있고 길이가 1 이상이다.** 한 마리는 특수 케이스가 아니라
 *    `splits.size == 1` 이다 — 옛 평평한 본문은 저쪽에서 한시적 호환일 뿐이고, 이 앱이
 *    깔리면 지워진다.
 */
data class VetVisitConfirmation(
    val visitedOn: LocalDate,
    val totalKrw: Int,
    val hospitalName: String?,
    val hospitalAddress: String?,
    val hospitalPhone: String?,
    val splits: List<VetVisitSplit>,
) {
    /** **빈 칸은 아예 안 보낸다.** 빈 문자열을 보내면 저쪽 패턴 검사가 422 를 낸다. */
    fun toJson(): JSONObject = JSONObject()
        .put("visited_on", visitedOn.toString())
        .put("total_krw", totalKrw)
        .put("splits", JSONArray(splits.map { it.toJson() }))
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

/**
 * 영수증 사진 한 장의 상한 (저쪽 `services/vet_visit.py` 의 `MAX_RECEIPT_BYTES`).
 *
 * 저쪽이 bridge 에서 413 으로 막지만, 다 올리고 나서 거절당하면 대역폭이 이미 나갔다.
 */
const val MAX_RECEIPT_BYTES = 12 * 1024 * 1024

/**
 * 확인 화면의 글자 수 상한. 저쪽 `VetVisitConfirmRequest` 의 `max_length` 와 같은 숫자다.
 *
 * **넘겨 보내면 화면이 엉뚱한 말을 한다.** FastAPI 검증 422 는 `detail` 이 배열이라
 * [com.daengs.app.chat.ChatApiError.from] 이 "요청 형식이 맞지 않아요. 앱을 업데이트해
 * 주세요." 로 떨어진다 — OCR 이 읽어 온 긴 병원 주소 하나 때문에 유저에게 앱을
 * 업데이트하라고 말하게 된다. 전화번호를 [phoneLooksValid] 로 막은 것과 같은 결이다.
 */
const val MAX_HOSPITAL_NAME = 60
const val MAX_HOSPITAL_ADDRESS = 200
const val MAX_REASON_DETAIL = 60

/** 금액 상한도 저쪽과 같다 (`total_krw` 는 `ge=0, le=100_000_000`). */
const val MAX_TOTAL_KRW = 100_000_000

// -- 아래는 배관 -------------------------------------------------------------

private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).ifBlank { null }

private fun <T> JSONArray?.toObjectList(parse: (JSONObject) -> T): List<T> =
    List(this?.length() ?: 0) { parse(this!!.getJSONObject(it)) }

private fun JSONObject.putIfPresent(key: String, value: String?): JSONObject =
    if (value.isNullOrBlank()) this else put(key, value.trim())

/** 모르는 값은 **칸째로 뺀다.** `null` 을 실어 보내는 것과 안 보내는 것은 저쪽에서 같다. */
private fun JSONObject.putIfPresent(key: String, value: Int?): JSONObject =
    if (value == null) this else put(key, value)
