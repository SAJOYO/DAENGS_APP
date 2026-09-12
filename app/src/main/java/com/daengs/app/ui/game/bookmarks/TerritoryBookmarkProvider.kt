package com.daengs.app.ui.game.bookmarks

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.auth.SessionProvider
import com.daengs.app.territory.bookmarks.*
import com.daengs.app.ui.theme.*

internal val LocalTerritoryBookmarks = staticCompositionLocalOf<TerritoryBookmarkController?> { null }

@Composable
internal fun TerritoryBookmarkProvider(sessions: SessionProvider, content: @Composable () -> Unit) {
    val account by sessions.accountScope.collectAsState()
    val repository = remember(sessions) {
        TerritoryBookmarkRepository(TerritoryBookmarkApi(), sessions::freshSession, { sessions.accountScope.value })
    }
    val scope = rememberCoroutineScope()
    val controller = remember(repository, account) { TerritoryBookmarkController(scope, repository, account) }
    DisposableEffect(controller) { onDispose { controller.close() } }
    CompositionLocalProvider(LocalTerritoryBookmarks provides controller) {
        content()
        val state = key(controller) { controller.state.collectAsState().value }
        if (state.status == BookmarkStatus.READY) state.message?.let { message ->
            AlertDialog(onDismissRequest = controller::dismissMessage, title = { Text("북마크") },
                text = { Text(message) }, confirmButton = { TextButton(onClick = controller::dismissMessage) { Text("확인") } })
        }
    }
}

/** The action reads the same member state in walking cards, owned cards, and the saved list. */
@Composable
internal fun TerritoryBookmarkAction(siteId: String) {
    val controller = LocalTerritoryBookmarks.current ?: return
    val state = key(controller) { controller.state.collectAsState().value }
    LaunchedEffect(controller) { controller.ensureLoaded() }
    var details by remember { mutableStateOf(false) }
    BookmarkStar(state.saved(siteId), state.busy, state.status == BookmarkStatus.READY) {
        if (state.status == BookmarkStatus.READY) controller.toggle(siteId) else details = true
    }
    if (details) AlertDialog(onDismissRequest = { details = false }, title = { Text("북마크") },
        text = { Text(state.message ?: if (state.status == BookmarkStatus.SIGN_IN)
            "로그인하면 전봇대를 저장할 수 있어요." else "저장 상태를 확인한 뒤 다시 눌러 주세요.") },
        confirmButton = {
            TextButton(onClick = { details = false; controller.refresh() }) {
                Text(if (state.status == BookmarkStatus.SIGN_IN) "확인" else "다시 불러오기")
            }
        })
}

@Composable
internal fun BookmarkStar(saved: Boolean, busy: Boolean, known: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = !busy, modifier = Modifier.size(48.dp).testTag("bookmark-star").semantics {
        contentDescription = when { busy -> "북마크 확인 중"; !known -> "북마크 상태 확인"; saved -> "북마크 해제"; else -> "북마크 저장" }
    }) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = TextDark)
        else Text(when { !known -> "?"; saved -> "★"; else -> "☆" },
            color = if (saved && known) DaengPinkDeep else TextDark, fontSize = 28.sp)
    }
}

@Preview(showBackground = true)
@Composable
private fun BookmarkStarPreview() = DaengsTheme { Row { BookmarkStar(false, false) {}; BookmarkStar(true, false) {}; BookmarkStar(false, true) {} } }
