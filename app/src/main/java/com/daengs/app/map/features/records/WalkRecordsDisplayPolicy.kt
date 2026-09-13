package com.daengs.app.map.features.records

import com.daengs.app.map.layers.traces.TraceBrushPolicy
import com.daengs.app.map.style.WalkRouteAppearance

data class TraceDensityBand(val minimum: Int, val opacity: Float)

/** Immutable value key shared by raster caches and the legend. */
class TraceDensityScale(bands: List<TraceDensityBand>) {
    val bands: List<TraceDensityBand> = java.util.Collections.unmodifiableList(bands.toList())
    init {
        require(this.bands.isNotEmpty() && this.bands.first().minimum == 1)
        require(this.bands.all { it.opacity.isFinite() && it.opacity in 0f..1f })
        require(this.bands.zipWithNext().all { (a, b) -> a.minimum < b.minimum && a.opacity <= b.opacity })
    }
    fun alpha(count: Int): Float {
        require(count >= 0)
        return bands.lastOrNull { count >= it.minimum }?.opacity ?: 0f
    }
    val maximum: Float get() = bands.last().opacity
    fun legend(): List<TraceLegendItem> = bands.mapIndexed { index, band ->
        val end = bands.getOrNull(index + 1)?.minimum?.minus(1)
        val key = if (end == null || end == band.minimum) "${band.minimum}" else "${band.minimum}-$end"
        val label = when (end) {
            null -> "${band.minimum}회 이상"
            band.minimum -> "${band.minimum}회"
            else -> "${band.minimum}–${end}회"
        }
        TraceLegendItem(key, label, band.opacity)
    }
    override fun equals(other: Any?) = other is TraceDensityScale && bands == other.bands
    override fun hashCode() = bands.hashCode()
    override fun toString() = bands.joinToString { "${it.minimum}:${it.opacity}" }

    companion object {
        val Default = TraceDensityScale(listOf(TraceDensityBand(1, .04f), TraceDensityBand(2, .12f),
            TraceDensityBand(3, .22f), TraceDensityBand(5, .34f), TraceDensityBand(8, .46f)))
    }
}

data class TraceLegendItem(val key: String, val label: String, val opacity: Float)
data class TraceDisplayPolicy(
    val rgb: Int,
    val density: TraceDensityScale = TraceDensityScale.Default,
    val brush: TraceBrushPolicy = TraceBrushPolicy(),
) { init { require(rgb in 0..0xFFFFFF) } }

sealed interface TraceView {
    data object All : TraceView
    data class Overlap(val minimum: Int) : TraceView { init { require(minimum in setOf(2, 3, 5)) } }
    data object Locations : TraceView
}

data class WalkRecordsDisplayPolicy(val trace: TraceDisplayPolicy, val route: WalkRouteAppearance)
