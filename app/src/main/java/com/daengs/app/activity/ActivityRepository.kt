package com.daengs.app.activity

import com.daengs.app.auth.Session
import kotlinx.coroutines.CancellationException

/** 앱 공용 SessionProvider를 통해서만 갱신한다. 결과 캐시·폴링·사용자 간 공유 상태는 없다. */
class ActivityRepository(
    private val client: ActivityClient,
    private val freshSession: suspend () -> Session?,
    private val currentSession: () -> Session?,
) {
    suspend fun currentSeason(): Result<ActivitySeason?> = read { client.currentSeason(it.accessToken) }

    suspend fun sessionLink(clientSessionId: String): Result<ActivitySessionLink> = read {
        client.sessionLink(it.accessToken, clientSessionId)
    }

    suspend fun walkSummary(window: ActivityWalkWindow): Result<ActivityWalkSummary> = read { session ->
        client.walkSummary(session.accessToken, window).also {
            require(it.ownerId == session.appUserId) { "Activity owner response mismatch" }
        }
    }

    suspend fun territorySummary(seasonId: String, petId: String): Result<ActivityTerritorySummary> = read {
        client.territorySummary(it.accessToken, seasonId, petId)
    }

    private suspend fun <T> read(operation: suspend (Session) -> T): Result<T> = try {
        val session = freshSession() ?: throw ActivityAuthenticationRequired()
        requireCurrent(session)
        val result = operation(session)
        requireCurrent(session)
        Result.success(result)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    private fun requireCurrent(session: Session) {
        val current = currentSession()
        // 같은 회원으로 재로그인했거나 다른 작업에서 refresh가 회전해도 보수적으로 폐기한다.
        if (current?.appUserId != session.appUserId || current.refreshToken != session.refreshToken) {
            throw ActivitySessionChanged()
        }
    }
}

class ActivityAuthenticationRequired : IllegalStateException("Activity authentication required")
class ActivitySessionChanged : IllegalStateException("Activity session changed during request")
