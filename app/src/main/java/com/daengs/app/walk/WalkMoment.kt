package com.daengs.app.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample

/** 같은 행동 지점으로 취급할 기본 반경. 길 건너나 이웃 시설을 합치지 않는 작은 실험값이다. */
const val WALK_MOMENT_GROUP_RADIUS_METERS = 5.0

/** 행동 지점은 경로선보다 위치 의미가 강하므로 더 엄격한 정확도를 요구한다. */
const val WALK_MOMENT_MAX_ACCURACY_METERS = 15f

/** 마지막으로 신뢰한 좌표가 이보다 오래됐으면 사용자가 지금 있는 자리라고 보지 않는다. */
const val WALK_MOMENT_MAX_AGE_NANOS = 10_000_000_000L

/** 한 장소에서 사용자가 직접 확인한 행동 하나와 그 시각. */
data class WalkMomentAction(
    val type: WalkMomentType,
    val recordedAtMillis: Long,
    val locationCapturedAtMillis: Long,
)

/**
 * 산책 중 보호자가 직접 남긴 장소별 행동 책갈피.
 *
 * GPS가 행동을 판정한 값이 아니다. 사용자가 버튼을 누른 순간의 증언이며, 가까운 위치에서
 * 누른 행동은 새 핀을 무한히 만들지 않고 이 장소의 `actions` 집합에 합친다. 같은 행동을
 * 반복해서 눌러도 최초 기록 하나만 유지한다.
 *
 * 서버 attestation과는 별개인 사용자 입력 원본을 Room에 남기고, 이 장소 묶음은 읽을 때
 * 현재 반경 규칙으로 다시 만든다. 그래서 실험 반경을 바꿔도 기존 기록을 이주시킬 필요가 없다.
 */
data class WalkMoment(
    val id: String,
    val point: GeoPoint,
    val actions: Map<WalkMomentType, WalkMomentAction>,
) {
    val types: Set<WalkMomentType> get() = actions.keys
    val latestRecordedAtMillis: Long get() = actions.values.maxOf { it.recordedAtMillis }

    val markerLabel: String
        get() {
            val ordered = WalkMomentType.entries.filter(types::contains)
            return when (ordered.size) {
                0 -> "산책 순간"
                1 -> ordered.first().label
                else -> "${ordered.first().label} 외 ${ordered.size - 1}개"
            }
        }

    val actionLabels: String
        get() = WalkMomentType.entries
            .filter(types::contains)
            .joinToString(" · ") { it.label }
}

enum class WalkMomentType(
    val behaviorCode: String,
    val label: String,
) {
    SNIFFING("sniffing", "킁킁"),
    EXCRETION("excretion", "배설"),
    BARKING("barking", "짖기"),
    NOTE("note", "특별한 순간"),
}

enum class WalkMomentOutcome { CREATED, MERGED, ALREADY_EXISTS }

sealed interface WalkEvent {
    data class MomentRecorded(
        val type: WalkMomentType,
        val outcome: WalkMomentOutcome,
        val momentId: String,
    ) : WalkEvent

    data object MomentLocationUnavailable : WalkEvent
}

data class WalkMomentUpdate(
    val moments: List<WalkMoment>,
    val selectedMomentId: String,
    val groupCreated: Boolean,
    val actionAdded: Boolean,
)

/**
 * 현재 좌표에서 가장 가까운 장소가 반경 안에 있으면 그 장소의 행동 집합을 갱신한다.
 * 반경 밖일 때만 새 장소 핀을 만든다.
 */
fun List<WalkMoment>.addOrGroupMoment(
    sample: LocationSample,
    candidateId: String,
    type: WalkMomentType,
    recordedAtMillis: Long,
    groupRadiusMeters: Double = WALK_MOMENT_GROUP_RADIUS_METERS,
): WalkMomentUpdate {
    val nearest = withIndex()
        .map { indexed -> indexed to indexed.value.point.distanceTo(sample.point) }
        .minByOrNull { it.second }
        ?.takeIf { it.second <= groupRadiusMeters }
        ?.first

    val action = WalkMomentAction(
        type = type,
        recordedAtMillis = recordedAtMillis,
        locationCapturedAtMillis = sample.capturedAtMillis,
    )

    if (nearest == null) {
        val created = WalkMoment(
            id = candidateId,
            point = sample.point,
            actions = mapOf(type to action),
        )
        return WalkMomentUpdate(
            moments = this + created,
            selectedMomentId = created.id,
            groupCreated = true,
            actionAdded = true,
        )
    }

    val existing = nearest.value
    if (type in existing.actions) {
        return WalkMomentUpdate(
            moments = this,
            selectedMomentId = existing.id,
            groupCreated = false,
            actionAdded = false,
        )
    }

    return WalkMomentUpdate(
        moments = toMutableList().also { list ->
            list[nearest.index] = existing.copy(actions = existing.actions + (type to action))
        },
        selectedMomentId = existing.id,
        groupCreated = false,
        actionAdded = true,
    )
}

/** 경로 단순화 여부와 무관하게 행동 지점 후보로 보관할 수 있는 정확도인가. */
fun LocationSample.isAccurateEnoughForMoment(): Boolean =
    accuracyMeters != null && accuracyMeters <= WALK_MOMENT_MAX_ACCURACY_METERS

/** 버튼을 누른 지금도 이 좌표를 현재 위치라고 부를 수 있는가. */
fun LocationSample.isFreshEnoughForMoment(nowElapsedRealtimeNanos: Long): Boolean {
    val capturedElapsedRealtimeNanos = elapsedRealtimeNanos ?: return false
    val age = nowElapsedRealtimeNanos - capturedElapsedRealtimeNanos
    return age in 0..WALK_MOMENT_MAX_AGE_NANOS
}
