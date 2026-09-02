package com.daengs.app.walk.sync

import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.RecordedSession
import com.daengs.app.walk.RecordedWeather
import org.json.JSONArray
import org.json.JSONObject

/**
 * 서버에 있는 산책 한 건. **좌표는 없다** — 목록에 오는 모양이다.
 *
 * `clientSessionId` 가 기기의 세션 id 와 **같은 값**이라, 되찾을 때 이것만 보고
 * "이미 내 폰에 있나"를 판단할 수 있다. 서버의 `id` 는 좌표를 받아올 때만 쓴다.
 */
data class RemoteWalk(
    val id: String,
    val clientSessionId: String,
    val dogIds: List<String>,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val weather: RecordedWeather?,
) {
    /** 로컬 DB 에 넣을 모양으로. **되찾은 것은 이미 서버에 있으므로 올린 것으로 표시한다.** */
    fun toSession(syncedAtMillis: Long): RecordedSession = RecordedSession(
        id = clientSessionId,
        dogIds = dogIds,
        startedAtMillis = startedAtMillis,
        endedAtMillis = endedAtMillis,
        weather = weather,
        syncedAtMillis = syncedAtMillis,
    )

    companion object {
        fun parse(json: JSONObject): RemoteWalk = RemoteWalk(
            id = json.getString("id"),
            clientSessionId = json.getString("client_session_id"),
            dogIds = json.optStringList("pet_ids"),
            startedAtMillis = json.getString("started_at").isoToMillis(),
            endedAtMillis = json.getString("ended_at").isoToMillis(),
            // 셋 중 코드가 없으면 날씨를 못 받은 산책이다. 억지로 만들지 않는다.
            weather = if (json.isNull("weather_code")) {
                null
            } else {
                RecordedWeather(
                    weatherCode = json.getInt("weather_code"),
                    isDay = if (json.isNull("is_day")) true else json.getBoolean("is_day"),
                    // ⚠️ **문자열로 온다.** 저쪽이 `Decimal` 이라 pydantic 이 정밀도를
                    //    지키려고 따옴표를 씌운다 (`Pet.weightKg` 와 같은 함정).
                    temperatureC = json.optStringOrNull("temperature_c")?.toFloatOrNull(),
                )
            },
        )

        /**
         * `pet_ids` 배열. **없으면 빈 목록이다.**
         *
         * 서버가 이 필드를 내려 주기 전에 올라간 산책이 있을 수 있다 — 그건 "아무도
         * 안 나갔다" 가 아니라 "모른다" 지만, 화면에서는 둘 다 아이를 안 그린다.
         */
        internal fun JSONObject.optStringList(key: String): List<String> {
            val array = optJSONArray(key) ?: return emptyList()
            return (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        }

        /** `optString` 은 JSON null 에도 빈 문자열을 준다. 0 과 "없음"을 구분해야 한다. */
        internal fun JSONObject.optStringOrNull(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
    }
}

/** 한 건 + 좌표. 되찾을 때 이걸로 로컬을 채운다. */
data class RemoteWalkDetail(val walk: RemoteWalk, val fixes: List<RecordedFix>) {
    companion object {
        fun parse(json: JSONObject): RemoteWalkDetail {
            val array = json.getJSONArray("points")
            return RemoteWalkDetail(
                walk = RemoteWalk.parse(json),
                fixes = (0 until array.length()).map { index ->
                    val point = array.getJSONObject(index)
                    RecordedFix(
                        clientSeq = point.getInt("client_seq"),
                        chainIndex = point.getInt("chain_index"),
                        atMillis = point.getString("at").isoToMillis(),
                        // 위경도도 문자열이다 (Decimal).
                        lat = point.getString("lat").toDouble(),
                        lng = point.getString("lng").toDouble(),
                        accuracyM = if (point.isNull("accuracy_m")) {
                            null
                        } else {
                            point.getDouble("accuracy_m").toFloat()
                        },
                        isMock = point.optBoolean("is_mock"),
                    )
                },
            )
        }
    }
}
