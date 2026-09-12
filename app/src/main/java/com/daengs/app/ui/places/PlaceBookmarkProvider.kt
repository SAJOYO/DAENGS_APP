package com.daengs.app.ui.places

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.*
import androidx.compose.ui.window.Popup
import com.daengs.app.DaengsApp

@Composable
internal fun rememberPlaceBookmarks(): PlaceBookmarkController? {
    val app = LocalContext.current.applicationContext as? DaengsApp ?: return null
    val sessions = app.sessionProvider
    val account by sessions.accountScope.collectAsState()
    val controller = remember(app, account) { app.placeBookmarks() }
    LaunchedEffect(controller) { controller.ensureLoaded() }
    return controller
}

@Composable
internal fun PlaceBookmarkFeedback(controller: PlaceBookmarkController, state: PlaceBookmarkState) {
    val snackbar = remember(controller) { SnackbarHostState() }
    LaunchedEffect(state.message, state.undo) {
        state.message?.let { message ->
            val result = snackbar.showSnackbar(message, actionLabel = if (state.undo != null) "실행 취소" else null,
                duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) controller.undo() else controller.dismissMessage()
        }
    }
    if (state.message != null) Popup(alignment = Alignment.BottomCenter) {
        SnackbarHost(snackbar, Modifier.navigationBarsPadding())
    }
}
