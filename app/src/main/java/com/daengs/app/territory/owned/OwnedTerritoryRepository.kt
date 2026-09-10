package com.daengs.app.territory.owned

import com.daengs.app.activity.ActivityAuthenticationRequired
import com.daengs.app.activity.ActivitySessionChanged
import com.daengs.app.auth.Session
import kotlinx.coroutines.CancellationException

class OwnedTerritoryRepository(
    private val client: OwnedTerritoryClient,
    private val freshSession: suspend () -> Session?,
    private val currentSession: () -> Session?,
) {
    suspend fun page(ownerId: String, petId: String?, cursor: String?): Result<OwnedTerritoryPage> = try {
        val session = freshSession() ?: throw ActivityAuthenticationRequired()
        requireCurrent(session, ownerId)
        val page = client.fetch(session.accessToken, petId, cursor)
        requireCurrent(session, ownerId)
        require(page.petId == petId) { "Owned territory filter mismatch" }
        Result.success(page)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    private fun requireCurrent(session: Session, ownerId: String) {
        val current = currentSession()
        if (session.appUserId != ownerId || current?.appUserId != ownerId || current.refreshToken != session.refreshToken) {
            throw ActivitySessionChanged()
        }
    }
}
