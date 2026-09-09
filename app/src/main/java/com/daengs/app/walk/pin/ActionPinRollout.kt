package com.daengs.app.walk.pin

import com.daengs.app.location.LocationSample
import com.daengs.app.walk.RecordedWalkAction
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.isAccurateEnoughForMoment
import com.daengs.app.walk.isFreshEnoughForMoment

object ActionPinRollout {
    // GCP v2 API/DB와 지원 정책 배포를 확인한 뒤 해제한다. 기존 v2 복구는 계속 유지한다.
    val legacyCreation: Boolean = true
}

/** 화면 좌표나 추정 핀이 아닌, 탭 시점에 유효한 실제 GPS만 기존 계약으로 기록한다. */
internal fun legacyWalkAction(
    sample: LocationSample?, id: String, sessionId: String, type: WalkMomentType,
    recordedAtMillis: Long, nowElapsedNanos: Long,
): RecordedWalkAction? {
    if (type == WalkMomentType.NOTE || sample == null || sample.isMock ||
        sample.accuracyMeters?.let { it.isFinite() && it > 0 } != true ||
        !sample.isAccurateEnoughForMoment() || !sample.isFreshEnoughForMoment(nowElapsedNanos) ||
        sample.capturedAtMillis > recordedAtMillis) return null
    return RecordedWalkAction(id, sessionId, type, recordedAtMillis,
        sample.capturedAtMillis, sample.point, sample.accuracyMeters)
}
