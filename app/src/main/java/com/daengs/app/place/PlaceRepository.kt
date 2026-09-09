package com.daengs.app.place

fun interface PlaceSearchRepository {
    suspend fun search(request: PlaceSearchRequest): PlaceSearchResponse
    suspend fun filterCapabilities(): PlaceFilterCapabilities = throw PlaceApiException(404, "filters unavailable")
    suspend fun searchFiltered(request: PlaceFilterRequest): PlaceFilterResponse = throw PlaceApiException(404, "filters unavailable")
}

class PlaceRepository(private val api: PlaceApi) : PlaceSearchRepository {
    override suspend fun filterCapabilities() = api.filterCapabilities()
    override suspend fun searchFiltered(request: PlaceFilterRequest) = api.searchFiltered(request)
    override suspend fun search(request: PlaceSearchRequest): PlaceSearchResponse =
        api.search(request)
}
