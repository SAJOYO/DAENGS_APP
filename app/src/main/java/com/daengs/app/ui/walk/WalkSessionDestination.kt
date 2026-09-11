package com.daengs.app.ui.walk

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.Text
import com.daengs.app.auth.AccountScope
import com.daengs.app.walk.WalkTrackingState
import com.daengs.app.walk.WalkTrackingController
import java.util.UUID

internal enum class WalkSessionOrigin(val backLabel: String) {
    COMPLETION("홈으로"), RECORDS("산책 목록으로"),
}

private val completionProcessEpoch = UUID.randomUUID().toString()

/** Owns the destination after the service's completion notification has been acknowledged. */
@Stable
internal class WalkSessionDestination(private val account: AccountScope) {
    var sessionId by mutableStateOf<String?>(null)
        private set
    private var expectedSessionId: String? = null

    fun destination(tracking: WalkTrackingState): String? {
        if (account.ownerId.isNullOrBlank()) return null
        if (tracking.activeSessionId != null || tracking.finishingSessionId != null) return null
        return sessionId ?: tracking.completedSessionId?.takeIf {
            tracking.ownerId == account.ownerId && (expectedSessionId == null || expectedSessionId == it)
        }
    }

    fun accept(tracking: WalkTrackingState) {
        val inFlight = tracking.activeSessionId ?: tracking.finishingSessionId
        if (inFlight != null) {
            sessionId = null
            expectedSessionId = inFlight.takeIf { tracking.ownerId == account.ownerId }
        } else sessionId = destination(tracking)
    }

    fun close() { sessionId = null; expectedSessionId = null }

    companion object {
        fun saver(account: AccountScope, epoch: String = completionProcessEpoch) =
            Saver<WalkSessionDestination, List<Any?>>(
                save = { listOf(account.ownerId, account.generation, epoch, it.sessionId, it.expectedSessionId) },
                restore = {
                    if (it.size != 5 || it[0] != account.ownerId || it[1] != account.generation || it[2] != epoch) null
                    else WalkSessionDestination(account).apply {
                        sessionId = it[3] as? String; expectedSessionId = it[4] as? String
                    }
                },
            )
    }
}

@Composable
internal fun rememberWalkSessionDestination(account: AccountScope): WalkSessionDestination =
    rememberSaveable(account, saver = remember(account) { WalkSessionDestination.saver(account) }) {
        WalkSessionDestination(account)
    }

/** The live subtree (including permission launchers and location subscriptions) is unmounted. */
@Composable
internal fun WalkSessionFlow(
    account: AccountScope, destination: WalkSessionDestination, controller: WalkTrackingController,
    onExit: () -> Unit, detail: @Composable (String, () -> Unit) -> Unit, live: @Composable () -> Unit,
) {
    val tracking by controller.state.collectAsState()
    WalkSessionCompletionGate(destination, tracking, { id ->
        val current = controller.state.value
        if (current.completedSessionId == id && current.activeSessionId == null &&
            current.finishingSessionId == null && current.ownerId == account.ownerId) controller.dismissCompletion()
    }, onExit, detail, live)
}

@Composable
internal fun WalkSessionCompletionGate(
    destination: WalkSessionDestination,
    tracking: WalkTrackingState,
    acknowledge: (String) -> Unit,
    onExit: () -> Unit,
    detail: @Composable (String, () -> Unit) -> Unit,
    live: @Composable () -> Unit,
) {
    val id = destination.destination(tracking)
    SideEffect {
        destination.accept(tracking)
        if (id != null && tracking.completedSessionId == id) acknowledge(id)
    }
    if (id == null) live() else key(id) {
        val close = { destination.close(); onExit() }
        BackHandler(onBack = close)
        detail(id, close)
    }
}

@Preview(showBackground = true)
@Composable
private fun CompletionGatePreview() {
    val destination = rememberWalkSessionDestination(AccountScope("preview", 0))
    WalkSessionCompletionGate(destination, WalkTrackingState(ownerId = "preview", completedSessionId = "walk"),
        {}, {}, { _, _ -> Text("저장된 산책 · 홈으로") }, { Text("산책 준비") })
}
