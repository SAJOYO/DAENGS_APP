package com.daengs.app.ui.walk

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.daengs.app.DaengsApp
import com.daengs.app.pet.Pet
import com.daengs.app.walk.WalkHistory
import com.daengs.app.walk.detail.StoredWalkDetailData
import com.daengs.app.walk.diary.DiaryComparisonFiles

/** App wiring stays here; the detail screen only sees a session's read/action contracts. */
@Composable
internal fun WalkSessionDetailRoute(
    sessionId: String, history: WalkHistory, onBack: () -> Unit,
    modifier: Modifier = Modifier, pets: List<Pet> = emptyList(),
    origin: WalkSessionOrigin = WalkSessionOrigin.RECORDS,
) {
    WalkDiaryMapScreen(sessionId, history, onBack, modifier, pets, origin)
}

@Composable
internal fun WalkDiaryMapScreen(
    sessionId: String, history: WalkHistory, onBack: () -> Unit,
    modifier: Modifier = Modifier, pets: List<Pet> = emptyList(),
    origin: WalkSessionOrigin = WalkSessionOrigin.RECORDS,
) {
    val app = LocalContext.current.applicationContext as DaengsApp
    val account by app.sessionProvider.accountScope.collectAsState()
    key(sessionId, account) {
        val data = remember(app, sessionId, history, account) {
            StoredWalkDetailData(sessionId, account, { app.sessionProvider.accountScope.value },
                history, app.walkEntryDao, app.walkEntries, app.walkPhotos,
                app.walkDiaryPublication::start, app.walkRuntime.delivery::enqueue,
                app.sessionProvider::freshSession,
                syncSession = { token, id ->
                    app.walkRuntime.sync.syncPendingSession(token, id, includeStoryboard = false)
                },
                refreshStoryboard = { token, id, remoteId ->
                    app.walkStoryboardSync.sync(token, id, remoteId, refresh = true)
                }, measurements = app.walkMeasurements)
        }
        val backupSource = remember(app, account) { app.routeBackupSource(account) }
        WalkDiaryMapForAccount(sessionId, data, data, onBack, modifier, pets, origin, account,
            backupAction = { backupSource?.let { WalkRouteBackupStatus(sessionId, it) } },
            readComparison = { DiaryComparisonFiles.read(app, it) })
    }
}
