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
    bottomPaddingPx: Int = 0,
    centerOn: GeoPoint? = null,
    onCameraIdle: (GeoPoint) -> Unit,
    onCameraGesture: () -> Unit,
    onSelectPlace: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    NaverMapSurface(
        scene = scene,
        searchOrigin = searchOrigin,
        followDevice = followDevice,
        avatarRes = avatarRes,
        bottomPaddingPx = bottomPaddingPx,
        centerOn = centerOn,
        onCameraIdle = onCameraIdle,
        onCameraGesture = onCameraGesture,
        onSelectPlace = onSelectPlace,
        modifier = modifier,
    )
}
