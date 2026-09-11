package com.daengs.app.walk.diary

import com.daengs.app.walk.store.WalkDao
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/** Durable deadline/result live in Room. These jobs only wake the same preparation. */
class WalkDiaryPublication(
    private val dao: WalkDao,
    private val owner: () -> String,
    private val scope: CoroutineScope,
    private val sync: suspend (String) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val active = ConcurrentHashMap.newKeySet<String>()

    fun start(id: String) {
        if (!active.add(id)) return
        scope.launch(dispatcher) {
            try {
                val account = owner()
                val state = dao.prepareLocalDiary(id, account) ?: return@launch
                if (state.publishedBundle != null) return@launch
                val remaining = (state.deadlineAtMillis - now()).coerceIn(0, 10_000)
                if (remaining > 0) {
                    // Upload/auth must never hold the local deadline job.
                    scope.launch(dispatcher) {
                        try { if (owner() == account) sync(id) }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { /* The durable upload worker retries separately. */ }
                    }
                    delay(remaining)
                }
                if (owner() == account && dao.session(id)?.ownerId == account)
                    dao.publishDiaryBase(id, now())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Keep the saved preparation for refresh/reentry; never crash the recording process.
                android.util.Log.w("WalkDiaryPublication", "Could not prepare the saved walk board")
            } finally { active.remove(id) }
        }
    }

    suspend fun recover() { dao.pendingDiaryPublications().forEach(::start) }
}
