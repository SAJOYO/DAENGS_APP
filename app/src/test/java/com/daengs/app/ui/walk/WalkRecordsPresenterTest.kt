package com.daengs.app.ui.walk

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.daengs.app.map.features.records.*
import com.daengs.app.map.layers.traces.WalkTraceSheet
import com.daengs.app.ui.walk.records.*
import com.daengs.app.walk.WalkSummary
import com.daengs.app.walk.diary.SpatialDiaryCellId
import com.daengs.app.walk.records.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WalkRecordsPresenterTest {
    @get:Rule val compose = createComposeRule()
    private val initial = WalkRecord(WalkSummary("one", listOf("dog"), 0L, 6000L, null, 2.0, 6000L, emptyList(), null),
        trace = WalkTraceSheet("one", cells = setOf(SpatialDiaryCellId(0, 0))))

    @Test fun `trace enrichment and density changes reuse original route and prepared geometry`() {
        val input = mutableStateOf(initial)
        val policy = mutableStateOf(DefaultRecordsTracePolicy)
        val reads = AtomicInteger()
        val source = object : WalkRecordsSource {
            override suspend fun select(query: WalkRecordsQuery) = WalkRecordsSelection(query, listOf(initial))
            override suspend fun loadRoute(record: WalkRecord): WalkSummary { reads.incrementAndGet(); return record.summary }
        }
        var route: RecordsRoutePresentation? = null
        var traces: RecordsTracePresentation? = null
        compose.setContent {
            val selected = remember(input.value) { WalkRecordsSelection(WalkRecordsQuery(), listOf(input.value)) }
            val currentRoute = rememberWalkRecordsRoute(input.value, source)
            val currentTraces = rememberWalkRecordsTraces(selected, true, TraceView.All, emptySet(), policy.value)
            SideEffect { route = currentRoute; traces = currentTraces }
            androidx.compose.foundation.text.BasicText("${currentRoute.summary?.sessionId}:${currentTraces.tiles?.size}")
        }
        compose.waitUntil(10000) { traces?.tiles != null && route?.summary != null }
        assertEquals(0x7263B6, policy.value.rgb)
        assertTrue(traces!!.tiles!!.all { tile -> tile.rgb!!.all { it == 0x7263B6 } })
        val prepared = traces!!.prepared
        compose.runOnIdle { policy.value = policy.value.copy(density = TraceDensityScale(listOf(TraceDensityBand(1, .01f)))) }
        compose.waitForIdle()
        compose.waitUntil(10000) { traces?.tiles != null && traces!!.tiles!!.all { tile -> tile.alpha.all { it <= .010001f } } }
        assertSame(prepared, traces!!.prepared)
        assertEquals(1, reads.get())
        compose.runOnIdle { input.value = input.value.copy(traceState = WalkTraceState.READY) }
        compose.waitForIdle()
        assertEquals(1, reads.get())
        assertEquals(initial.summary, route!!.summary)
    }

    @Test fun `wrong session response is rejected and retry reloads only the selected route`() {
        val reads = AtomicInteger()
        val source = object : WalkRecordsSource {
            override suspend fun select(query: WalkRecordsQuery) = WalkRecordsSelection(query, listOf(initial))
            override suspend fun loadRoute(record: WalkRecord): WalkSummary =
                if (reads.incrementAndGet() == 1) record.summary.copy(sessionId = "stale") else record.summary
        }
        var route: RecordsRoutePresentation? = null
        compose.setContent {
            val current = rememberWalkRecordsRoute(initial, source)
            SideEffect { route = current }
            androidx.compose.foundation.text.BasicText("${current.summary?.sessionId}:${current.error}")
        }
        compose.waitUntil(10000) { route?.error != null }
        assertNull(route!!.summary)
        compose.runOnIdle { route!!.retry() }
        compose.waitForIdle()
        compose.waitUntil(10000) { route?.summary != null }
        assertEquals("one", route!!.summary!!.sessionId)
        assertNull(route!!.error)
        assertEquals(2, reads.get())
    }
}
