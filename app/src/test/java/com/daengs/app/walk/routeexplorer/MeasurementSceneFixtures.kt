package com.daengs.app.walk.routeexplorer

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.*

/** Synthetic source-owned sections, including repeated wall clocks in separate clock epochs. */
internal fun measuredSceneDetail(id: String = "measurement-a", secondVisit: Boolean = false,
    retained: List<Int> = listOf(0, 1, 2, 3, 4)): WalkSessionDetail {
    val fixes = (0 until if (secondVisit) 10 else 5).map { seq ->
        val visit = seq / 5; val i = seq % 5
        RecordedFix(seq, visit, 10_000 + i * 2_000L, 37.5, 127.0 + i * 4.0 / 88_000, 1f, false,
            ingressSeq = seq.toLong(), sourceEpoch = "epoch-$visit", clockEpochId = "clock-$visit",
            elapsedRealtimeNanos = (i+1) * 2_000_000_000L, recordingEligible = true)
    }
    val groups = fixes.chunked(5).map { group -> retained.map { group[it] } }
    val segments = groups.mapIndexed { n, group -> WalkRouteSegment(n, group.mapIndexed { i, f ->
        WalkRoutePoint(GeoPoint(f.lat, f.lng), f.atMillis, 1f, (n * 10_000L) + f.elapsedRealtimeNanos!! / 1_000_000,
            (n * 16.0) + i * 4.0, null, n, i, f.elapsedRealtimeNanos)
    }) }
    val summary = WalkSummary("measured", emptyList(), 0, 100_000, null, 123.0, 90_000,
        segments.map { it.points.map { p -> LocationSample(p.point, p.capturedAtMillis, p.elapsedRealtimeNanos, 1f) } },
        segments.first().points.first().point, measurementVersion = "motion-v1")
    fun boundary(f: RecordedFix) = WalkMeasurementBoundary(f.sourceEpoch!!, f.clockEpochId!!, f.clientSeq, null,
        f.atMillis, GeoPoint(f.lat, f.lng))
    val boundaries = mapOf("record_start" to WalkMeasurementBoundary("epoch-0", "clock-0", null, "epoch_start", 0, null),
        "record_end" to WalkMeasurementBoundary(fixes.last().sourceEpoch!!, fixes.last().clockEpochId!!, null, "epoch_end", 100_000, null),
        "first_observed" to boundary(fixes.first()), "last_observed" to boundary(fixes.last()),
        "first_walking" to boundary(groups.first().first()), "last_walking" to boundary(groups.last().last()))
    val measurement = WalkMeasurementDetail(id, "digest-$id", boundaries, emptyList(), "owner",
        groups.mapIndexed { i, group -> MeasurementWalkingSection("section-$i", group.map { it.measurementRef(summary.sessionId) }) },
        fixes.map { it.measurementRef(summary.sessionId) }.toSet())
    return WalkSessionDetail(summary, WalkSessionRoute(segments), emptyList(), observations = fixes, measurement = measurement)
}

internal fun measuredScene(detail: WalkSessionDetail, seq: Int = 2, title: String = "장면"): DiaryScene {
    val f = detail.observations.single { it.clientSeq == seq }; val point = GeoPoint(f.lat, f.lng)
    val source = StoryboardScene("source", f.atMillis, title, "함께 걸었다", "", "source-$seq",
        observation = StoryboardObservation(f.clientSeq, f.chainIndex, f.atMillis, point))
    return DiaryScene("measured/scene", "measured", f.atMillis, title, "함께 걸었다", point, "", source = source)
}
