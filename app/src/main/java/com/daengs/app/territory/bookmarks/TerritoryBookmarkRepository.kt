package com.daengs.app.territory.bookmarks

import com.daengs.app.activity.ActivityAuthenticationRequired
import com.daengs.app.activity.ActivitySessionChanged
import com.daengs.app.auth.AccountScope
import com.daengs.app.auth.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A login lifetime owns every read/write. Rotating access tokens does not change this scope. */
class TerritoryBookmarkRepository(
    private val client: TerritoryBookmarkClient,
    private val freshSession: suspend () -> Session?,
    private val currentAccount: () -> AccountScope,
) {
    suspend fun list(account: AccountScope) = request(account) { client.list(it) }
    suspend fun set(account: AccountScope, siteId: String, saved: Boolean) =
        request(account) { requireBookmarkSite(siteId); client.set(it, siteId, saved) }

    private suspend fun <T> request(account: AccountScope, block: suspend (String) -> T): Result<T> = try {
        if (account.ownerId == null) throw ActivityAuthenticationRequired()
        requireCurrent(account)
        val session = freshSession() ?: throw ActivityAuthenticationRequired()
        requireCurrent(account)
        if (session.appUserId != account.ownerId) throw ActivitySessionChanged()
        val result = block(session.accessToken)
        currentCoroutineContext().ensureActive()
        requireCurrent(account)
        Result.success(result)
    } catch (error: CancellationException) { throw error
    } catch (error: Exception) { Result.failure(error) }

    private fun requireCurrent(account: AccountScope) {
        if (currentAccount() != account) throw ActivitySessionChanged()
    }
}
