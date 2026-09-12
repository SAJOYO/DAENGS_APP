package com.daengs.app.map.provider.naver

import android.graphics.Color
import android.graphics.PointF
import com.daengs.app.R
import com.daengs.app.map.layers.territory.*
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.CircleOverlay
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage

/** Holds SDK objects until their site disappears or their map is disposed. */
internal class NaverTerritoryOverlay(
    private val map: NaverMap,
    initial: TerritoryRenderState,
    private val icons: Map<TerritoryPoleStyle, OverlayImage>,
    private val density: Float,
    private val onSelect: (String) -> Unit,
) : TerritoryOverlayHandle {
    private val marker = Marker().apply {
        anchor = PointF(TerritoryPoleArt.ANCHOR_X, TerritoryPoleArt.ANCHOR_Y)
        captionMinZoom = 0.0
        subCaptionTextSize = 11f
        subCaptionMinZoom = 0.0
        setOnClickListener { onSelect(initial.id); true }
    }
    private var ring: CircleOverlay? = null
    private var paw: Marker? = null
    private var state = initial
    private var lastFrame: TerritoryFeedbackFrame? = null
    private var lastMarked: Boolean? = null

    init {
        apply(null, initial)
        frame(TerritoryFeedbackFrame(), false)
        marker.map = map
    }

    override fun update(previous: TerritoryRenderState, next: TerritoryRenderState) = apply(previous, next)

    private fun apply(old: TerritoryRenderState?, next: TerritoryRenderState) {
        val point = LatLng(next.point.latitude, next.point.longitude)
        if (old?.point != next.point) {
            marker.position = point
            ring?.center = point
            paw?.position = point
        }
        if (old?.caption != next.caption) marker.captionText = next.caption
        if (old?.style != next.style) marker.icon = icons.getValue(next.style)
        if (old?.alpha != next.alpha) marker.alpha = next.alpha
        if (old?.selected != next.selected) marker.zIndex = if (next.selected) 100 else 30
        if (old?.hideCollisions != next.hideCollisions) {
            marker.isHideCollidedMarkers = next.hideCollisions
            marker.isHideCollidedSymbols = next.hideCollisions
        }
        if (old?.range != next.range) {
            marker.subCaptionText = next.range?.label.orEmpty()
            marker.subCaptionColor = next.range?.outlineArgb ?: Color.BLACK
        }
        if (next.radius == null) {
            ring?.map = null
            ring = null
        } else {
            if (ring == null) ring = CircleOverlay().apply {
                center = point; radius = next.radius; zIndex = -1
                this.map = this@NaverTerritoryOverlay.map
            } else if (old?.radius != next.radius) ring?.radius = next.radius
        }
        if (!next.paw) {
            paw?.map = null
            paw = null
        } else if (paw == null) {
            paw = Marker().apply {
                position = point; anchor = PointF(.5f, 1.6f)
                width = 36; height = 36; alpha = 0f
                icon = OverlayImage.fromResource(R.drawable.ic_territory_paw)
                zIndex = 101
                setOnClickListener { onSelect(next.id); true }
                this.map = this@NaverTerritoryOverlay.map
            }
        }
        // New rings/paws and changed range colors must receive the current animation frame.
        if (old?.range != next.range || old?.radius != next.radius || old?.paw != next.paw) lastFrame = null
        state = next
    }

    override fun frame(frame: TerritoryFeedbackFrame, marked: Boolean) {
        if (lastFrame == frame && lastMarked == marked) return
        val size = TerritoryPoleArt.size(frame.markerScale)
        if (marker.width != size.first) marker.width = size.first
        if (marker.height != size.second) marker.height = size.second
        ring?.apply {
            val tint = checkNotNull(state.range).outlineArgb
            val fill = Color.argb((20 + 20 * frame.glow).toInt(), Color.red(tint), Color.green(tint), Color.blue(tint))
            val stroke = ((2 + frame.glow) * density).toInt().coerceAtLeast(1)
            if (color != fill) color = fill
            if (outlineColor != tint) outlineColor = tint
            if (outlineWidth != stroke) outlineWidth = stroke
        }
        paw?.apply {
            val sizePx = (36 * frame.pawScale).toInt()
            val tint = if (marked) Color.rgb(227, 145, 45) else Color.rgb(60, 150, 115)
            if (alpha != frame.pawAlpha) alpha = frame.pawAlpha
            if (width != sizePx) width = sizePx
            if (height != sizePx) height = sizePx
            if (iconTintColor != tint) iconTintColor = tint
        }
        lastFrame = frame
        lastMarked = marked
    }

    override fun remove() {
        marker.map = null
        ring?.map = null
        paw?.map = null
    }
}
