package com.daengs.app.walk.store

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.daengs.app.walk.WalkEntry
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.util.UUID

@Entity(tableName = "walk_entry", foreignKeys = [ForeignKey(
    entity = WalkSessionRow::class, parentColumns = ["id"], childColumns = ["sessionId"],
    onDelete = ForeignKey.CASCADE,
)], indices = [Index("sessionId")])
data class WalkEntryRow(
    @PrimaryKey val id: String,
    val sessionId: String,
    val payload: String?,
    val revision: Int,
    val mutationId: String,
    val dirty: Boolean,
    val syncError: String? = null,
    val pinPayload: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "0") val pinRevision: Int = 0,
    @androidx.room.ColumnInfo(defaultValue = "0") val isV2: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "0") val pinDirty: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "0") val pinChainIndex: Int = 0,
    /** Frozen request: retries must not reuse an ID with a changed body. */
    val pendingRequest: String? = null,
) {
    fun entry(): WalkEntry? = payload?.let { WalkEntry.parse(id, sessionId, JSONObject(it)).copy(
        pin = pinPayload?.let { com.daengs.app.walk.pin.ActionPin(it) }, syncPending = dirty || pinDirty || pendingRequest != null,
        syncError = syncError, baseVersion = com.daengs.app.walk.WalkEntryVersion(revision, mutationId)) }
}

class WalkEntryStore(private val dao: WalkDao, private val owner: (() -> String)? = null) {
    fun observe(sessionId: String) = dao.observeEntries(sessionId).map { rows ->
        if (owner != null && dao.session(sessionId)?.ownerId != owner.invoke()) emptyList()
        else rows.mapNotNull { it.entry() }.sortedBy { it.recordedAtMillis }
    }

    suspend fun save(entry: WalkEntry) {
        require(owner == null || dao.session(entry.sessionId)?.ownerId == owner.invoke()) {
            "현재 계정의 산책 기록이 아닙니다."
        }
        val content = entry.validate().toJson().toString()
        require(entry.type == com.daengs.app.walk.WalkMomentType.NOTE || entry.point != null ||
            dao.entry(entry.id)?.isV2 == true) { "새 행동은 행동 핀 저장 경로를 사용해야 합니다." }
        dao.saveEntryChecked(entry.id, entry.sessionId, content, UUID.randomUUID().toString(),
            entry.baseVersion?.revision, entry.baseVersion?.mutationId, owner?.invoke())
    }

    suspend fun delete(id: String): String? {
        val row = dao.entry(id) ?: return null
        require(owner == null || dao.session(row.sessionId)?.ownerId == owner.invoke())
        dao.deletePinAwareEntry(id, UUID.randomUUID().toString())
        return row.sessionId
    }
    /** 편집창과 Snackbar 모두 같은 삭제/전달 경계를 사용한다. */
    suspend fun deleteAndEnqueue(id: String, enqueue: suspend (String) -> Unit) {
        delete(id)?.let { enqueue(it) }
    }
}
