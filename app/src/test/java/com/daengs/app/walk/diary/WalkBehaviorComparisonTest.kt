package com.daengs.app.walk.diary

import com.daengs.app.map.layers.spatial.mapBounds
import com.daengs.app.map.layers.spatial.paintCells
import com.daengs.app.map.layers.spatial.spatialDiaryAlpha
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.support.behaviorComparisonFixture
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WalkBehaviorComparisonTest {
    @Test fun `entry identity is scoped to its walk`() {
        val view = WalkBehaviorComparison.parse(behaviorComparisonFixture())
        val first = view.evidence.first()
        val second = first.copy(walkId = view.baseline.walkIds.last())
        val both = view.copy(matching = view.baseline, evidence = listOf(first, second),
            summary = view.summary.copy(entryCount = 2, unlocatedEntryCount = 0))
        assertNotEquals(both.evidence[0].key, both.evidence[1].key)
        assertTrue(runCatching { both.copy(evidence = listOf(first, first)) }.isFailure)
    }
    @Test fun `server fixture preserves a distinct subset and unlocated behavior evidence`() {
        val view = WalkBehaviorComparison.parse(behaviorComparisonFixture())
        assertTrue(view.baseline.walkIds.containsAll(view.matching.walkIds))
        assertTrue(view.baseline.walkIds.size > view.matching.walkIds.size)
        assertTrue(view.summary.unlocatedEntryCount > 0)
        assertEquals(view.evidence.size, view.summary.entryCount)
        assertEquals(view.matching.walkIds.size.toDouble(), view.matching.field.denominator, 0.0)
        assertEquals(view.query.toJson().getJSONObject("walk_selector").getString("pet_id"), view.evidence.first().content.petId)
    }

    @Test fun `missing displayed pin never reuses the immutable original GPS`() {
        val json = behaviorComparisonFixture().getJSONArray("evidence").getJSONObject(0)
            .put("pin", JSONObject.NULL)
            .put("location", JSONObject().put("lat", 37.5).put("lng", 127.0)
                .put("captured_at", "2026-09-01T00:00:00Z").put("accuracy_m", 5))
        val evidence = BehaviorComparisonEvidence.parse(json)
        assertNotNull(evidence.content.point)
        assertNull(evidence.point)
        assertEquals("위치 없이 남긴 행동", evidence.locationLabel)
    }

    @Test fun `subset outside the baseline and inconsistent denominator are rejected`() {
        val json = behaviorComparisonFixture()
        json.getJSONObject("matching").getJSONArray("walk_ids").put(0, "unrelated-walk")
        assertTrue(runCatching { WalkBehaviorComparison.parse(json) }.isFailure)
        val wrongDenominator = behaviorComparisonFixture()
        wrongDenominator.getJSONObject("matching").getJSONObject("field").put("denominator", 100)
        assertTrue(runCatching { WalkBehaviorComparison.parse(wrongDenominator) }.isFailure)
    }

    @Test fun `duplicate walks and notes cannot silently enter a comparison`() {
        val json = behaviorComparisonFixture()
        val ids = json.getJSONObject("baseline").getJSONArray("walk_ids")
        ids.put(ids.getString(0))
        assertTrue(runCatching { WalkBehaviorComparison.parse(json) }.isFailure)
        assertTrue(runCatching { WalkBehaviorComparisonQuery(SpatialDiaryQuery("pet"), WalkMomentType.NOTE) }.isFailure)
    }

    @Test fun `empty and identical subsets keep one baseline viewport and fixed paint scale`() {
        val view = WalkBehaviorComparison.parse(behaviorComparisonFixture())
        val empty = view.copy(matching = BehaviorWalkGroup(emptyList(), view.matching.field.copy(denominator = 0.0, cells = emptyList())),
            evidence = emptyList(), summary = view.summary.copy(entryCount = 0, recordedDayCount = 0, unlocatedEntryCount = 0))
        assertEquals(view.mapBounds(), empty.mapBounds())
        val field = view.baseline.paintCells(view.projection.radiusU)
        assertEquals(view.baseline.field.cells.size, field.size)
        assertEquals(spatialDiaryAlpha(0.001), 0.36f, 0.0001f)
        assertEquals(0f, spatialDiaryAlpha(0.0), 0.0f)
        assertTrue(spatialDiaryAlpha(0.1) > spatialDiaryAlpha(0.001))
        assertEquals(view.baseline.paintCells(view.projection.radiusU), empty.baseline.paintCells(empty.projection.radiusU))
    }
}
