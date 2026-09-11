package com.daengs.app.walk.diary

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.*
import com.daengs.app.walk.store.WalkPhotoRow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** Offline source board. Only actual records and accepted route samples; no motion classification. */
object LocalDiaryBoard {
    const val FORMAT = "walk-local-diary-board-v1"

    fun build(walk: WalkSummary, fixes: List<RecordedFix>, entries: List<WalkEntry>, photos: List<WalkPhotoRow>): String {
        val route = walk.toSessionRoute()
        val fixesByTime = fixes.filter { !it.isMock }.groupBy { it.atMillis }
        fun exact(point: WalkRoutePoint?) = point?.let { p ->
            fixesByTime[p.capturedAtMillis].orEmpty().singleOrNull {
                GeoPoint(it.lat, it.lng).distanceTo(p.point) < 0.01 }
        }
        fun item(id: String, at: Long, title: String, body: String, kind: String,
            point: GeoPoint? = null, fix: RecordedFix? = null): JSONObject =
            JSONObject().put("id", id).put("at", at).put("title", title).put("body", body).put("kind", kind)
                .put("point", point?.let { JSONArray(listOf(it.latitude, it.longitude)) })
                .put("fix", fix?.let { JSONArray(listOf(it.clientSeq, it.chainIndex, it.atMillis)) })

        fun boundary(start: Boolean): JSONObject {
            val at = if (start) walk.startedAtMillis else requireNotNull(walk.endedAtMillis)
            val p = (if (start) route.start else route.end)?.takeIf { it.capturedAtMillis == at }
            val fix = exact(p)
            return item(if (start) "start" else "end", at,
                if (start) "산책의 시작" else "산책을 마치며",
                if (start) "산책을 시작했다." else "산책을 마쳤다.", "session_boundary",
                p?.point.takeIf { fix != null }, fix)
        }
        val records = entries.sortedWith(compareBy<WalkEntry> { it.recordedAtMillis }.thenBy { it.id }).map {
            val scene = entryScene(it)
            item(scene.id, scene.atMillis, scene.title, scene.body, scene.diary!!.recordKind, scene.diary.point)
                .put("entry", it.id).put("source", it.toJson())
        } + photos.map {
            item("photo:${it.id}", it.capturedAtMillis, "산책 사진", "사진을 남겼다.", "photo",
                GeoPoint(it.lat, it.lng)).put("photo", it.id)
        }
        val selected = mutableListOf<WalkRoutePoint>()
        // Each continuous segment has its own distance axis. A gap is never a walking interval.
        repeat((5 - records.size).coerceAtLeast(0)) {
            val choice = route.segments.flatMap { segment ->
                val points = segment.points
                if (points.size < 3) return@flatMap emptyList()
                val covered = listOf(points.first(), points.last()) +
                    selected.filter { it.segmentIndex == segment.index } +
                    records.mapNotNull { record ->
                        val at = record.getLong("at")
                        if (at !in points.first().capturedAtMillis..points.last().capturedAtMillis) null
                        else points.minByOrNull { abs(it.capturedAtMillis - at) }
                    }
                points.filter { exact(it) != null }.map { p ->
                    p to covered.minOf { abs(it.cumulativeDistanceMeters - p.cumulativeDistanceMeters) }
                }
            }.maxByOrNull { it.second }
            if (choice != null && choice.second >= 80.0) selected += choice.first
        }
        val middle = (records + selected.map {
            val fix = requireNotNull(exact(it))
            item("checkpoint:${fix.chainIndex}:${fix.clientSeq}", it.capturedAtMillis,
                "걸어온 길", "이 길을 따라 걸었다.", "route_checkpoint", it.point, fix)
        }).sortedWith(compareBy<JSONObject> { it.getLong("at") }.thenBy { it.getString("id") })
        return JSONObject().put("format", FORMAT).put("session_id", walk.sessionId)
            .put("scenes", JSONArray(listOf(boundary(true)) + middle + boundary(false))).toString()
    }

    fun entryScene(entry: WalkEntry): StoryboardScene {
        val body = when (entry.type.behaviorCode) {
            "sniffing" -> "냄새를 맡았다."
            "excretion" -> "배변을 했다."
            "barking" -> "짖었다."
            else -> entry.note.orEmpty()
        }
        val kind = if (entry.type == WalkMomentType.NOTE) "note" else "behavior"
        return StoryboardScene("entry:${entry.id}", entry.recordedAtMillis, entry.type.label, body, "",
            storyboardHash(canonicalJson(entry.toJson())), entryReference =
                StoryboardEntryReference(entry.id, null, entry.petId, kind == "note"),
            diary = DiarySceneContent(entry.note ?: entry.type.behaviorCode, kind,
                point = if (entry.pin != null) entry.pin.point else entry.point, locationLabel = ""),
            bodyScope = SceneBodyScope.SCENE)
    }

    fun parse(text: String): GeoStoryboardBundle {
        val root = JSONObject(text)
        require(root.getString("format") == FORMAT)
        val items = root.getJSONArray("scenes")
        val scenes = (0 until items.length()).map { index ->
            val item = items.getJSONObject(index)
            val point = item.optJSONArray("point")?.let { GeoPoint(it.getDouble(0), it.getDouble(1)) }
            val entry = item.optString("entry").takeIf { it.isNotBlank() }
            val source = item.optJSONObject("source")
            val kind = item.getString("kind")
            StoryboardScene(item.getString("id"), item.getLong("at"), item.getString("title"),
                item.getString("body"), "", storyboardHash(canonicalJson(item)), sourcePayload = item.toString(),
                entryReference = entry?.let { StoryboardEntryReference(it, null, null, kind == "note") },
                observation = item.optJSONArray("fix")?.let {
                    StoryboardObservation(it.getInt(0), it.getInt(1), it.getLong(2), requireNotNull(point))
                },
                diary = DiarySceneContent(source?.optString(if (kind == "note") "note" else "behavior_code").orEmpty(),
                    kind, item.optString("photo").takeIf(String::isNotBlank), point, "", order = index + 1),
                bodyScope = SceneBodyScope.SCENE)
        }
        require(scenes.first().id == "start" && scenes.last().id == "end")
        require(scenes.map { it.id }.distinct().size == scenes.size)
        return GeoStoryboardBundle(root.getString("session_id"), storyboardHash(text), false, scenes, text)
    }

    /** Explicit user changes update their own card; sync acknowledgements never replace prose. */
    fun withUserChanges(board: GeoStoryboardBundle, base: String, entries: List<WalkEntry>,
        photoIds: Set<String>): GeoStoryboardBundle {
        val original = JSONObject(base).getJSONArray("scenes")
        val sources = (0 until original.length()).map { original.getJSONObject(it) }
            .filter { it.has("entry") }.associate { it.getString("entry") to canonicalJson(it.getJSONObject("source")) }
        val live = entries.associateBy { it.id }
        val scenes = board.scenes.mapNotNull { scene ->
            val id = scene.entryReference?.entryId
            when {
                id != null && id !in live -> null
                id != null && sources[id] != canonicalJson(live.getValue(id).toJson()) -> entryScene(live.getValue(id))
                id != null -> scene.copy(diary = scene.diary?.copy(point = live.getValue(id).let {
                    if (it.pin != null) it.pin.point else it.point
                }))
                scene.diary?.photoId != null && scene.diary.photoId !in photoIds -> null
                else -> scene
            }
        } + entries.filter { e -> board.scenes.none { it.entryReference?.entryId == e.id } }.map(::entryScene)
        return board.copy(scenes = scenes)
    }
}
