package com.daengs.app.walk.sync

import com.daengs.app.walk.store.WalkDao
import com.daengs.app.walk.store.WalkEntryRow
import org.json.JSONObject

/** 위치 finalize와 별도로 현재 기록을 전송/복원한다. 실패는 Worker에 전달한다. */
class WalkEntrySync(
    private val dao: WalkDao,
    private val v2: WalkEntryV2Sync? = null,
    private val request: suspend (String, String, String, JSONObject?) -> JSONObject = { token, path, method, body ->
        WalkApi.call(token, path, method, body, parse = ::JSONObject).getOrThrow()
    },
) {
    private val mutex = kotlinx.coroutines.sync.Mutex()
    suspend fun sync(token: String, sessionId: String, walkId: String) {
        mutex.lock()
        try { syncLocked(token, sessionId, walkId) } finally { mutex.unlock() }
    }

    private suspend fun syncLocked(token: String, sessionId: String, walkId: String) {
        if (v2?.sync(token, sessionId, walkId) == true) return
        for (row in dao.entries(sessionId).filter { it.dirty && it.syncError == null }) {
            val response = try { if (row.payload == null) {
                request(token, "/$walkId/entries/${row.id}?expected_revision=${row.revision}&mutation_id=${row.mutationId}",
                    "DELETE", null)
            } else {
                request(token, "/$walkId/entries/${row.id}", "PUT", JSONObject().apply {
                    put("expected_revision", row.revision)
                    put("mutation_id", row.mutationId)
                    put("content", JSONObject(row.payload))
                })
            } } catch (e: WalkHttpException) {
                if (e.statusCode != 409) throw e
                val latest = request(token, "/$walkId/entries", "GET", null).getJSONArray("entries")
                val remote = (0 until latest.length()).map { latest.getJSONObject(it) }
                    .firstOrNull { it.getString("id") == row.id } ?: throw e
                if (remote.isNull("content")) dao.acceptDeletedEntry(row.id, remote.getInt("revision"))
                else if (row.payload == null) {
                    // 삭제는 사용자가 이미 선택했다. 최신 버전으로 삭제를 다시 전송한다.
                    dao.acknowledgeEntry(row.id, remote.getInt("revision"), "")
                    throw java.io.IOException("삭제를 최신 기록 버전으로 다시 전송합니다.", e)
                } else dao.conflictEntry(row.id, remote.getInt("revision"), row.mutationId,
                    "다른 기기에서 바뀐 기록이에요. 아래 내용을 확인하고 저장하면 이 내용으로 반영해요.")
                continue
            }
            // 요청 중 사용자가 다시 수정한 경우 새 payload는 유지하고 서버 버전만 전진시킨다.
            dao.acknowledgeEntry(row.id, response.getInt("revision"), row.mutationId)
        }
        val result = request(token, "/$walkId/entries", "GET", null)
        val entries = result.getJSONArray("entries")
        for (i in 0 until entries.length()) {
            val entry = entries.getJSONObject(i)
            val row = WalkEntryRow(entry.getString("id"), sessionId,
                entry.optJSONObject("content")?.toString(), entry.getInt("revision"),
                entry.getString("mutation_id"), false)
            dao.insertEntry(row)
            // 확정 삭제는 로컬 충돌/미전송 수정으로 되살릴 수 없다.
            if (row.payload == null) dao.acceptDeletedEntry(row.id, row.revision)
            else dao.acceptEntry(row.id, row.payload, row.revision, row.mutationId)
        }
    }
}
