package com.daengs.app.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import org.junit.Assert.*
import org.junit.Test

class WalkHistoryThumbnailTest {
    @Test fun `long route retains its start corners end and gaps without mutating the source`() {
        val path = (0..6000).map { i -> LocationSample(GeoPoint(37.5 + i * .00003, 127.0), i * 3000L) }
        val detour = listOf(LocationSample(GeoPoint(37.8,127.0), 20_000_000),
            LocationSample(GeoPoint(37.81,127.01),20_001_000), LocationSample(GeoPoint(37.8,127.02),20_002_000))
        val summary = WalkSummary("s",emptyList(),0,30_000_000,null,10000.0,20_000_000,
            listOf(path,detour),path.first().point)
        val thumbnail = summary.forHistoryThumbnail()
        assertEquals(2,thumbnail.segments.size)
        assertEquals(path.first(),thumbnail.segments.first().first())
        assertEquals(path.last(),thumbnail.segments.first().last())
        assertEquals(detour,thumbnail.segments.last())
        assertTrue(thumbnail.segments.first().size<20)
        assertEquals(6001,summary.segments.first().size)
        assertEquals(summary.distanceMeters,thumbnail.distanceMeters,0.0)
    }
}
