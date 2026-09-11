package com.daengs.app.ui.storage

import android.Manifest
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.screening.Photo
import com.daengs.app.screening.PreparedPhoto
import com.daengs.app.ui.camera.CameraPreview
import com.daengs.app.ui.camera.hasCameraPermission
import com.daengs.app.ui.camera.rememberCameraController
import com.daengs.app.ui.camera.takePicture
import com.daengs.app.ui.common.DaengsTextAction
import com.daengs.app.ui.common.DaengsWideButton
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import kotlinx.coroutines.launch
import java.io.File

/**
 * 영수증 사진의 긴 변.
 *
 * `Photo.MAX_EDGE`(1600)는 피부 병변용이다 — 진단 크롭은 원본보다 한참 작아도 되지만,
 * 영수증은 **항목명이 잔글씨**라 그 크기에서 뭉갠다. q90 JPEG 이면 이 크기여도 저쪽
 * 상한 12 MiB 에 한참 못 미친다.
 */
const val RECEIPT_EDGE = 2400

/**
 * 영수증 한 장을 집어 **JPEG 으로 구워** 돌려준다.
 *
 * `PetPhotoPicker` 의 뼈대에서 **원형 틀을 뺀 것**이다 — 영수증은 얼굴처럼 잘라 넣을
 * 자리가 없고, 잘리면 합계 줄이 날아간다.
 *
 * ⚠️ **PNG 가 와도 JPEG 으로 굽는다.** 저쪽 키 빌더가 `jpeg`/`webp` 만 받고, 올릴 때
 *    Content-Type 이 티켓과 다르면 415 다. [Photo.prepare] 가 항상 JPEG 을 낸다.
 *
 * ⚠️ **카메라 권한이 없으면 시스템 카메라로 못 떨어진다.** 매니페스트에 `CAMERA` 를
 *    선언한 앱은 그 권한 없이 `ACTION_IMAGE_CAPTURE` 조차 못 띄운다 — 안드로이드가
 *    막는다 (`ChatScreen` 의 `CAMERA_DENIED` 주석). 거부하면 갤러리로 안내한다.
 *
 * @param onDone 구운 사진. **null 이면 그만둔 것이다** (`PetPhotoPicker` 와 같은 규칙).
 */
@Composable
fun ReceiptPicker(onDone: (PreparedPhoto?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var shooting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var cameraGranted by remember { mutableStateOf(hasCameraPermission(context)) }

    // 전면을 덮는 화면은 back 을 잡는다 (`PetPhotoPicker` 와 같은 규칙). 안 잡으면
    // 뒤로가기를 홈이 받아서, 오버레이는 그대로인 채 뒤에서 화면이 바뀐다.
    BackHandler { onDone(null) }

    /** 고른 자리에서 바이트까지. 실패하면 화면에 남아 다시 고를 수 있다. */
    fun bake(uri: Uri) {
        busy = true
        scope.launch {
            Photo.prepare(context, uri, RECEIPT_EDGE)
                .onSuccess { onDone(it) }
                .onFailure {
                    message = "사진을 읽지 못했어요. 다시 골라 주세요."
                    busy = false
                }
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        // 고르기 창에서 그냥 나온 것. 화면을 닫지 않고 그대로 둔다 — 카메라도 남아 있다.
        if (uri != null) bake(uri)
    }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
        if (granted) shooting = true else message = CAMERA_DENIED
    }

    Column(
        Modifier
            .fillMaxSize()
            // **아래 목록으로 터치가 새는 것을 막는다.** 이 화면은 저장소 목록을
            // 교체하는 게 아니라 그 위에 겹치므로, 안 막으면 빈 자리를 눌렀을 때
            // 그 좌표에 있던 [삭제]나 전화번호가 눌린다. 인셋 패딩보다 앞에 둬야
            // 상태바 자리까지 덮는다.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .background(CreamBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("영수증 찍기", color = TextDark, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            DaengsTextAction("닫기", { onDone(null) }, tint = TextMuted)
        }
        Text(
            "합계 줄까지 들어가게 찍어 주세요. 아래가 잘리면 총액을 손으로 적게 돼요.",
            color = TextMuted,
            fontSize = 13.sp,
        )

        message?.let { Text(it, color = DaengsColors.Error, fontSize = 13.sp) }

        if (shooting && cameraGranted) {
            val camera = rememberCameraController(videoEnabled = false)
            CameraPreview(
                camera,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(14.dp)),
            )
            DaengsWideButton(
                label = "찍기",
                onClick = {
                    val file = receiptFile(context.cacheDir)
                    if (file == null) {
                        message = "사진 저장 공간을 준비하지 못했어요."
                        return@DaengsWideButton
                    }
                    busy = true
                    takePicture(
                        context,
                        camera,
                        file,
                        onSaved = { bake(Uri.fromFile(file)) },
                        onError = {
                            file.delete()
                            busy = false
                            message = "촬영에 실패했어요. 다시 찍어 주세요."
                        },
                    )
                },
                enabled = !busy,
                busy = busy,
                accent = true,
            )
            DaengsTextAction("그만두기", { shooting = false })
        } else {
            DaengsWideButton(
                label = "사진 찍기",
                onClick = {
                    message = null
                    if (cameraGranted) shooting = true else askCamera.launch(Manifest.permission.CAMERA)
                },
                enabled = !busy,
                accent = true,
            )
            DaengsWideButton(
                label = "갤러리에서 고르기",
                onClick = {
                    message = null
                    pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                enabled = !busy,
            )
        }
    }
}

/**
 * 캐시 안의 한 자리. 못 만들면 null 이다 — 저장 공간이 없는 기기가 있다.
 *
 * **이름을 고정한다.** 찍을 때마다 새 이름을 만들면 캐시가 계속 는다 —
 * `Photo.kt` 가 같은 이유로 이미 그렇게 하고 있고, CameraX 는 가장 큰 해상도로 찍어서
 * 한 장이 3~8MB 다. 영수증은 굽고 나면 다시 쓸 일이 없는 파일이라 남길 이유가 없다.
 * 덮어쓰기는 `takePicture` 가 앞에서 `file.delete()` 를 해 주므로 안전하다.
 */
private fun receiptFile(cacheDir: File): File? = runCatching {
    val directory = File(cacheDir, "receipts")
    check(directory.isDirectory || directory.mkdirs())
    File(directory, "capture.jpg")
}.getOrNull()

/**
 * 카메라 권한을 거부했을 때. **시스템 카메라로 떨어질 수 없어서** 갤러리를 가리킨다 —
 * 그쪽은 권한 없이 된다 (`ChatScreen` 과 같은 문장의 이유).
 */
private const val CAMERA_DENIED = "카메라 권한이 꺼져 있어요. 설정에서 켜거나, 갤러리에서 골라 주세요."

@Preview(widthDp = 411, heightDp = 760, showBackground = true)
@Composable
private fun ReceiptPickerPreview() = DaengsTheme { ReceiptPicker(onDone = {}) }
