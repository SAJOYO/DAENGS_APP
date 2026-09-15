package com.daengs.app.walk.shared

import com.daengs.app.auth.AuthApi
import com.daengs.app.care.CareActor
import org.json.JSONArray
import org.json.JSONObject

/**
 * 함께 돌보는 강아지의 산책 한 건 — 서버 `GET /app/pets/{pet_id}/walks` 의 항목
 * (SAJOYO/DAENGS_dev#539 `schemas/walk_group.py`).
 *
 * **읽기 전용이다.** 이 모델로 기기 저장소(Room)의 내 산책을 만들거나 고치지 않는다 — 산책의
 * 소유·수정·삭제는 올린 사람의 `/app/walks` 에만 있다.
 *
 * **게임·점령 결과는 없다.** 서버도 싣지 않는다 — 이번 결정으로 후순위 보류다.
 */
data class SharedWalk(
    val id: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    /** `ended_at - started_at` 초. 늘 있다. */
    val durationS: Long,
    /** 봉인된 최신 계산의 이동 거리(m). 아직 계산 전이면 null. */
    val distanceM: Int?,
    val movingS: Int?,
    /** 다녀온 사람. 지금 그 그룹의 구성원이 아니면 닉네임이 없고 "이전 보호자" 로 그린다. */
    val actor: CareActor,
    /** 내가 올린 산책인가. 내 것은 기기 기록 목록에 이미 있으니 함께 보기에서 뺀다. */
    val isMine: Boolean,
    /** 내가 볼 수 있는 강아지만. */
    val petIds: List<String>,
) {
    companion object {
        fun parse(json: JSONObject): SharedWalk = SharedWalk(
            id = json.getString("id"),
            startedAtMs = json.getString("started_at").toEpochMillis(),
            endedAtMs = json.getString("ended_at").toEpochMillis(),
            durationS = json.getLong("duration_s"),
            distanceM = json.optIntOrNull("distance_m"),
            movingS = json.optIntOrNull("moving_s"),
            actor = CareActor.parse(json.getJSONObject("actor")),
            isMine = json.getBoolean("is_mine"),
            petIds = json.optJSONArray("pet_ids").strings(),
        )
    }
}

/** 경로의 한 점. 서버는 소수(Decimal)라 JSON 에서 문자열로도 숫자로도 올 수 있다. */
data class SharedWalkPoint(val atMs: Long, val lat: Double, val lng: Double)

/** 상세 — 목록 항목에 경로(시각·위도·경도)만 더한 것. */
data class SharedWalkDetail(val walk: SharedWalk, val points: List<SharedWalkPoint>) {
    companion object {
        fun parse(json: JSONObject): SharedWalkDetail {
            val raw = json.optJSONArray("points")
            val points = List(raw?.length() ?: 0) { i ->
                val point = raw!!.getJSONObject(i)
                SharedWalkPoint(point.getString("at").toEpochMillis(), point.decimal("lat"), point.decimal("lng"))
            }
            return SharedWalkDetail(SharedWalk.parse(json), points)
        }
    }
}

/** 목록 한 페이지. [nextCursor] 가 있으면 그대로 다음 요청의 `cursor` 로 넘긴다. */
data class SharedWalkPage(val petId: String, val walks: List<SharedWalk>, val nextCursor: String?) {
    companion object {
        fun parse(json: JSONObject): SharedWalkPage {
            val raw = json.optJSONArray("walks")
            return SharedWalkPage(
                petId = json.getString("pet_id"),
                walks = List(raw?.length() ?: 0) { SharedWalk.parse(raw!!.getJSONObject(it)) },
                nextCursor = if (json.isNull("next_cursor")) null else json.optString("next_cursor").ifBlank { null },
            )
        }
    }
}

private fun String.toEpochMillis(): Long = with(AuthApi) { this@toEpochMillis.toEpochMs() }

private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key)) null else getInt(key)

private fun JSONObject.decimal(key: String): Double = when (val value = get(key)) {
    is Number -> value.toDouble()
    else -> value.toString().toDouble()
}

private fun JSONArray?.strings(): List<String> = List(this?.length() ?: 0) { this!!.getString(it) }
