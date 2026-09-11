package com.daengs.app.walk.sync

import androidx.room.withTransaction
import com.daengs.app.walk.WalkSyncState
import com.daengs.app.walk.store.*
import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/** Additive transport. A sealed upload is still pending until actual engine output matches. */
class WalkMotionPrecisionSync(
    private val database: WalkDatabase,
    private val owner: () -> String,
    private val now: () -> Long = System::currentTimeMillis,
    private val request: suspend (String, String, String, JSONObject?) -> JSONObject = { token, path, method, body ->
        WalkApi.call(token, path, method, body, parse = ::JSONObject).getOrThrow()
    },
) {
    private val dao get() = database.walkDao()
    private fun checkOwner(account: String) { check(account.isNotBlank() && owner() == account) }

    internal suspend fun sync(token: String, id: String, walkId: String, account: String) {
        try {
            val plan = database.withTransaction {
                checkOwner(account)
                val session = dao.session(id) ?: error("산책이 삭제됐어요.")
                check(session.ownerId == account && session.serverWalkId == walkId)
                if (session.coordinateOrigin !in setOf("captured", "verified") ||
                    !WalkMotionContract.measured(session.toModel()) || dao.motionPrecision(id)?.verifiedAtMillis != null) return@withTransaction null
                val p = current(id, walkId, account)
                val old = dao.motionPrecision(id)
                if (old == null) dao.insertMotionPrecision(p.row())
                else check(old.manifestFingerprint == p.manifestHash && old.evidenceFingerprint == p.evidenceHash &&
                    WalkPrecisionContract.manifestDigest(JSONObject(old.manifestJson)) == p.manifestHash)
                checkOwner(account)
                p
            } ?: return
            if (!supports(token, account)) throw IOException("서버의 GPS 정밀 백업 지원을 기다리고 있어요.")
            val path = "/$walkId/motion-precision"
            val received = WalkPrecisionContract.validateStatus(call(account, token, path, "PUT", plan.manifest), plan, false)
            for (i in plan.chunks.indices) {
                if (i in received) continue
                val page = call(account, token, "$path/chunks/$i", "PUT", plan.chunk(i))
                validatePage(page, plan.manifestHash, i, plan.chunks[i].size)
                check(page.getString("chunk_fingerprint") == plan.chunkHashes[i])
            }
            WalkPrecisionContract.validateStatus(call(account, token, "$path/complete", "POST", plan.receipt()), plan, true)
            save(plan, account, null)
            val receipt = WalkPrecisionContract.verify(plan, call(account, token, "/$walkId/motion-calculation", "GET", null))
            save(plan, account, receipt)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (owner() == account) database.withTransaction {
                checkOwner(account)
                if (dao.session(id)?.ownerId == account) dao.motionPrecision(id)?.takeIf { it.verifiedAtMillis == null }?.let {
                    dao.updateMotionPrecision(it.copy(lastError = if (e.isRetryableWalkDeliveryFailure()) "RETRY_PENDING" else "VERIFICATION_REJECTED"))
                }
                checkOwner(account)
            }
            throw e
        }
    }

    private suspend fun current(id: String, walkId: String, account: String): WalkPrecisionContract.Plan {
        checkOwner(account)
        val session = requireNotNull(dao.session(id))
        check(session.ownerId == account && session.serverWalkId == walkId && session.coordinateOrigin in setOf("captured", "verified"))
        val rows = dao.fixes(id)
        check(rows.all { it.latBits != null && it.lngBits != null && (it.accuracyM == null || it.accuracyBits != null) })
        val input = requireNotNull(dao.motionInput(id, account))
        check(input.session.syncState == WalkSyncState.DERIVED)
        val base = WalkMotionContract.create(input)
        val backup = requireNotNull(dao.motionBackup(id))
        check(backup.completedAtMillis != null && backup.evidenceFingerprint == base.evidenceHash)
        return WalkPrecisionContract.create(base)
    }

    private suspend fun save(plan: WalkPrecisionContract.Plan, account: String, receipt: JSONObject?) = database.withTransaction {
        val id = plan.base.input.session.id
        check(current(id, requireNotNull(plan.base.input.session.serverWalkId), account).evidenceHash == plan.evidenceHash)
        val old = requireNotNull(dao.motionPrecision(id))
        check(old.evidenceFingerprint == plan.evidenceHash)
        dao.updateMotionPrecision(old.copy(completedAtMillis = old.completedAtMillis ?: now(),
            verifiedAtMillis = if (receipt != null) now() else old.verifiedAtMillis,
            verificationJson = receipt?.toString() ?: old.verificationJson, lastError = null))
        checkOwner(account)
    }

    internal suspend fun needsRestore(token: String, id: String, walkId: String, account: String): Boolean {
        checkOwner(account)
        val session = dao.session(id) ?: return false
        check(session.ownerId == account && session.serverWalkId == walkId)
        if (session.coordinateOrigin != null || !supports(token, account)) return false
        val status = status(token, walkId, account) ?: return false
        return when (status.getString("state")) {
            "complete" -> true
            "collecting" -> false
            else -> error("GPS 정밀 백업 상태가 달라요.")
        }
    }

    internal data class Restored(val plan: WalkPrecisionContract.Plan, val receipt: JSONObject)

    internal suspend fun restore(token: String, base: WalkMotionContract.Plan, account: String): Restored? {
        if (!supports(token, account)) return null
        val walkId = requireNotNull(base.input.session.serverWalkId)
        val status = status(token, walkId, account) ?: return null
        if (status.getString("state") == "collecting") throw IOException("GPS 정밀 백업이 아직 진행 중이에요.")
        check(status.getString("state") == "complete")
        val manifest = status.getJSONObject("manifest")
        val hash = WalkPrecisionContract.manifestDigest(manifest)
        val points = mutableListOf<JSONObject>()
        // Bound page count by the already validated base, never by an untrusted remote integer.
        for (i in base.chunks.indices) points += validatePage(
            call(account, token, "/$walkId/motion-precision/chunks/$i", "GET", null), hash, i, base.chunks[i].size)
        val plan = WalkPrecisionContract.read(base, manifest, points)
        WalkPrecisionContract.validateStatus(status, plan, true)
        return Restored(plan, WalkPrecisionContract.verify(plan,
            call(account, token, "/$walkId/motion-calculation", "GET", null)))
    }

    private suspend fun status(token: String, walkId: String, account: String): JSONObject? = try {
        call(account, token, "/$walkId/motion-precision", "GET", null)
    } catch (e: WalkHttpException) { if (e.statusCode == 404) null else throw e }

    private suspend fun supports(token: String, account: String): Boolean {
        val caps = try { call(account, token, "/motion-capabilities", "GET", null) }
        catch (e: WalkHttpException) { if (e.statusCode == 404) return false else throw e }
        check(caps.getString("version") == WalkMotionContract.VERSION && caps.get("backup_supported") is Boolean)
        if (!caps.getBoolean("backup_supported") || !caps.has("precision_versions")) return false
        val versions = caps.getJSONArray("precision_versions")
        if ((0 until versions.length()).none { versions.getString(it) == WalkPrecisionContract.VERSION }) return false
        val calculations = caps.getJSONArray("calculation_versions")
        check((0 until calculations.length()).any { calculations.getString(it) == WalkPrecisionContract.CALCULATION })
        return true
    }

    private suspend fun call(account: String, token: String, path: String, method: String, body: JSONObject?): JSONObject {
        checkOwner(account)
        try { return request(token, path, method, body) } finally { checkOwner(account) }
    }

    private fun validatePage(page: JSONObject, hash: String, index: Int, size: Int): List<JSONObject> {
        check(page.getString("manifest_fingerprint") == hash && page.getInt("chunk_index") == index)
        return WalkMotionStore.objects(page.getJSONArray("points")).also {
            check(it.size == size && page.getString("chunk_fingerprint") == WalkPrecisionContract.chunkDigest(it))
        }
    }
}

internal fun WalkPrecisionContract.Plan.row() = WalkMotionPrecisionRow(base.input.session.id, manifest.toString(), manifestHash, evidenceHash)
