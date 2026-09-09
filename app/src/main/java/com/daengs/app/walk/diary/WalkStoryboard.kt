package com.daengs.app.walk.diary

import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.WalkSummary
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class StoryboardScene(
    val id: String, val atMillis: Long, val title: String, val body: String,
    val evidence: String, val fingerprint: String, val available: Boolean = true,
    val hidden: Boolean = false, val needsReview: Boolean = false,
    val sourcePayload: String? = null,
    val entryReference: StoryboardEntryReference? = null,
    val observation: StoryboardObservation? = null,
    val diary: DiarySceneContent? = null,
)

data class StoryboardEntryReference(val entryId: String, val revision: Long?, val petId: String?, val isNote: Boolean = false)

data class SceneEdit(val id: String, val title: String, val body: String, val hidden: Boolean,
                     val sourceFingerprint: String, val atMillis: Long) {
    fun toJson() = JSONObject().put("id", id).put("title", title).put("body", body)
        .put("hidden", hidden).put("source", sourceFingerprint).put("at", atMillis)
}

data class StoryboardDraft(val edits: List<SceneEdit> = emptyList(), val reviewed: String? = null) {
    fun edit(scene: StoryboardScene, title: String = scene.title, body: String = scene.body,
             hidden: Boolean = scene.hidden, acknowledge: Boolean = false) = copy(
        edits = edits.filterNot { it.id == scene.id } + SceneEdit(scene.id, title, body, hidden,
            if (acknowledge) scene.fingerprint else
                edits.firstOrNull { it.id == scene.id }?.sourceFingerprint ?: scene.fingerprint,
            scene.atMillis))

    fun toJson(): String = JSONObject().put("version", 1)
        .put("edits", JSONArray().apply { edits.forEach { put(it.toJson()) } })
        .put("reviewed", reviewed ?: JSONObject.NULL).toString()

    companion object {
        fun parse(payload: String?): StoryboardDraft {
            if (payload == null) return StoryboardDraft()
            val obj = JSONObject(payload)
            require(obj.getInt("version") == 1)
            val array = obj.getJSONArray("edits")
            return StoryboardDraft((0 until array.length()).map { i ->
                val e = array.getJSONObject(i)
                SceneEdit(e.getString("id"), e.getString("title"), e.getString("body"),
                    e.getBoolean("hidden"), e.getString("source"), e.getLong("at"))
            }, if (obj.isNull("reviewed")) null else obj.getString("reviewed"))
        }
    }
}

fun storyboardScenes(walk: WalkSummary, entries: List<WalkEntry>, draft: StoryboardDraft): List<StoryboardScene> {
    val sources = mutableListOf(StoryboardScene("start", walk.startedAtMillis, "산책 시작", "",
        "산책 시작 시각", walk.startedAtMillis.toString()))
    entries.filter { it.sessionId == walk.sessionId }.forEach { e ->
        sources += StoryboardScene("entry:${e.id}", e.recordedAtMillis, e.type.label,
            e.note.orEmpty(), buildString {
                append("직접 남긴 기록 · ${e.type.label}")
                e.note?.let { append("\n메모: $it") }
                e.petId?.let { append("\n반려견 ID: $it") }
                append(e.pin?.let { "\n${it.label}" } ?: if (e.point == null)
                    if (e.type == com.daengs.app.walk.WalkMomentType.NOTE) "\n위치 없는 메모" else "\n위치 없는 행동"
                    else "\n위치: ${e.point.latitude}, ${e.point.longitude}")
            }, storyboardHash(e.toJson().toString() + (e.pin?.payload ?: "")))
    }
    // A pinless walk still has an honest session summary. No fabricated environment or motion scenes.
    if (walk.endedAtMillis != null) sources += StoryboardScene("end", walk.endedAtMillis,
        "산책 마무리", "이동거리 ${walk.distanceMeters.toInt()}m · 활동 시간 ${walk.activeDurationMillis / 60000}분",
        "저장된 산책 요약에서 계산", storyboardHash("${walk.endedAtMillis}:${walk.distanceMeters}:${walk.activeDurationMillis}"))
    return applyStoryboardEdits(sources, draft)
}

fun applyStoryboardEdits(sources: List<StoryboardScene>, draft: StoryboardDraft): List<StoryboardScene> {
    val byId = sources.associateBy { it.id }
    val scenes = sources.map { source ->
        draft.edits.firstOrNull { it.id == source.id }?.let { edit ->
            source.copy(title = edit.title, body = edit.body, hidden = edit.hidden,
                needsReview = edit.sourceFingerprint != source.fingerprint)
        } ?: source
    }.toMutableList()
    draft.edits.filter { it.id !in byId }.forEach { edit ->
        scenes += StoryboardScene(edit.id, edit.atMillis, edit.title, edit.body,
            "원본 기록이 삭제되었습니다. 작성한 문구는 보존되며 검토본에서 제외됩니다.",
            "deleted", available = false, hidden = edit.hidden, needsReview = true)
    }
    return scenes.sortedWith(compareBy<StoryboardScene> { it.atMillis }.thenBy { it.diary?.order ?: Int.MAX_VALUE }.thenBy { it.id })
}

fun storyboardSnapshot(sessionId: String, scenes: List<StoryboardScene>, title: String? = null): String =
    JSONObject().put("version", 1).put("session_id", sessionId).put("scenes", JSONArray().apply {
        scenes.filter { it.available && !it.hidden }.forEach { scene ->
            put(JSONObject().put("id", scene.id).put("at", scene.atMillis).put("title", scene.title)
                .put("body", scene.body).put("evidence", scene.evidence).put("source", scene.fingerprint)
                .put("source_payload", scene.sourcePayload?.let { JSONObject(it) } ?: JSONObject.NULL))
        }
    }).apply { title?.let { put("diary_title", it) } }.toString()

internal fun storyboardHash(text: String): String = MessageDigest.getInstance("SHA-256")
    .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
