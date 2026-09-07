package com.daengs.app.walk.diary

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.WalkPhoto
import com.daengs.app.walk.WalkSummary
import java.time.Instant
import java.time.ZoneId

enum class DiarySeason(val label: String) {
    SPRING("봄"), SUMMER("여름"), AUTUMN("가을"), WINTER("겨울");
}

enum class DiaryWeather(val label: String) {
    DRY("강수 없음"), RAIN("비"), SNOW("눈"), MIXED("비·눈"), UNKNOWN("정보 없음");
}

fun WalkSummary.diarySeason(): DiarySeason = when (
    Instant.ofEpochMilli(startedAtMillis).atZone(ZoneId.of("Asia/Seoul")).monthValue
) {
    in 3..5 -> DiarySeason.SPRING
    in 6..8 -> DiarySeason.SUMMER
    in 9..11 -> DiarySeason.AUTUMN
    else -> DiarySeason.WINTER
}

/** Only recorded WMO observations. Missing/unsupported codes never mean dry. */
fun WalkSummary.diaryWeather(): DiaryWeather = when (weather?.weatherCode) {
    0, 1, 2, 3, 45, 48 -> DiaryWeather.DRY
    51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99 -> DiaryWeather.RAIN
    71, 73, 75, 77, 85, 86 -> DiaryWeather.SNOW
    else -> DiaryWeather.UNKNOWN
}

fun WalkSummary.matchesDiary(dogId: String?, seasons: Set<DiarySeason>, weather: Set<DiaryWeather>): Boolean =
    (dogId == null || dogId in dogIds) && (seasons.isEmpty() || diarySeason() in seasons) &&
        (weather.isEmpty() || diaryWeather() in weather)

data class DiaryScene(
    val id: String,
    val sessionId: String,
    val atMillis: Long,
    val title: String,
    val body: String,
    val point: GeoPoint?,
    val evidence: String,
    val needsReview: Boolean = false,
    val photo: WalkPhoto? = null,
    val entryId: String? = null,
)

data class DiaryWalk(val summary: WalkSummary, val scenes: List<DiaryScene>, val notice: String,
    val title: String? = null)

/** Read-only projection: never mutates saved text, hiding choices, or the reviewed snapshot. */
fun diaryWalk(
    walk: WalkSummary,
    entries: List<WalkEntry>,
    photos: List<WalkPhoto>,
    draft: StoryboardDraft,
    analysis: StoryboardAnalysisView,
): DiaryWalk {
    val localEntries = entries.filter { it.sessionId == walk.sessionId }
    val sources = analysis.bundle?.scenes?.map { scene ->
        val id = scene.id.removePrefix("geo:")
        if (id == "start" || id == "end" || id.startsWith("entry:")) scene.copy(id = id) else scene
    }
    val scenes = (if (sources == null) storyboardScenes(walk, localEntries, draft)
        else applyStoryboardEdits(sources, draft)).filter { it.available && !it.hidden }.map { scene ->
        val entryId = scene.entryReference?.entryId ?: scene.id.takeIf { it.startsWith("entry:") }
            ?.removePrefix("entry:")
        // An automatic scene's route distance is not an App GPS coordinate. Do not guess one.
        val point = localEntries.firstOrNull { it.id == entryId }?.point
        DiaryScene("${walk.sessionId}/${scene.id}", walk.sessionId, scene.atMillis,
            scene.title, scene.body, point, scene.evidence, scene.needsReview, entryId = entryId)
    } + photos.filter { it.sessionId == walk.sessionId }.map { photo ->
        DiaryScene("${walk.sessionId}/photo:${photo.id}", walk.sessionId, photo.capturedAtMillis,
            "산책 사진", "이날 남긴 사진", photo.point, "촬영할 때 저장한 위치", photo = photo)
    }
    return DiaryWalk(walk, scenes.sortedWith(compareBy<DiaryScene> { it.atMillis }.thenBy { it.id }),
        analysis.notice, analysis.bundle?.takeIf { it.sessionId == walk.sessionId }?.title)
}

/** Same coordinate records share a marker; membership stays distinct and chronological. */
fun diaryLocationGroups(scenes: List<DiaryScene>): List<List<DiaryScene>> = scenes
    .filter { it.point != null }.groupBy { it.point }.values
    .map { it.sortedWith(compareBy<DiaryScene> { scene -> scene.atMillis }.thenBy { scene -> scene.id }) }
