package com.daengs.app.walk.diary

import com.daengs.app.walk.support.titledDiaryFixture
import android.app.Application
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WalkDiaryTitleTest {
    @Test fun `v3 title survives serialization and reviewed snapshot tracks title changes`() {
        val bundle = GeoStoryboardBundle.parse(titledDiaryFixture().toString())
        assertEquals("함께 남긴 산책 기록", bundle.title)
        assertEquals(bundle.title, GeoStoryboardBundle.parse(bundle.rawJson).title)
        val snapshot = storyboardSnapshot("s", bundle.scenes, bundle.title)
        assertEquals(bundle.title, JSONObject(snapshot).getString("diary_title"))
        assertNotEquals(snapshot, storyboardSnapshot("s", bundle.scenes, "다른 제목"))
        assertFalse(JSONObject(storyboardSnapshot("s", bundle.scenes)).has("diary_title"))
    }

    @Test fun `absent title remains valid but ungrounded multiline oversized titles are rejected`() {
        val missing = titledDiaryFixture().put("title", JSONObject.NULL).put("title_fact_ids", JSONArray())
        assertNull(GeoStoryboardBundle.parse(missing.toString()).title)
        listOf(
            titledDiaryFixture().put("title_fact_ids", JSONArray(listOf("invented"))),
            titledDiaryFixture().put("title", "산책\n제목"),
            titledDiaryFixture().put("title", "가".repeat(41)),
            titledDiaryFixture().put("title", "  "),
            titledDiaryFixture().put("title", JSONObject.NULL),
        ).forEach { assertTrue(runCatching { GeoStoryboardBundle.parse(it.toString()) }.isFailure) }
        val old = titledDiaryFixture().put("format", GeoStoryboardBundle.FORMAT_V2)
        old.remove("title"); old.remove("title_fact_ids")
        assertNull(GeoStoryboardBundle.parse(old.toString()).title)
    }
}
