package com.daengs.app.ui.walk

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.BuildConfig
import com.daengs.app.DaengsApp
import com.daengs.app.ui.theme.*
import com.daengs.app.walk.diary.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun DiaryPlaceComparisonScreen(
    snapshot: DiaryComparisonSnapshot, onBack: () -> Unit, onApply: (DiaryPlaceComparison) -> Unit,
) {
    if (!BuildConfig.DEBUG) return
    val context = LocalContext.current.applicationContext
    val app = context as DaengsApp
    suspend fun requireOwner() {
        check(app.sessionProvider.accountScope.value.ownerId == snapshot.ownerId &&
            app.walkEntryDao.session(snapshot.sessionId)?.ownerId == snapshot.ownerId) {
            "현재 계정에서 볼 수 없는 산책이에요. 산책 목록을 다시 열어 주세요."
        }
    }
    val scope = rememberCoroutineScope()
    var message by remember(snapshot.digest) { mutableStateOf("현재 장면을 비교 자료로 준비하고 있어요.") }
    var busy by remember(snapshot.digest) { mutableStateOf(true) }
    LaunchedEffect(snapshot.digest) {
        try {
            requireOwner()
            DiaryComparisonFiles.prepare(context, snapshot)
            message = "장면을 준비했어요. PC에서 장소 설명을 생성한 뒤 결과를 확인해 주세요."
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            message = "비교 자료를 준비하지 못했어요. 화면을 다시 열어 주세요."
        } finally { busy = false }
    }
    BackHandler(onBack = onBack)
    DiaryPlaceComparisonContent(snapshot.scenes.size, message, busy, onBack) {
        if (busy) return@DiaryPlaceComparisonContent
        busy = true
        scope.launch {
            try {
                requireOwner()
                val result = DiaryComparisonFiles.read(context, snapshot)
                requireOwner()
                onApply(result)
            }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                message = (e as? IllegalStateException)?.message
                    ?: (e as? IllegalArgumentException)?.message ?: "비교 결과를 읽지 못했어요."
            } finally { busy = false }
        }
    }
}

@Composable
private fun DiaryPlaceComparisonContent(count: Int, message: String, busy: Boolean, onBack: () -> Unit, onRead: () -> Unit) {
    Column(Modifier.fillMaxSize().background(CreamBg).windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack) { Text("‹ 산책으로") }
        Text("장소 설명 비교", style = MaterialTheme.typography.headlineSmall)
        Text("현재 ${count}개 장면의 위치·시각·순서를 유지하고, 같은 지도에서 기본 설명과 장소 설명을 비교해요.")
        Text("비교 결과는 원래 일기와 사용자 기록에 저장되지 않아요.", color = TextMuted)
        Text(message)
        Button(onClick = onRead, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("결과 확인") }
    }
}

@Composable
internal fun DiaryPlaceComparisonSwitch(usePlaces: Boolean, onChange: (Boolean) -> Unit, onEvidence: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !usePlaces, onClick = { onChange(false) }, label = { Text("기본 설명") })
        FilterChip(selected = usePlaces, onClick = { onChange(true) }, label = { Text("장소 설명") })
        TextButton(onClick = onEvidence) { Text("근거") }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun DiaryPlaceComparisonPreview() {
    DaengsTheme { DiaryPlaceComparisonContent(6, "장면을 준비했어요.", false, {}, {}) }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun DiaryPlaceComparisonSwitchPreview() {
    DaengsTheme { DiaryPlaceComparisonSwitch(true, {}) }
}
