package com.daengs.app.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionProviderTest {
    @Test
    fun `동시 요청은 회전 refresh를 한 번만 보낸다`() = runBlocking {
        val refreshEntered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var stored: Session? = expiredSession()
        var refreshCalls = 0
        val refreshed = aliveSession("new")
        val provider = provider(
            load = { stored },
            save = { stored = it },
            clear = { stored = null },
            refresh = {
                refreshCalls += 1
                refreshEntered.complete(Unit)
                release.await()
                Result.success(refreshed)
            },
        )

        val first = async { provider.freshSession() }
        refreshEntered.await()
        val second = async { provider.freshSession() }
        yield()
        release.complete(Unit)

        assertEquals(listOf(refreshed, refreshed), listOf(first.await(), second.await()))
        assertEquals(1, refreshCalls)
        assertEquals(refreshed, stored)
    }

    @Test
    fun `refresh 중 로그아웃하면 늦은 응답이 토큰을 되살리지 않는다`() = runBlocking {
        val refreshEntered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var stored: Session? = expiredSession()
        val provider = provider(
            load = { stored },
            save = { stored = it },
            clear = { stored = null },
            refresh = {
                refreshEntered.complete(Unit)
                release.await()
                Result.success(aliveSession("late"))
            },
        )

        val refreshing = async { provider.freshSession() }
        refreshEntered.await()
        provider.clear()
        release.complete(Unit)

        assertNull(refreshing.await())
        assertNull(stored)
    }

    @Test
    fun `refresh 중 새 로그인하면 옛 응답이 새 세션을 덮지 않는다`() = runBlocking {
        val refreshEntered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var stored: Session? = expiredSession()
        val provider = provider(
            load = { stored },
            save = { stored = it },
            clear = { stored = null },
            refresh = {
                refreshEntered.complete(Unit)
                release.await()
                Result.success(aliveSession("late"))
            },
        )

        val refreshing = async { provider.freshSession() }
        refreshEntered.await()
        val login = aliveSession("login")
        provider.save(login)
        release.complete(Unit)

        assertNull(refreshing.await())
        assertEquals(login, stored)
    }

    @Test
    fun `토큰 refresh는 로컬 산책을 읽는 계정 scope를 바꾸지 않는다`() = runBlocking {
        var stored: Session? = expiredSession()
        val refreshed = aliveSession("new")
        val provider = provider(
            load = { stored },
            save = { stored = it },
            clear = { stored = null },
            refresh = { Result.success(refreshed) },
        )
        val before = provider.accountScope.value

        assertEquals(refreshed, provider.freshSession())

        assertEquals("user-1", before.ownerId)
        assertEquals(before, provider.accountScope.value)
    }

    @Test
    fun `로그아웃 뒤 같은 회원으로 로그인해도 옛 계정 scope는 재사용되지 않는다`() {
        var stored: Session? = aliveSession("old")
        val provider = provider(
            load = { stored },
            save = { stored = it },
            clear = { stored = null },
            refresh = { error("refresh 없이 계정 변경을 알려야 한다") },
        )
        val before = provider.accountScope.value

        provider.clear()
        assertEquals(AccountScope(null, before.generation + 1), provider.accountScope.value)

        provider.save(aliveSession("login"))
        assertEquals(AccountScope(before.ownerId, before.generation + 2), provider.accountScope.value)
    }

    @Test
    fun `refresh 만료로 저장 세션을 지우면 계정 scope도 로그아웃을 알린다`() = runBlocking {
        var stored: Session? = expiredSession().copy(refreshExpiresAtMs = NOW)
        val provider = provider(
            load = { stored },
            save = { stored = it },
            clear = { stored = null },
            refresh = { error("만료된 refresh를 요청하면 안 된다") },
        )
        val before = provider.accountScope.value

        assertNull(provider.freshSession())

        assertNull(stored)
        assertEquals(AccountScope(null, before.generation + 1), provider.accountScope.value)
    }

    /** 서버가 refresh 를 거절했다 — 다시 로그인해야 하지만, 앱이 먼저 지우지는 않는다. */
    @Test
    fun `refresh 가 401 이면 다시 로그인하라고 알리고 저장 세션은 그대로 둔다`() = runBlocking {
        var stored: Session? = expiredSession()
        val provider = provider(
            load = { stored },
            save = { stored = it },
            clear = { stored = null },
            refresh = { Result.failure(AuthApi.HttpStatusException(401, "만료된 세션입니다")) },
        )

        assertEquals(SessionCheck.LoginRequired, provider.checkSession())
        assertEquals(expiredSession(), stored)
    }

    /** 망이 끊겼거나 서버가 죽었다 — 일시적인 실패로 로그아웃시키지 않는다. */
    @Test
    fun `refresh 가 망 실패나 5xx 면 연결 실패로 알리고 세션을 지우지 않는다`() = runBlocking {
        for (failure in listOf(java.io.IOException("timeout"), AuthApi.HttpStatusException(503, "서버 오류 (503)"))) {
            var stored: Session? = expiredSession()
            val provider = provider(
                load = { stored },
                save = { stored = it },
                clear = { stored = null },
                refresh = { Result.failure(failure) },
            )

            assertEquals(SessionCheck.Unreachable, provider.checkSession())
            assertEquals(expiredSession(), stored)
            assertNull(provider.freshSession())
        }
    }

    @Test
    fun `저장된 세션이 없으면 로그인이 필요하다`() = runBlocking {
        val provider = provider(load = { null }, save = {}, clear = {}, refresh = { error("부르면 안 된다") })

        assertEquals(SessionCheck.LoginRequired, provider.checkSession())
    }

    @Test
    fun `살아 있는 access 는 refresh 없이 그대로 쓴다`() = runBlocking {
        val alive = aliveSession("now")
        val provider = provider(load = { alive }, save = {}, clear = {}, refresh = { error("부르면 안 된다") })

        assertEquals(SessionCheck.Fresh(alive), provider.checkSession())
    }

    private fun provider(
        load: () -> Session?,
        save: (Session) -> Unit,
        clear: () -> Unit,
        refresh: suspend (String) -> Result<Session>,
    ) = SessionProvider(
        loadSession = load,
        saveSession = save,
        clearSession = clear,
        refreshSession = refresh,
        configured = { true },
        now = { NOW },
    )

    private fun expiredSession() = Session(
        appUserId = "user-1",
        accessToken = "old-access",
        refreshToken = "old-refresh",
        accessExpiresAtMs = NOW,
        refreshExpiresAtMs = Long.MAX_VALUE,
    )

    private fun aliveSession(suffix: String) = Session(
        appUserId = "user-1",
        accessToken = "access-$suffix",
        refreshToken = "refresh-$suffix",
        accessExpiresAtMs = Long.MAX_VALUE,
        refreshExpiresAtMs = Long.MAX_VALUE,
    )

    private companion object {
        const val NOW = 1_000_000L
    }
}
