package com.daengs.app.map.provider.naver

import com.daengs.app.map.shell.WalkLayerStack
import org.junit.Assert.*
import org.junit.Test

class WalkMapDiagnosticsTest {
    @Test fun `late sheets are applied below already attached routes and markers`() {
        val stack = NaverWalkLayerOrder.resolve(WalkLayerStack.RECORDS)
        val actual = mutableMapOf<String, Int>()
        // Simulate arbitrary async attachment order at the same setter/readback adapter used by SDK layers.
        listOf("route" to stack.route, "marker" to stack.markers, "sheet" to stack.traces).forEach { (id, z) ->
            assertEquals(z, applyNativeWalkOrder(z, { actual[id] = it }, { actual.getValue(id) }))
        }
        assertTrue(actual.getValue("sheet") < actual.getValue("route"))
        assertTrue(actual.getValue("route") < actual.getValue("marker"))
        assertEquals(17, applyNativeWalkOrder(stack.route, {}, { 17 })) // Readback is never the requested value by assumption.
    }

    @Test fun `removed overlays do not survive in debug readings`() {
        val diagnostics = WalkMapDiagnostics()
        val token = Any()
        diagnostics.attached(token, NativeWalkLayerReading("동선", -100000, "pink"))
        diagnostics.attached(token, NativeWalkLayerReading("동선", -100000, "blue"))
        assertEquals("blue", diagnostics.overlays.values.single().description)
        diagnostics.detached(token)
        diagnostics.detached(token)
        assertTrue(diagnostics.overlays.isEmpty())
    }
}
