package com.daengs.app.map.provider.naver

import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.territory.*

/** Only properties consumed by the map; ready and feedback identity are deliberately absent. */
internal data class TerritoryRenderState(
    val id: String,
    val point: GeoPoint,
    val caption: String,
    val style: TerritoryPoleStyle,
    val alpha: Float,
    val selected: Boolean,
    val radius: Double?,
    val range: TerritoryRangeStyle?,
    val paw: Boolean,
) {
    val hideCollisions get() = !selected && radius == null
}

internal fun TerritorySiteMarkerState.renderState(): TerritoryRenderState {
    val caption = if (selected) label else if (!occupancyKnown) "확인 전" else when (occupancy) {
        TerritoryMarkerOccupancy.NEUTRAL -> ""
        TerritoryMarkerOccupancy.UNVERIFIED -> if (isMine) "내 미인증" else "상대 미인증"
        TerritoryMarkerOccupancy.VERIFIED -> if (isMine) "내 인증" else "상대 인증"
    }
    return TerritoryRenderState(id, point, caption.ifEmpty { if (radiusMeters != null) "전봇대" else "" },
        TerritoryPoleStyle.of(if (occupancyKnown) occupancy else TerritoryMarkerOccupancy.NEUTRAL, isMine),
        if (occupancyKnown) 1f else .55f, selected, radiusMeters,
        radiusMeters?.let { territoryRangeStyle(proximity) },
        selected && feedback?.kind in setOf(TerritoryFeedbackKind.MARKED, TerritoryFeedbackKind.VERIFIED))
}

internal interface TerritoryOverlayHandle {
    fun update(previous: TerritoryRenderState, next: TerritoryRenderState)
    fun frame(frame: TerritoryFeedbackFrame, marked: Boolean)
    fun remove()
}

/** One instance per map lifetime. All calls occur on the map's main thread. */
internal class TerritoryOverlayStore(
    private val create: (TerritoryRenderState) -> TerritoryOverlayHandle,
    private val probe: TerritoryOverlayProbe? = null,
) {
    private data class Entry(val handle: TerritoryOverlayHandle, var state: TerritoryRenderState)
    private val entries = linkedMapOf<String, Entry>()
    private var lastStates: List<TerritoryRenderState> = emptyList()

    fun sync(states: List<TerritoryRenderState>) {
        if (lastStates == states) return
        val started = if (probe != null) System.nanoTime() else 0L
        val incoming = states.associateBy { it.id }
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val (id, entry) = iterator.next()
            if (id !in incoming) {
                entry.handle.remove()
                iterator.remove()
                probe?.let { it.removed++ }
            }
        }
        incoming.forEach { (id, state) ->
            val entry = entries[id]
            if (entry == null) {
                entries[id] = Entry(create(state), state)
                probe?.let { it.created++ }
            } else if (entry.state != state) {
                entry.handle.update(entry.state, state)
                entry.state = state
                probe?.let { it.updated++ }
            }
        }
        lastStates = states.toList()
        probe?.syncNanos?.add(System.nanoTime() - started)
    }

    // Step 2 will limit traversal to active/prior effect IDs. Unchanged SDK values are skipped by the handle.
    fun frame(feedback: TerritoryFeedback?, progress: Float) {
        entries.forEach { (id, entry) ->
            val kind = feedback?.takeIf { it.siteId == id }?.kind
            entry.handle.frame(territoryFeedbackFrame(kind, progress), kind == TerritoryFeedbackKind.MARKED)
        }
    }

    fun clear() {
        entries.values.forEach { it.handle.remove() }
        probe?.let { it.removed += entries.size }
        entries.clear()
        lastStates = emptyList()
    }
}
