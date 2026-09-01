package com.daengs.app.walk

import android.content.Context
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.StateFlow

class ForegroundWalkTrackingController(
    context: Context,
    private val store: WalkTrackingStore,
) : WalkTrackingController {
    private val appContext = context.applicationContext

    override val state: StateFlow<WalkTrackingState> = store.state

    override fun start(dogIds: List<String>) {
        ContextCompat.startForegroundService(
            appContext,
            WalkTrackingService.commandIntent(appContext, WalkTrackingService.ACTION_START)
                .putStringArrayListExtra(
                    WalkTrackingService.EXTRA_DOG_IDS,
                    ArrayList(dogIds),
                ),
        )
    }

    override fun pause() = send(WalkTrackingService.ACTION_PAUSE)

    override fun resume() = send(WalkTrackingService.ACTION_RESUME)

    override fun stop() = send(WalkTrackingService.ACTION_STOP)

    private fun send(action: String) {
        appContext.startService(WalkTrackingService.commandIntent(appContext, action))
    }
}
