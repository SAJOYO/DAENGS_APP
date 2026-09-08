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
) {
    fun entry(): WalkEntry? = payload?.let { WalkEntry.parse(id, sessionId, JSONObject(it)).copy(
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
        dao.saveEntryChecked(entry.id, entry.sessionId, content, UUID.randomUUID().toString(),
            entry.baseVersion?.revision, entry.baseVersion?.mutationId, owner?.invoke())
    }

    suspend fun delete(id: String): String? {
        val row = dao.entry(id) ?: return null
        require(owner == null || dao.session(row.sessionId)?.ownerId == owner.invoke())
        dao.editEntry(id, null, UUID.randomUUID().toString())
        return row.sessionId
    }
    /** 편집창과 Snackbar 모두 같은 삭제/전달 경계를 사용한다. */
    suspend fun deleteAndEnqueue(id: String, enqueue: suspend (String) -> Unit) {
        delete(id)?.let { enqueue(it) }
    }
}
