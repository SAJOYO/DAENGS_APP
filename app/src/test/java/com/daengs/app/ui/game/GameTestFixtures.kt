package com.daengs.app.ui.game

import com.daengs.app.activity.*
import com.daengs.app.auth.Session

internal const val GAME_PET_A = "00000000-0000-0000-0000-000000000001"
internal const val GAME_PET_B = "00000000-0000-0000-0000-000000000002"
internal val gameTestSession = Session("owner", "token", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
internal open class GameTestClient : ActivityClient {
    var season: ActivitySeason? = previewGameOverview().season
    var error: Exception? = null
    override suspend fun currentSeason(token: String): ActivitySeason? { error?.let { throw it }; return season }
    override suspend fun territorySummary(token: String, seasonId: String, petId: String) =
        checkNotNull(previewGameOverview().summary).copy(seasonId = seasonId, petId = petId)
    override suspend fun sessionLink(token: String, clientSessionId: String): ActivitySessionLink = error("unused")
    override suspend fun walkSummary(token: String, window: ActivityWalkWindow): ActivityWalkSummary = error("unused")
}
internal fun gameTestRepository(client: ActivityClient) = ActivityRepository(client, { gameTestSession }, { gameTestSession })
