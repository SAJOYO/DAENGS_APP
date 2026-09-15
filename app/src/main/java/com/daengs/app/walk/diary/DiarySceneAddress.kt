package com.daengs.app.walk.diary

import org.json.JSONArray
import org.json.JSONObject

/** Provider administrative components, retained independently from generated prose. */
data class DiarySceneAddress(
    val sido: String?,
    val sigungu: String?,
    val dong: String?,
    val addressType: String?,
) {
    fun cardLabel(): String {
        val city = when (sido) {
            "서울특별시" -> "서울"
            "부산광역시" -> "부산"
            "대구광역시" -> "대구"
            "인천광역시" -> "인천"
            "광주광역시" -> "광주"
            "대전광역시" -> "대전"
            "울산광역시" -> "울산"
            else -> null
        }
        return listOfNotNull(city ?: sido.takeIf { sigungu == null }, sigungu, dong)
            .distinct().joinToString(" ")
    }
}

internal fun addressText(value: Any?): String? = (value as? String)?.trim()
    ?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() && !it.equals("null", true) }

/** Shared by both saved diary wire formats. Never combine components from different records. */
internal fun sceneAddress(places: JSONArray): DiarySceneAddress? {
    val addresses = (0 until places.length()).mapNotNull { index ->
        val piece = places.getJSONObject(index)
        require(piece.getString("kind") == "place_reference")
        val facts = piece.getJSONObject("facts")
        fun text(key: String) = addressText(facts.opt(key))
        DiarySceneAddress(text("sido"), text("sigungu"), text("dong"), text("address_type"))
            .takeIf { it.cardLabel().isNotBlank() }
    }.distinct()
    // Ambiguous snapshots must not produce a made-up combined address.
    return addresses.singleOrNull()
}
