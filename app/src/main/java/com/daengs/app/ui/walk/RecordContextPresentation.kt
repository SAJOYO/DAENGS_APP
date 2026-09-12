package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.map.layers.completedroute.*
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.distanceTo
import com.daengs.app.walk.routeexplorer.CompletedRouteReview
import com.daengs.app.walk.trajectory.*
import java.util.Locale

internal fun recordContextTitle(value: RecordContext): String = when (value.kind) {
    RecordContextKind.START -> "기록 시작과 첫 확인 위치"
    RecordContextKind.END -> "기록 종료와 마지막 확인 위치"
    RecordContextKind.GAP -> "경로 미확인"
    RecordContextKind.TRANSITION -> if (value.durationMillis == null) "시각 기준 미확정" else "이동 구분 전환"
}
private fun RecordMovement?.label(): String = when (this) {
    RecordMovement.WALKING -> "보행"
    RecordMovement.EXCLUDED -> "보행거리 제외 이동"
    RecordMovement.UNRESOLVED -> "보행 미확정 관측"
    else -> "위치 기록"
}
internal fun recordContextTime(value: RecordContext): String =
    if (value.kind in setOf(RecordContextKind.START, RecordContextKind.END)) formatRouteExplorerClock(value.fromMillis)
    else formatRouteExplorerClock(value.fromMillis) + "–" + formatRouteExplorerClock(value.toMillis) +
        (value.durationMillis?.let { " · ${seconds(it)}초" } ?: " · 시간 길이 미확정")
private fun seconds(millis: Long) = String.format(Locale.ROOT, "%.3f", millis / 1000.0).trimEnd('0').trimEnd('.')
private fun locationLine(label: String, location: ConfirmedRecordLocation?, eventAt: Long? = null): String {
    if (location == null) return "$label: 확인된 위치가 없어요."
    val relation = eventAt?.let { if (location.atMillis < it) " (사건 이전)" else if (location.atMillis > it) " (사건 이후)" else "" }.orEmpty()
    return "$label: ${formatRouteExplorerClock(location.atMillis)}$relation"
}
internal fun recordContextDescription(value: RecordContext): String = when (value.kind) {
    RecordContextKind.START, RecordContextKind.END -> {
        val starting = value.kind == RecordContextKind.START
        val event = if (starting) "시작" else "종료"
        val fix = if (starting) value.after else value.before
        listOfNotNull("기록 $event: ${formatRouteExplorerClock(value.fromMillis)}",
            locationLine(if (starting) "첫 확인 위치" else "마지막 확인 위치", fix, value.fromMillis.takeIf { value.durationMillis != null }),
            value.walkingEndpoint?.let { "${if (starting) "보행선 시작" else "보행선 끝"}: ${formatRouteExplorerClock(it.capturedAtMillis)}" },
            value.walkingEndpoint?.let { if (!starting && value.durationMillis != null && it.capturedAtMillis <= value.fromMillis)
                "보행선 마지막 점에서 기록 종료까지 ${seconds(value.fromMillis - it.capturedAtMillis)}초예요." else null },
            if (value.durationMillis == null) "관측 시각의 순서가 불확실해 사건과 위치 사이의 시간 길이는 확정하지 않아요." else null,
            "지도에는 위치가 확인된 시점의 점을 표시해요. 사건 시각의 위치로 바꾸지 않아요.").joinToString("\n")
    }
    RecordContextKind.GAP -> listOf(
        "${value.beforeMovement.label()} → 경로 미확인 → ${value.afterMovement.label()}",
        locationLine("앞쪽 확인 위치", value.before), locationLine("뒤쪽 확인 위치", value.after),
        when {
            value.durationMillis == null -> "시각 또는 기기 시간 기준이 달라 이 사이의 시간 길이는 확정하지 않아요."
            ConnectionReason.CHAIN_BOUNDARY in value.reasons -> "기록 구간이 나뉘어 있어요. 그 사이 이동 경로는 확인되지 않아요."
            ConnectionReason.POSITION_INVALID in value.reasons || ConnectionReason.POSITION_UNCERTAIN in value.reasons ->
                "위치 품질 때문에 이 사이의 경로를 확인하지 못했어요."
            ConnectionReason.OBSERVATION_GAP in value.reasons -> "위치 관측의 시간 간격이 벌어진 구간이에요."
            else -> "관측 연결 근거가 충분하지 않아 이 사이의 경로는 표시하지 않아요."
        },
        if (value.before != null && value.after != null) "점선은 전후 위치의 관계 안내예요. 실제 이동 경로나 이동 방향이 아니에요."
        else "양 끝 위치가 모두 확인되지 않아 지도 안내선은 만들지 않아요."
    ).joinToString("\n")
    RecordContextKind.TRANSITION -> if (value.durationMillis == null)
        "관측 시각의 순서나 시간 기준이 바뀌었어요. 기존 보행선은 유지하지만 이 범위의 시간 길이나 재생 위치는 확정하지 않아요."
        else "${value.beforeMovement.label()} → ${value.afterMovement.label()}\n" +
            "여기서 이동의 집계 구분이 바뀌어요. 보조 경로는 보행거리 합계에 더하지 않아요."
}

