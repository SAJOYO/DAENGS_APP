package com.daengs.app.walk.trajectory

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.*
import com.daengs.app.walk.routeexplorer.*
import org.junit.Assert.*
import org.junit.Test

class ObservedRouteReviewTest {
    private val session = RecordedSession("observed", startedAtMillis = 0, endedAtMillis = 100_000)
    private fun fix(i: Int, x: Double, at: Long = 10_000L + i * 1_000L) = RecordedFix(i, 0, at, 0.0, x / 111_195, 1f, false)
    private fun fast() = (0..12).map { fix(it, it * 12.0) }
    private fun read(raw: List<RecordedFix>) = readCompletedRoute(session, raw)
    private fun scene(f: RecordedFix, id: String = "scene") = DiaryScene("observed/$id", "observed", f.atMillis,
        "장면", "원본 본문", GeoPoint(f.lat, f.lng), "", source = StoryboardScene(id, f.atMillis, "장면", "", "", "revision",
            observation = StoryboardObservation(f.clientSeq, f.chainIndex, f.atMillis, GeoPoint(f.lat, f.lng))))

    @Test fun `excluded scene uses its observed range without becoming a walking focus`() {
        val raw = fast(); val detail = read(raw); val review = CompletedRouteReview(detail)
        val before = detail.summary
        val result = review.recordSceneFocus(scene(raw[6]))
        assertEquals(SceneRouteRelation.OBSERVED_EXCLUDED, result.relation)
        assertTrue(result.paths.isEmpty()); assertTrue(result.observedParts.single().directions.isNotEmpty())
        assertEquals(scene(raw[6]).point, result.point)
        assertEquals(6, result.binding?.observationSeq)
        assertEquals(SceneRouteRelation.NO_ROUTE, review.sceneFocus(scene(raw[6])).relation)
        assertEquals(before, detail.summary); assertEquals(0.0, detail.summary.distanceMeters, 0.0)
    }

    @Test fun `end event may show earlier confirmed context while arbitrary retiming cannot`() {
        val raw = fast(); val review = CompletedRouteReview(read(raw))
        val end = scene(raw.last(), "end").copy(atMillis = 100_000)
        val context = review.recordSceneFocus(end)
        assertEquals(SceneRouteRelation.OBSERVED_EXCLUDED, context.relation)
        assertEquals(100_000L, context.binding?.eventAtMillis)
        assertEquals(raw.last().atMillis, context.binding?.locationAtMillis)
        assertTrue(review.recordSceneFocus(scene(raw[6]).copy(atMillis = 100_000)).observedParts.isEmpty())
        assertTrue(review.recordSceneFocus(scene(raw[6], "end").copy(atMillis = 100_000)).observedParts.isEmpty())
    }

    @Test fun `normal scene retains its walking range and owners never get a second line`() {
        val raw = (0..20).map { fix(it, it * 2.0) }; val detail = read(raw); val review = CompletedRouteReview(detail)
        assertTrue(review.observed.sections.isEmpty())
        assertEquals(review.sceneFocus(scene(raw[5])), review.recordSceneFocus(scene(raw[5])))
        val bridge = (0..8).map { fix(it, it * 2.0) } + (9..11).map { fix(it, 16.0 + (it - 8) * 12) } +
            (12..20).map { fix(it, 52.0 + (it - 11) * 2) }
        val conflict = CompletedRouteReview(read(bridge)).observed
        assertTrue(conflict.assessment.intervals.any { it.auxiliaryUse == AuxiliaryUse.OWNERSHIP_CONFLICT })
        assertTrue(conflict.sections.isEmpty())
    }

    @Test fun `continuous small fixes can have unknown walking use without inventing arrows`() {
        val raw = (0..8).map { fix(it, if (it % 2 == 0) 0.0 else 0.5) }
        val review = CompletedRouteReview(read(raw))
        val part = review.observed.sections.single()
        assertEquals(LegacyWalkingUse.UNRESOLVED, part.walkingUse)
        assertTrue(part.directions.isEmpty())
        assertEquals(SceneRouteRelation.OBSERVED_UNRESOLVED, review.recordSceneFocus(scene(raw[4])).relation)
    }

