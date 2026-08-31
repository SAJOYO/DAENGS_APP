package com.daengs.app.map.layers.trail

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.map.shell.MapScene
import com.daengs.app.walk.TrailSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailLayerStateTest {
    @Test
    fun `an empty snapshot produces no map paths`() {
        assertTrue(TrailSnapshot().toTrailLayerState().paths.isEmpty())
    }

    @Test
    fun `a pending one point segment is preserved`() {
        val point = GeoPoint(37.5665, 126.9780)
        val snapshot = TrailSnapshot(segments = listOf(listOf(sample(point, 1L))))

        assertEquals(listOf(listOf(point)), snapshot.toTrailLayerState().paths)
    }

    @Test
    fun `separate recording segments remain separate map paths`() {
        val first = GeoPoint(37.5665, 126.9780)
        val second = GeoPoint(37.5667, 126.9782)
        val resumed = GeoPoint(37.5670, 126.9785)
        val snapshot = TrailSnapshot(
            segments = listOf(
                listOf(sample(first, 1L), sample(second, 2L)),
                listOf(sample(resumed, 3L)),
            ),
        )

        assertEquals(
            listOf(listOf(first, second), listOf(resumed)),
            snapshot.toTrailLayerState().paths,
        )
    }

    @Test
    fun `map scene has no trail until a caller supplies one`() {
        assertEquals(TrailLayerState(), MapScene().trail)
    }

    private fun sample(point: GeoPoint, capturedAtMillis: Long) = LocationSample(
        point = point,
        capturedAtMillis = capturedAtMillis,
        accuracyMeters = 5f,
    )
}
