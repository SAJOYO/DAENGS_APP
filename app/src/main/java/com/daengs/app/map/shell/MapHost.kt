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
    leftPaddingPx: Int = 0,
    topPaddingPx: Int = 0,
    rightPaddingPx: Int = 0,
    centerOn: GeoPoint? = null,
    centerZoom: Double? = null,
    /** Lower bound for an explicit selection; keeps a user's closer zoom. */
    centerMinZoom: Double? = null,
    /** Explicit camera intent; repeated selection of the same coordinate is also an event. */
    cameraRequestKey: Int = 0,
    centerYFraction: Float = .5f,
    /** Reframe on actual map size changes, not on an overlay's drag position. */
    keepSelectionVisible: Boolean = false,
    fitBounds: List<GeoPoint>? = null,
    onCameraIdle: (GeoPoint) -> Unit,
    onCameraGesture: () -> Unit,
    onSelectPlace: (String) -> Unit,
    onSelectTerritorySite: (String) -> Unit = {},
    onSelectMoment: (String) -> Unit = {},
    onSelectRouteEndpoint: (String) -> Unit = {},
    onMapTap: (GeoPoint) -> Unit = {},
    modifier: Modifier = Modifier,
    /** Only read when the map is created; later snapshots must not drive the camera. */
    initialCamera: MapCameraSnapshot? = null,
    onCameraSnapshot: ((MapCameraSnapshot) -> Unit)? = null,
    onRouteDirectionCount: (Int) -> Unit = {},
    onSelectRecordContext: (String) -> Unit = {},
) {
    NaverMapSurface(
        scene = scene,
        searchOrigin = searchOrigin,
        followDevice = followDevice,
        avatarRes = avatarRes,
        avatarPhoto = avatarPhoto,
        bottomPaddingPx = bottomPaddingPx,
        leftPaddingPx = leftPaddingPx, topPaddingPx = topPaddingPx, rightPaddingPx = rightPaddingPx,
        centerOn = centerOn,
        centerZoom = centerZoom,
        centerMinZoom = centerMinZoom,
        cameraRequestKey = cameraRequestKey,
        centerYFraction = centerYFraction,
        keepSelectionVisible = keepSelectionVisible,
        fitBounds = fitBounds,
        onCameraIdle = onCameraIdle,
        onCameraGesture = onCameraGesture,
        onSelectPlace = onSelectPlace,
        onSelectTerritorySite = onSelectTerritorySite,
        onSelectMoment = onSelectMoment,
        onSelectRouteEndpoint = onSelectRouteEndpoint,
        onMapTap = onMapTap,
        modifier = modifier,
        initialCamera = initialCamera,
        onCameraSnapshot = onCameraSnapshot,
        onRouteDirectionCount = onRouteDirectionCount,
        onSelectRecordContext = onSelectRecordContext,
    )
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun SessionMapHostPreview() {
    MapHost(MapScene(sessionExplorer = com.daengs.app.map.layers.completedroute.SessionRouteExplorerLayerState()),
        searchOrigin = null, followDevice = false, onCameraIdle = {}, onCameraGesture = {}, onSelectPlace = {})
}
