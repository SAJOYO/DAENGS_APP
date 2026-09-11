package com.daengs.app.map.provider.naver

import android.graphics.Color
import android.graphics.PointF
import androidx.compose.runtime.*
import com.daengs.app.R
import com.daengs.app.map.layers.territory.*
import com.daengs.app.ui.walk.rememberTerritoryFeedbackProgress
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.CircleOverlay
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage

private data class SiteOverlays(val marker: Marker, val ring: CircleOverlay?, val paw: Marker?)

/** GPS 재조회와 효과 프레임을 분리해, 매 프레임 SDK overlay를 만들지 않는다. */
@Composable
internal fun NaverTerritoryLayer(map: NaverMap?, sites: List<TerritorySiteMarkerState>, onSelect: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val icons = remember(context) {
        TerritoryMarkerOccupancy.entries.associateWith {
            OverlayImage.fromBitmap(territoryMarkerIcon(context, it))
        }
    }
    val overlays = remember(map) { mutableMapOf<String, SiteOverlays>() }
    val latestSelect by rememberUpdatedState(onSelect)
    val feedback = sites.firstOrNull { it.selected && it.feedback != null }?.feedback
    val progress = rememberTerritoryFeedbackProgress(feedback)
    DisposableEffect(map, sites) {
        if (map != null) sites.forEach { site ->
            val point = LatLng(site.point.latitude, site.point.longitude)
            val marker = Marker().apply {
                position = point
                captionText = if (site.selected) site.label else if (!site.occupancyKnown) "확인 전" else when (site.occupancy) {
                    TerritoryMarkerOccupancy.NEUTRAL -> ""
                    TerritoryMarkerOccupancy.UNVERIFIED -> "미인증"
                    TerritoryMarkerOccupancy.VERIFIED -> "인증"
                }
                captionMinZoom = 0.0
                if (site.radiusMeters != null) {
                    if (captionText.isEmpty()) captionText = "전봇대"
                    val rangeStyle = territoryRangeStyle(site.proximity)
                    subCaptionText = rangeStyle.label
                    subCaptionColor = rangeStyle.outlineArgb
                    subCaptionTextSize = 11f
                    subCaptionMinZoom = 0.0
                }
                val size = TerritoryPoleArt.size()
                width = size.first; height = size.second
                anchor = PointF(TerritoryPoleArt.ANCHOR_X, TerritoryPoleArt.ANCHOR_Y)
                icon = icons.getValue(site.occupancy)
                alpha = if (site.occupancyKnown) 1f else .55f
                zIndex = if (site.selected) 100 else 30
                // 성공 발자국이 같은 위치에 떠도 선택한 전봇대가 충돌 숨김 처리되면 안 된다.
                isHideCollidedMarkers = !site.selected && site.radiusMeters == null
                isHideCollidedSymbols = !site.selected && site.radiusMeters == null
                setOnClickListener { latestSelect(site.id); true }
                this.map = map
            }
            val ring = site.radiusMeters?.let { meters -> CircleOverlay().apply {
                center = point; radius = meters
                zIndex = -1
                this.map = map
            } }
            val paw = if (site.selected && site.feedback?.kind in setOf(TerritoryFeedbackKind.MARKED, TerritoryFeedbackKind.VERIFIED)) Marker().apply {
                position = point; anchor = PointF(.5f, 1.6f)
                width = 36; height = 36; alpha = 0f
                icon = OverlayImage.fromResource(R.drawable.ic_territory_paw)
                zIndex = 101
                setOnClickListener { latestSelect(site.id); true }
                this.map = map
            } else null
            overlays[site.id] = SiteOverlays(marker, ring, paw)
        }
        onDispose {
            overlays.values.forEach { it.marker.map = null; it.ring?.map = null; it.paw?.map = null }
            overlays.clear()
        }
    }
    SideEffect {
        sites.forEach { site ->
            val overlay = overlays[site.id] ?: return@forEach
            val active = feedback?.takeIf { it.siteId == site.id }
            val frame = territoryFeedbackFrame(active?.kind, progress)
            val accent = if (active?.kind == TerritoryFeedbackKind.MARKED) Color.rgb(227, 145, 45) else Color.rgb(60, 150, 115)
            val size = TerritoryPoleArt.size(frame.markerScale)
            overlay.marker.width = size.first
            overlay.marker.height = size.second
            overlay.ring?.apply {
                val tint = territoryRangeStyle(site.proximity).outlineArgb
                color = Color.argb((20 + 20 * frame.glow).toInt(), Color.red(tint), Color.green(tint), Color.blue(tint))
                outlineColor = tint
                outlineWidth = ((2 + frame.glow) * density).toInt().coerceAtLeast(1)
                // radius는 판정 반경 그대로 둔다. 빛만 바뀌며 점령 범위는 늘어나지 않는다.
            }
            overlay.paw?.apply {
                alpha = frame.pawAlpha
                width = (36 * frame.pawScale).toInt(); height = width
                iconTintColor = accent
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 320, heightDp = 620)
@Composable
private fun NaverTerritoryLayerPreview() {
    com.daengs.app.ui.walk.TerritoryRangePreview()
}
