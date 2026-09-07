package com.daengs.app.ui.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import org.junit.Assert.*
import org.junit.Test

class WalkGpsPresentationTest {
    private val sample = LocationSample(GeoPoint(37.5,127.0), 1000, 1_000_000_000L, 3f)
    @Test fun freshAccurateFixIsGoodWithoutASession() {
        assertTrue(walkGpsPresentation(true,true,null,sample,2_000_000_000L).good)
    }
    @Test fun invalidStaleMockOrDeniedFixCannotAppearHealthy() {
        for (fix in listOf(null, sample.copy(elapsedRealtimeNanos = null), sample.copy(elapsedRealtimeNanos = 3_000_000_000L),
            sample.copy(accuracyMeters = null), sample.copy(accuracyMeters = 30f), sample.copy(accuracyMeters = -1f),
            sample.copy(accuracyMeters = Float.NaN), sample.copy(isMock = true))) {
            assertFalse(walkGpsPresentation(true,true,null,fix,2_000_000_000L).good)
        }
        assertFalse(walkGpsPresentation(true,true,null,sample,12_000_000_000L).good)
        assertFalse(walkGpsPresentation(false,false,null,sample,2_000_000_000L).good)
        assertFalse(walkGpsPresentation(true,false,null,sample,2_000_000_000L).good)
        assertFalse(walkGpsPresentation(true,true,"위치 오류",sample,2_000_000_000L).good)
    }
}
