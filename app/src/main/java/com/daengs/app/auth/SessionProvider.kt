package com.daengs.app.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 토큰 없이 구분하는 로그인 생애. 같은 회원의 재로그인도 다른 scope다. */
data class AccountScope(val ownerId: String?, val generation: Long)

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

    suspend fun freshSession(): Session? = refreshMutex.withLock {
        val snapshot = synchronized(stateLock) { generation to loadSession() }
        val saved = snapshot.second ?: return@withLock null
        val at = now()

        if (saved.accessAlive(at)) {
            return@withLock synchronized(stateLock) {
                saved.takeIf { generation == snapshot.first }
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
            return@withLock null
        }
        if (!configured()) return@withLock null

        val refreshed = refreshSession(saved.refreshToken).getOrNull() ?: return@withLock null
        synchronized(stateLock) {
            if (generation != snapshot.first) return@synchronized null
            saveSession(refreshed)
            publishAccountScope(refreshed)
            refreshed
        }
    }

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
