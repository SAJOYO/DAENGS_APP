package com.daengs.app.ui.walk

import com.daengs.app.walk.routeexplorer.*
import org.json.JSONObject

/** No path arrays or display vertex indices. The current read supplies all geometry again. */
internal object WalkExplorationBookmark {
    private const val VERSION = 1
    private fun integer(o: JSONObject, name: String): Long {
        val value = o.get(name)
        require(value is Int || value is Long)
        return (value as Number).toLong()
    }
    private fun time(a: MeasurementTimeAddress) = JSONObject().put("epoch", a.sourceEpoch).put("clock", a.clockEpoch).put("nanos", a.elapsedNanos)
    private fun time(o: JSONObject) = MeasurementTimeAddress(o.getString("epoch"), o.getString("clock"), integer(o, "nanos"))
    private fun revisions(view: WalkDiaryReadView, scene: com.daengs.app.walk.diary.DiaryScene) =
        SceneBindingKey.revisions(scene, view.diary?.sourceEntries?.singleOrNull { it.id == scene.entryId })
    fun encode(owner: String, view: WalkDiaryReadView, state: WalkRouteExplorerState, reading: JSONObject = JSONObject()): String? {
        if (owner.isBlank() || view.scenesLoading || state.review !== view.route.review) return null
        val measurement = view.route.detail.measurement
        val selection = JSONObject()
        when (val s = state.selection) {
            WalkRouteSelection.Overview -> selection.put("kind", "overview")
            is WalkRouteSelection.Scene -> {
                val scene = view.diary?.scenes?.singleOrNull { it.id == s.id } ?: return null
                val focus = view.focusFor(scene)
                if (measurement != null && focus == null) return null
                val (event, revision) = revisions(view, scene)
                selection.put("kind", "scene").put("id", s.id).put("event", event).put("scene", revision)
            }
            is WalkRouteSelection.Replay -> {
                selection.put("kind", "replay")
                if (measurement != null) selection.put("at", time(view.route.review.timeline?.address(s.elapsedMillis) ?: return null))
                else selection.put("elapsed", s.elapsedMillis)
            }
            is WalkRouteSelection.Slice -> selection.put("kind", "slice").put("from", time(s.from)).put("until", time(s.until))
            is WalkRouteSelection.Section -> {
                val section = state.selectedSection ?: return null
                selection.put("kind", "section")
                if (measurement != null) selection.put("id", measurement.walkingSections.getOrNull(section.index)?.id ?: return null)
                else selection.put("from", section.startedAtMillis).put("until", section.endedAtMillis)
            }
            is WalkRouteSelection.Auxiliary -> selection.put("kind", "auxiliary").put("id", s.id)
            is WalkRouteSelection.Gap, is WalkRouteSelection.Transition, is WalkRouteSelection.Event ->
                selection.put("kind", "context").put("id", state.selectedContext?.id ?: return null)
            is WalkRouteSelection.Passage -> {
                // Unaddressable spatial queries reopen the overview, never an older saved visit.
                selection.put("kind", "overview")
                run {
                    val pass = state.selectedPass ?: return@run
                    val raw = view.route.detail.observations
                    val a = raw.singleOrNull { it.atMillis == pass.startedAtMillis } ?: return@run
                    val b = raw.singleOrNull { it.atMillis == pass.endedAtMillis } ?: return@run
                    val timeline = view.route.review.timeline ?: return@run
                    val start = MeasurementTimeAddress(a.sourceEpoch ?: return@run, a.clockEpochId ?: return@run, a.elapsedRealtimeNanos ?: return@run)
                    val end = MeasurementTimeAddress(b.sourceEpoch ?: return@run, b.clockEpochId ?: return@run, b.elapsedRealtimeNanos ?: return@run)
                    if (timeline.slice(timeline.position(start) ?: return@run, timeline.position(end) ?: return@run) == null) return@run
                    selection.put("kind", "slice").put("from", time(start)).put("until", time(end))
                }
            }
        }
        return JSONObject().put("version", VERSION).put("owner", owner).put("session", view.route.detail.summary.sessionId)
            .put("identity", view.route.explorationIdentity).put("selection", selection).put("speed", state.playbackSpeed.name)
            .put("panel", state.panelOpen).put("reading", reading).toString().takeIf { it.toByteArray().size <= 16_384 }
    }
    data class Restored(val selection: WalkRouteSelection, val speed: RoutePlaybackSpeed, val panel: Boolean, val reading: JSONObject)
    fun decode(payload: String, owner: String, view: WalkDiaryReadView): Restored? = runCatching {
        require(payload.toByteArray().size <= 16_384 && !view.scenesLoading)
        val o = JSONObject(payload)
        require(integer(o, "version") == VERSION.toLong() && o.getString("owner") == owner && owner.isNotBlank() &&
            o.getString("session") == view.route.detail.summary.sessionId && o.getString("identity") == view.route.explorationIdentity)
        val s = o.getJSONObject("selection"); val review = view.route.review
        val reading = o.optJSONObject("reading") ?: JSONObject()
        val selection = when (s.getString("kind")) {
            "overview" -> WalkRouteSelection.Overview
            "scene" -> {
                val scene = view.diary?.scenes?.singleOrNull { it.id == s.getString("id") } ?: error("removed scene")
                val (event, revision) = revisions(view, scene)
                require(event == s.getString("event"))
                if (revision != s.getString("scene")) reading.remove("body")
                WalkRouteSelection.Scene(scene.id)
            }
            "replay" -> {
                val at = if (view.route.detail.measurement != null) review.timeline?.position(time(s.getJSONObject("at")))
                    else integer(s, "elapsed").takeIf { it in 0..(if (review.context.available)
                        requireNotNull(review.context.durationMillis) else view.route.index.durationMillis) }
                WalkRouteSelection.Replay(requireNotNull(at))
            }
            "slice" -> {
                val a = time(s.getJSONObject("from")); val b = time(s.getJSONObject("until"))
                val t = requireNotNull(review.timeline)
                requireNotNull(t.slice(requireNotNull(t.position(a)), requireNotNull(t.position(b))))
                WalkRouteSelection.Slice(a, b)
            }
            "section" -> {
                val measurement = view.route.detail.measurement
                val section = if (measurement != null) measurement.walkingSections.indexOfFirst { it.id == s.getString("id") }
                    .takeIf { it >= 0 }?.let { review.sections.getOrNull(it) }
                else review.sections.singleOrNull { it.startedAtMillis == s.getLong("from") && it.endedAtMillis == s.getLong("until") }
                WalkRouteSelection.Section(requireNotNull(section).index)
            }
            "auxiliary" -> WalkRouteSelection.Auxiliary(review.observed.sections.single { it.id == s.getString("id") }.id)
            "context" -> {
                val value = requireNotNull(review.context.context(s.getString("id")))
                when (value.kind) {
                    com.daengs.app.walk.trajectory.RecordContextKind.GAP -> WalkRouteSelection.Gap(value.id)
                    com.daengs.app.walk.trajectory.RecordContextKind.TRANSITION -> WalkRouteSelection.Transition(value.id)
                    else -> WalkRouteSelection.Event(value.id)
                }
            }
            else -> error("unknown selection")
        }
        Restored(selection, RoutePlaybackSpeed.valueOf(o.getString("speed")), o.getBoolean("panel"), reading)
    }.getOrNull()
}
