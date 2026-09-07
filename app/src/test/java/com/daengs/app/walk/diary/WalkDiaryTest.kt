package com.daengs.app.walk.diary

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class WalkDiaryTest {
    private fun walk(id: String = "s", at: String = "2026-08-31T15:00:00Z", code: Int? = 61) =
        WalkSummary(id, listOf("dog-a", "dog-b"), Instant.parse(at).toEpochMilli(),
            Instant.parse(at).toEpochMilli() + 600_000, code?.let { RecordedWeather(it, true, null) },
            100.0, 600_000, emptyList(), null)
    private val point = GeoPoint(37.5, 127.0)
    private fun entry(id: String = "a", session: String = "s") = WalkEntry(id, session,
        WalkMomentType.SNIFFING, 1000, point, 990, 5f)
    private val noAnalysis = StoryboardAnalysisView(null, false, "직접 남긴 기록")

    @Test fun `계절은 한국 시간 시작일이며 다견과 같은 축 OR 조건을 함께 적용한다`() {
        val target = walk()
        assertEquals(DiarySeason.AUTUMN, target.diarySeason())
        assertEquals(DiarySeason.SUMMER, walk(at = "2026-08-31T14:59:59Z").diarySeason())
        assertTrue(target.matchesDiary("dog-b", setOf(DiarySeason.SUMMER, DiarySeason.AUTUMN), setOf(DiaryWeather.RAIN)))
        assertFalse(target.matchesDiary("dog-c", emptySet(), emptySet()))
        assertFalse(target.matchesDiary(null, setOf(DiarySeason.SUMMER), emptySet()))
        assertFalse(target.matchesDiary(null, emptySet(), setOf(DiaryWeather.SNOW)))
        assertTrue(target.matchesDiary(null, emptySet(), emptySet()))
    }

    @Test fun `날씨 누락과 지원하지 않는 코드가 강수 없음에 섞이지 않는다`() {
        for (code in listOf(null, -1, 999)) {
            assertEquals(DiaryWeather.UNKNOWN, walk(code = code).diaryWeather())
            assertFalse(walk(code = code).matchesDiary(null, emptySet(), setOf(DiaryWeather.DRY)))
        }
        assertEquals(DiaryWeather.SNOW, walk(code = 85).diaryWeather())
        assertEquals(DiaryWeather.DRY, walk(code = 45).diaryWeather())
        assertEquals(DiaryWeather.RAIN, walk(code = 67).diaryWeather())
    }

    @Test fun `숨김과 사용자 문구를 보존하고 다른 산책의 좌표를 가져오지 않는다`() {
        val recorded = entry()
        val source = storyboardScenes(walk(), listOf(recorded), StoryboardDraft()).first { it.id == "entry:a" }
        val edited = StoryboardDraft().edit(source, title = "내가 쓴 제목", body = "내 문구")
        val result = diaryWalk(walk(), listOf(recorded, entry("other", "another")), emptyList(), edited, noAnalysis)
        val scene = result.scenes.first { it.id == "s/entry:a" }
        assertEquals("내가 쓴 제목", scene.title); assertEquals(point, scene.point)
        assertFalse(result.scenes.any { it.id.contains("other") })
        assertTrue(result.scenes.filter { it.id in listOf("s/start", "s/end") }.all { it.point == null })
        val hidden = diaryWalk(walk(), listOf(recorded), emptyList(), edited.edit(source, hidden = true), noAnalysis)
        assertFalse(hidden.scenes.any { it.id == "s/entry:a" })
    }

    @Test fun `자동 장면은 거리 정보가 있어도 좌표를 추측하지 않는다`() {
        val bundle = GeoStoryboardBundle.parse(javaClass.getResource("/storyboard/pinless.json")!!.readText())
        val result = diaryWalk(walk(), emptyList(), emptyList(), StoryboardDraft(),
            StoryboardAnalysisView(bundle, true, "분석 장면"))
        assertTrue(result.scenes.isNotEmpty())
        assertTrue(result.scenes.all { it.point == null })
    }

    @Test fun `같은 위치의 여러 세션 장면을 잃지 않고 시간순으로 묶는다`() {
        val a = DiaryScene("a/start", "a", 20, "a", "", point, "")
        val b = a.copy(id = "b/start", sessionId = "b", atMillis = 10)
        val noPoint = a.copy(id = "n", point = null)
        assertEquals(listOf(listOf(b, a)), diaryLocationGroups(listOf(a, noPoint, b)))
    }

    @Test fun `사진은 원본 파일과 위치를 유지하고 다른 세션 사진은 제외한다`() {
        val photo = WalkPhoto("photo", "s", 2000, point, java.io.File("photo.jpg"))
        val result = diaryWalk(walk(), emptyList(), listOf(photo, photo.copy(sessionId = "other")),
            StoryboardDraft(), noAnalysis)
        val item = result.scenes.single { it.photo != null }
        assertEquals(photo, item.photo); assertEquals(point, item.point)
    }
}
