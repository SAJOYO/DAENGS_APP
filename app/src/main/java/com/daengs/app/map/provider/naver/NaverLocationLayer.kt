package com.daengs.app.map.provider.naver

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import com.daengs.app.R
import com.daengs.app.location.GeoPoint
import com.daengs.app.ui.theme.DaengPink
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.LocationOverlay
import com.naver.maps.map.overlay.OverlayImage

/** Owns the device location display. Map lifetime and camera following belong to the Surface. */
@Composable
internal fun NaverLocationLayer(
    naverMap: NaverMap?,
    currentPosition: GeoPoint?,
    @DrawableRes avatarRes: Int?,
    avatarPhoto: Bitmap?,
) {
    val context = LocalContext.current
    DisposableEffect(naverMap) {
        val map = naverMap
        val overlay: LocationOverlay? = map?.locationOverlay
        // LocationOverlay rotates its main icon with the map. Counteract that rotation
        // so the portrait stays upright; travel course belongs to the separate arrow.
        val listener = NaverMap.OnCameraChangeListener { _, _ ->
            overlay?.bearing = map?.cameraPosition?.bearing?.toFloat() ?: 0f
        }
        overlay?.bearing = map?.cameraPosition?.bearing?.toFloat() ?: 0f
        map?.addOnCameraChangeListener(listener)
        onDispose {
            map?.removeOnCameraChangeListener(listener)
            overlay?.isVisible = false
        }
    }

    LaunchedEffect(naverMap, currentPosition) {
        val overlay = naverMap?.locationOverlay ?: return@LaunchedEffect
        val point = currentPosition
        if (point == null) {
            overlay.isVisible = false
        } else {
            overlay.position = LatLng(point.latitude, point.longitude)
            overlay.isVisible = true
        }
    }

    // Capture SDK defaults before applying any portrait, so removing one cannot leave an old face.
    val defaultLocationIcon = remember(naverMap) {
        naverMap?.locationOverlay?.let { Triple(it.icon, it.iconWidth, it.iconHeight) }
    }
    LaunchedEffect(naverMap, avatarRes, avatarPhoto) {
        val overlay = naverMap?.locationOverlay ?: return@LaunchedEffect
        val defaults = defaultLocationIcon ?: return@LaunchedEffect
        overlay.circleColor = LOCATION_CIRCLE
        // **올린 사진이 앞선다.** 앱의 다른 얼굴이 다 그 규칙이라(`avatarSource`),
        // 지도만 견종 그림이면 같은 아이가 화면마다 다르게 보인다.
        val bitmap = locationAvatarBitmap(context, avatarPhoto, avatarRes, AVATAR_PX, AVATAR_RING_PX)
        overlay.icon = bitmap?.let(OverlayImage::fromBitmap) ?: defaults.first
        overlay.iconWidth = if (bitmap == null) defaults.second else AVATAR_PX
        overlay.iconHeight = if (bitmap == null) defaults.third else AVATAR_PX
    }
}

/** 내 위치 얼굴의 한 변(px)과 흰 테두리 두께. */
private const val AVATAR_PX = 96
private const val AVATAR_RING_PX = 5f

/** 위치 정확도 원. 기본 파랑 대신 앱 분홍을 옅게 깐다. */
private val LOCATION_CIRCLE = DaengPink.copy(alpha = 0.18f).toArgb()

@Preview(showBackground = true)
@Composable
private fun LocationAvatarPreview() {
    val context = LocalContext.current
    val bitmap = remember(context) {
        locationAvatarBitmap(context, null, R.drawable.ic_location_paw, AVATAR_PX, AVATAR_RING_PX)
    } ?: return
    Image(bitmap.asImageBitmap(), "내 위치", Modifier.size(with(LocalDensity.current) { AVATAR_PX.toDp() }))
}
