package com.daengs.app.activity

import com.daengs.app.auth.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ActivityRepositoryTest {
    private fun session(owner: String = ActivityFixtures.OWNER, refresh: String = "refresh") =
        Session(owner, "fresh-access", refresh, Long.MAX_VALUE, Long.MAX_VALUE)

    @Test fun `uses shared fresh session for all three reads without eager calls`() = runBlocking {
        val auth = session()
        val client = FakeClient()
        var refreshes = 0
        val repo = ActivityRepository(client, { refreshes++; auth }, { auth })
        assertEquals(0, client.tokens.size)
        repo.sessionLink(ActivityFixtures.SESSION).getOrThrow()
        repo.walkSummary(ActivityFixtures.window).getOrThrow()
        repo.territorySummary(ActivityFixtures.SEASON, ActivityFixtures.PET).getOrThrow()
        assertEquals(3, refreshes)
        assertEquals(listOf("fresh-access", "fresh-access", "fresh-access"), client.tokens)
    }

    @Test fun `missing authentication never calls server`() = runBlocking {
        val client = FakeClient()
        val result = ActivityRepository(client, { null }, { null }).sessionLink(ActivityFixtures.SESSION)
        assertTrue(result.exceptionOrNull() is ActivityAuthenticationRequired)
        assertTrue(client.tokens.isEmpty())
    }

    @Test fun `logout account switch and same account relogin discard late responses`() = runBlocking {
        listOf(null, session(ActivityFixtures.PET), session(refresh = "new-login")).forEach { replacement ->
            val initial = session()
            var current: Session? = initial
            val client = FakeClient().apply { afterRead = { current = replacement } }
            val result = ActivityRepository(client, { initial }, { current }).sessionLink(ActivityFixtures.SESSION)
            assertTrue(result.exceptionOrNull() is ActivitySessionChanged)
        }
    }

    @Test fun `session replaced during refresh does not start a request`() = runBlocking {
        val client = FakeClient()
        val result = ActivityRepository(client, { session() }, { session(ActivityFixtures.PET) })
            .sessionLink(ActivityFixtures.SESSION)
        assertTrue(result.exceptionOrNull() is ActivitySessionChanged)
        assertTrue(client.tokens.isEmpty())
    }

    @Test fun `different response owner is rejected`() = runBlocking {
        val auth = session()
        val client = FakeClient().apply { responseOwner = ActivityFixtures.PET }
        assertTrue(ActivityRepository(client, { auth }, { auth }).walkSummary(ActivityFixtures.window).isFailure)
    }

    @Test fun `disabled service stays a failure and is not retried or cached as empty`() = runBlocking {
        val auth = session()
        val failure = ActivityHttpException(503, "activity_disabled")
        val client = FakeClient().apply { afterRead = { throw failure } }
        val result = ActivityRepository(client, { auth }, { auth }).sessionLink(ActivityFixtures.SESSION)
        assertSame(failure, result.exceptionOrNull())
        assertEquals(1, client.tokens.size)
    }

    @Test fun `cancellation propagates instead of becoming a Result failure`() = runBlocking {
        val auth = session()
        val client = FakeClient().apply { afterRead = { throw CancellationException("cancel") } }
        val error = runCatching {
            ActivityRepository(client, { auth }, { auth }).sessionLink(ActivityFixtures.SESSION)
        }.exceptionOrNull()
        assertTrue(error is CancellationException)
    }

    private class FakeClient : ActivityClient {
        val tokens = mutableListOf<String>()
        var afterRead: () -> Unit = {}
        var responseOwner = ActivityFixtures.OWNER
        override suspend fun sessionLink(token: String, clientSessionId: String): ActivitySessionLink {
            tokens += token; afterRead()
            return ActivityJson.session(ActivityFixtures.session())
        }
        override suspend fun walkSummary(token: String, window: ActivityWalkWindow): ActivityWalkSummary {
            tokens += token; afterRead()
            return ActivityJson.walk(ActivityFixtures.walk()).copy(ownerId = responseOwner)
        }
        override suspend fun territorySummary(token: String, seasonId: String, petId: String): ActivityTerritorySummary {
            tokens += token; afterRead()
            return ActivityJson.territory(ActivityFixtures.territory())
        }
    }
}
