package com.daengs.app.map.provider.naver

import android.graphics.PointF
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.daengs.app.R
import com.daengs.app.location.GeoPoint
import com.daengs.app.location.TravelHeading
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.LocationOverlay
import com.naver.maps.map.overlay.OverlayImage
import kotlinx.coroutines.delay

/** A separate marker lets course rotate without rotating the dog's portrait. */
@Composable
internal fun NaverTravelHeadingLayer(map: NaverMap?, point: GeoPoint?, heading: TravelHeading?) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val arrow = remember(map) {
        Marker().apply {
            icon = OverlayImage.fromResource(R.drawable.ic_walk_travel_heading)
            width = 180
            height = 180
            anchor = PointF(0.5f, 0.5f)
            // Billboard plus camera-relative angle keeps the arrow outside the 96px portrait,
            // even when the map is tilted. The camera itself is never changed here.
            isFlat = false
            isForceShowIcon = true
            isHideCollidedMarkers = false
            isHideCollidedSymbols = false
            globalZIndex = LocationOverlay.DEFAULT_GLOBAL_Z_INDEX - 1
        }
    }
    DisposableEffect(map, arrow, heading) {
        val listener = NaverMap.OnCameraChangeListener { _, _ ->
            arrow.angle = screenTravelBearing(heading?.degrees ?: 0f, map?.cameraPosition?.bearing ?: 0.0)
        }
        map?.addOnCameraChangeListener(listener)
        onDispose {
            map?.removeOnCameraChangeListener(listener)
            arrow.map = null
        }
    }
    LaunchedEffect(map, arrow, point, heading, lifecycle) {
        if (map == null || point == null || heading == null) {
            arrow.map = null
            return@LaunchedEffect
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                val remaining = heading.remainingMillis(SystemClock.elapsedRealtimeNanos())
                if (remaining > 0) {
                    arrow.position = com.naver.maps.geometry.LatLng(point.latitude, point.longitude)
                    arrow.angle = screenTravelBearing(heading.degrees, map.cameraPosition.bearing)
                    arrow.map = map
                    delay(remaining)
                }
            } finally {
                // Also remove on navigation away, backgrounding, or signal timeout.
                arrow.map = null
            }
        }
    }
}

internal fun screenTravelBearing(course: Float, cameraBearing: Double): Float =
    (((course - cameraBearing) % 360 + 360) % 360).toFloat()

@Preview(showBackground = true)
@Composable
private fun TravelHeadingArrowPreview() {
    Image(painterResource(R.drawable.ic_walk_travel_heading), "이동 방향", Modifier.size(64.dp))
}
