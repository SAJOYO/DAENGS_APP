package com.daengs.app.ui.walk

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.map.layers.completedroute.*
import com.daengs.app.ui.theme.*
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
    val overview = state.mode == RouteExplorerMode.OVERVIEW || state.mode == RouteExplorerMode.REPLAY && state.timeRange == null
    val parts = if (review.observed.matches(review.detail)) review.observed.sections.flatMap { section ->
        val selected = if (state.mode == RouteExplorerMode.SCENE) focus?.observedParts?.filter { it.id == section.id }.orEmpty()
            else if (state.timeRange != null) state.selectedSlice?.observed?.filter { it.id == section.id }.orEmpty()
            else listOfNotNull(state.selectedAuxiliary?.takeIf { it.id == section.id })
        listOf(section.toRenderPart(false)) + selected.map { it.toRenderPart(true) }
    } else emptyList()
    return SessionRouteExplorerLayerState(
        highlightPaths = if (state.mode == RouteExplorerMode.SCENE) focus?.paths.orEmpty() else state.highlightPaths,
        cursor = state.replayFrame?.point, useOverviewDirections = overview, observedParts = parts,
        recordContext = recordContextLayer(review, state.selectedContext))
}

private fun ObservedRouteSection.toRenderPart(selected: Boolean) = RouteRenderPart(id, role(), path, selected,
    directions.map { RecordDirectionEdge("$id:${it.fromSeq}-${it.toSeq}", it.from, it.to) })

internal fun sceneRouteNotice(focus: SceneRouteFocus): String {
    val part = focus.observedParts.firstOrNull() ?: return sceneRouteNotice(focus.relation)
    val binding = focus.binding
    val context = if (binding != null && binding.locationAtMillis != binding.eventAtMillis) {
        (if (binding.locationAtMillis < binding.eventAtMillis) "이 장면 이전에" else "이 장면 이후에") +
            " 확인된 위치와 주변 경로예요. "
    } else "이 장면의 관측 경로를 강조했어요. "
    return context + (if (part.role() == RecordRouteRole.OBSERVED_EXCLUDED) "보행거리에는 포함되지 않아요."
        else "보행 여부는 확인되지 않았어요.") +
        if (part.directions.isEmpty()) " 이동 방향은 확인하기 어려워요." else ""
}

@Composable
internal fun ObservedRouteLegend(roles: List<RecordRouteRole>) {
    if (roles.isEmpty()) return
    val distinct = roles.distinct()
    var open by remember(distinct) { mutableStateOf(false) }
    val title = if (distinct.size == 1) RecordPresentationPolicy.label(distinct.single()) else "관측 경로 ${distinct.size}종"
    Box {
        Surface(onClick = { open = true }, shape = RoundedCornerShape(10.dp), color = CardWhite,
            shadowElevation = 2.dp, modifier = Modifier.testTag("diary-map-legend")
                .semantics { contentDescription = "$title 설명" }) {
            Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ObservedRouteSwatch(distinct.first())
                Text(title, fontSize = 11.sp, color = TextDark)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false },
            modifier = Modifier.widthIn(max = 280.dp).testTag("diary-map-legend-help")) {
            distinct.forEach { role -> Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ObservedRouteSwatch(role)
                    Text(RecordPresentationPolicy.label(role), style = MaterialTheme.typography.labelLarge)
                }
                Text(if (role == RecordRouteRole.OBSERVED_EXCLUDED)
                    "위치 기록은 남아 있지만 보행거리 합계에는 포함되지 않는 이동이에요."
                    else "위치는 기록됐지만 보행 여부가 확정되지 않았어요. 보행거리에 추가하지 않아요.",
                    Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = TextDark)
            } }
        }
    }
}

@Composable
private fun ObservedRouteSwatch(role: RecordRouteRole) {
    Canvas(Modifier.size(18.dp, 16.dp)) {
        val style = RecordPresentationPolicy.stroke(role, false)
        val a = Offset(0f, size.height / 2); val b = Offset(size.width, size.height / 2)
        drawLine(Color(style.color), a, b, (style.widthDp + 2 * style.railWidthDp).dp.toPx())
        drawLine(Color(style.centerColor), a, b, style.widthDp.dp.toPx())
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, widthDp = 320)
@Composable
private fun ObservedRouteLegendPreview() { DaengsTheme {
    ObservedRouteLegend(RecordRouteRole.entries)
} }
