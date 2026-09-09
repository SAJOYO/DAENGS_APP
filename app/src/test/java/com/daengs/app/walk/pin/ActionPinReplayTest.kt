package com.daengs.app.walk.pin

import java.util.Random
import org.junit.Assert.*
import org.junit.Test

/** Synthetic known truth, not a claim about phone/GPS accuracy. No network or device fixtures. */
class ActionPinReplayTest {
    @Test fun `replay stationary walking corner and stop against last clean coordinate baseline`() {
        val cases = linkedMapOf<String, (Int) -> Pair<Double, Double>>(
            "stationary" to { _ -> 0.0 to 0.0 },
            "walking" to { t -> 1.5 * t to 0.0 },
            "corner" to { t -> if (t <= 0) t.toDouble() to 0.0 else 0.0 to t.toDouble() },
            "stop_at_tap" to { t -> if (t <= 0) 1.5 * t to 0.0 else 0.0 to 0.0 },
        )
        cases.forEach { (name, truth) ->
            val initialErrors = mutableListOf<Double>()
            val finalErrors = mutableListOf<Double>()
            val baselineErrors = mutableListOf<Double>()
            repeat(100) { seed ->
                val random = Random(seed.toLong())
                val samples = listOf(-8, -6, -4, -2, 2, 4, 6, 8).map { t ->
                    val (x, y) = truth(t)
                    pinFix(t, x + (random.nextDouble() - 0.5) * 2, y + (random.nextDouble() - 0.5) * 2)
                } + pinFix(0, 300.0, 200.0).copy(accuracyMeters = 45f)
                val estimator = ActionPinEstimator()
                val initial = estimator.begin(pinRequest(), samples)
                val final = estimator.finish(initial, samples, PIN_TAP + 8_000)
                val baseline = samples.last { it.atMillis < PIN_TAP }
                initialErrors.add(pinDistance(pinPoint(0.0), initial.point!!))
                finalErrors.add(pinDistance(pinPoint(0.0), final.point!!))
                baselineErrors.add(pinDistance(pinPoint(0.0), baseline.point))
                assertEquals(ActionPinState.RESOLVED, final.state)
                assertEquals(PIN_TAP, final.request.targetAtMillis)
                assertFalse(final.sourceRefs.contains(samples.last().ref))
            }
            val sorted = finalErrors.sorted()
            println("PIN_REPLAY $name n=100 baseline_mean_m=${baselineErrors.average()} initial_mean_m=${initialErrors.average()} final_mean_m=${finalErrors.average()} final_p95_m=${sorted[94]} final_max_m=${sorted.last()}")
            assertTrue("$name must improve mean error over last clean fix", finalErrors.average() < baselineErrors.average())
            assertTrue("$name max error=${sorted.last()}", sorted.last() < 3.0)
        }
    }

    @Test fun `plausible sustained GPS bias remains unknowable without independent evidence`() {
        val samples = listOf(-8, -6, -4, -2, 2, 4, 6, 8).map { pinFix(it, 1.5 * it, 30.0) }
        val estimator = ActionPinEstimator()
        val final = estimator.finish(estimator.begin(pinRequest(), samples), samples, PIN_TAP + 8_000)
        val actualError = pinDistance(pinPoint(0.0), final.point!!)
        assertTrue(actualError > 29.0)
        assertEquals(ActionPinUncertaintyBasis.UNKNOWN, final.uncertaintyBasis)
        println("PIN_REPLAY sustained_bias actual_error_m=$actualError uncertainty=unknown")
    }
}
