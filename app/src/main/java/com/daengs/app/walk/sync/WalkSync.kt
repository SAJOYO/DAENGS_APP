package com.daengs.app.walk.sync

import android.util.Log
import com.daengs.app.walk.WalkFixLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 기기와 서버를 맞춘다.
 *
 * 규칙이 하나뿐이다 — **끝난 기록은 다시 바뀌지 않는다.** 그래서 병합도 충돌도 없다.
 * 없으면 넣고 있으면 건드리지 않는다.
 *
 * 방향도 단순하다.
 *
 * - **올리기**: 끝났는데 안 올라간 것을 올린다
 * - **되찾기**: 서버에 있는데 이 기기에 없는 것을 내려받는다
 *
 * 화면은 이걸 안 본다. 동기화가 로컬 DB 를 채우고, 목록·상세는 **로컬 하나만** 읽는다 —
 * 두 소스를 화면에서 합치면 그때부터 어느 쪽이 맞는지 따져야 한다.
 */
class WalkSync(
    private val log: WalkFixLog,
    private val api: WalkApiClient = DefaultWalkApiClient,
    private val now: () -> Long = System::currentTimeMillis,
    /**
     * 실패를 어디에 적을지. 기본은 logcat 이다.
     *
     * 바깥에서 넣을 수 있게 둔 이유는 **JVM 테스트에서 `android.util.Log` 가 안 돌기**
     * 때문이다. 전역 스위치(`isReturnDefaultValues`)를 켜면 다른 테스트의 진짜 실패까지
     * 조용해지므로, 이 자리만 갈아끼운다.
     */
    private val warn: (String, Throwable) -> Unit = { message, cause ->
        Log.w(TAG, message, cause)
    },
) {

    /**
     * 한 번 맞춘다. **실패해도 조용하다.**
     *
     * 걷다가 지하철에 들어가면 실패하는 것이 정상이고, 그때 사용자에게 할 말이 없다 —
     * 다음 기회에 다시 올린다. 부르는 쪽은 결과를 안 봐도 된다.
     *
     * @param accessToken 로그인 상태의 토큰. **없으면 아무것도 안 한다** — 산책은
     *   로그인 없이도 되고, 그때는 기기에만 남는 것이 맞다.
     */
    suspend fun syncOnce(accessToken: String?): Unit = withContext(Dispatchers.IO) {
        val token = accessToken ?: return@withContext
        if (!WalkApi.configured) return@withContext
        runCatching { push(token) }.onFailure { it.warn("올리기") }
        runCatching { pull(token) }.onFailure { it.warn("되찾기") }
    }

    /** 끝났는데 안 올라간 것을 올린다. */
    private suspend fun push(token: String) {
        for (session in log.unsyncedSessions()) {
            val fixes = log.fixes(session.id)
            val head = fixes.take(POINTS_PER_REQUEST)
            val walkId = api.upload(token, session, head).getOrElse {
                // 한 건이 실패해도 나머지는 시도한다 — 한 산책이 특별히 커서 막히는
                // 경우에 다른 기록까지 볼모가 되면 안 된다.
                it.warn("산책 ${session.id.take(8)} 올리기")
                continue
            }
            // 긴 산책은 나눠 보낸다. 좌표 순번이 서버 PK 라 나눠도 중복이 안 생긴다.
            var failed = false
            for (chunk in fixes.drop(POINTS_PER_REQUEST).chunked(POINTS_PER_REQUEST)) {
                api.appendPoints(token, walkId, chunk).onFailure {
                    it.warn("좌표 이어붙이기")
                    failed = true
                }
                if (failed) break
            }
            // 좌표를 다 못 올렸으면 **올라갔다고 표시하지 않는다.** 다음에 다시 올리면
            // 이미 있는 것은 서버가 알아서 건너뛴다.
            if (!failed) log.markSynced(session.id, now())
        }
    }

    /** 서버에 있는데 이 기기에 없는 것을 내려받는다. */
    private suspend fun pull(token: String) {
        val remote = api.list(token).getOrElse {
            it.warn("목록 받기")
            return
        }
        // 로컬에 이미 있는지는 **세션 id 하나로** 판단한다. 기기가 만든 id 를 서버가
        // 그대로 들고 있어서다.
        val mine = log.finishedSessions().map { it.id }.toSet()
        for (walk in remote) {
            if (walk.clientSessionId in mine) continue
            val detail = api.detail(token, walk.id).getOrElse {
                it.warn("산책 ${walk.id.take(8)} 받기")
                continue
            }
            // 되찾은 것은 이미 서버에 있으므로 올린 것으로 표시해 둔다.
            log.openSession(detail.walk.toSession(syncedAtMillis = now()))
            for (fix in detail.fixes) log.append(detail.walk.clientSessionId, fix)
        }
    }

    private fun Throwable.warn(what: String) {
        warn("산책 동기화 — $what 에 실패했다. 다음에 다시 시도한다.", this)
    }

    private companion object {

        /**
         * 한 번에 보낼 좌표 수.
         *
         * 지금 nginx 한도가 20MB 라 두 시간 산책(약 5천 점 · 650KB)도 한 번에 들어간다.
         * 그래도 나누는 이유는 **한도가 서버 설정이라 바뀔 수 있고, 그때 실패하는 쪽이
         * 사용자의 기록**이기 때문이다. 2천 점이면 약 270KB 다.
         */
        const val POINTS_PER_REQUEST = 2_000
    }
}

private const val TAG = "WalkSync"

/** 테스트가 서버 없이 돌 수 있게 낸 얇은 경계. 진짜는 [WalkApi] 다. */
interface WalkApiClient {
    suspend fun upload(
        token: String,
        session: com.daengs.app.walk.RecordedSession,
        fixes: List<com.daengs.app.walk.RecordedFix>,
    ): Result<String>

    suspend fun appendPoints(
        token: String,
        walkId: String,
        fixes: List<com.daengs.app.walk.RecordedFix>,
    ): Result<Unit>

    suspend fun list(token: String): Result<List<RemoteWalk>>

    suspend fun detail(token: String, walkId: String): Result<RemoteWalkDetail>
}

private object DefaultWalkApiClient : WalkApiClient {
    override suspend fun upload(
        token: String,
        session: com.daengs.app.walk.RecordedSession,
        fixes: List<com.daengs.app.walk.RecordedFix>,
    ) = WalkApi.upload(token, session, fixes)

    override suspend fun appendPoints(
        token: String,
        walkId: String,
        fixes: List<com.daengs.app.walk.RecordedFix>,
    ) = WalkApi.appendPoints(token, walkId, fixes)

    override suspend fun list(token: String) = WalkApi.list(token)

    override suspend fun detail(token: String, walkId: String) = WalkApi.detail(token, walkId)
}
