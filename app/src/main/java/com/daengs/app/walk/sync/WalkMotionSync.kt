package com.daengs.app.walk.sync

import com.daengs.app.walk.store.WalkDatabase
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** Storage capability only. Never enables v2 pins or claims server motion calculation parity. */
class WalkMotionSync(
    database: WalkDatabase,
    private val owner: () -> String,
    private val precision: WalkMotionPrecisionSync? = null,
    private val now: () -> Long = System::currentTimeMillis,
    private val restorationGuard: suspend (String, suspend () -> Unit) -> Unit = { _, work -> work() },
    private val request: suspend (String, String, String, JSONObject?) -> JSONObject = { token, path, method, body ->
        WalkApi.call(token, path, method, body, parse = ::JSONObject).getOrThrow()
    },
) {
    private val store = WalkMotionStore(database, owner)
    private val mutex = Mutex()

    /** Old local records only need the small receipt probe; don't redownload their entire GPS each sync. */
    suspend fun hasCompletedBackup(token: String, walkId: String, account: String): Boolean {
        if (!supports(token, account)) return false
        val status = try { call(account, token, "/$walkId/motion-backup", "GET", null) }
        catch (e: WalkHttpException) { if (e.statusCode == 404) return false else throw e }
        return when (status.getString("state")) {
            "complete" -> true
            "collecting" -> false
            else -> error("GPS 백업 상태가 달라요.")
        }
    }

    suspend fun sync(token: String, sessionId: String, walkId: String) = mutex.withLock {
        val account = owner()
        syncBackup(token, sessionId, walkId, account)
        precision?.sync(token, sessionId, walkId, account)
    }

    suspend fun needsPrecisionRestore(token: String, id: String, walkId: String, account: String): Boolean =
        precision?.needsRestore(token, id, walkId, account) ?: false

    private suspend fun syncBackup(token: String, sessionId: String, walkId: String, account: String) {
        try {
            val plan = store.freeze(sessionId, walkId, account) ?: return
            if (store.isComplete(sessionId, account)) return
            if (!supports(token, account)) throw IOException("서버의 GPS 측정 백업 지원을 기다리고 있어요.")
            val path = "/$walkId/motion-backup"
            val status = call(account, token, path, "PUT", plan.manifest)
            val received = WalkMotionContract.validateStatus(status, plan, complete = false).toSet()
            for (index in plan.chunks.indices) {
                if (index in received) continue
                val reply = call(account, token, "$path/chunks/$index", "PUT", plan.chunk(index))
                check(reply.getString("manifest_fingerprint") == plan.manifestHash && reply.getInt("chunk_index") == index)
                check(reply.getString("chunk_fingerprint") == plan.chunkHashes[index])
                check(WalkMotionContract.chunkDigest(WalkMotionStore.objects(reply.getJSONArray("points"))) == plan.chunkHashes[index])
            }
            val complete = call(account, token, "$path/complete", "POST", plan.receipt())
            WalkMotionContract.validateStatus(complete, plan, complete = true)
            store.acknowledge(plan, account, now())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // Keep the frozen payload pending without storing response bodies or account tokens.
            if (owner() == account) store.failed(sessionId, account,
                if (failure.isRetryableWalkDeliveryFailure()) "RETRY_PENDING" else "CONTRACT_REJECTED")
            throw failure
        }
    }

    /** Complete backup is decoded before a single local write. A collecting backup stays remote. */
    suspend fun restore(token: String, detail: RemoteWalkDetail, account: String, expectedLocal: Boolean? = null) = mutex.withLock {
        store.checkOwner(account)
        val hadLocal = expectedLocal ?: store.hasLocal(detail.walk.clientSessionId, account)
        val session = detail.walk.toSession(now()).copy(ownerId = account)
        var plan: WalkMotionContract.Plan? = null
        if (supports(token, account)) {
            val path = "/${detail.walk.id}/motion-backup"
            val status = try { call(account, token, path, "GET", null) }
            catch (missing: WalkHttpException) { if (missing.statusCode == 404) null else throw missing }
            if (status != null) {
                if (status.getString("state") == "collecting") throw IOException("GPS 측정 백업이 아직 진행 중이에요.")
                check(status.getString("state") == "complete")
                val manifest = status.getJSONObject("manifest")
                val count = with(WalkMotionContract) { manifest.long("point_count") }
                check(count in 0..WalkMotionContract.MAX_POINTS && count == detail.fixes.size.toLong())
                val points = mutableListOf<JSONObject>()
                val manifestHash = WalkMotionContract.manifestDigest(manifest)
                for (index in 0 until ((count + 255) / 256).toInt()) {
                    val page = call(account, token, "$path/chunks/$index", "GET", null)
                    check(page.getString("manifest_fingerprint") == manifestHash && page.getInt("chunk_index") == index)
                    val values = WalkMotionStore.objects(page.getJSONArray("points"))
                    check(values.size == minOf(256L, count - index * 256L).toInt())
                    check(page.getString("chunk_fingerprint") == WalkMotionContract.chunkDigest(values))
                    points += values
                }
                plan = WalkMotionContract.read(session, detail.fixes, manifest, points)
                WalkMotionContract.validateStatus(status, plan, complete = true)
            }
        }
        val precise = plan?.let { precision?.restore(token, it, account) }
        val selected = precise?.plan?.base ?: plan
        restorationGuard(account) { store.restore(session, selected?.input?.fixes ?: detail.fixes, selected, account, now(), hadLocal, precise) }
    }

    private suspend fun supports(token: String, account: String): Boolean {
        val caps = try { call(account, token, "/motion-capabilities", "GET", null) }
        catch (e: WalkHttpException) { if (e.statusCode == 404) return false else throw e }
        check(caps.getString("version") == WalkMotionContract.VERSION && caps.get("backup_supported") is Boolean)
        if (!caps.getBoolean("backup_supported")) return false
        check(caps.get("calculation_verified") == false && caps.getInt("chunk_size") == 256 &&
            caps.getInt("max_points") == 100_000 && caps.getInt("max_epochs") == 1024) { "GPS 백업 계약이 달라요." }
        return true
    }

    private suspend fun call(account: String, token: String, path: String, method: String, body: JSONObject?): JSONObject {
        store.checkOwner(account)
        try { return request(token, path, method, body) }
        finally { store.checkOwner(account) }
    }
}
