package com.daengs.app.place.bookmarks

import com.daengs.app.activity.ActivityAuthenticationRequired
import com.daengs.app.activity.ActivitySessionChanged
import com.daengs.app.auth.AccountScope
import com.daengs.app.auth.Session
import com.daengs.app.place.PlaceKey
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonObject

class PlaceBookmarkRepository(private val client: PlaceBookmarkClient,
    private val freshSession: suspend () -> Session?, private val currentAccount: () -> AccountScope) {
    suspend fun list(account: AccountScope) = request(account) { client.list(it) }
    suspend fun set(account: AccountScope, key: PlaceKey, saved: Boolean) = request(account) { client.set(it, key, saved) }
    suspend fun search(account: AccountScope, filters: JsonObject) = request(account) { client.search(it, filters) }
    suspend fun interpret(account: AccountScope, query: String, filters: JsonObject) =
        request(account) { client.interpret(it, query, filters) }
    fun isCurrent(account: AccountScope) = currentAccount() == account
    private suspend fun <T> request(account: AccountScope, block: suspend (String) -> T): T {
        if (account.ownerId == null) throw ActivityAuthenticationRequired()
        fun current() { if (currentAccount() != account) throw ActivitySessionChanged() }
        current()
        val session = freshSession() ?: throw ActivityAuthenticationRequired()
        current()
        if (session.appUserId != account.ownerId) throw ActivitySessionChanged()
        val result = block(session.accessToken)
        currentCoroutineContext().ensureActive(); current()
        return result
    }
}
