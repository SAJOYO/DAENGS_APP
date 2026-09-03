package com.daengs.app.map.features.places

import com.daengs.app.map.layers.places.PlaceMarkerState
import com.daengs.app.place.DogAccessState
import com.daengs.app.place.PlaceKey
import com.daengs.app.place.PlaceKind
import com.daengs.app.place.PlaceSearchGroup
import com.daengs.app.place.PlaceSearchHit
import com.daengs.app.place.PlaceSortType
import com.daengs.app.place.retryable
import com.daengs.app.place.supportsParkingPreference

data class PlaceCategory(
    val kind: PlaceKind,
    val label: String,
)

data class DogAccessCoverage(
    val compatible: Int,
    val incompatible: Int,
    val unknown: Int,
)

sealed interface PlaceResultsPresentation {
    data class Loading(val message: String) : PlaceResultsPresentation
    data object Failed : PlaceResultsPresentation
    data class Initial(val message: String) : PlaceResultsPresentation
    data class Empty(val message: String) : PlaceResultsPresentation
    data class Content(
        val countLabel: String,
        val hits: List<PlaceSearchHit>,
    ) : PlaceResultsPresentation
}

data class PlacePanelPresentation(
    val selectedKind: PlaceKind,
    val title: String,
    val originDescription: String,
    val sortDescription: String,
    val showParkingPreference: Boolean,
    val shoppingNotice: String? = null,
    val parkingCoverage: String? = null,
    val dogAccessCoverage: String? = null,
    val errorMessage: String? = null,
    val retryable: Boolean = false,
    val results: PlaceResultsPresentation,
)

/** 서버가 정한 종류 그대로다. 여가/볼일 같은 축을 임의로 만들지 않는다 — 칩 하나가 서버 그룹 하나다. */
val PLACE_CATEGORIES = listOf(
    PlaceCategory(PlaceKind.CAFE, "카페"),
    PlaceCategory(PlaceKind.RESTAURANT, "음식점"),
    PlaceCategory(PlaceKind.PET_SHOP, "펫샵"),
    PlaceCategory(PlaceKind.SHOPPING, "일반 쇼핑"),
    PlaceCategory(PlaceKind.GROOMING, "미용"),
    PlaceCategory(PlaceKind.BOARDING, "위탁"),
    PlaceCategory(PlaceKind.HOSPITAL, "동물병원"),
    PlaceCategory(PlaceKind.PHARMACY, "약국"),
    PlaceCategory(PlaceKind.TRAVEL, "여행지"),
    PlaceCategory(PlaceKind.LEISURE, "레저"),
    PlaceCategory(PlaceKind.PENSION, "펜션"),
    PlaceCategory(PlaceKind.HOTEL, "호텔"),
    PlaceCategory(PlaceKind.STAY, "숙박"),
    PlaceCategory(PlaceKind.MUSEUM, "박물관"),
    PlaceCategory(PlaceKind.GALLERY, "미술관"),
    PlaceCategory(PlaceKind.ARTS_CENTER, "문예회관"),
    PlaceCategory(PlaceKind.CULTURE, "문화시설"),
    PlaceCategory(PlaceKind.ETC, "기타"),
)

val DEFAULT_PLACE_KIND = PlaceKind.CAFE

