package com.daengs.app.walk

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * [WalkFixLog]에 쓰는 유일한 입구.
 *
 * 명령을 제출 순서대로 하나씩 실행하므로 세션 행보다 fix가 먼저 저장되는 경쟁을 막는다.
 * 후속 Foreground Service는 서비스보다 오래 사는 애플리케이션 scope를 이 writer에 넘긴다.
 */
class WalkFixWriter(
    private val log: WalkFixLog,
    scope: CoroutineScope,
) {
    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val _failure = MutableStateFlow<String?>(null)

    /** 가장 최근 저장 실패. 한 명령이 실패해도 뒤 명령의 저장은 계속한다. */
    val failure: StateFlow<String?> = _failure.asStateFlow()

    init {
        scope.launch {
            for (command in commands) {
                try {
                    command()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    _failure.value = error.message ?: "산책 기록을 저장하지 못했습니다."
                }
            }
        }
    }

    fun openSession(session: RecordedSession) = enqueue { log.openSession(session) }

    fun append(sessionId: String, fix: RecordedFix) = enqueue { log.append(sessionId, fix) }

    fun closeSession(sessionId: String, endedAtMillis: Long) =
        enqueue { log.closeSession(sessionId, endedAtMillis) }

    fun deleteSession(sessionId: String) = enqueue { log.deleteSession(sessionId) }

    /** 이 호출보다 먼저 제출한 명령이 성공 또는 실패로 끝날 때까지 기다린다. */
    suspend fun flush() {
        val barrier = CompletableDeferred<Unit>()
        enqueue { barrier.complete(Unit) }
        barrier.await()
    }

    fun clearFailure() {
        _failure.value = null
    }

    private fun enqueue(command: suspend () -> Unit) {
        check(commands.trySend(command).isSuccess) { "walk fix writer is unavailable" }
    }
}
