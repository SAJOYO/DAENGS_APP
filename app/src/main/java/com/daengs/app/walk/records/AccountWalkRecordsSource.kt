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
    photos: com.daengs.app.walk.store.WalkPhotoStore? = null,
): WalkRecordsSource? {
    val scope = sessions.accountScope.value
    val ownerId = scope.ownerId ?: return null
    val stored = RoomWalkRecordsSource(database, ownerId, currentOwner = {
        ownerId.takeIf { sessions.accountScope.value == scope }.orEmpty()
    }, zone = zone)
    val reader = photos?.let { com.daengs.app.walk.diary.WalkDiaryReader(database.walkDao(), it) {
        ownerId.takeIf { sessions.accountScope.value == scope }.orEmpty()
    } }
    val local = object : WalkRecordsSource {
        // A mismatch already present when this collector starts must also invalidate the source.
        override val changes = merge(stored.changes, sessions.accountScope.filter { it != scope }.map { Unit })
        override suspend fun select(query: WalkRecordsQuery) = stored.select(query)
        override suspend fun loadRoute(record: WalkRecord) = stored.loadRoute(record)
        override fun observeDiary(record: WalkRecord) = reader?.observe(listOf(record.summary))?.map { diaries ->
            check(sessions.accountScope.value == scope) { "산책을 읽는 동안 계정이 변경됐어요." }
            diaries.singleOrNull()
        } ?: super.observeDiary(record)
    }
    return TraceLoadingWalkRecordsSource(local, ownerId,
        isCurrentAccount = { sessions.accountScope.value == scope },
        freshSession = sessions::freshSession, fetch = sheetsApi::query)
}
