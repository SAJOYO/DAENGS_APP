package com.daengs.app.map.provider.naver

import android.graphics.PointF
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.R
import com.daengs.app.map.layers.stays.StayStampMarkerState
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage

/** Automatic place traces never invoke the owner's action editor. */
@Composable
internal fun NaverStayStampLayer(map: NaverMap?, stamps: List<StayStampMarkerState>) {
    val density = LocalDensity.current
    val width = with(density) { 56.dp.roundToPx() }
    val height = with(density) { 34.dp.roundToPx() }
    DisposableEffect(map, stamps, width, height) {
        val markers = if (map == null) emptyList() else stamps.map { stamp ->
            Marker().apply {
                position = LatLng(stamp.point.latitude, stamp.point.longitude)
                icon = OverlayImage.fromResource(R.drawable.dog_resting_leash_portrait)
                this.width = width
                this.height = height
                anchor = PointF(0.5f, 0.5f)
                zIndex = 45
                isHideCollidedMarkers = false
                captionMinZoom = 0.0
                setOnClickListener {
                    captionText = if (captionText.isEmpty()) "머문 자리 · 휴대폰 위치로 추정" else ""
                    true
                }
                this.map = map
            }
        }
        onDispose { markers.forEach { it.map = null } }
    }
}

/** Asset at its display size; SDK placement still requires a device check. */
@Preview(showBackground = true, backgroundColor = 0xFFFFFAF4)
@Composable
private fun StayStampIconPreview() {
    Image(painterResource(R.drawable.dog_resting_leash_portrait), "머문 자리",
        modifier = Modifier.size(56.dp, 34.dp))
}
