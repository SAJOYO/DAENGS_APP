package com.daengs.app.ui.walk

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daengs.app.ui.camera.*
import com.daengs.app.walk.WalkPhoto
import com.daengs.app.walk.WalkPhotoCapture
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@Composable
internal fun WalkPhotoCaptureDialog(beginCapture: () -> WalkPhotoCapture?,
    onSave: (WalkPhotoCapture, File) -> kotlinx.coroutines.Deferred<WalkPhoto>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestBegin by rememberUpdatedState(beginCapture)
    val latestSave by rememberUpdatedState(onSave)
    var permission by remember { mutableStateOf(hasCameraPermission(context)) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permission = it
        if (!it) message = "카메라 권한이 필요해요. 앱 설정에서 허용해 주세요."
    }
    // 저장은 Application 소유. 화면이 사라져도 CameraX 완료 파일을 넘긴다.
    var attached by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { onDispose { attached = false } }
    Dialog(onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.White).safeDrawingPadding()
            .padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("산책 사진", style = MaterialTheme.typography.titleLarge)
            Text("강아지와 오늘의 산책을 사진으로 남겨요.")
            if (permission) {
                val camera = rememberCameraController(videoEnabled = false)
                CameraPreview(camera, Modifier.fillMaxWidth().aspectRatio(3f / 4f))
                Button(enabled = !busy, onClick = {
                    val capture = latestBegin()
                    if (capture == null) {
                        message = "산책 중 현재 위치가 잡히면 촬영할 수 있어요. GPS를 확인해 주세요."
                    } else {
                        val file = runCatching {
                            val dir = File(context.cacheDir, "walk-photo-capture")
                            check(dir.isDirectory || dir.mkdirs())
                            File(dir, "${UUID.randomUUID()}.jpg")
                        }.getOrNull()
                        if (file == null) message = "사진 저장 공간을 준비하지 못했어요."
                        else {
                            busy = true
                            takePicture(context, camera, file, onSaved = {
                                val saving = latestSave(capture, file)
                                if (attached) scope.launch {
                                    try {
                                        saving.await()
                                        onDismiss()
                                    } catch (e: Exception) {
                                        if (e is kotlinx.coroutines.CancellationException) throw e
                                        message = "사진을 저장하지 못했어요. 다시 촬영해 주세요."
                                    } finally { busy = false }
                                }
                            }, onError = {
                                file.delete(); busy = false
                                message = "촬영에 실패했어요. 다시 찍어 주세요."
                            })
                        }
                    }
                }) { Text(if (busy) "사진 저장 중…" else "촬영하고 위치에 남기기") }
            } else Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("카메라 권한 허용") }
            Text("이 기기에 저장돼요.", style = MaterialTheme.typography.bodySmall)
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(enabled = !busy, onClick = onDismiss) { Text("산책으로 돌아가기") }
        }
    }
}

@Composable
internal fun WalkPhotoDialog(photo: WalkPhoto, onDelete: (suspend (String) -> Unit)?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember(photo.id) { mutableStateOf(false) }
    var confirmDelete by remember(photo.id) { mutableStateOf(false) }
    var error by remember(photo.id) { mutableStateOf<String?>(null) }
    var loaded by remember(photo.id) { mutableStateOf(false) }
    val bitmap by produceState<android.graphics.Bitmap?>(null, photo.file) {
        value = try { com.daengs.app.screening.Photo.decodeUpright(context, Uri.fromFile(photo.file), 1200) }
        catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
        loaded = true
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("산책 사진 · ${formatWalkClock(photo.capturedAtMillis)}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                bitmap?.let { Image(it.asImageBitmap(), "산책 중 촬영한 사진",
                    Modifier.fillMaxWidth().heightIn(max = 380.dp), contentScale = ContentScale.Fit) }
                    ?: Text(if (loaded) "사진 파일을 읽을 수 없어요." else "사진을 불러오는 중…")
                Text("촬영한 위치에 남긴 사진 · 이 기기에 저장", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("닫기") } },
        dismissButton = { if (onDelete != null) TextButton(enabled = !busy, onClick = { confirmDelete = true }) { Text("사진 삭제") } })
    if (confirmDelete && onDelete != null) AlertDialog(onDismissRequest = { if (!busy) confirmDelete = false },
        title = { Text("이 사진을 삭제할까요?") }, text = { Text("사진과 사진 Pin이 함께 지워져요.") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                try { onDelete(photo.id); onDismiss() }
                catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    error = "사진을 삭제하지 못했어요."
                } finally { busy = false; confirmDelete = false }
            }
        }) { Text("삭제") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { confirmDelete = false }) { Text("취소") } })
}

@Preview @Composable
private fun WalkPhotoCapturePreview() { MaterialTheme { WalkPhotoCaptureDialog({ null }, { _, _ -> kotlinx.coroutines.CompletableDeferred() }, {}) } }

@Preview @Composable
private fun WalkPhotoPreview() { MaterialTheme { WalkPhotoDialog(WalkPhoto("preview", "s", 0,
    com.daengs.app.location.GeoPoint(37.5, 127.0), File("preview.jpg")), {}, {}) } }