fun PlaceDiscoveryState.toPanelPresentation(): PlacePanelPresentation {
    val selectedKind = selectedPlaceKind(this)
    val group = response?.groups?.firstOrNull()
    val failure = (search as? PlaceSearchState.Failed)?.failure
    return PlacePanelPresentation(
        selectedKind = selectedKind,
        title = placePanelTitle(selectedKind),
        originDescription =
        "${originLabel(originMode)} · 카테고리 하나씩 사실 그대로 검색합니다.",
        sortDescription = group?.let(::sortLabel) ?: "가까운 순",
        showParkingPreference = selectedKind.supportsParkingPreference(),
        shoppingNotice = if (selectedKind == PlaceKind.SHOPPING) {
            "일반 쇼핑은 원천 데이터에 주차·입장 조건 같은 상세 사실이 대부분 없습니다."
        } else {
            null
        },
        parkingCoverage = group?.sort?.coverage?.get("parking")?.let { coverage ->
            "반환 결과 주차 정보 · 가능 ${coverage.knownTrue} · " +
                "불가 ${coverage.knownFalse} · 미상 ${coverage.unknown}"
        },
        dogAccessCoverage = group?.let(::dogAccessCoverage)?.let { coverage ->
            "입장 평가 · 가능 ${coverage.compatible} · 불일치 ${coverage.incompatible} · " +
                "미상 ${coverage.unknown}"
        },
        errorMessage = error,
        retryable = failure?.retryable == true,
        results = when {
            loading -> PlaceResultsPresentation.Loading(
                "${categoryLabel(selectedKind)} 찾는 중",
            )
            search is PlaceSearchState.Failed -> PlaceResultsPresentation.Failed
            group == null && requestedKinds.isEmpty() -> PlaceResultsPresentation.Initial(
                "현재 위치를 확인하면 주변 ${categoryLabel(selectedKind)}를 보여드릴게요.",
            )
            group == null || group.results.isEmpty() -> PlaceResultsPresentation.Empty(
                "이 반경에서 ${categoryLabel(selectedKind)} 결과를 찾지 못했습니다.",
            )
            else -> PlaceResultsPresentation.Content(
                countLabel = "${group.results.size}곳" +
                    if (group.truncated) " · 서버 한도에서 잘림" else "",
                hits = group.results,
            )
        },
    )
}

fun selectedPlaceKind(state: PlaceDiscoveryState): PlaceKind =
    state.requestedKinds.singleOrNull() ?: DEFAULT_PLACE_KIND

fun canonicalPlaceMarkers(state: PlaceDiscoveryState): List<PlaceMarkerState> =
    state.response?.groups.orEmpty().flatMap { group ->
        group.results.map { hit ->
            PlaceMarkerState(
                id = placeMarkerId(hit.place.key),
                point = hit.place.point,
                label = hit.place.name,
                selected = hit.place.key == state.selectedPlaceKey,
                iconGroup = hit.place.iconGroup,
            )
        }
    }

fun canonicalPlaceKeysByMarker(state: PlaceDiscoveryState): Map<String, PlaceKey> =
    state.response?.groups.orEmpty().flatMap(PlaceSearchGroup::results)
        .associate { hit -> placeMarkerId(hit.place.key) to hit.place.key }

/** 길이 접두사로 source와 ref의 경계를 보존해 서로 다른 원천 키의 marker id 충돌을 막는다. */
fun placeMarkerId(key: PlaceKey): String = "place:${key.source.length}:${key.source}${key.ref}"

fun categoryLabel(kind: PlaceKind): String =
    PLACE_CATEGORIES.firstOrNull { it.kind == kind }?.label ?: kind.wire

fun placePanelTitle(kind: PlaceKind): String = when (kind) {
    PlaceKind.HOSPITAL -> "내 주변 동물병원"
    else -> "내 주변 장소"
}

fun originLabel(mode: PlaceOriginMode): String = when (mode) {
    PlaceOriginMode.DEVICE -> "내 위치 기준"
    PlaceOriginMode.PINNED -> "지도를 움직인 위치 기준"
}

fun dogAccessCoverage(group: PlaceSearchGroup): DogAccessCoverage? {
    val evaluations = group.results.mapNotNull { it.evaluations.dogAccess }
    if (evaluations.isEmpty()) return null
    return DogAccessCoverage(
        compatible = evaluations.count { it.state == DogAccessState.COMPATIBLE },
        incompatible = evaluations.count { it.state == DogAccessState.INCOMPATIBLE },
        unknown = evaluations.count { it.state == DogAccessState.UNKNOWN },
    )
}

fun sortLabel(group: PlaceSearchGroup): String = when (group.sort.type) {
    PlaceSortType.DISTANCE -> "가까운 순"
    PlaceSortType.DISTANCE_PREFERRED -> {
        val band = group.sort.bandMeters
        if (band == null) "서버 지정 선호순" else "${band}m 구간 안에서 주차 가능 우선"
    }
}
