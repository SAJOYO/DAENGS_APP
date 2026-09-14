package com.daengs.app.ui.walk

import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import com.daengs.app.location.GeoPoint
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.*
import com.daengs.app.walk.routeexplorer.CompletedRouteReview
import com.daengs.app.walk.routeexplorer.RouteExplorerIndex

@Preview(showBackground = true, widthDp = 390, heightDp = 360)
@Preview(showBackground = true, widthDp = 320, heightDp = 360)
@Composable
private fun RouteExplorerPanelPreview() {
    val scope = rememberCoroutineScope()
    val state = remember {
        val summary = WalkSummary("preview", emptyList(), 0, 300_000, null, 80.0, 300_000,
            listOf(listOf(GeoPoint(37.5, 127.0), GeoPoint(37.5003, 127.0)),
                listOf(GeoPoint(37.51, 127.0), GeoPoint(37.5103, 127.0))).mapIndexed { segment, points ->
                points.mapIndexed { index, point -> com.daengs.app.location.LocationSample(
                    point, 10_000L + segment * 200_000 + index * 30_000, accuracyMeters = 3f) }
            }, null)
        val detail = WalkSessionDetail(summary, summary.toSessionRoute(), emptyList())
        WalkRouteExplorerState(scope, 300_000).apply {
            replaceRoute(RouteExplorerIndex(detail.route), CompletedRouteReview(detail), 300_000)
        }
    }
    DaengsTheme { WalkRouteExplorerPanel(state, {}) }
}
