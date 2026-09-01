package com.daengs.app.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.ImageCapture
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

/**
 * 앱 안 카메라 미리보기.
 *
 * **가이드를 찍는 동안 보여 주려고 있다.** 시스템 카메라로 던지면 그 위에 아무것도
 * 못 얹는다 — 피부는 병변에 네모를 맞춰야 하고 보행은 뒤에서 전신이 들어와야 하는데,
 * 둘 다 찍고 나서 알면 늦다.
 *
 * **[LifecycleCameraController] 를 쓴다.** 사진과 영상 두 가지를 쓰는데, 유스케이스를
 * 손으로 묶으면(`ProcessCameraProvider.bindToLifecycle`) 두 화면이 같은 배관을 각자
 * 짜야 한다. 컨트롤러는 그걸 한 덩어리로 들고 있어서 화면은 "찍어" 만 말하면 된다.
 *
 * 화면이 사라지면 [DisposableEffect] 가 카메라를 놓는다. 안 놓으면 다음에 열 때
 * 검은 화면이 뜨거나 다른 앱이 카메라를 못 연다.
 */
@Composable
fun CameraPreview(
    controller: LifecycleCameraController,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(controller, lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }
    Box(modifier) {
        AndroidView(
            factory = { context ->
                PreviewView(context).apply {
                    this.controller = controller
                    // 잘라서 채운다. **부르는 쪽이 화면 상자를 사진과 같은 비율로
                    // 잡아 주면** 잘릴 것이 없어서, 화면에서 본 자리가 곧 찍히는
                    // 자리가 된다 (`SkinCaptureScreen` 이 그렇게 한다).
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * 뒷면 카메라 컨트롤러 하나.
 *
 * [videoEnabled] 가 true 면 녹화까지 쓴다. **두 가지를 같이 켜지 않는다** — 사진과
 * 영상을 동시에 묶으면 기기에 따라 해상도가 떨어지거나 아예 못 묶는다.
 */
@Composable
fun rememberCameraController(videoEnabled: Boolean): LifecycleCameraController {
    val context = LocalContext.current
    return remember(videoEnabled) {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(
                if (videoEnabled) LifecycleCameraController.VIDEO_CAPTURE
                else LifecycleCameraController.IMAGE_CAPTURE,
            )
            if (!videoEnabled) {
                // **가장 큰 해상도로 찍는다.** 기본값은 화면 크기에 맞춘 것이라,
                // 1200만 화소 카메라에서 시스템 카메라의 절반도 안 되는 사진이 나온다.
                // 병변을 보는 사진이라 화질이 곧 판정이다.
                imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                imageCaptureResolutionSelector = highestFourThree()
                // 미리보기는 **비율만** 맞춘다. 크기는 CameraX 가 고르게 둔다 —
                // 여기까지 최대 해상도를 요구하면 기기가 그 조합을 못 열고
                // `onDeviceError` 로 카메라가 닫힌다 (실기기에서 그렇게 나왔다).
                // 화면에서 본 자리와 찍힌 자리가 같으려면 **비율만** 같으면 된다.
                previewResolutionSelector = fourThree()
            }
        }
    }
}

/**
 * 4:3 중에서 제일 큰 것.
 *
 * 4:3 인 이유는 **센서의 온전한 화각**이기 때문이다. 16:9 는 위아래를 잘라 낸 것이라
 * 같은 거리에서 병변이 더 크게 잡히는 대신 주변 피부가 덜 들어온다 — 서버가 주변
 * 피부까지 보고 판정한다.
 */
private fun highestFourThree(): ResolutionSelector = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
    .build()

/** 비율만 4:3. 크기는 맡긴다. */
private fun fourThree(): ResolutionSelector = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
    .build()

/**
 * 카메라 권한이 있나.
 *
 * **없다고 촬영을 막지 않는다.** 부르는 쪽이 시스템 카메라로 떨어진다 — 지금 되는
 * 일이 안 되게 만드는 변경은 하지 않는다.
 */
fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

/**
 * 사진 한 장을 [file] 에 찍는다.
 *
 * **성공 콜백이 파일을 다시 알려 주지 않는다.** 부르는 쪽이 이미 아는 자리이므로
 * 그대로 쓰면 된다 — 시스템 카메라(`TakePicture`)가 성공 여부만 주던 것과 같은 결이다.
 *
 * 회전은 CameraX 가 EXIF 로 적어 준다. 읽는 쪽([com.daengs.app.screening.Photo])이
 * 이미 EXIF 를 보고 세우므로 여기서 돌리지 않는다 — 두 군데서 돌리면 뒤집힌다.
 */
fun takePicture(
    context: Context,
    controller: LifecycleCameraController,
    file: java.io.File,
    onSaved: () -> Unit,
    onError: () -> Unit,
) {
    file.delete()
    val options = androidx.camera.core.ImageCapture.OutputFileOptions.Builder(file).build()
    runCatching {
        controller.takePicture(
            options,
            // **메인 스레드로 받는다.** 콜백이 화면 상태를 건드리는데, 남는
            // 스레드에서 받으면 부를 때마다 스레드가 하나씩 새로 생기고 그대로 남는다.
            ContextCompat.getMainExecutor(context),
            object : androidx.camera.core.ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(
                    output: androidx.camera.core.ImageCapture.OutputFileResults,
                ) {
                    onSaved()
                }

                override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                    onError()
                }
            },
        )
    }.onFailure { onError() }
}
