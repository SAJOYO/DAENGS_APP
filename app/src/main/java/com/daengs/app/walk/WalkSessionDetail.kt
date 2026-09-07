package com.daengs.app.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample

/** 사용자가 산책 중 버튼으로 직접 남긴 행동 원본. 가까운 기록끼리 묶는 반경은 저장하지 않는다. */
data class RecordedWalkAction(
    val id: String,
    val sessionId: String,
    val type: WalkMomentType,
    val recordedAtMillis: Long,
    val locationCapturedAtMillis: Long,
    val point: GeoPoint,
    val accuracyMeters: Float?,
)

/** 완료 직후와 지난 산책 화면이 함께 읽는 저장된 한 세션. */
data class WalkSessionDetail(
    val summary: WalkSummary,
    val route: WalkSessionRoute,
    val moments: List<WalkMoment>,
    val stayStamps: List<StayStamp> = emptyList(),
    /** Original upload identities for automatic scene anchoring; loaded only for this session. */
    val observations: List<RecordedFix> = emptyList(),
)

/** 저장 원본에서 현재의 장소 묶음 반경으로 지도 순간을 다시 만든다. */
fun List<RecordedWalkAction>.toMomentGroups(
    groupRadiusMeters: Double = WALK_MOMENT_GROUP_RADIUS_METERS,
): List<WalkMoment> = sortedWith(compareBy(RecordedWalkAction::recordedAtMillis, RecordedWalkAction::id))
    .fold(emptyList()) { moments, action ->
        moments.addOrGroupMoment(
            sample = LocationSample(
                point = action.point,
                capturedAtMillis = action.locationCapturedAtMillis,
                accuracyMeters = action.accuracyMeters,
            ),
            candidateId = "moment-${action.id}",
            type = action.type,
            recordedAtMillis = action.recordedAtMillis,
            groupRadiusMeters = groupRadiusMeters,
        ).moments
    }
