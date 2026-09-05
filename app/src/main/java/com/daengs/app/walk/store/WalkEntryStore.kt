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
    fun entry(): WalkEntry? = payload?.let { WalkEntry.parse(id, sessionId, JSONObject(it)).copy(syncError = syncError) }
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
        val existing = dao.entry(entry.id)
        if (existing == null) {
            dao.insertEntry(WalkEntryRow(entry.id, entry.sessionId, content, 0,
                UUID.randomUUID().toString(), true))
        } else {
            require(existing.sessionId == entry.sessionId && existing.payload != null)
            check(dao.editEntry(entry.id, content, UUID.randomUUID().toString()) == 1) {
                "이미 삭제된 기록입니다."
            }
        }
    }

    suspend fun delete(id: String) {
        val row = dao.entry(id) ?: return
        require(owner == null || dao.session(row.sessionId)?.ownerId == owner.invoke())
        dao.editEntry(id, null, UUID.randomUUID().toString())
    }
}
