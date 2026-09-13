package com.daengs.app.map.provider.naver

import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.MultipartPathOverlay
import com.naver.maps.map.overlay.PathOverlay
import org.junit.Assert.assertTrue
import org.junit.Test

class NaverWalkLayerOrderTest {
    @Test fun `composed sheets are below both explicit routes and SDK path defaults`() {
        // Regresses the former -100: it covered paths whose SDK default is -100000.
        assertTrue(NaverWalkLayerOrder.TRACE_SHEETS < NaverWalkLayerOrder.ROUTE)
        assertTrue(NaverWalkLayerOrder.TRACE_SHEETS < PathOverlay.DEFAULT_GLOBAL_Z_INDEX)
        assertTrue(NaverWalkLayerOrder.TRACE_SHEETS < MultipartPathOverlay.DEFAULT_GLOBAL_Z_INDEX)
    }

    @Test fun `route remains beneath map labels and endpoint markers`() {
        assertTrue(NaverWalkLayerOrder.ROUTE < 0)
        assertTrue(NaverWalkLayerOrder.ROUTE < Marker.DEFAULT_GLOBAL_Z_INDEX)
    }
}
