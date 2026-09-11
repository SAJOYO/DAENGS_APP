package com.daengs.app.ui.game.owned

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.shell.MapCameraSnapshot
import com.daengs.app.map.shell.MapHost
import com.daengs.app.map.shell.MapScene
import com.daengs.app.ui.theme.*

internal data class OwnedMapPresentation(
    val scene: MapScene, val centerOn: GeoPoint?, val requestKey: Int,
    val camera: MapCameraSnapshot?,
)

@Composable
internal fun OwnedTerritoryMap(value: OwnedMapPresentation, onSelect: (String?) -> Unit, onCamera: (MapCameraSnapshot) -> Unit) {
    if (LocalInspectionMode.current) {
        Box(Modifier.fillMaxSize().background(PinkSoft), contentAlignment = Alignment.Center) {
            Text("내 점령지 지도 · ${value.scene.territorySites.size}곳", color = TextDark)
        }
    } else MapHost(
        scene = value.scene, searchOrigin = null, followDevice = false,
        centerOn = value.centerOn, cameraRequestKey = value.requestKey,
        fitBounds = value.scene.territorySites.map { it.point }.takeIf { value.centerOn == null && value.camera == null },
        keepSelectionVisible = true,
        onCameraIdle = {}, onCameraGesture = {}, onSelectPlace = {},
        onSelectTerritorySite = { onSelect(it) }, onMapTap = { onSelect(null) }, modifier = Modifier.fillMaxSize(),
        initialCamera = value.camera, onCameraSnapshot = onCamera,
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 420)
@Composable
private fun OwnedTerritoryMapPreview() = DaengsTheme {
    OwnedTerritoryMap(OwnedMapPresentation(ownedTerritoryScene(listOf(previewOwnedSite()), null), null, 0, null), {}, {})
}
