package com.daengs.app.territory.support

import com.daengs.app.territory.TerritoryActionClient
import com.daengs.app.territory.TerritoryActionDao
import com.daengs.app.territory.TerritoryActionException
import com.daengs.app.territory.TerritoryOperation
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

internal const val WALK = "00000000-0000-0000-0000-000000000001"
internal const val DOG = "00000000-0000-0000-0000-000000000002"
internal const val DOG2 = "00000000-0000-0000-0000-000000000003"
internal const val SITE = "territory-site:hex-v1:140:1:2"
internal const val CLAIM = "00000000-0000-0000-0000-000000000004"

internal class MemoryActions : TerritoryActionDao {
    val rows = MutableStateFlow<List<TerritoryOperation>>(emptyList())
    private var next = 0L
    override fun observe(): Flow<List<TerritoryOperation>> = rows
    override suspend fun all() = rows.value
    override suspend fun insert(row: TerritoryOperation): Long {
        if (rows.value.any { it.identity == row.identity }) return -1
        val id = ++next
        rows.value = rows.value + row.copy(sequence = id)
        return id
    }
    override suspend fun update(row: TerritoryOperation) { rows.value = rows.value.map { if (it.sequence == row.sequence) row else it } }
    override suspend fun delete(sequence: Long) { rows.value = rows.value.filter { it.sequence != sequence } }
}

internal class ClaimServer : TerritoryActionClient {
    var start: String? = null
    var phase = "RECORDING"
    var version = 0L
    var failMarkAfterCommit = false
    var rejectMark: String? = null
    var conflictPhaseOnce = false
    var wrongClaim = false
    val calls = mutableListOf<Triple<String, String, String?>>()
    val committed = mutableMapOf<String, Pair<String, String>>()
    fun sessionResponse() = JSONObject(checkNotNull(start)).put("client_session_id", WALK)
        .put("phase", phase).put("version", version).toString()
    override suspend fun request(token: String, method: String, path: String, body: String?): String {
        calls += Triple(method, path, body)
        if (method == "PUT") { if (start == null) start = body; return sessionResponse() }
        if (method == "GET") return sessionResponse()
        if (method == "PATCH") {
            if (conflictPhaseOnce) { conflictPhaseOnce = false; version++; throw TerritoryActionException(409, "session_changed") }
            val request = JSONObject(body!!)
            val desired = request.getString("phase")
            val expected = request.getLong("expected_version")
            if (expected != version && !(version == expected + 1 && phase == desired)) throw TerritoryActionException(409, "session_changed")
            if (phase != desired) { version++; phase = desired }
            return sessionResponse()
        }
        val request = JSONObject(body!!)
        val site = request.getString("site_id")
        committed[site]?.let {
            if (it.first != body) throw TerritoryActionException(409, "attempt_identity_conflict")
            return it.second
        }
        rejectMark?.let { throw TerritoryActionException(409, it) }
        check(phase == "RECORDING")
        val response = """{"claim_id":"$CLAIM","client_session_id":"$WALK","claiming_pet_id":"${request.getString("claiming_pet_id")}","disposition":"GRANTED","photo_status":"NOT_SUBMITTED","current_photo_id":null,"resolution_code":null,"site":{"site_id":"$site","version":1,"occupancy":{"owner_pet_id":"${request.getString("claiming_pet_id")}","owner_pet_name":"보리","is_mine":true,"certification":"UNVERIFIED","occupied_at":"1970-01-01T00:00:02Z"}}}"""
        committed[site] = body to response
        if (failMarkAfterCommit) { failMarkAfterCommit = false; throw IOException("response lost") }
        return if (wrongClaim) response.replace(CLAIM, "invalid") else response
    }
}
