package com.daengs.app.walk.support

import com.daengs.app.walk.diary.GeoStoryboardBundle
import org.json.JSONArray
import org.json.JSONObject

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
