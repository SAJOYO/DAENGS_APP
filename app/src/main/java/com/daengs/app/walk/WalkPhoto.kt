package com.daengs.app.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import java.io.File

/** 일기 사진. 점령 시도·인증 큐와 연결하지 않는 원본이다. */
data class WalkPhoto(
    val id: String,
    val sessionId: String,
    val capturedAtMillis: Long,
    val point: GeoPoint,
    val file: File,
)

/** 카메라 완료 콜백이 아니라 셔터를 누르는 순간 확정한다. */
data class WalkPhotoCapture(val sessionId: String, val ownerId: String,
    val capturedAtMillis: Long, val sample: LocationSample)

fun beginWalkPhotoCapture(tracking: WalkTrackingState, ownerId: String,
    nowMillis: Long, nowElapsedNanos: Long): WalkPhotoCapture? {
    val session = tracking.activeSessionId ?: return null
    if (tracking.trail.state != TrackingState.RECORDING || tracking.finishingSessionId != null ||
        tracking.ownerId != ownerId) return null
    val sample = tracking.latestMomentFix ?: return null
    val accuracy = sample.accuracyMeters ?: return null
    if (sample.isMock || !accuracy.isFinite() || accuracy !in 0f..15f ||
        !sample.isFreshEnoughForMoment(nowElapsedNanos)) return null
    return WalkPhotoCapture(session, ownerId, nowMillis, sample)
}
