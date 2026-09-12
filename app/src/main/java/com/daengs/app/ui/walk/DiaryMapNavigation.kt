package com.daengs.app.ui.walk

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.shell.MapLocationTarget
import com.daengs.app.map.shell.MapVisibilityResult
import com.daengs.app.walk.WalkSessionDetail
import com.daengs.app.walk.diary.DiaryScene
import com.daengs.app.walk.diary.diaryLocationGroups
import com.daengs.app.walk.routeexplorer.CompletedRouteReview

internal enum class DiaryMapView { WALKING, WHOLE, CUSTOM }

internal data class DiaryCameraRequest(
    val bounds: List<GeoPoint> = emptyList(), val center: GeoPoint? = null,
    val zoom: Double? = null, val minZoom: Double? = null, val expandedContext: Boolean = false,
    val revision: Int = 0,
)

/** Only explicit navigation changes a camera request. New data and map selections do not. */
@Stable
internal class DiaryMapNavigation {
    var camera by mutableStateOf(DiaryCameraRequest())
        private set
    var view by mutableStateOf(DiaryMapView.WALKING)
        private set
    fun initialize(points: List<GeoPoint>) {
        if (camera.bounds.isEmpty() && camera.center == null && points.isNotEmpty()) fit(points, DiaryMapView.WALKING)
    }
    fun fit(points: List<GeoPoint>, mode: DiaryMapView = DiaryMapView.CUSTOM, expandedContext: Boolean = false) {
        val bounds = diaryCameraBounds(points)
        if (bounds.isEmpty()) return
        camera = DiaryCameraRequest(bounds = bounds, expandedContext = expandedContext, revision = camera.revision + 1)
        view = mode
    }
    fun locate(point: GeoPoint?, fromMap: Boolean = false, zoom: Double? = null, minZoom: Double? = null) {
        if (fromMap || point == null || !point.isDiaryLocation()) return
        camera = DiaryCameraRequest(center = point, bounds = camera.bounds, zoom = zoom, minZoom = minZoom,
            revision = camera.revision + 1)
        view = DiaryMapView.CUSTOM
    }
    fun gesture() { view = DiaryMapView.CUSTOM }

    companion object {
        // Only two bounding corners, never a whole walk in the saved-state bundle.
        val Saver = listSaver<DiaryMapNavigation, Any>(save = { state ->
            val c = state.camera
            listOf(state.view.name, c.revision, c.expandedContext, c.zoom ?: -1.0, c.minZoom ?: -1.0,
                c.center?.latitude ?: Double.NaN, c.center?.longitude ?: Double.NaN) +
                c.bounds.flatMap { listOf(it.latitude, it.longitude) }
        }, restore = { values -> DiaryMapNavigation().apply {
            view = DiaryMapView.valueOf(values[0] as String)
            camera = DiaryCameraRequest(
                bounds = values.drop(7).chunked(2).map { GeoPoint(it[0] as Double, it[1] as Double) },
                center = (values[5] as Double).takeIf { it.isFinite() }?.let { GeoPoint(it, values[6] as Double) },
                zoom = (values[3] as Double).takeIf { it >= 0 }, minZoom = (values[4] as Double).takeIf { it >= 0 },
                expandedContext = values[2] as Boolean, revision = values[1] as Int)
        } })
    }
}

internal fun GeoPoint.isDiaryLocation() = latitude.isFinite() && longitude.isFinite() &&
    latitude in -90.0..90.0 && longitude in -180.0..180.0

internal fun diaryCameraBounds(points: List<GeoPoint>): List<GeoPoint> {
    val valid = points.filter { it.isDiaryLocation() }
    if (valid.isEmpty()) return emptyList()
    return listOf(GeoPoint(valid.minOf { it.latitude }, valid.minOf { it.longitude }),
        GeoPoint(valid.maxOf { it.latitude }, valid.maxOf { it.longitude })).distinct()
}

internal fun diaryWholeRecordBounds(detail: WalkSessionDetail, review: CompletedRouteReview?, scenes: List<DiaryScene>): List<GeoPoint> =
    diaryCameraBounds(detail.route.bounds + review?.observed?.sections.orEmpty().flatMap { it.path } +
        review?.context?.contexts.orEmpty().flatMap { it.locations } + scenes.mapNotNull { it.point })
        .ifEmpty { diaryCameraBounds(listOfNotNull(detail.summary.anchor)) }

internal fun diaryWalkingBounds(detail: WalkSessionDetail, review: CompletedRouteReview?, scenes: List<DiaryScene>): List<GeoPoint> =
    diaryCameraBounds(detail.route.bounds).ifEmpty { diaryWholeRecordBounds(detail, review, scenes) }

/** Count scene identities, including every number sharing a marker, at its actual display position. */
internal fun diaryVisibilityTargets(scenes: List<DiaryScene>): List<MapLocationTarget> =
    diaryLocationGroups(scenes.filter { it.point?.isDiaryLocation() == true }).flatMap { group ->
        group.map { it.id to requireNotNull(group.first().point) }
    }.groupBy({ it.first }, { it.second }).map { (id, points) -> MapLocationTarget(id, points.distinct()) }

internal fun diaryOffscreenScenes(scenes: List<DiaryScene>, result: MapVisibilityResult?): List<DiaryScene> {
    val visible = result?.visibleIds ?: return emptyList()
    val located = result.query.targets.map { it.id }.toSet()
    return scenes.distinctBy { it.id }.filter { it.id in located && it.id !in visible }
}
