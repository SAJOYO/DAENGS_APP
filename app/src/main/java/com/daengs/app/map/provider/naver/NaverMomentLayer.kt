package com.daengs.app.map.provider.naver

import android.graphics.Color
import android.graphics.PointF
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import com.daengs.app.R
import com.daengs.app.map.layers.moments.MomentMarkerState
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage

/** Owns ordinary action/photo pins. Grouped record/diary pins and the camera have separate owners. */
@Composable
internal fun NaverMomentLayer(
    naverMap: NaverMap?,
    visibleMoments: List<MomentMarkerState>,
    onSelectMoment: (String) -> Unit,
    diagnostics: WalkMapDiagnostics?,
    globalZ: Int,
) {
    val context = LocalContext.current
    val latestMomentCallback by rememberUpdatedState(onSelectMoment)
    val photoFiles = visibleMoments.mapNotNull { it.photoFile }.distinct()
    val photoIcons by androidx.compose.runtime.produceState<Map<java.io.File, OverlayImage>>(emptyMap(), photoFiles) {
        val loaded = mutableMapOf<java.io.File, OverlayImage>()
        for (file in photoFiles) {
            try {
                val photo = com.daengs.app.screening.Photo.decodeUpright(context, android.net.Uri.fromFile(file), 96)
                val pin = android.graphics.Bitmap.createBitmap(104, 112, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(pin)
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                paint.color = Color.WHITE
                canvas.drawRoundRect(0f, 0f, 104f, 104f, 14f, 14f, paint)
                val tail = android.graphics.Path().apply { moveTo(42f, 102f); lineTo(52f, 112f); lineTo(62f, 102f); close() }
                canvas.drawPath(tail, paint)
                val edge = minOf(photo.width, photo.height)
                val x = (photo.width - edge) / 2; val y = (photo.height - edge) / 2
                canvas.drawBitmap(photo, android.graphics.Rect(x, y, x + edge, y + edge),
                    android.graphics.RectF(6f, 6f, 98f, 98f), paint)
                photo.recycle()
                loaded[file] = OverlayImage.fromBitmap(pin)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // 원본 파일을 잃었어도 Pin을 눌러 확인·삭제할 수 있다.
            }
        }
        value = loaded
    }
    val badgeDensity = LocalDensity.current.density
    DisposableEffect(naverMap, visibleMoments, photoIcons, badgeDensity, diagnostics, globalZ) {
        val map = naverMap
        val markers = if (map == null) emptyList() else visibleMoments.map { moment ->
            Marker().apply {
                position = LatLng(moment.point.latitude, moment.point.longitude)
                val behaviorArt = moment.takeIf { it.behaviors.isNotEmpty() }?.let {
                    actionMarkerBitmap(context, it.behaviors, it.selected, it.sequenceLabel, badgeDensity)
                }
                val badge = moment.sequenceLabel?.takeIf { behaviorArt == null }
                    ?.let { diaryPinBitmap(it, moment.selected, badgeDensity) }
                captionText = if (badge == null) moment.label else ""
                captionMinZoom = 12.0
                width = behaviorArt?.width ?: badge?.width ?: if (moment.selected) MOMENT_MARKER_PX_SELECTED else MOMENT_MARKER_PX
                height = behaviorArt?.height ?: badge?.height ?: if (moment.selected) MOMENT_MARKER_PX_SELECTED else MOMENT_MARKER_PX
                anchor = if (behaviorArt != null) PointF(0.5f, 0.5f)
                    else if (badge == null) MARKER_ANCHOR else PointF(0.5f, 1f)
                icon = behaviorArt?.let(OverlayImage::fromBitmap) ?: badge?.let(OverlayImage::fromBitmap) ?: photoIcons[moment.photoFile]
                    ?: OverlayImage.fromResource(R.drawable.ic_walk_moment)
                zIndex = if (moment.aboveRouteEndpoints) {
                    if (moment.selected) 140 else 120
                } else if (moment.selected) SELECTED_MARKER_Z else MOMENT_MARKER_Z
                isHideCollidedMarkers = false
                setOnClickListener {
                    latestMomentCallback(moment.id)
                    true
                }
                applyNativeWalkOrder(globalZ, { globalZIndex = it }, { globalZIndex })
                this.map = map
                diagnostics?.attached(this, NativeWalkLayerReading("마커", globalZIndex, "행동"))
            }
        }
        onDispose { markers.forEach { it.map = null; diagnostics?.detached(it) } }
    }
}

private const val MOMENT_MARKER_PX = 64
private const val MOMENT_MARKER_PX_SELECTED = 82
/** Above facilities, below selected markers. */
private const val MOMENT_MARKER_Z = 50
private const val SELECTED_MARKER_Z = 100
private val MARKER_ANCHOR = PointF(0.5f, 0.933f)

@Preview(showBackground = true)
@Composable
private fun NaverMomentLayerPreview() {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val bitmap = remember(context, density) { diaryPinBitmap("1", false, density) }
    Image(bitmap.asImageBitmap(), "산책 순간 핀")
}
