package com.daengs.app.map.provider.naver

import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.map.layers.completedroute.RecordContextLayerState
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage
import com.naver.maps.map.overlay.PolylineOverlay

/** Relation guide has its own SDK overlay. It never enters a route/direction/replay collection. */
@Composable
internal fun NaverRecordContextLayer(map: NaverMap?, state: RecordContextLayerState?, density: Float,
    onSelect: (String) -> Unit) {
    val select by rememberUpdatedState(onSelect)
    DisposableEffect(map, state, density) {
        val markers = if (map == null) emptyList() else state?.markers.orEmpty().map { item ->
            val bitmap = if (item.gapBoundary) diaryGapPinBitmap(item.selected, density)
                else diaryPinBitmap(item.label, item.selected, density, endpoint = true)
            Marker(LatLng(item.point.latitude, item.point.longitude)).apply {
                icon = OverlayImage.fromBitmap(bitmap); width = bitmap.width; height = bitmap.height
                anchor = PointF(.5f, 0f)
                // Selection changes ink, never the gap's footprint or hit-test priority.
                zIndex = if (item.gapBoundary) 81 else if (item.selected) 101 else 79
                isHideCollidedMarkers = false
                setOnClickListener { select(item.contextId); true }
                this.map = map
            }
        }
        val guide = if (map == null) null else state?.selectedGapGuide?.let { command ->
            PolylineOverlay().apply {
                coords = listOf(command.beforePoint, command.afterPoint).map { LatLng(it.latitude, it.longitude) }
                width = (2 * density).toInt().coerceAtLeast(1)
                color = 0xff77717d.toInt(); setPattern((6 * density).toInt().coerceAtLeast(1), (5 * density).toInt().coerceAtLeast(1))
                zIndex = 3
                setOnClickListener { select(command.contextId); true }
                this.map = map
            }
        }
        onDispose { guide?.map = null; markers.forEach { it.map = null } }
    }
}

@Preview(showBackground = true)
@Composable
private fun GapRelationStylePreview() {
    Canvas(Modifier.size(180.dp, 48.dp)) {
        drawLine(Color(0xff77717d), Offset(12.dp.toPx(), size.height / 2), Offset(size.width - 12.dp.toPx(), size.height / 2),
            2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())))
    }
}
