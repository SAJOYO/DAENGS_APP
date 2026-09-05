package com.daengs.app.ui.walk

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daengs.app.territory.PhotoSimulation
import com.daengs.app.territory.ClaimPhotoStatus
import com.daengs.app.territory.TerritoryPhotoJob
import com.daengs.app.ui.camera.CameraPreview
import com.daengs.app.ui.camera.hasCameraPermission
import com.daengs.app.ui.camera.rememberCameraController
import com.daengs.app.ui.camera.takePicture
import com.daengs.app.ui.theme.DaengsTheme
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

@Composable
internal fun TerritoryCaptureDialog(
    siteId: String,
    beginCapture: suspend () -> String?,
    onSaved: (String, File, PhotoSimulation) -> Deferred<Boolean>,
    onDismiss: () -> Unit,
    onCaptureFailed: (String) -> Unit = {},
    online: Boolean = false,
) {
    val context = LocalContext.current
    var permission by remember { mutableStateOf(hasCameraPermission(context)) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var simulation by remember { mutableStateOf(PhotoSimulation.ACCEPT) }
    val scope = rememberCoroutineScope()
    var capturing by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val cancel by rememberUpdatedState(onCaptureFailed)
    DisposableEffect(Unit) { onDispose { if (!saving) capturing?.let(cancel) } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permission = it
        if (!it) message = "카메라 권한이 필요해요. 거절했다면 앱 설정에서 허용해 주세요."
    }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.White).safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("영역표시 인증", style = MaterialTheme.typography.titleLarge)
            Text(if (online) "강아지와 전봇대 주변 모습이 함께 나오게 찍어 주세요" else "$siteId · 강아지와 현장 모습이 함께 나오게 찍어 주세요")
            Text(if (online) "사진은 현재 위치에서 촬영하고 서버에서 확인해요 · 인증 범위 10m" else "점령 연습 · 사진 내용은 검사하지 않는 페이크 판정입니다")
            if (permission) {
                val camera = rememberCameraController(videoEnabled = false)
                CameraPreview(camera, Modifier.fillMaxWidth().aspectRatio(3f / 4f))
                Button(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        val attempt = try { beginCapture() }
                            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (_: Exception) { null }
                        if (attempt == null) {
                            busy = false
                            message = "촬영할 수 없어요 · 같은 산책에서 해당 장소에 다시 접근해 주세요"
                        } else {
                            capturing = attempt
                            val file = runCatching {
                                val directory = File(context.cacheDir, "territory-photos")
                                check(directory.isDirectory || directory.mkdirs())
                                File(directory, "${UUID.randomUUID()}.jpg")
                            }.getOrNull()
                            if (file == null) {
                                onCaptureFailed(attempt); capturing = null; busy = false
                                message = "사진 저장 공간을 준비하지 못했어요"
                            }
                            else {
                                busy = true
                                val selectedSimulation = simulation
                                takePicture(context, camera, file, onSaved = {
                                    saving = true
                                    val saved = onSaved(attempt, file, selectedSimulation)
                                    scope.launch {
                                        val success = try { saved.await() }
                                            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                            catch (_: Exception) { false }
                                        capturing = null; saving = false; busy = false
                                        if (success) onDismiss() else {
                                            file.delete()
                                            message = "사진을 저장하지 못했어요 · 다시 촬영해 주세요"
                                        }
                                    }
                                }, onError = {
                                    file.delete()
                                    onCaptureFailed(attempt); capturing = null
                                    busy = false
                                    message = "촬영에 실패했어요 · 다시 찍어 주세요"
                                })
                            }
                        }
                    }
                }) { Text(if (busy) "사진 저장 중…" else "촬영하고 산책 계속") }
            } else {
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("카메라 권한 허용") }
            }
            if (!online) {
                Text("테스트 판정 선택")
                PhotoSimulation.entries.forEach { choice ->
                    TextButton(enabled = !busy, onClick = { simulation = choice }) {
                        Text("${if (simulation == choice) "●" else "○"} ${choice.label}")
                    }
                }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(enabled = !busy, onClick = onDismiss) { Text("산책으로 돌아가기") }
        }
    }
}

@Composable
internal fun TerritoryPhotoStatus(jobs: List<TerritoryPhotoJob>, onRetry: (String) -> Unit, modifier: Modifier = Modifier) {
    if (jobs.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val pending = jobs.count { it.status == ClaimPhotoStatus.PENDING && !it.conflict }
    Surface(modifier, tonalElevation = 2.dp) {
        TextButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(if (pending > 0) "사진 인증 연습 · ${pending}건 확인 중 ›" else "사진 인증 연습 · 결과 보기 ›",
                style = MaterialTheme.typography.bodySmall)
        }
    }
    if (expanded) AlertDialog(onDismissRequest = { expanded = false },
        title = { Text("사진 인증 연습") },
        confirmButton = { TextButton(onClick = { expanded = false }) { Text("닫기") } },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                jobs.asReversed().forEach { job ->
                    val label = when {
                        job.conflict -> "점유가 변경돼 확정 보류"
                        job.status == ClaimPhotoStatus.VERIFIED -> "인증 완료"
                        job.status == ClaimPhotoStatus.REJECTED -> "부적합 · 현장에서 다시 촬영"
                        job.status == ClaimPhotoStatus.RETRY_PENDING -> "통신 장애 · 저장한 사진으로 재시도"
                        else -> "확인 중 · 산책을 계속해도 돼요"
                    }
                    Text("${job.siteId} · $label", style = MaterialTheme.typography.bodySmall)
                    if (job.status == ClaimPhotoStatus.RETRY_PENDING && !job.conflict) {
                        TextButton(onClick = { onRetry(job.attemptId) }) { Text("판정 재시도 (페이크 성공)") }
                    }
                }
            }
        })
}

@Preview(showBackground = true)
@Composable
private fun TerritoryPhotoStatusPreview() {
    DaengsTheme { TerritoryPhotoStatus(listOf(TerritoryPhotoJob("a", "전봇대 A", "c", File("sample.jpg"))), {}) }
}

@Preview(showBackground = true)
@Composable
private fun TerritoryCaptureDialogPreview() {
    DaengsTheme { TerritoryCaptureDialog("전봇대 A", { null }, { _, _, _ -> CompletableDeferred(false) }, {}) }
}

@Preview(showBackground = true)
@Composable
private fun OnlineTerritoryCapturePreview() {
    DaengsTheme { TerritoryCaptureDialog("전봇대 A", { null }, { _, _, _ -> CompletableDeferred(false) }, {}, online = true) }
}
