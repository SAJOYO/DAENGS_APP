package com.daengs.app.ui.walk

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.map.layers.completedroute.*
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.WalkSessionDetail
import com.daengs.app.walk.routeexplorer.SceneRouteFocus
import com.daengs.app.walk.trajectory.*

internal fun ObservedRouteSection.role() = if (walkingUse == LegacyWalkingUse.EXCLUDED)
    RecordRouteRole.OBSERVED_EXCLUDED else RecordRouteRole.OBSERVED_UNRESOLVED
internal fun observedRouteLabel(section: ObservedRouteSection) = RecordPresentationPolicy.label(section.role())
internal fun observedRouteDescription(section: ObservedRouteSection) = when (section.role()) {
    RecordRouteRole.OBSERVED_EXCLUDED -> "위치가 이어서 기록된 경로예요. 보행거리 합계에는 포함되지 않아요."
    RecordRouteRole.OBSERVED_UNRESOLVED -> "위치가 이어서 기록됐지만 보행 여부는 확정되지 않았어요. 보행거리를 추가하지 않아요."
}

/** Same pure adapter for the real detail screen and the phone review app. */
internal fun recordPresentationLayer(state: WalkRouteExplorerState, detail: WalkSessionDetail?,
    focus: SceneRouteFocus?): SessionRouteExplorerLayerState {
    val review = state.review?.takeIf { it.detail === detail }
        ?: return SessionRouteExplorerLayerState(useOverviewDirections = false)
    val overview = state.mode in setOf(RouteExplorerMode.OVERVIEW, RouteExplorerMode.REPLAY)
    val parts = if (review.observed.matches(review.detail)) review.observed.sections.flatMap { section ->
        val selected = if (state.mode == RouteExplorerMode.SCENE) focus?.observedParts?.filter { it.id == section.id }.orEmpty()
            else listOfNotNull(state.selectedAuxiliary?.takeIf { it.id == section.id })
        listOf(section.toRenderPart(false)) + selected.map { it.toRenderPart(true) }
    } else emptyList()
    return SessionRouteExplorerLayerState(
        highlightPaths = if (state.mode == RouteExplorerMode.SCENE) focus?.paths.orEmpty() else state.highlightPaths,
        cursor = state.replayFrame?.point, useOverviewDirections = overview, observedParts = parts)
}

private fun ObservedRouteSection.toRenderPart(selected: Boolean) = RouteRenderPart(id, role(), path, selected,
    directions.map { RecordDirectionEdge("$id:${it.fromSeq}-${it.toSeq}", it.from, it.to) })

internal fun sceneRouteNotice(focus: SceneRouteFocus): String {
    val part = focus.observedParts.firstOrNull() ?: return sceneRouteNotice(focus.relation)
    val binding = focus.binding
    val context = if (binding != null && binding.locationAtMillis != binding.eventAtMillis) {
        (if (binding.locationAtMillis < binding.eventAtMillis) "이 장면 이전에" else "이 장면 이후에") +
            " 확인된 ${formatRouteExplorerClock(binding.locationAtMillis)} 위치와 주변 경로예요. "
    } else "이 장면의 관측 경로를 강조했어요. "
    return context + (if (part.role() == RecordRouteRole.OBSERVED_EXCLUDED) "보행거리에는 포함되지 않아요."
        else "보행 여부는 확인되지 않았어요.") +
        if (part.directions.isEmpty()) " 이동 방향은 확인하기 어려워요." else ""
}

@Composable
internal fun ObservedRouteLegend(roles: List<RecordRouteRole>) {
    if (roles.isEmpty()) return
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        roles.distinct().forEach { role ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Canvas(Modifier.size(32.dp, 18.dp)) {
                    val style = RecordPresentationPolicy.stroke(role, false)
                    val a = Offset(0f, size.height / 2); val b = Offset(size.width, size.height / 2)
                    drawLine(Color(style.color), a, b, (style.widthDp + 2 * style.railWidthDp).dp.toPx())
                    drawLine(Color(style.centerColor), a, b, style.widthDp.dp.toPx())
                }
                Text(RecordPresentationPolicy.label(role), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, widthDp = 320)
@Composable
private fun ObservedRouteLegendPreview() { DaengsTheme {
    ObservedRouteLegend(RecordRouteRole.entries)
} }
