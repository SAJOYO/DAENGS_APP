package com.daengs.app.ui.walk.review

import com.daengs.app.walk.routeexplorer.*
import com.daengs.app.walk.trajectory.LegacyWalkingUse
import org.junit.Assert.*
import org.junit.Test

class ObservedRouteMapFixtureTest {
    @Test fun `phone fixture separates walking revisits gap excluded travel and uncertain stationary fixes`() {
        val fixture = observedRouteMapFixture(); val review = CompletedRouteReview(fixture.detail)
        val scenes = fixture.scenes
        assertEquals(scenes[0].point, scenes[1].point)
        assertNotEquals(review.recordSceneFocus(scenes[0]).paths, review.recordSceneFocus(scenes[1]).paths)
        assertEquals(setOf(LegacyWalkingUse.EXCLUDED, LegacyWalkingUse.UNRESOLVED), review.observed.sections.map { it.walkingUse }.toSet())
        assertTrue(review.recordSceneFocus(scenes[2]).observedParts.isEmpty())
        assertTrue(review.recordSceneFocus(scenes[2]).paths.isEmpty())
        assertTrue(review.recordSceneFocus(scenes[3]).observedParts.single().directions.isNotEmpty())
        assertEquals(SceneRouteRelation.OBSERVED_UNRESOLVED, review.recordSceneFocus(scenes[4]).relation)
        assertTrue(review.recordSceneFocus(scenes[4]).observedParts.single().directions.isEmpty())
        assertTrue(fixture.folder.isEmpty())
    }
}
