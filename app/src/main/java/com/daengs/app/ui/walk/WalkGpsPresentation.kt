package com.daengs.app.ui.walk

import com.daengs.app.location.LocationSample
import com.daengs.app.walk.WALK_MOMENT_MAX_ACCURACY_METERS
import com.daengs.app.walk.isFreshEnoughForMoment

internal data class WalkGpsPresentation(val good: Boolean, val unavailable: Boolean, val detail: String)

/** GPS quality belongs to the current position feed, independently of the walk session. */
internal fun walkGpsPresentation(granted: Boolean, precise: Boolean, error: String?,
    sample: LocationSample?, nowNanos: Long): WalkGpsPresentation {
    if (!granted || !precise) return WalkGpsPresentation(false, true, "정확한 위치 권한을 허용해 주세요")
    if (error != null) return WalkGpsPresentation(false, true, error)
    if (sample?.isMock == true) return WalkGpsPresentation(false, true, "모의 위치에서는 현장 인증을 할 수 없어요")
    if (sample == null || !sample.isFreshEnoughForMoment(nowNanos)) {
        return WalkGpsPresentation(false, false, "정확한 새 위치를 기다리고 있어요")
    }
    val accuracy = sample.accuracyMeters
    if (accuracy == null || !accuracy.isFinite() || accuracy < 0 || accuracy > WALK_MOMENT_MAX_ACCURACY_METERS) {
        return WalkGpsPresentation(false, false, "위치 오차가 줄어들기를 기다리고 있어요")
    }
    return WalkGpsPresentation(true, false, "현재 위치를 확인했어요")
}
