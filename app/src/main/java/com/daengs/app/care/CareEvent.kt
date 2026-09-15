package com.daengs.app.care

import com.daengs.app.auth.AuthApi
import org.json.JSONArray
import org.json.JSONObject

/**
 * 케어 기록의 종류. 저쪽 `schemas/care_event.py` 의 `CareEventKind` 와 같은 셋이다.
 *
 * **`WALK` 가 없다.** 산책은 서버 `walks` 가 진실이고 이 화면은 그 수를 읽기만 한다
 * (SAJOYO/DAENGS_dev#332). 여기에 산책을 넣으면 두 표가 서로 다른 답을 낸다.
 */
enum class CareKind(val wire: String, val label: String) {
    MEAL("meal", "밥"),
    MEDICATION("medication", "약"),
    SNACK("snack", "간식"),
    ;

    companion object {
        fun parse(wire: String): CareKind =
            entries.firstOrNull { it.wire == wire }
                ?: throw IllegalArgumentException("모르는 케어 종류입니다: $wire")
    }
}

/** 서버가 돌려준 기록 한 건 (`CareEventResponse`). */
data class CareEvent(
    val id: String,
    val petId: String,
    val kind: CareKind,
    val occurredAtMs: Long,
    val note: String?,
    val clientEventId: String,
    /**
     * 기록 당시 행동한 사람. 옛 응답에는 필드 자체가 없을 수 있고, 필드가 있어도
     * **속이 비어 있을 수 있다** ([CareActor] 머리말).
     */
    val actor: CareActor? = null,
) {
    companion object {
        fun parse(json: JSONObject): CareEvent = CareEvent(
            id = json.getString("id"),
            petId = json.getString("pet_id"),
            kind = CareKind.parse(json.getString("kind")),
            occurredAtMs = json.getString("occurred_at").toEpochMillis(),
            note = json.optStringOrNull("note"),
            clientEventId = json.getString("client_event_id"),
            actor = json.optJSONObject("actor")?.let(CareActor::parse),
        )
    }
}

/**
 * 하루 요약 (`CareDaySummaryResponse`) — "밥 2 · 약 1 · 간식 3 · 산책 1" 과 그날 목록.
 *
 * [day] 는 **서버가 정한 날**이다 (서울 자정 경계). 앱이 "오늘" 을 따로 계산하지 않고
 * 이 값을 보여 준다 — 자정 근처에 두 쪽이 다른 날을 말하는 일이 없게.
 */
data class CareDaySummary(
    val petId: String,
    val day: String,
    val timezone: String,
    val meal: Int,
    val medication: Int,
    val snack: Int,
    val walk: Int,
    val events: List<CareEvent>,
    /**
     * 그날 그 아이가 나간 산책과 **누가 다녀왔는지** (`walk_rows`). 다른 보호자가 다녀온 산책도
     * 들어온다. 옛 서버는 이 칸이 없어 빈 목록이다 — 그때도 [walk] 수는 그대로 온다.
     */
    val walkRows: List<CareWalkRow> = emptyList(),
) {
    companion object {
        fun parse(json: JSONObject): CareDaySummary = CareDaySummary(
            petId = json.getString("pet_id"),
            day = json.getString("day"),
            timezone = json.optString("timezone", "Asia/Seoul"),
            meal = json.optInt("meal"),
            medication = json.optInt("medication"),
            snack = json.optInt("snack"),
            walk = json.optInt("walk"),
            events = json.optJSONArray("events").toObjectList(CareEvent::parse),
            walkRows = json.optJSONArray("walk_rows").toObjectList(CareWalkRow::parse),
        )
    }
}

/**
 * 하루 요약의 산책 한 줄 (`DayWalkOut`). **읽기만 한다** — 산책은 서버 `walks` 가 진실이고,
 * 고치거나 지우는 길은 올린 사람의 산책 기록에만 있다.
 *
 * [actor] 는 케어 기록과 같은 규칙이다 — 지금 그 아이의 구성원이 아니면 닉네임이 없고
 * "이전 보호자" 로 그린다.
 */
data class CareWalkRow(
    val walkId: String,
    val startedAtMs: Long,
    val actor: CareActor,
) {
    companion object {
        fun parse(json: JSONObject): CareWalkRow = CareWalkRow(
            walkId = json.getString("walk_id"),
            startedAtMs = json.getString("started_at").toEpochMillis(),
            actor = json.optJSONObject("actor")?.let(CareActor::parse) ?: CareActor(null, null),
        )
    }
}

// -- 아래는 배관 -------------------------------------------------------------

private fun String.toEpochMillis(): Long = with(AuthApi) { this@toEpochMillis.toEpochMs() }

private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).ifBlank { null }

private fun <T> JSONArray?.toObjectList(parse: (JSONObject) -> T): List<T> =
    List(this?.length() ?: 0) { parse(this!!.getJSONObject(it)) }
