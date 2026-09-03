package com.daengs.app.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import org.junit.Assert.assertEquals
import org.junit.Test

class WalkMomentTest {
    @Test
    fun `버튼 시각과 마지막 GPS 시각을 섞지 않는다`() {
        val sample = LocationSample(
            point = GeoPoint(37.5665, 126.9780),
            capturedAtMillis = 1_000L,
        )

        val update = emptyList<WalkMoment>().addOrGroupMoment(
            sample = sample,
            candidateId = "moment-7",
            type = WalkMomentType.EXPLORE,
            recordedAtMillis = 1_450L,
        )
        val moment = update.moments.single()
        val action = moment.actions.getValue(WalkMomentType.EXPLORE)

        assertEquals("moment-7", moment.id)
        assertEquals(1_450L, action.recordedAtMillis)
        assertEquals(1_000L, action.locationCapturedAtMillis)
        assertEquals(sample.point, moment.point)
    }

    @Test
    fun `5미터 안의 서로 다른 행동은 한 장소 집합에 합친다`() {
        val first = LocationSample(GeoPoint(37.566500, 126.978000), capturedAtMillis = 1_000L)
        val nearby = LocationSample(GeoPoint(37.566530, 126.978000), capturedAtMillis = 2_000L)

        val initial = emptyList<WalkMoment>().addOrGroupMoment(
            sample = first,
            candidateId = "moment-1",
            type = WalkMomentType.EXPLORE,
            recordedAtMillis = 1_100L,
        )
        val grouped = initial.moments.addOrGroupMoment(
            sample = nearby,
            candidateId = "moment-2",
            type = WalkMomentType.SOCIAL,
            recordedAtMillis = 2_100L,
        )

        assertEquals(false, grouped.groupCreated)
        assertEquals(true, grouped.actionAdded)
        assertEquals("moment-1", grouped.selectedMomentId)
        assertEquals(setOf(WalkMomentType.EXPLORE, WalkMomentType.SOCIAL), grouped.moments.single().types)
    }

    @Test
    fun `같은 장소에서 같은 행동을 반복해도 집합은 늘지 않는다`() {
        val sample = LocationSample(GeoPoint(37.5665, 126.9780), capturedAtMillis = 1_000L)
        val initial = emptyList<WalkMoment>().addOrGroupMoment(
            sample = sample,
            candidateId = "moment-1",
            type = WalkMomentType.EXPLORE,
            recordedAtMillis = 1_100L,
        )
        val repeated = initial.moments.addOrGroupMoment(
            sample = sample,
            candidateId = "moment-2",
            type = WalkMomentType.EXPLORE,
            recordedAtMillis = 2_100L,
        )

        assertEquals(false, repeated.groupCreated)
        assertEquals(false, repeated.actionAdded)
        assertEquals(1, repeated.moments.size)
        assertEquals(1, repeated.moments.single().actions.size)
        assertEquals(1_100L, repeated.moments.single().latestRecordedAtMillis)
    }

    @Test
    fun `반경 밖의 행동은 새 장소로 만든다`() {
        val first = LocationSample(GeoPoint(37.566500, 126.978000), capturedAtMillis = 1_000L)
        val far = LocationSample(GeoPoint(37.566580, 126.978000), capturedAtMillis = 2_000L)
        val initial = emptyList<WalkMoment>().addOrGroupMoment(
            sample = first,
            candidateId = "moment-1",
            type = WalkMomentType.EXPLORE,
            recordedAtMillis = 1_100L,
        )
        val separated = initial.moments.addOrGroupMoment(
            sample = far,
            candidateId = "moment-2",
            type = WalkMomentType.EXPLORE,
            recordedAtMillis = 2_100L,
        )

        assertEquals(true, separated.groupCreated)
        assertEquals(2, separated.moments.size)
    }

    @Test
    fun `행동 위치는 15미터 이하 정확도만 후보가 된다`() {
        val accurate = LocationSample(
            point = GeoPoint(37.5665, 126.9780),
            capturedAtMillis = 1_000L,
            accuracyMeters = 15f,
        )
        val inaccurate = accurate.copy(accuracyMeters = 15.1f)

        assertEquals(true, accurate.isAccurateEnoughForMoment())
        assertEquals(false, inaccurate.isAccurateEnoughForMoment())
        assertEquals(false, accurate.copy(accuracyMeters = null).isAccurateEnoughForMoment())
    }

    @Test
    fun `행동 위치는 monotonic 시각 기준 10초까지만 유효하다`() {
        val sample = LocationSample(
            point = GeoPoint(37.5665, 126.9780),
            capturedAtMillis = 1_000L,
            elapsedRealtimeNanos = 20_000_000_000L,
            accuracyMeters = 5f,
        )

        assertEquals(true, sample.isFreshEnoughForMoment(30_000_000_000L))
        assertEquals(false, sample.isFreshEnoughForMoment(30_000_000_001L))
        assertEquals(false, sample.isFreshEnoughForMoment(19_999_999_999L))
        assertEquals(false, sample.copy(elapsedRealtimeNanos = null).isFreshEnoughForMoment(20_000_000_000L))
    }

    @Test
    fun `행동 네 종류의 서버용 코드는 서로 다르다`() {
        assertEquals(4, WalkMomentType.entries.size)
        assertEquals(4, WalkMomentType.entries.map { it.behaviorCode }.toSet().size)
    }

    @Test
    fun `저장 행동은 시각 순서로 읽어 현재 반경의 장소로 다시 묶는다`() {
        val groups = listOf(
            action("late", WalkMomentType.SOCIAL, 2_000L, 37.56651),
            action("first", WalkMomentType.EXPLORE, 1_000L, 37.56650),
            action("far", WalkMomentType.SPECIAL, 3_000L, 37.56700),
        ).toMomentGroups()

        assertEquals(2, groups.size)
        assertEquals("moment-first", groups.first().id)
        assertEquals(setOf(WalkMomentType.EXPLORE, WalkMomentType.SOCIAL), groups.first().types)
    }

    @Test
    fun `같은 장소와 행동이 중복 저장돼도 최초 증언만 화면에 남긴다`() {
        val groups = listOf(
            action("first", WalkMomentType.EXPLORE, 1_000L, 37.56650),
            action("repeat", WalkMomentType.EXPLORE, 2_000L, 37.56651),
        ).toMomentGroups()

        assertEquals(1, groups.single().actions.size)
        assertEquals(1_000L, groups.single().latestRecordedAtMillis)
    }

    private fun action(
        id: String,
        type: WalkMomentType,
        recordedAtMillis: Long,
        latitude: Double,
    ) = RecordedWalkAction(
        id = id,
        sessionId = "walk-1",
        type = type,
        recordedAtMillis = recordedAtMillis,
        locationCapturedAtMillis = recordedAtMillis - 100L,
        point = GeoPoint(latitude, 126.9780),
        accuracyMeters = 5f,
    )
}
