package com.daengs.app.map.provider.naver

import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.completedroute.CompletedRouteLayerState
import com.daengs.app.map.layers.trail.TrailLayerState
import com.daengs.app.map.style.*

/** Ordinal within a live/completed recording segment list; never joins separate segments. */
internal data class WalkRouteKey(val completed: Boolean, val index: Int, val speed: Boolean)
internal data class WalkRouteSource(val key: WalkRouteKey,
    val speedPoints: List<WalkSpeedPoint> = emptyList(), val points: List<GeoPoint> = emptyList())
internal data class WalkRouteRenderState(val key: WalkRouteKey, val parts: List<WalkSpeedPart>, val dimmed: Boolean,
    val stroke: WalkRouteStroke = WalkRouteStroke(), val themeId: String = "", val unknownColor: Int = 0)

internal fun walkRouteSources(trail: TrailLayerState, completed: CompletedRouteLayerState): List<WalkRouteSource> = buildList {
    fun group(done: Boolean, speed: List<List<WalkSpeedPoint>>, plain: List<List<GeoPoint>>) {
        // A nonempty speed list owns the display, even when all its segments have <2 points.
        if (speed.isNotEmpty()) speed.forEachIndexed { index, points ->
            add(WalkRouteSource(WalkRouteKey(done, index, true), speedPoints = points))
        } else plain.forEachIndexed { index, points ->
            add(WalkRouteSource(WalkRouteKey(done, index, false), points = points))
        }
    }
    group(false, trail.speedPaths, trail.paths)
    group(true, completed.speedPaths, completed.paths)
}

internal interface WalkRouteHandle {
    fun update(previous: WalkRouteRenderState, next: WalkRouteRenderState)
    fun remove()
}

/** Map-owned cache. Inputs must be immutable snapshots, including each nested point list.
 * Equal copied segments reuse their prepared parts; append-only speed segments repaint their
 * interpolation tail. Other edits invalidate the segment cache. No recording data is altered.
 */
internal class WalkRouteOverlayStore(
    private val create: (WalkRouteRenderState) -> WalkRouteHandle,
    private val probe: WalkRouteProbe? = null,
) {
    private data class Entry(var source: WalkRouteSource, var policy: WalkStylePolicy, var theme: String,
        var state: WalkRouteRenderState, var handle: WalkRouteHandle?, val speedCache: WalkSpeedPathCache?)
    private val entries = linkedMapOf<WalkRouteKey, Entry>()
    private var lastSources: List<WalkRouteSource>? = null
    private var lastPolicy: WalkStylePolicy? = null
    private var lastTheme: String? = null
    private var lastDim = false
    private var lastStroke = WalkRouteStroke()

    fun sync(sources: List<WalkRouteSource>, policy: WalkStylePolicy, theme: String, dimCompleted: Boolean, stroke: WalkRouteStroke = WalkRouteStroke()) {
        if (sources === lastSources && policy == lastPolicy && theme == lastTheme && dimCompleted == lastDim && stroke == lastStroke) return
        val desired = sources.mapTo(mutableSetOf()) { it.key }
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val (key, entry) = iterator.next()
            if (key !in desired) { remove(entry); iterator.remove() }
        }
        for (source in sources) {
            val entry = entries[source.key]
            val cache = entry?.speedCache ?: if (source.key.speed) WalkSpeedPathCache(probe?.let { stats ->
                { count -> stats.pointsPrepared += count; stats.edgesPainted += (count - 1).coerceAtLeast(0) }
            }) else null
            val changed = entry == null || entry.source != source || entry.policy != policy || entry.theme != theme
            val parts = if (changed) prepare(source, policy, theme, cache) else entry!!.state.parts
            // Coordinate-only legacy paths retain the existing unknown-speed appearance.
            val next = WalkRouteRenderState(source.key, parts, source.key.speed && source.key.completed && dimCompleted, stroke, theme, policy.unknownColor)
            if (entry == null) {
                entries[source.key] = Entry(source, policy, theme, next,
                    if (parts.isEmpty()) null else sdk { create(next).also { probe?.created = (probe?.created ?: 0) + 1 } }, cache)
            } else {
                when {
                    parts.isEmpty() -> remove(entry)
                    entry.handle == null -> entry.handle = sdk { create(next).also { probe?.created = (probe?.created ?: 0) + 1 } }
                    next != entry.state -> sdk {
                        entry.handle!!.update(entry.state, next)
                        probe?.updated = (probe?.updated ?: 0) + 1
                    }
                }
                entry.source = source; entry.policy = policy; entry.theme = theme; entry.state = next
            }
        }
        lastSources = sources; lastPolicy = policy; lastTheme = theme; lastDim = dimCompleted; lastStroke = stroke
    }

    private fun prepare(source: WalkRouteSource, policy: WalkStylePolicy, theme: String,
        cache: WalkSpeedPathCache?): List<WalkSpeedPart> {
        val started = if (probe != null) System.nanoTime() else 0L
        val parts = if (source.key.speed) checkNotNull(cache).paint(source.speedPoints, policy, theme)
            else if (source.points.size >= 2) listOf(WalkSpeedPart(source.points, policy.unknownColor)) else emptyList()
        probe?.let {
            it.prepared++
            if (!source.key.speed) it.pointsPrepared += source.points.size
            it.prepareNanos += System.nanoTime() - started
        }
        return parts
    }

    private fun <T> sdk(block: () -> T): T {
        val started = if (probe != null) System.nanoTime() else 0L
        return try { block() } finally { probe?.sdkNanos?.add(System.nanoTime() - started) }
    }

    private fun remove(entry: Entry) {
        entry.handle?.let { handle -> sdk { handle.remove(); probe?.removed = (probe?.removed ?: 0) + 1 } }
        entry.handle = null
    }

    fun clear() {
        entries.values.forEach(::remove); entries.clear()
        lastSources = null; lastPolicy = null; lastTheme = null; lastDim = false
    }
}
