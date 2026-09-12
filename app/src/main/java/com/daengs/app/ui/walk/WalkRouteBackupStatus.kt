package com.daengs.app.ui.walk

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextMuted
import com.daengs.app.walk.sync.WalkRouteBackupSource
import com.daengs.app.walk.sync.WalkRouteBackupState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@Composable
internal fun WalkRouteBackupStatus(id: String, source: WalkRouteBackupSource, modifier: Modifier = Modifier) {
    val state by remember(id, source) { source.observe(id).catch { e ->
        if (e is CancellationException) throw e
        emit(null)
    } }.collectAsState(initial = null)
    var requesting by remember(id, source) { mutableStateOf(false) }
    var requested by remember(id, source, state) { mutableStateOf(false) }
    var error by remember(id, source, state) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    state?.let { value ->
        WalkRouteBackupIcon(value, requesting, requested, error, onRequest = {
            if (!requesting && !requested) {
                requesting = true; error = false
                scope.launch {
                    try {
                        requested = source.request(id)
                        error = !requested
                        Toast.makeText(context, if (requested) "재전송을 요청했어요. 연결되면 다시 시도해요."
                            else "전송을 요청하지 못했어요. 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
                    }
                    catch (e: Exception) {
                        if (e is CancellationException) throw e
                        error = true
                        Toast.makeText(context, "전송을 요청하지 못했어요. 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
                    }
                    finally { requesting = false }
                }
            }
        }, modifier = modifier)
    }
}

@Composable
internal fun WalkRouteBackupIcon(state: WalkRouteBackupState, requesting: Boolean = false,
    requested: Boolean = false, error: Boolean = false, onRequest: () -> Unit, modifier: Modifier = Modifier) {
    if (state != WalkRouteBackupState.NEEDS_RETRY) return
    val description = when {
        requesting -> "경로 백업 재전송 요청 중"
        requested -> "경로 백업 재전송 요청됨"
        error -> "경로 백업 요청 실패 · 다시 전송"
        else -> "경로 백업 오류 · 다시 전송"
    }
    IconButton(onClick = onRequest, enabled = !requesting && !requested,
        modifier = modifier.size(48.dp).testTag("route-backup-request")
            .semantics { contentDescription = description }) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            DaengsIconView(DaengsIcon.Cloud, Modifier.size(22.dp),
                tint = TextMuted.copy(alpha = if (requesting || requested) .4f else 1f))
            Box(Modifier.align(Alignment.TopEnd).size(6.dp)
                .background(MaterialTheme.colorScheme.error.copy(alpha = if (requesting || requested) .4f else 1f), CircleShape))
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun WalkRouteBackupPreview() = DaengsTheme {
    Row {
        WalkRouteBackupIcon(WalkRouteBackupState.NEEDS_RETRY, onRequest = {})
        WalkRouteBackupIcon(WalkRouteBackupState.NEEDS_RETRY, requested = true, onRequest = {})
    }
}
