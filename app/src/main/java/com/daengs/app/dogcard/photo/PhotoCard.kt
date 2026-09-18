package com.daengs.app.dogcard.photo

import org.json.JSONException
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * 서버가 그려 준 포토 카드 한 장. 계약은 저쪽 `routers/ai_card.py` (#537, #593).
 *
 * **Room 에 두지 않는다.** 누끼 카드는 오프라인에서 먼저 생겨 기기 표가 필요했지만,
 * 이 카드는 서버에서만 생긴다 — 정본이 서버다 (docs/photo-cards.md §3).
 */
data class PhotoCard(
    val id: String,
    val dogId: String?,
    /** 무엇을 만들었나 — 달이면 `"4"`, 종류면 `"strawberry"`. 옛 `month` 자리다 ([PhotoCardKey]). */
    val key: PhotoCardKey,
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

/**
 * 한 장을 읽는다. **`card` 를 먼저 보고 없으면 `month` 로 만든다** — #593 배포 전 서버가 남아
 * 있을 수 있다. 둘 다 없는 행은 그릴 칸을 찾을 수 없어 [JSONException] 으로 튄다(옛 코드가
 * `getInt("month")` 로 튀던 것과 같은 자리다).
 */
fun parsePhotoCard(json: JSONObject): PhotoCard = PhotoCard(
    id = json.getString("id"),
    dogId = json.optStringOrNull("dog_id"),
    key = PhotoCardKey.from(
        card = json.optStringOrNull("card"),
        month = if (json.isNull("month")) null else json.optInt("month").takeIf { it in 1..12 },
    ) ?: throw JSONException("카드 종류가 없는 행 (${json.optString("id")})"),
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

/**
 * 목록 응답. `dailyRemaining` 은 #543 배포 뒤에만 온다 — 없거나 `null` 이면
 * 배포 전이라는 뜻이라 앱은 막지 않는다 (docs/photo-cards.md §9.1).
 */
data class PhotoCardList(val cards: List<PhotoCard>, val dailyRemaining: Int?)

fun parsePhotoCardList(body: String): PhotoCardList {
    val json = JSONObject(body)
    val arr = json.getJSONArray("cards")
    val cards = (0 until arr.length()).map { parsePhotoCard(arr.getJSONObject(it)) }
    val remaining = if (!json.has("daily_remaining") || json.isNull("daily_remaining")) {
        null
    } else {
        json.optInt("daily_remaining")
    }
    return PhotoCardList(cards, remaining)
}

/**
 * 메타는 쿼리다 (본문은 사진 원시 바이트). 한글 이름은 UTF-8 로 인코딩한다.
 * `titleName` 은 앞뒤 공백을 걷어 비어 있으면 아예 안 보낸다 (docs/photo-cards.md §9.2).
 *
 * **`card` 만 보내고 `month` 는 안 보낸다** (#593, D-085). 서버는 둘 다 받지만 값이 어긋나면
 * 400 `card_conflict` 를 주므로, 어긋날 수 있는 길을 아예 안 만든다. 달 카드도 `card=4` 로 간다.
 */
fun photoCardQuery(card: PhotoCardKey, dogName: String, dogId: String?, titleName: String? = null): String = buildString {
    append("card=").append(URLEncoder.encode(card.raw, "UTF-8"))
    append("&dog_name=").append(URLEncoder.encode(dogName, "UTF-8"))
    if (dogId != null) append("&dog_id=").append(URLEncoder.encode(dogId, "UTF-8"))
    val trimmedTitle = titleName?.trim()
    if (!trimmedTitle.isNullOrEmpty()) append("&title_name=").append(URLEncoder.encode(trimmedTitle, "UTF-8"))
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
fun photoFailureText(card: PhotoCardKey, errorCode: String?): String {
    val next = when (errorCode) {
        "no_image" -> "강아지가 잘 보이는 다른 사진으로 해 주세요"
        else -> "잠시 뒤 다시 만들어 주세요"
    }
    return "${card.label} 카드를 만들지 못했어요 · $next"
}

private fun parseInstantMillis(raw: String): Long? =
    runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()
        // 오프셋이 없으면 서버 UTC 로 본다.
        ?: runCatching { LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()

private fun JSONObject.optStringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
