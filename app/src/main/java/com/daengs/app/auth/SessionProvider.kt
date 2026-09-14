package com.daengs.app.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 토큰 없이 구분하는 로그인 생애. 같은 회원의 재로그인도 다른 scope다. */
data class AccountScope(val ownerId: String?, val generation: Long)

/** [SessionProvider.checkSession] 의 결과. 망 실패와 로그인 만료를 가른다. */
sealed interface SessionCheck {
    data class Fresh(val session: Session) : SessionCheck

    /** 저장된 세션이 없거나 refresh 가 만료·거절(401·403)됐다. 다시 로그인해야 한다. */
    data object LoginRequired : SessionCheck

    /** 서버에 못 닿았거나 5xx 였다. 세션은 그대로 두고 다시 시도한다. */
    data object Unreachable : SessionCheck
}

/**
 * access 토큰을 다시 받을 수 있는 경로와 저장 변경을 프로세스에서 하나로 모은다.
 *
 * refresh 토큰은 한 번 쓰면 새 값으로 회전한다. 화면과 백그라운드 Worker가 같은 옛
 * refresh 토큰을 동시에 보내면 서버의 재사용 감지로 회원의 모든 세션이 끊길 수 있다.
 * [refreshMutex]가 네트워크 refresh를 직렬화하고, [generation]은 그 사이 로그아웃이나
 * 새 로그인이 일어나면 늦게 돌아온 옛 응답이 새 세션을 덮지 못하게 한다.
 */
class SessionProvider internal constructor(
    private val loadSession: () -> Session?,
    private val saveSession: (Session) -> Unit,
    private val clearSession: () -> Unit,
    private val refreshSession: suspend (String) -> Result<Session>,
    private val configured: () -> Boolean,
    private val now: () -> Long,
) {
    constructor(store: TokenStore) : this(
        loadSession = store::load,
        saveSession = store::save,
        clearSession = store::clear,
        refreshSession = AuthApi::refresh,
        configured = { AuthApi.configured },
        now = System::currentTimeMillis,
    )

    private val refreshMutex = Mutex()
    private val stateLock = Any()
    private var generation = 0L
    private val mutableAccountScope = MutableStateFlow(synchronized(stateLock) {
        scopeFor(loadSession())
    })
    val accountScope: StateFlow<AccountScope> = mutableAccountScope.asStateFlow()

    suspend fun freshSession(): Session? = (checkSession() as? SessionCheck.Fresh)?.session

    /**
     * [freshSession] 과 같지만 **못 받은 이유**를 돌려준다.
     *
     * 망이 끊긴 것과 로그인이 만료된 것을 가른다 — 앞쪽은 다시 시도하면 되고 뒤쪽은 다시
     * 로그인해야 한다. **어느 쪽이든 여기서 로그아웃시키지 않는다**(refresh 가 이미 만료돼
     * 지우던 기존 갈래만 그대로다). 서버가 refresh 를 거절한 것(401·403)도 저장 세션은
     * 두고, 다시 로그인할지는 화면이 사용자에게 묻는다.
     */
    suspend fun checkSession(): SessionCheck = refreshMutex.withLock {
        val snapshot = synchronized(stateLock) { generation to loadSession() }
        val saved = snapshot.second ?: return@withLock SessionCheck.LoginRequired
        val at = now()

        if (saved.accessAlive(at)) {
            return@withLock synchronized(stateLock) {
                if (generation == snapshot.first) SessionCheck.Fresh(saved) else superseded()
            }
        }
        if (!saved.refreshAlive(at)) {
            synchronized(stateLock) {
                if (generation == snapshot.first) {
                    generation += 1
                    clearSession()
                    publishAccountScope(null)
                }
            }
            return@withLock SessionCheck.LoginRequired
        }
        if (!configured()) return@withLock SessionCheck.Unreachable

        val refreshed = refreshSession(saved.refreshToken).getOrElse { failure ->
            val status = (failure as? AuthApi.HttpStatusException)?.status
            return@withLock if (status == 401 || status == 403) {
                SessionCheck.LoginRequired
            } else {
                SessionCheck.Unreachable
            }
        }
        synchronized(stateLock) {
            if (generation != snapshot.first) return@synchronized superseded()
            saveSession(refreshed)
            publishAccountScope(refreshed)
            SessionCheck.Fresh(refreshed)
        }
    }

    /** 도는 사이 로그아웃·새 로그인이 끼었다. 지금 저장된 것을 기준으로 다시 물어보게 한다. */
    private fun superseded(): SessionCheck =
        if (loadSession() == null) SessionCheck.LoginRequired else SessionCheck.Unreachable

    /** 새 로그인. 진행 중이던 옛 refresh 응답보다 이 세션이 권위가 있다. */
    fun save(session: Session) = synchronized(stateLock) {
        generation += 1
        saveSession(session)
        publishAccountScope(session)
    }

    /** 로그아웃/탈퇴. 진행 중이던 refresh가 끝나도 토큰을 되살리지 못한다. */
    fun clear() = synchronized(stateLock) {
        generation += 1
        clearSession()
        publishAccountScope(null)
    }

    private fun scopeFor(session: Session?) = AccountScope(
        ownerId = session?.appUserId?.takeIf { it.isNotBlank() },
        generation = generation,
    )

    /** stateLock 안에서 저장이 성공한 뒤에만 알린다. 정상 refresh는 같은 값이라 emit하지 않는다. */
    private fun publishAccountScope(session: Session?) {
        mutableAccountScope.value = scopeFor(session)
    }
}
