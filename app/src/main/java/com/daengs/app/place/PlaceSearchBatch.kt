package com.daengs.app.place

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerializationException

/** 한 화면 요청을 최대 세 묶음으로 실행. 하나라도 실패하면 부분 결과를 성공으로 노출하지 않는다. */
suspend fun searchPlaceBatches(
    repository: PlaceSearchRepository,
    requests: List<PlaceSearchRequest>,
): PlaceSearchResponse = coroutineScope {
    require(requests.isNotEmpty() && requests.size <= 3)
    if (requests.size == 1) return@coroutineScope repository.search(requests.single()).also { it.requireDogEcho(requests.single()) }
    val responses = requests.map { request ->
        async {
            repository.search(request).also { response ->
                response.requireDogEcho(request)
                if (response.groups.map { it.kind } != request.kinds) {
                    throw SerializationException("Missing or reordered category batch")
                }
            }
        }
    }.awaitAll()
    if (responses.any { it.conditions != responses.first().conditions || it.dogs != responses.first().dogs }) {
        throw SerializationException("Different dog conditions across category batches")
    }
    PlaceSearchResponse(responses.first().conditions, responses.flatMap { it.groups }, responses.first().dogs)
}

/** Old servers may ignore dogs. Missing, stale or incomplete evaluation must not look successful. */
fun PlaceSearchResponse.requireDogEcho(request: PlaceSearchRequest) {
    if (dogs != request.dogs) throw SerializationException("Server did not confirm selected dogs")
    val refs = dogs.map { it.ref }
    if (groups.flatMap { it.results }.any { it.evaluations.dogs.map { dog -> dog.ref } != refs }) {
        throw SerializationException("Server returned incomplete per-dog evaluations")
    }
    if (groups.flatMap { it.results }.any { hit ->
        hit.place.match.kind !in listOf(PlaceKind.HOSPITAL, PlaceKind.PHARMACY) &&
            hit.evaluations.dogs.any { it.dogAccess == null || it.restrictions == null }
    }) throw SerializationException("Server returned missing dog evaluation axes")
}

/** 전체보기의 표시 정책. 원본 그룹과 분류는 보존하고 화면에서만 canonical ID를 접는다. */
fun PlaceSearchResponse.overviewHits(preferParking: Boolean): List<PlaceSearchHit> =
    groups.flatMap { it.results }.distinctBy { it.place.key }.sortedWith(
        compareBy<PlaceSearchHit> { if (preferParking) it.place.distanceMeters / 500 else 0 }
            .thenBy { if (preferParking && it.place.facts.parking == true) 0 else 1 }
            .thenBy { it.place.distanceMeters }
            .thenBy { it.place.key.source }
            .thenBy { it.place.key.ref },
    )
