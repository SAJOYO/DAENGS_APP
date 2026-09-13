package com.daengs.app.ui.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.records.*
import java.time.ZoneId

/** Opt-in intent fixture: nearby points plus exactly repeated actions, entirely in memory. */
internal object WalkRecordsDenseLabFixture : WalkRecordsSource by WalkRecordsLabFixture {
    val records by lazy {
        WalkRecordsLabFixture.records.mapIndexed { index, record ->
            val walk = record.summary
            val edge = walk.segments.first().take(2).map { it.point }
            record.copy(entries = (0..11).map { ordinal ->
                val fraction = if (ordinal < 3) .45 else ((ordinal - 3) + index % 3 / 3.0) / 10
                val point = GeoPoint(edge[0].latitude + (edge[1].latitude - edge[0].latitude) * fraction,
                    edge[0].longitude + (edge[1].longitude - edge[0].longitude) * fraction)
                val at = walk.startedAtMillis + (ordinal + 1) * 30_000L
                WalkEntry("dense-$index-$ordinal", walk.sessionId,
                    when { ordinal < 7 -> WalkMomentType.SNIFFING; ordinal < 10 -> WalkMomentType.EXCRETION; else -> WalkMomentType.BARKING },
                    at, point, at, petId = walk.dogIds.first())
            })
        }
    }
    override suspend fun select(query: WalkRecordsQuery) = selectWalkRecords(records, query, ZoneId.systemDefault())
}
