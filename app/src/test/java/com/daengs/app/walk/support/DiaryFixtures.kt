package com.daengs.app.walk.support

import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.diary.GeoStoryboardBundle
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

private object DiaryResources

fun diaryFixture(): JSONObject = JSONObject(DiaryResources::class.java.getResource("/storyboard/diary-v1.json")!!.readText())

/** Byte-identical to DEV backend/evals/walk-diary/board-v1.json. */
fun diaryBoardFixture(): JSONObject = JSONObject(DiaryResources::class.java.getResource("/storyboard/diary-board-v1.json")!!.readText())

internal fun titledDiaryFixture(): JSONObject {
    val value = JSONObject(DiaryResources::class.java.getResource("/storyboard/v2-short.json")!!.readText())
        .put("format", GeoStoryboardBundle.FORMAT_V3).put("session_id", "s").put("synthetic", false)
        .put("title", "함께 남긴 산책 기록")
    val fact = value.getJSONArray("scenes").getJSONObject(0).getJSONArray("facts").getJSONObject(0).getString("id")
    return value.put("title_fact_ids", JSONArray(listOf(fact)))
}

internal fun sceneAnchorFixture(): Pair<GeoStoryboardBundle, List<RecordedFix>> {
    val data = JSONObject(DiaryResources::class.java.getResource("/storyboard/v4-observations.json")!!.readText())
    val array = data.getJSONArray("observations")
    return GeoStoryboardBundle.parse(data.getJSONObject("bundle").toString()) to (0 until array.length()).map { i ->
        val p = array.getJSONObject(i)
        RecordedFix(p.getInt("client_seq"), p.getInt("chain_index"), Instant.parse(p.getString("at")).toEpochMilli(),
            p.getDouble("lat"), p.getDouble("lng"), p.getDouble("accuracy_m").toFloat(), p.getBoolean("is_mock"))
    }
}
