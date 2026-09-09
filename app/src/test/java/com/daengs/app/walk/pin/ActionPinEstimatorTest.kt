package com.daengs.app.walk.pin

import com.daengs.app.location.GeoPoint
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class ActionPinEstimatorTest {
    private val estimator = ActionPinEstimator()
    private val request = pinRequest()

    @Test fun `normal selector preserves original coordinate and captured reference without refinement`() {
        val raw = pinFix(-1, 3.0, 4.0)
        val result = estimator.begin(request, listOf(raw), directFix = raw.ref)
        assertEquals(ActionPinState.RESOLVED, result.state)
        assertEquals(ActionPinMethod.OBSERVED, result.method)
        assertEquals(raw.point, result.point)
        assertEquals(listOf(raw.ref), result.sourceRefs)
        assertEquals(5.0, result.uncertaintyMeters!!, 0.0)
        assertEquals(ActionPinUncertaintyBasis.PROVIDER_ACCURACY, result.uncertaintyBasis)
        assertEquals(PIN_TAP, result.resolveByMillis)
        assertSame(result, estimator.finish(result, listOf(pinFix(2, 100.0)), PIN_TAP + 8_000))
    }

    @Test fun `fallback never silently promotes an accurate sample to observed`() {
        val result = estimator.begin(request, listOf(pinFix(0, 2.0)))
        assertEquals(ActionPinState.PROVISIONAL, result.state)
        assertEquals(ActionPinMethod.LAST_KNOWN, result.method)
        assertNull(result.uncertaintyMeters)
    }

    @Test fun `invalid stale foreign and future direct selections take fallback`() {
        val base = pinFix(0, 0.0)
        val invalid = listOf(
            pinFix(-11, 0.0), pinFix(1, 0.0), base.copy(ownerId = "someone-else"),
            base.copy(sessionId = "another-walk"), base.copy(chainIndex = 1),
            base.copy(isMock = true), base.copy(accuracyMeters = 0f),
            base.copy(accuracyMeters = Float.NaN), base.copy(accuracyMeters = 16f),
            base.copy(point = GeoPoint(91.0, 127.0)),
        )
        invalid.forEach { raw ->
            assertEquals(ActionPinState.PROVISIONAL, estimator.begin(request, listOf(raw), raw.ref).state)
        }
        val boundary = pinFix(-10, 0.0).copy(accuracyMeters = 15f)
        assertEquals(ActionPinMethod.OBSERVED, estimator.begin(request, listOf(boundary), boundary.ref).method)
    }

    @Test fun `walking prediction ignores tap jump and later interpolates at original tap time`() {
        val before = listOf(pinFix(-8, -12.0), pinFix(-6, -9.0), pinFix(-4, -6.0))
        val jump = pinFix(0, 300.0).copy(accuracyMeters = 45f)
        val initial = estimator.begin(request, before + jump)
        assertEquals(ActionPinMethod.ESTIMATED, initial.method)
        assertNear(0.0, 0.0, initial, 0.05)
        assertFalse(initial.sourceRefs.contains(jump.ref))
        val final = estimator.finish(initial, before + jump + pinFix(2, 3.0) + pinFix(4, 6.0), PIN_TAP + 4_000, ActionPinReason.REFINED)
        assertEquals(ActionPinState.RESOLVED, final.state)
        assertEquals(ActionPinReason.REFINED, final.reason)
        assertEquals(request, final.request)
        assertEquals(initial.resolveByMillis, final.resolveByMillis)
        assertNear(0.0, 0.0, final, 0.05)
        assertFalse(final.sourceRefs.contains(jump.ref))
        assertTrue(final.sourceRefs.any { it.atMillis > PIN_TAP })
        assertUnknown(final)
    }

    @Test fun `stationary jitter is estimated without reporting provider accuracy as model error`() {
        val samples = listOf(pinFix(-6, 2.0), pinFix(-4, -1.0), pinFix(-2, 1.0), pinFix(2, -1.0), pinFix(4, 1.0))
        val initial = estimator.begin(request, samples)
        val final = estimator.finish(initial, samples, PIN_TAP + 8_000)
        assertEquals(ActionPinMethod.ESTIMATED, final.method)
        assertNear(0.0, 0.0, final, 1.0)
        assertUnknown(final)
    }

    @Test fun `small isolated tap spike and falsely accurate large jump are screened`() {
        val clean = listOf(-8, -6, -4, -2, 2, 4, 6).map { pinFix(it, it * 1.5) }
        for (jump in listOf(pinFix(0, 10.0), pinFix(0, 300.0))) {
            val samples = clean + jump
            val final = estimator.finish(estimator.begin(request, samples), samples, PIN_TAP + 8_000)
            assertNear(0.0, 0.0, final, 0.05)
            assertFalse(final.sourceRefs.contains(jump.ref))
        }
    }

    @Test fun `poor fix at exact tap cannot override a supported higher quality trajectory`() {
        val clean = listOf(-8, -6, -4, -2, 2, 4, 6).map { pinFix(it, it * 1.5) }
        val rough = pinFix(0, 5.0).copy(accuracyMeters = 45f)
        val final = estimator.finish(estimator.begin(request, clean + rough), clean + rough, PIN_TAP + 8_000)
        assertNear(0.0, 0.0, final, 0.05)
        assertFalse(final.sourceRefs.contains(rough.ref))
    }

    @Test fun `corner uses nearest bracket instead of projecting the old heading through the turn`() {
        val samples = listOf(pinFix(-6, -6.0), pinFix(-4, -4.0), pinFix(-2, -2.0), pinFix(2, 0.0, 2.0), pinFix(4, 0.0, 4.0))
        val final = estimator.finish(estimator.begin(request, samples), samples, PIN_TAP + 8_000)
        assertNear(0.0, 0.0, final, 1.5)
        assertEquals(ActionPinMethod.ESTIMATED, final.method)
    }

    @Test fun `missing future observations keep initial prediction exactly`() {
        val history = listOf(pinFix(-8, -12.0), pinFix(-6, -9.0), pinFix(-4, -6.0))
        val initial = estimator.begin(request, history)
        val final = estimator.finish(initial, emptyList(), PIN_TAP + 8_000)
        assertEquals(ActionPinState.RESOLVED, final.state)
        assertEquals(initial.point, final.point)
        assertEquals(initial.sourceRefs, final.sourceRefs)
        assertEquals(initial.method, final.method)
        assertEquals(ActionPinReason.DEADLINE, final.reason)
    }

    @Test fun `old or poor coordinates still produce last known and preserve their true age`() {
        listOf(pinFix(-90, -100.0), pinFix(-1, 10.0).copy(accuracyMeters = 500f)).forEach { raw ->
            val final = estimator.finish(estimator.begin(request, listOf(raw)), emptyList(), PIN_TAP + 8_000)
            assertEquals(ActionPinMethod.LAST_KNOWN, final.method)
            assertEquals(raw.point, final.point)
            assertEquals(listOf(raw.ref), final.sourceRefs)
            assertUnknown(final)
        }
    }

    @Test fun `prediction horizon expires to a real historical coordinate`() {
        val samples = listOf(pinFix(-12, -18.0), pinFix(-9, -13.5), pinFix(-6, -9.0))
        val initial = estimator.begin(request, samples)
        assertEquals(ActionPinMethod.LAST_KNOWN, initial.method)
        assertEquals(samples.last().point, initial.point)
    }

    @Test fun `no raw coordinate keeps a provisional action then finishes unlocated`() {
        val initial = estimator.begin(request, emptyList())
        assertEquals(ActionPinState.PROVISIONAL, initial.state)
        assertNull(initial.point)
        val final = estimator.finish(initial, emptyList(), PIN_TAP + 8_000)
        assertEquals(ActionPinState.UNLOCATED, final.state)
        assertEquals(ActionPinMethod.NONE, final.method)
        assertEquals(request, final.request)
        assertTrue(final.sourceRefs.isEmpty())
        assertUnknown(final)
    }

    @Test fun `future only motion back estimates tap instead of changing timestamp`() {
        val samples = listOf(pinFix(2, 3.0), pinFix(4, 6.0), pinFix(6, 9.0))
        val initial = estimator.begin(request, samples)
        assertNull(initial.point)
        val final = estimator.finish(initial, samples, PIN_TAP + 8_000)
        assertNear(0.0, 0.0, final, 0.05)
        assertEquals(ActionPinMethod.ESTIMATED, final.method)
        assertEquals(PIN_TAP, final.request.targetAtMillis)
        assertTrue(final.sourceRefs.all { it.atMillis > PIN_TAP })
    }

    @Test fun `single future fix is explicitly a zero motion estimate not observed or last known`() {
        val initial = estimator.begin(request, emptyList())
        val future = pinFix(3, 4.0).copy(accuracyMeters = null)
        assertSame(initial, estimator.finish(initial, listOf(future), PIN_TAP + 3_000, ActionPinReason.REFINED))
        val final = estimator.finish(initial, listOf(future), PIN_TAP + 8_000)
        assertEquals(future.point, final.point)
        assertEquals(ActionPinMethod.ESTIMATED, final.method)
        assertUnknown(final)
    }

    @Test fun `an isolated future jump cannot displace existing position`() {
        val raw = pinFix(-1, 1.0)
        val initial = estimator.begin(request, listOf(raw))
        val final = estimator.finish(initial, listOf(raw, pinFix(2, 400.0)), PIN_TAP + 8_000)
        assertEquals(initial.point, final.point)
        assertEquals(initial.sourceRefs, final.sourceRefs)
    }

    @Test fun `deadline recovery ignores samples collected after original window`() {
        val initial = estimator.begin(request, emptyList())
        val final = estimator.finish(initial, listOf(pinFix(9, 0.0)), PIN_TAP + 50_000, ActionPinReason.RECOVERED)
        assertEquals(ActionPinState.UNLOCATED, final.state)
        assertEquals(initial.resolveByMillis, final.resolveByMillis)
        val atDeadline = pinFix(8, 1.0)
        val onTime = estimator.finish(initial, listOf(atDeadline), PIN_TAP + 50_000, ActionPinReason.RECOVERED)
        assertEquals(atDeadline.point, onTime.point)
    }

    @Test fun `computed time and session cutoff each fence observation availability`() {
        val initial = estimator.begin(request, emptyList())
        val samples = listOf(pinFix(3, 3.0), pinFix(4, 4.0), pinFix(6, 6.0))
        assertNull(estimator.finish(initial, samples, PIN_TAP + 2_000, ActionPinReason.SESSION_ENDED).point)
        val recovered = estimator.finish(initial, samples, PIN_TAP + 90_000, ActionPinReason.RECOVERED, PIN_TAP + 2_000)
        assertNull(recovered.point)
    }

    @Test fun `late begin does not consume post tap observations`() {
        val initial = estimator.begin(request, listOf(pinFix(2, 2.0)), computedAtMillis = PIN_TAP + 20_000)
        assertNull(initial.point)
        assertEquals(PIN_TAP + 8_000, initial.resolveByMillis)
    }

    @Test fun `failure retains estimate and session ending closes without waiting`() {
        val initial = estimator.begin(request, listOf(pinFix(-1, 0.0)))
        for (reason in listOf(ActionPinReason.ESTIMATOR_FAILED, ActionPinReason.SESSION_ENDED, ActionPinReason.RECOVERED)) {
            val final = estimator.finish(initial, emptyList(), PIN_TAP + 1_000, reason)
            assertEquals(ActionPinState.RESOLVED, final.state)
            assertEquals(initial.point, final.point)
            assertEquals(reason, final.reason)
        }
    }

    @Test fun `terminal result cannot reopen or move on repeated completion`() {
        val initial = estimator.begin(request, emptyList())
        val final = estimator.finish(initial, emptyList(), PIN_TAP + 8_000)
        assertSame(final, estimator.finish(final, listOf(pinFix(4, 100.0)), PIN_TAP + 10_000))
    }

    @Test fun `owner session and pause boundaries do not contribute to a motion fit`() {
        val foreign = listOf(
            pinFix(-4, -4.0).copy(ownerId = "other"),
            pinFix(-2, -2.0).copy(sessionId = "other"),
            pinFix(2, 2.0).copy(chainIndex = 1),
        )
        assertNull(estimator.finish(estimator.begin(request, foreign), foreign, PIN_TAP + 8_000).point)
        val resumed = request.copy(chainIndex = 1)
        val prior = listOf(pinFix(-8, -8.0), pinFix(-6, -6.0), pinFix(-4, -4.0))
        val initial = estimator.begin(resumed, prior)
        assertEquals(ActionPinMethod.LAST_KNOWN, initial.method)
        val after = pinFix(2, 200.0).copy(chainIndex = 1)
        val final = estimator.finish(initial, prior + after, PIN_TAP + 8_000)
        assertEquals(prior.last().point, final.point)
    }

    @Test fun `invalid geometry mock and negative identities never become evidence`() {
        val raw = pinFix(-1, 1.0)
        val samples = listOf(
            raw.copy(point = GeoPoint(Double.NaN, 0.0)), raw.copy(clientSeq = -1),
            raw.copy(clientSeq = 1, point = GeoPoint(0.0, Double.POSITIVE_INFINITY)),
            raw.copy(clientSeq = 2, point = GeoPoint(-91.0, 0.0)),
            raw.copy(clientSeq = 3, point = GeoPoint(0.0, 181.0)),
            raw.copy(clientSeq = 4, chainIndex = -1), raw.copy(clientSeq = 5, isMock = true),
            raw.copy(clientSeq = 6, atMillis = -1),
        )
        val final = estimator.finish(estimator.begin(request, samples), samples, PIN_TAP + 8_000)
        assertEquals(ActionPinState.UNLOCATED, final.state)
    }

    @Test fun `missing or invalid accuracy permits coordinate retention but never a quality claim`() {
        for (accuracy in listOf(null, -1f, 0f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val raw = pinFix(-1, 1.0).copy(accuracyMeters = accuracy)
            val result = estimator.begin(request, listOf(raw), raw.ref)
            assertEquals(ActionPinMethod.LAST_KNOWN, result.method)
            assertEquals(raw.point, result.point)
            assertUnknown(result)
        }
    }

    @Test fun `duplicates are idempotent conflicts discarded and input order irrelevant`() {
        val good = listOf(pinFix(-8, -8.0), pinFix(-6, -6.0), pinFix(-4, -4.0))
        val conflicted = pinFix(-1, 500.0)
        val samples = good + good + conflicted + conflicted.copy(point = pinPoint(-500.0))
        val result = estimator.begin(request, samples.reversed())
        val expected = estimator.begin(request, good)
        assertEquals(expected.point, result.point)
        assertEquals(expected.sourceRefs, result.sourceRefs)
        assertEquals(result.sourceRefs.size, result.sourceRefs.distinct().size)
    }

    @Test fun `multiple samples at the same instant cannot masquerade as movement support`() {
        val samples = (0..30).map { pinFix(-2, it.toDouble()).copy(clientSeq = it) }
        assertEquals(ActionPinMethod.LAST_KNOWN, estimator.begin(request, samples).method)
    }

    @Test fun `each tap has independent identity and no ten action cap`() {
        val samples = listOf(pinFix(-1, 1.0))
        val results = (0..20).map { estimator.begin(request.copy(resolutionId = UUID.randomUUID()), samples) }
        assertEquals(21, results.map { it.request.resolutionId }.distinct().size)
        assertTrue(results.all { it.point != null })
    }

    @Test fun `input list mutation cannot change saved result or its references`() {
        val samples = mutableListOf(pinFix(-1, 1.0))
        val result = estimator.begin(request, samples)
        samples.clear()
        assertEquals(1, result.sourceRefs.size)
        assertThrows(UnsupportedOperationException::class.java) {
            (result.sourceRefs as MutableList<ActionPinSourceRef>).clear()
        }
    }

    @Test fun `invalid lifecycle calls fail explicitly instead of silently extending the window`() {
        val initial = estimator.begin(request, emptyList())
        assertThrows(IllegalArgumentException::class.java) { estimator.finish(initial, emptyList(), PIN_TAP + 1_000) }
        assertThrows(IllegalArgumentException::class.java) { estimator.finish(initial, emptyList(), PIN_TAP - 1, ActionPinReason.RECOVERED) }
        assertThrows(IllegalArgumentException::class.java) { estimator.finish(initial, emptyList(), PIN_TAP, ActionPinReason.DIRECT_FIX) }
        assertThrows(IllegalArgumentException::class.java) { estimator.begin(request, emptyList(), computedAtMillis = PIN_TAP - 1) }
        assertThrows(IllegalArgumentException::class.java) { request.copy(targetAtMillis = Long.MAX_VALUE) }
    }

    @Test fun `longitude crossing and polar coordinates stay finite and local`() {
        for (origin in listOf(GeoPoint(0.0, 179.99999), GeoPoint(89.99999, 30.0))) {
            val samples = listOf(-6, -4, -2, 2, 4).map { t ->
                val longitude = (origin.longitude + t * 0.000005 + 540.0) % 360.0 - 180.0
                pinFix(t, 0.0).copy(point = GeoPoint(origin.latitude, longitude))
            }
            val final = estimator.finish(estimator.begin(request, samples), samples, PIN_TAP + 8_000)
            assertTrue(final.point!!.isUsablePinPoint())
            assertTrue(pinDistance(origin, final.point) < 1.0)
        }
    }

    private fun assertUnknown(result: ActionPinResolution) {
        assertNull(result.uncertaintyMeters)
        assertEquals(ActionPinUncertaintyBasis.UNKNOWN, result.uncertaintyBasis)
    }
}

internal const val PIN_TAP = 1_700_000_100_000L
internal fun pinRequest() = ActionPinRequest(UUID.fromString("00000000-0000-4000-8000-000000000001"), "owner", "walk", 0, PIN_TAP)
internal fun pinPoint(east: Double, north: Double = 0.0) = GeoPoint(
    Math.toDegrees(north / 6_371_008.8), Math.toDegrees(east / 6_371_008.8),
)
internal fun pinFix(seconds: Int, east: Double, north: Double = 0.0) = ActionPinObservation(
    "owner", "walk", seconds + 100, 0, PIN_TAP + seconds * 1_000L, pinPoint(east, north), 5f,
)
internal fun assertNear(east: Double, north: Double, result: ActionPinResolution, tolerance: Double) {
    assertNotNull(result.point)
    val error = pinDistance(pinPoint(east, north), result.point!!)
    assertTrue("error=$error m, expected <= $tolerance m", error <= tolerance)
}
