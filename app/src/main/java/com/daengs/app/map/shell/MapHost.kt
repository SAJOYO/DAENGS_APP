package com.daengs.app.map.shell

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.provider.naver.NaverMapSurface

@Composable
fun MapHost(
    scene: MapScene,
    searchOrigin: GeoPoint?,
    followDevice: Boolean,
    @DrawableRes avatarRes: Int? = null,
    /** 사용자가 올린 프로필 사진. 있으면 [avatarRes] 보다 이쪽이 앞선다. */
    avatarPhoto: android.graphics.Bitmap? = null,
    bottomPaddingPx: Int = 0,
    centerOn: GeoPoint? = null,
    centerZoom: Double? = null,
    fitBounds: List<GeoPoint>? = null,
    onCameraIdle: (GeoPoint) -> Unit,
    onCameraGesture: () -> Unit,
    onSelectPlace: (String) -> Unit,
    onSelectTerritorySite: (String) -> Unit = {},
    onSelectMoment: (String) -> Unit = {},
    onSelectRouteEndpoint: (String) -> Unit = {},
    onMapTap: (GeoPoint) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    NaverMapSurface(
        scene = scene,
        searchOrigin = searchOrigin,
        followDevice = followDevice,
        avatarRes = avatarRes,
        avatarPhoto = avatarPhoto,
        bottomPaddingPx = bottomPaddingPx,
        centerOn = centerOn,
        centerZoom = centerZoom,
        fitBounds = fitBounds,
        onCameraIdle = onCameraIdle,
        onCameraGesture = onCameraGesture,
        onSelectPlace = onSelectPlace,
        onSelectTerritorySite = onSelectTerritorySite,
        onSelectMoment = onSelectMoment,
        onSelectRouteEndpoint = onSelectRouteEndpoint,
        onMapTap = onMapTap,
        modifier = modifier,
    )
}