    @Test fun `jump gap and pause never lend an edge to the focused scene`() {
        for (suffix in listOf(
            (7..13).map { fix(it, 1_000.0 + (it - 7) * 12) },
            (7..13).map { fix(it, 84.0 + (it - 7) * 12, 60_000 + (it - 7) * 1_000L) },
            (7..13).map { fix(it, 84.0 + (it - 7) * 12).copy(chainIndex = 1) })) {
            val raw = fast().take(7) + suffix
            val review = CompletedRouteReview(read(raw))
            assertTrue(review.observed.sections.none { part -> part.fixes.any { it.clientSeq == 6 } && part.fixes.any { it.clientSeq == 7 } })
            val selected = review.recordSceneFocus(scene(raw[8]))
            assertTrue(selected.observedParts.flatMap { it.fixes }.none { it.clientSeq <= 6 })
        }
    }

    @Test fun `outbound and returning observations at the same place use their own time ranges`() {
        val raw = (0..58).map { i -> fix(i, if (i <= 50) i * 12.0 else 600.0 - (i - 50) * 12) }
        val review = CompletedRouteReview(read(raw))
        val out = review.recordSceneFocus(scene(raw[44])).observedParts.single()
        val back = review.recordSceneFocus(scene(raw[56])).observedParts.single()
        assertEquals(raw[44].lng, raw[56].lng, 0.0)
        assertTrue(out.directions.all { it.to.longitude > it.from.longitude })
        assertTrue(back.directions.all { it.to.longitude < it.from.longitude })
        assertTrue(out.fixes.last().clientSeq < back.fixes.first().clientSeq)
    }

    @Test fun `source validation and edited location state cannot borrow an observed range`() {
        val raw = fast(); val detail = read(raw); val review = CompletedRouteReview(detail); val valid = scene(raw[6])
        val bad = listOf(valid.copy(sessionId = "other"), valid.copy(point = GeoPoint(1.0, 1.0)),
            valid.copy(source = valid.source!!.copy(observation = valid.source.observation!!.copy(chainIndex = 1))),
            valid.copy(content = DiarySceneContent("", "note", locationLabel = "", positionState = "provisional")),
            valid.copy(content = DiarySceneContent("", "note", locationLabel = "", locationAtMillis = 999)))
        bad.forEach { assertTrue(review.recordSceneFocus(it).observedParts.isEmpty()) }
        assertFalse(review.observed.matches(detail.copy(observations = raw.dropLast(1))))
        assertTrue(CompletedRouteReview(detail.copy(legacyRouteEvidence = null)).observed.sections.isEmpty())
        assertTrue(CompletedRouteReview(detail.copy(summary = detail.summary.copy(measurementVersion = "motion-v1"))).observed.sections.isEmpty())
    }

    @Test fun `unanchored photo matches only its unique observed time and current position`() {
        val raw = fast(); val review = CompletedRouteReview(read(raw))
        val photo = scene(raw[6]).copy(source = null, atMillis = raw[6].atMillis + 500,
            point = GeoPoint(0.0, 78.0 / 111_195))
        assertEquals(SceneRouteRelation.OBSERVED_EXCLUDED, review.recordSceneFocus(photo).relation)
        assertTrue(review.recordSceneFocus(photo.copy(point = GeoPoint(0.0, 1.0))).observedParts.isEmpty())
        assertTrue(review.recordSceneFocus(photo.copy(content = DiarySceneContent("", "photo", locationLabel = "",
            locationMethod = "last_known"))).observedParts.isEmpty())
        val gap = raw.take(7) + raw.drop(7).map { it.copy(atMillis = it.atMillis + 30_000) }
        assertTrue(CompletedRouteReview(read(gap)).recordSceneFocus(photo).observedParts.isEmpty())
    }

    @Test fun `short steps with broad accuracy need coherent local displacement rather than jitter arrows`() {
        val raw = fast().map { it.copy(accuracyM = 10f) }
        val directions = CompletedRouteReview(read(raw)).observed.sections.single().directions
        assertTrue(directions.isNotEmpty())
        assertTrue(directions.all { it.evidenceToSeq - it.evidenceFromSeq > 1 })
        assertTrue(directions.all { it.fromSeq + 1 == it.toSeq })
        val jitter = (0..12).map { fix(it, if (it % 2 == 0) 0.0 else 12.0).copy(accuracyM = 10f) }
        assertTrue(observedDirectionEdges(jitter).isEmpty())
        assertTrue(observedDirectionEdges(raw.map { it.copy(atMillis = it.atMillis * 10) }).isEmpty())
    }
}
