package com.daengs.app.walk.routeexplorer

import com.daengs.app.walk.*
import com.daengs.app.walk.diary.*
import com.daengs.app.walk.diary.relational.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class RelationalSceneBindingTest {
    private fun scene(detail: WalkSessionDetail, seqs: List<Int>): DiaryScene {
        val original = measuredScene(detail)
        val anchor = RelationalAnchor(Instant.ofEpochMilli(original.atMillis), "recorded_at", original.point,
            Instant.ofEpochMilli(original.atMillis), 2.0, "resolved", RelationalPositionMethod.ESTIMATED,
            seqs.map { seq -> detail.observations.single { it.clientSeq == seq }.let {
                RelationalFixRef(it.clientSeq.toLong(), it.chainIndex.toLong(), Instant.ofEpochMilli(it.atMillis))
            } })
        val empty = RelationalDiaryPart(RelationalPartStatus.NOT_REQUESTED, "", RelationalSemanticStatus.NOT_PUBLISHED)
        val snapshot = RelationalSceneSnapshot("card", detail.summary.sessionId, anchor.eventAt, anchor.point, 2.0,
            anchor.method, emptyList(), emptyMap(), emptyMap())
        val card = RelationalDiaryCard("card", anchor, RelationalHeader("card", null, null), empty, empty, "", snapshot, null, emptyList())
        return original.copy(source = original.source!!.copy(observation = null), relational = card)
    }

    @Test fun `estimated receipt selects its source section despite repeated times and positions`() {
        val detail = measuredSceneDetail(secondVisit = true)
        val first = CompletedRouteReview(detail).recordSceneFocus(scene(detail, listOf(1, 3)))
        val second = CompletedRouteReview(detail).recordSceneFocus(scene(detail, listOf(6, 8)))
        assertEquals(SceneRouteRelation.CONNECTED, first.relation)
        assertEquals(SceneRouteRelation.CONNECTED, second.relation)
        assertEquals("section-0", first.binding!!.sectionId)
        assertEquals("section-1", second.binding!!.sectionId)
        assertNotEquals(first.key!!.eventRevision, second.key!!.eventRevision)
    }

    @Test fun `missing altered mixed or unusable references cannot borrow matching route geometry`() {
        val detail = measuredSceneDetail(secondVisit = true)
        val valid = scene(detail, listOf(1, 3))
        val anchor = valid.relational!!.anchor
        val wrong = valid.copy(relational = valid.relational.copy(anchor = anchor.copy(sourceFixes = listOf(anchor.sourceFixes.first().copy(clientSeq = 999)))))
        val empty = valid.copy(relational = valid.relational.copy(anchor = anchor.copy(sourceFixes = emptyList())))
        for (candidate in listOf(wrong, empty, scene(detail, listOf(1, 8)))) {
            val focus = CompletedRouteReview(detail).recordSceneFocus(candidate)
            assertEquals(SceneRouteRelation.NO_ROUTE, focus.relation)
            assertEquals(candidate.point, focus.point)
            assertTrue(focus.paths.isEmpty())
        }
        val unusable = detail.copy(measurement = detail.measurement!!.copy(usableSources = detail.measurement.usableSources.filterNot { it.clientSeq == 1 }.toSet()))
        assertTrue(CompletedRouteReview(unusable).recordSceneFocus(valid).paths.isEmpty())
    }

    @Test fun `legacy read without a measurement cannot infer an estimated walking connection`() {
        val detail = measuredSceneDetail()
        val candidate = scene(detail, listOf(1, 3))
        val review = CompletedRouteReview(detail.copy(measurement = null))
        assertEquals(SceneRouteRelation.NO_ROUTE, review.recordSceneFocus(candidate).relation)
        assertEquals(SceneRouteRelation.NO_ROUTE, review.sceneFocus(candidate).relation)
        assertEquals(candidate.point, review.recordSceneFocus(candidate).point)
    }

    @Test fun `prediction outside source times remains a position only`() {
        val detail = measuredSceneDetail()
        val candidate = scene(detail, listOf(0, 1))
        assertEquals(SceneRouteRelation.NO_ROUTE, CompletedRouteReview(detail).recordSceneFocus(candidate).relation)
    }
}
