package com.daengs.app.walk.records

import com.daengs.app.auth.SessionProvider
import com.daengs.app.walk.store.WalkDatabase
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import java.time.ZoneId

/** One login's local records. Recreate after login; an old source never adopts another session. */
fun accountWalkRecordsSource(
    database: WalkDatabase,
    sessions: SessionProvider,
    zone: ZoneId = ZoneId.systemDefault(),
    sheetsApi: WalkRecordSheetsApi = WalkRecordSheetsApi(),
): WalkRecordsSource? {
    val scope = sessions.accountScope.value
    val ownerId = scope.ownerId ?: return null
    val stored = RoomWalkRecordsSource(database, ownerId, currentOwner = {
        ownerId.takeIf { sessions.accountScope.value == scope }.orEmpty()
    }, zone = zone)
    val local = object : WalkRecordsSource {
        // A mismatch already present when this collector starts must also invalidate the source.
        override val changes = merge(stored.changes, sessions.accountScope.filter { it != scope }.map { Unit })
        override suspend fun select(query: WalkRecordsQuery) = stored.select(query)
    }
    return TraceLoadingWalkRecordsSource(local, ownerId,
        isCurrentAccount = { sessions.accountScope.value == scope },
        freshSession = sessions::freshSession, fetch = sheetsApi::query)
}
