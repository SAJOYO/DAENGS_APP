package com.daengs.app.ui.walk.records

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.auth.AccountScope
import com.daengs.app.pet.Pet
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextMuted
import com.daengs.app.ui.walk.shared.SharedWalksRoute
import com.daengs.app.walk.records.WalkRecordsSource
import com.daengs.app.walk.shared.SharedWalksHolder
import kotlinx.coroutines.CancellationException

/** The caller keeps this login's source and state above its navigation branches. */
@Composable
internal fun WalkRecordsRoute(
    accountScope: AccountScope,
    source: WalkRecordsSource?,
    state: WalkRecordsRouteState,
    pets: List<Pet>?,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onSync: suspend () -> Unit,
    detailContent: @Composable (String, () -> Unit) -> Unit,
    photoOf: (String) -> androidx.compose.ui.graphics.ImageBitmap? = { null },
    /**
     * 다른 보호자가 다녀온 산책의 **읽기 전용** 공동 조회. null 이면 입구를 안 보인다.
     * 기기 기록 선택·지도·페이지와 섞지 않고 따로 연다.
     */
    sharedWalks: SharedWalksHolder? = null,
) {
    // An expired login must not show even a restored detail id.
    if (accountScope.ownerId.isNullOrBlank() || source == null) {
        BackHandler(onBack = onBack)
        Column(Modifier.fillMaxSize().background(CreamBg)
            .windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text("로그인하면 산책 기록을 볼 수 있어요.", modifier = Modifier.testTag("records-login-required"))
            TextButton(onClick = onSignIn, modifier = Modifier.testTag("records-sign-in")) { Text("로그인") }
            TextButton(onClick = onBack, modifier = Modifier.testTag("records-route-back")) { Text("뒤로") }
        }
        return
    }
    var sharedOpen by rememberSaveable(accountScope) { mutableStateOf(false) }
    val opened = state.openedSessionId
    if (opened != null) {
        detailContent(opened, state::closeDetail)
        return
    }
    if (sharedWalks != null && sharedOpen) {
        SharedWalksRoute(sharedWalks, pets.orEmpty(), onBack = {
            sharedWalks.closeDetail()
            sharedOpen = false
        })
        return
    }

    var syncFailed by remember(accountScope, source) { mutableStateOf(false) }
    val sync by rememberUpdatedState(onSync)
    LaunchedEffect(accountScope, source) {
        try {
            sync()
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            syncFailed = true
        }
    }
    Column(Modifier.fillMaxSize().background(CreamBg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (syncFailed) Text("동기화하지 못했어요. 저장된 기록은 계속 볼 수 있어요.",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)
                .testTag("records-sync-notice"),
            color = TextMuted, style = MaterialTheme.typography.labelSmall)
        if (sharedWalks != null) TextButton(onClick = { sharedOpen = true },
            modifier = Modifier.padding(horizontal = 10.dp).testTag("records-shared-open")) {
            Text("함께 돌보는 보호자의 산책 보기")
        }
        RetainedWalkRecords(state) {
            WalkRecordsScreen(source, pets.orEmpty(), onBack = {
                state.captureRecords()
                onBack()
            }, onOpen = state::open, modifier = Modifier.weight(1f), petsLoaded = pets != null, photoOf = photoOf)
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 780)
@Composable
private fun WalkRecordsSignInPreview() {
    val account = AccountScope(null, 0)
    val state = rememberWalkRecordsRouteState(account)
    DaengsTheme { WalkRecordsRoute(account, null, state, emptyList(), {}, {}, {}, { _, _ -> }) }
}
