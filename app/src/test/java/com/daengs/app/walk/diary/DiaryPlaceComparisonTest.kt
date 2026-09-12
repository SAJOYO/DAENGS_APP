package com.daengs.app.walk.diary

import com.daengs.app.location.GeoPoint
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DiaryPlaceComparisonTest {
    private val scenes = listOf(
        DiaryScene("walk/note", "walk", 1000, "기록", "물을 마셨다.\n  메모 원문  ", GeoPoint(37.5, 127.0), "", entryId = "note"),
        DiaryScene("walk/end", "walk", 2000, "종료", "", null, ""),
    )
    private fun snapshot(owner: String = "owner", list: List<DiaryScene> = scenes) = DiaryComparisonSnapshot.create(owner, "walk", list)
    private fun result(snapshot: DiaryComparisonSnapshot): JSONObject = JSONObject()
        .put("format", "diary-scene-comparison-result-v2").put("snapshot_sha256", snapshot.digest)
        .put("model_status", "accepted").put("model", "test-writer").put("retrieved_at", "2026-09-12T00:00:00Z")
        .put("scenes", JSONArray(snapshot.scenes.mapIndexed { index, scene -> JSONObject()
            .put("id", scene.id).put("coverage", "공간 1 · 환경 0 · 동선 0")
            .put("background", if (index == 0) "공원 등록 위치에서 약 80m 떨어진 지점이다." else "")
            .put("evidence_ids", JSONArray(if (index == 0) listOf("park") else emptyList<String>()))
            .put("evidence", JSONArray(if (index == 0) listOf(JSONObject().put("id", "park").put("description", "공원 · 등록 위치와 80m")) else emptyList<JSONObject>()))
        }))

    @Test fun `toggle changes only display body and preserves original whitespace blank text anchors and record identities`() {
        val original = snapshot()
        val comparison = DiaryPlaceComparison.parse(result(original).toString(), original)
        val shown = comparison.project(original, true)
        assertEquals(scenes[0].copy(body = "공원 등록 위치에서 약 80m 떨어진 지점이다.\n\n${scenes[0].body}"), shown[0])
        assertEquals(scenes[1], shown[1])
        assertEquals(scenes, comparison.project(original, false))
        assertEquals(scenes, original.scenes)
    }

    @Test fun `changed account edit hidden scene position or order invalidates old result`() {
        val original = snapshot()
        val raw = result(original).toString()
        val changed = listOf(snapshot("other"), snapshot(list = scenes.reversed()),
            snapshot(list = scenes.dropLast(1)), snapshot(list = scenes.map { it.copy(body = "") }),
            snapshot(list = scenes.map { it.copy(point = GeoPoint(37.6, 127.1)) }))
        changed.forEach { assertThrows(IllegalArgumentException::class.java) { DiaryPlaceComparison.parse(raw, it) } }
    }

    @Test fun `changed underlying scene context invalidates comparison even with identical display text`() {
        fun withSource(kind: String) = scenes.map { it.copy(source = StoryboardScene(
            "source", it.atMillis, it.title, it.body, "", "fingerprint",
            sourcePayload = JSONObject().put("kind", kind).toString(),
        )) }
        val before = snapshot(list = withSource("route_checkpoint"))
        val after = snapshot(list = withSource("movement_observation"))
        assertEquals("route_checkpoint", JSONObject(before.json).getJSONArray("scenes")
            .getJSONObject(0).getJSONObject("source_scene").getString("kind"))
        assertThrows(IllegalArgumentException::class.java) {
            DiaryPlaceComparison.parse(result(before).toString(), after)
        }
    }

    @Test fun `another scene order invented citation or duplicate evidence is rejected`() {
        val original = snapshot()
        val reordered = result(original).apply { getJSONArray("scenes").getJSONObject(0).put("id", "walk/end") }
        val invented = result(original).apply { getJSONArray("scenes").getJSONObject(0).put("evidence_ids", JSONArray(listOf("unknown"))) }
        val duplicated = result(original).apply { getJSONArray("scenes").getJSONObject(0).getJSONArray("evidence").let { it.put(it.getJSONObject(0)) } }
        listOf(reordered, invented, duplicated).forEach {
            assertThrows(IllegalArgumentException::class.java) { DiaryPlaceComparison.parse(it.toString(), original) }
        }
    }

    @Test fun `failed generation or prose without evidence never becomes a comparison`() {
        val original = snapshot()
        val failed = result(original).put("model_status", "unavailable")
        val ungrounded = result(original).apply { getJSONArray("scenes").getJSONObject(1).put("background", "공원에서 쉬었다.") }
        listOf(failed, ungrounded).forEach {
            assertThrows(IllegalArgumentException::class.java) { DiaryPlaceComparison.parse(it.toString(), original) }
        }
    }
}
