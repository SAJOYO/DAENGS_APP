package com.daengs.app.walk.sync

import androidx.room.withTransaction
import com.daengs.app.auth.AccountScope
import com.daengs.app.walk.*
import com.daengs.app.walk.store.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** General detail consumer. A cache row means every page and local motion result was verified. */
class WalkMeasurementSync(private val database: WalkDatabase, private val account: () -> AccountScope,
    private val request: suspend (String, String, String) -> String = { token, path, method ->
        WalkApi.call(token, path, method, maxResponseBytes = 1_000_000, parse = { it }).getOrThrow()
    }) {
    private val dao get() = database.walkDao()
    fun changes(id: String) = dao.observeMeasurement(id).map { Unit }
    private suspend fun checkScope(expected: AccountScope) {
        currentCoroutineContext().ensureActive()
        if (expected.ownerId.isNullOrBlank() || expected != account()) throw CancellationException("산책 계정이 변경됐어요.")
    }

    private suspend fun plan(id: String, expected: AccountScope): WalkPrecisionContract.Plan? {
        checkScope(expected)
        val row = dao.session(id) ?: return null
        if (row.ownerId != expected.ownerId || row.serverWalkId == null || row.coordinateOrigin !in setOf("captured", "verified")) return null
        val precision = dao.motionPrecision(id) ?: return null
        if (precision.verifiedAtMillis == null || precision.verificationJson == null) return null
        val input = dao.motionInput(id, requireNotNull(expected.ownerId)) ?: return null
        if (!WalkMotionContract.measured(input.session) || input.session.endedAtMillis == null || input.fixes.size > 100_000) return null
        val p = WalkPrecisionContract.create(WalkMotionContract.create(input))
        val base = dao.motionBackup(id) ?: return null
        if (base.completedAtMillis == null || base.evidenceFingerprint != p.base.evidenceHash ||
            precision.evidenceFingerprint != p.evidenceHash || precision.manifestFingerprint != p.manifestHash) return null
        val receipt = JSONObject(precision.verificationJson)
        if (receipt.getString("evidence_fingerprint") != p.base.evidenceHash || receipt.getString("precision_fingerprint") != p.evidenceHash) return null
        checkScope(expected)
        return p
    }

    suspend fun cached(local: WalkSessionDetail): WalkSessionDetail = withContext(Dispatchers.IO) {
        val scope = account()
        if (scope.ownerId.isNullOrBlank()) return@withContext local
        try {
            val snapshot = database.withTransaction {
                val p = plan(local.summary.sessionId, scope) ?: return@withTransaction null
                val row = dao.measurement(local.summary.sessionId) ?: return@withTransaction null
                Triple(p, row, dao.measurementChunks(row.sessionId))
            } ?: return@withContext local
            val (p, row, pages) = snapshot
            require(WalkMeasurementContract.hash(row.summaryJson) == row.summaryHash)
            require(pages.map { it.chunkIndex } == pages.indices.toList())
            val result = WalkMeasurementContract.adopt(row.summaryJson, pages.map { it.payload }, p, requireNotNull(scope.ownerId), local)
            require(result.measurement?.id == row.measurementId)
            checkScope(scope)
            result
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { checkScope(scope); local }
    }

    suspend fun refresh(token: String, id: String) = withContext(Dispatchers.IO) {
        val scope = account()
        val p = database.withTransaction { plan(id, scope) } ?: return@withContext
        val local = readCompletedRoute(p.base.input.session, p.base.input.fixes, p.base.input.epochs)
        if (cached(local).measurement != null) return@withContext
        suspend fun call(path: String, method: String = "GET"): String {
            checkScope(scope)
            return request(token, path, method).also { checkScope(scope) }
        }
        val caps = try { JSONObject(call("/trajectory-capabilities")) }
        catch (e: WalkHttpException) { if (e.statusCode == 404) return@withContext else throw e }
        if (!caps.optBoolean("persisted_measurements_supported")) return@withContext
        val versions = caps.optJSONArray("measurement_versions") ?: return@withContext
        if ((0 until versions.length()).none { versions.getString(it) == WalkMeasurementContract.VERSION }) return@withContext
        val base = "/${p.base.input.session.serverWalkId}/measurements"
        val query = "?version=${WalkMeasurementContract.VERSION}"
        val text = call(base + query, "POST")
        val summary = WalkMeasurementContract.summary(text, p, requireNotNull(scope.ownerId))
        val measurementId = summary.getJSONObject("measurement").getString("measurement_id")
        val descriptors = summary.getJSONArray("required_route_chunks")
        val pages = (0 until descriptors.length()).map { i ->
            call("$base/$measurementId/chunks/$i$query")
        }
        WalkMeasurementContract.adopt(text, pages, p, scope.ownerId, local)
        database.withTransaction {
            checkScope(scope)
            val current = plan(id, scope) ?: throw CancellationException("산책 입력이 변경됐어요.")
            require(current.base.evidenceHash == p.base.evidenceHash && current.evidenceHash == p.evidenceHash)
            dao.deleteMeasurement(id)
            dao.insertMeasurement(WalkMeasurementRow(id, measurementId, text, WalkMeasurementContract.hash(text)))
            dao.insertMeasurementChunks(pages.mapIndexed { i, raw -> WalkMeasurementChunkRow(id, i, raw) })
            checkScope(scope)
        }
    }
}
