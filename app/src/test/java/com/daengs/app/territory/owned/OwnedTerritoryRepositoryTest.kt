package com.daengs.app.territory.owned

import com.daengs.app.activity.ActivityAuthenticationRequired
import com.daengs.app.activity.ActivitySessionChanged
import com.daengs.app.auth.Session
import com.daengs.app.territory.support.ownedJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OwnedTerritoryRepositoryTest {
    @Test fun `authentication is checked before and after a read including relogin`() = runBlocking {
        val original = Session("owner", "access", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
        listOf(null, original.copy(appUserId = "other"), original.copy(refreshToken = "new")).forEach { next ->
            var session: Session? = original
            val repository = OwnedTerritoryRepository(OwnedTerritoryClient { token, _, _ ->
                assertEquals("access", token); session = next; parseOwnedTerritories(ownedJson(), null)
            }, { original }, { session })
            assertTrue(repository.page("owner", null, null).exceptionOrNull() is ActivitySessionChanged)
        }
    }

    @Test fun `missing or wrong refreshed member never reaches the server`() = runBlocking {
        val client = OwnedTerritoryClient { _, _, _ -> error("must not call") }
        assertTrue(OwnedTerritoryRepository(client, { null }, { null }).page("owner", null, null).exceptionOrNull() is ActivityAuthenticationRequired)
        val other = Session("other", "access", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
        assertTrue(OwnedTerritoryRepository(client, { other }, { other }).page("owner", null, null).exceptionOrNull() is ActivitySessionChanged)
    }

    @Test fun `failures stay failures and cancellation propagates`() = runBlocking {
        val auth = Session("owner", "access", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
        val disabled = OwnedTerritoryHttpException(503, "activity_disabled")
        var failure: Exception = disabled
        val repository = OwnedTerritoryRepository(OwnedTerritoryClient { _, _, _ -> throw failure }, { auth }, { auth })
        assertSame(disabled, repository.page("owner", null, null).exceptionOrNull())
        failure = CancellationException()
        assertTrue(runCatching { repository.page("owner", null, null) }.exceptionOrNull() is CancellationException)
    }
}
