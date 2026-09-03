package com.daengs.app.territory

fun interface TerritorySiteRepository {
    suspend fun nearby(request: NearbyTerritorySitesRequest): TerritorySitePage
}

class HttpTerritorySiteRepository(
    private val api: TerritorySiteApi,
) : TerritorySiteRepository {
    override suspend fun nearby(request: NearbyTerritorySitesRequest): TerritorySitePage =
        api.nearby(request)
}
