package com.daengs.app.walk.routeexplorer

import com.daengs.app.walk.*
import com.daengs.app.walk.trajectory.*
import org.junit.Assert.*
import org.junit.Test

class MeasurementObservedReviewTest {
    @Test fun `final ownership separates walking excluded unresolved and gap without adding distance`() {
        val detail = measuredObservedDetail()
        val review = CompletedRouteReview(detail)
        val parts = review.observed.sections
        assertTrue(review.observed.matches(detail))
        assertFalse(review.observed.matches(detail.copy()))
        assertEquals(listOf(2..4, 4..6, 7..8, 8..10, 10..11), parts.map { it.fixes.first().clientSeq..it.fixes.last().clientSeq })
        assertEquals(listOf(LegacyWalkingUse.EXCLUDED, LegacyWalkingUse.UNRESOLVED, LegacyWalkingUse.UNRESOLVED,
            LegacyWalkingUse.EXCLUDED, LegacyWalkingUse.UNRESOLVED), parts.map { it.walkingUse })
        val gap = review.context.contexts.single { it.kind == RecordContextKind.GAP }
        assertEquals(6, gap.fromSeq); assertEquals(7, gap.toSeq); assertEquals(32_000L, gap.durationMillis)
        assertNull(review.context.durationMillis)
        assertTrue(review.context.frameAt(24_000).inGap)
        assertEquals(123.0, detail.summary.distanceMeters, 0.0)
        val walking = detail.measurement!!.walkingSections.flatMap { it.points.zipWithNext().map { (a, b) -> a.clientSeq to b.clientSeq } }.toSet()
        assertTrue(parts.flatMap { it.fixes.zipWithNext().map { (a, b) -> a.clientSeq to b.clientSeq } }.none { it in walking })
    }

    @Test fun `source owned scenes select their observation part and shared vertices prefer walking then incoming`() {
        val detail = measuredObservedDetail(); val review = CompletedRouteReview(detail)
        for ((seq, relation) in listOf(2 to SceneRouteRelation.CONNECTED, 3 to SceneRouteRelation.OBSERVED_EXCLUDED,
                4 to SceneRouteRelation.OBSERVED_EXCLUDED, 5 to SceneRouteRelation.OBSERVED_UNRESOLVED,
                9 to SceneRouteRelation.OBSERVED_EXCLUDED, 11 to SceneRouteRelation.CONNECTED)) {
            val focus = review.recordSceneFocus(measuredScene(detail, seq))
            assertEquals("source $seq", relation, focus.relation)
            assertEquals(seq, focus.binding!!.locationSource!!.clientSeq)
            if (relation != SceneRouteRelation.CONNECTED) {
                assertTrue(focus.paths.isEmpty())
                assertEquals(focus.binding.sectionId, focus.observedParts.single().id)
                val selected = focus.observedParts.single()
                assertTrue(selected.directions.all { it.evidenceFromSeq >= selected.fixes.first().clientSeq &&
                    it.evidenceToSeq <= selected.fixes.last().clientSeq })
            }
        }
    }

    @Test fun `clock correction preserves source directions and gap duration without wall time playback`() {
        val detail = measuredObservedDetail(clockCorrection = true); val review = CompletedRouteReview(detail)
        val gap = review.context.contexts.single { it.kind == RecordContextKind.GAP }
        assertTrue(gap.fromMillis > gap.toMillis)
        assertEquals(32_000L, gap.durationMillis)
        assertNull(review.context.gapAt(gap.fromMillis + 1))
        assertNull(review.context.temporalNeighbors(measuredScene(detail, 9)))
        val focus = review.recordSceneFocus(measuredScene(detail, 9))
        assertEquals(SceneRouteRelation.OBSERVED_EXCLUDED, focus.relation)
        assertTrue(focus.observedParts.single().directions.isNotEmpty())
        assertEquals(measuredObservedDetail().measurement!!.auxiliarySections.map { it.id },
            detail.measurement!!.auxiliarySections.map { it.id })
    }

    @Test fun `uncertain displacement hides arrows but keeps observed geometry and explanation`() {
        val detail = measuredObservedDetail(accuracy = 25f); val review = CompletedRouteReview(detail)
        assertTrue(review.observed.sections.all { it.directions.isEmpty() && it.path.size >= 2 })
        val focus = review.recordSceneFocus(measuredScene(detail, 3))
        assertEquals(SceneRouteRelation.OBSERVED_EXCLUDED, focus.relation)
        assertTrue(focus.observedParts.single().directions.isEmpty())
    }

    @Test fun `opposite direction legs retain original edge addresses and never draw a return chord`() {
        val fixes = measuredObservedDetail().observations.take(5).mapIndexed { i, f ->
            f.copy(lng = 127.0 + listOf(0, 12, 24, 12, 0)[i] / 88_000.0) }
        val edges = observedDirectionEdges(fixes, monotonic = true)
        assertEquals(listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4), edges.map { it.fromSeq to it.toSeq })
        assertTrue(edges[1].to.longitude > edges[1].from.longitude)
        assertTrue(edges[2].to.longitude < edges[2].from.longitude)
    }
}