internal fun recordContextLayer(review: CompletedRouteReview, selection: RecordContext?): RecordContextLayerState? {
    if (!review.context.available) return null
    val endpoints = mutableListOf<RouteEndpointMarkerState>()
    val markers = mutableListOf<RecordContextMarker>()
    for (value in review.context.contexts) {
        val selected = selection?.id == value.id
        when (value.kind) {
            RecordContextKind.START, RecordContextKind.END -> {
                val starting = value.kind == RecordContextKind.START
                val location = if (starting) value.after else value.before
                val walking = value.walkingEndpoint
                val same = location != null && walking?.point?.distanceTo(location.point)?.let { it <= 1.0 } == true
                walking?.let { endpoints += RouteEndpointMarkerState(value.id, it.point,
                    if (starting) (if (same) "첫 확인 · 보행 시작" else "보행 시작") else (if (same) "마지막 확인 · 보행 끝" else "보행 끝"),
                    if (starting) RouteEndpointKind.START else RouteEndpointKind.END, selected, compact = true) }
                if (!same && location != null) markers += RecordContextMarker(value.id, location.point,
                    if (starting) "첫 확인" else "마지막 확인", selected)
            }
            RecordContextKind.GAP -> listOfNotNull(value.before, value.after).distinctBy { it.point }.forEach {
                markers += RecordContextMarker(value.id, it.point, if (selected) "경로 미확인" else "?", selected)
            }
            RecordContextKind.TRANSITION -> value.before?.let {
                markers += RecordContextMarker(value.id, it.point, "전환", selected)
            }
        }
    }
    val guide = selection?.takeIf { it.kind == RecordContextKind.GAP }?.let { value ->
        val before = value.before?.point; val after = value.after?.point
        if (before == null || after == null || before == after) null else GapGuideCommand(value.id, before, after)
    }
    // A return to exactly the same point must not stack two identical-position endpoint stamps.
    // Both control events remain in the flow list; a selected event keeps its own click target.
    val groupedEndpoints = endpoints.groupBy { it.point }.values.map { group ->
        if (group.size == 1) group.single() else (group.firstOrNull { it.selected } ?: group.last())
            .copy(label = "보행 시작 · 끝", kind = RouteEndpointKind.START_END)
    }
    return RecordContextLayerState(markers.distinctBy { it.contextId to it.point }, guide, groupedEndpoints)
}

@Composable
internal fun RecordContextDetail(value: RecordContext) {
    Text(recordContextTitle(value), style = MaterialTheme.typography.titleMedium)
    Text(recordContextTime(value), style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(12.dp))
    Text(recordContextDescription(value), style = MaterialTheme.typography.bodyMedium)
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun RecordContextPreview() { DaengsTheme { Column(Modifier.padding(20.dp)) {
    RecordContextDetail(RecordContext("preview", RecordContextKind.GAP, 1, 2, 0, 30_000, 30_000,
        null, null, RecordMovement.WALKING, RecordMovement.EXCLUDED))
} } }
