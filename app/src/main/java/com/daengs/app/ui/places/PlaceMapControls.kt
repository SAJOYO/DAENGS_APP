package com.daengs.app.ui.places

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.location.GeoPoint
import com.daengs.app.ui.places.lab.NearbyLocationIcon
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import kotlin.math.*

/** 초기 카메라 배치·자동 이동은 검색 제안이 아니다. 손으로 이동한 뒤 멈춘 좌표만 쓴다. */
internal data class PlaceMapCamera(val point: GeoPoint? = null, val movedByUser: Boolean = false) {
    fun gesture() = copy(point = null, movedByUser = true)
    fun idle(point: GeoPoint) = copy(point = point)
    fun submitted() = copy(movedByUser = false)
    fun searchPoint(origin: GeoPoint?): GeoPoint? = point?.takeIf {
        movedByUser && (origin == null || distanceMeters(it, origin) >= 25)
    }
}

private fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
    val y = Math.toRadians(a.latitude - b.latitude)
    val x = Math.toRadians(a.longitude - b.longitude) * cos(Math.toRadians((a.latitude + b.latitude) / 2))
    return 6_371_000 * sqrt(x * x + y * y)
}

@Composable
internal fun PlaceMapControls(
    canSearchMap: Boolean, onMapSearch: () -> Unit, onDeviceSearch: () -> Unit,
    assistant: @Composable () -> Unit = {},
) {
    Box(Modifier.fillMaxSize().testTag("place-map-controls")) {
        if (canSearchMap) Button(onClick = onMapSearch, modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DaengsColors.BrandPrimary, contentColor = DaengsColors.Surface)) {
            Text("이 주변 검색", fontSize = 13.sp)
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(start = 16.dp, end = 16.dp, bottom = 40.dp)
            .testTag("place-assistant-dock"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = CircleShape, shadowElevation = 2.dp, color = DaengsColors.Surface) {
                IconButton(onClick = onDeviceSearch, modifier = Modifier.size(48.dp)
                    .semantics { contentDescription = "내 주변 검색" }) {
                    NearbyLocationIcon(Modifier.size(22.dp))
                }
            }
            assistant()
        }
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 240)
@Composable
private fun MapControlsPreview() {
    DaengsTheme { PlaceMapControls(true, {}, {}) { PlaceDogAssistant(false, false, false, {}, {}, {}) {} } }
}
