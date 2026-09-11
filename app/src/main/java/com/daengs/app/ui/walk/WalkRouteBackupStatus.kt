package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.ui.theme.DaengsTheme
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
    state?.let { value ->
        WalkRouteBackupNotice(value, requesting, requested, error, onRequest = {
            if (!requesting && !requested) {
                requesting = true; error = false
                scope.launch {
                    try { requested = source.request(id) }
                    catch (e: Exception) { if (e is CancellationException) throw e; error = true }
                    finally { requesting = false }
                }
            }
        }, modifier = modifier)
    }
}

@Composable
internal fun WalkRouteBackupNotice(state: WalkRouteBackupState, requesting: Boolean = false,
    requested: Boolean = false, error: Boolean = false, onRequest: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp).testTag("route-backup-status"),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(when (state) {
                WalkRouteBackupState.PENDING -> "경로 백업 대기"
                WalkRouteBackupState.CHECKING -> "경로 백업 확인 중"
                WalkRouteBackupState.NEEDS_RETRY -> "경로 백업을 다시 확인해야 해요"
                WalkRouteBackupState.COMPLETE -> "경로 백업 완료"
            }, style = MaterialTheme.typography.labelMedium)
            if (state != WalkRouteBackupState.COMPLETE) {
                Text(when {
                    error -> "전송을 요청하지 못했어요. 다시 시도해 주세요."
                    requested -> "전송을 요청했어요. 연결되면 다시 시도해요."
                    else -> "경로는 기기에 저장되어 있어요."
                }, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (state != WalkRouteBackupState.COMPLETE) {
            TextButton(onClick = onRequest, enabled = !requesting && !requested,
                modifier = Modifier.testTag("route-backup-request")) {
                Text(if (requesting) "요청 중…" else if (requested) "전송 요청됨" else "전송 요청")
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun WalkRouteBackupPreview() = DaengsTheme {
    Column { WalkRouteBackupState.entries.forEach { WalkRouteBackupNotice(it, onRequest = {}) } }
}
