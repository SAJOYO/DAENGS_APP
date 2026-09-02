package com.daengs.app.ui.camera

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.video.AudioConfig
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.io.File

/**
 * 앱 안에서 영상을 찍는 상태.
 *
 * 한 단추로 시작하고 멈춘다 — [recording] 이 지금 찍는 중인지이고, [toggle] 이 그
 * 단추다.
 */
class VideoRecorder internal constructor(
    private val start: () -> Unit,
    private val stop: () -> Unit,
    val recordingProvider: () -> Boolean,
) {
    val recording: Boolean get() = recordingProvider()

    fun toggle() {
        if (recording) stop() else start()
    }
}

/**
 * [file] 에 영상을 담는 녹화기.
 *
 * **소리를 안 담는다.** 보행 분석은 걷는 모습만 보고, 담으면 `RECORD_AUDIO` 권한이
 * 하나 더 는다 — 쓰지도 않을 권한을 묻는 창이 뜬다.
 *
 * 화면이 사라지면 찍던 것을 멈춘다. 안 멈추면 파일이 열린 채 남아서, 다음에 찍을 때
 * 같은 자리에 못 쓴다.
 */
@SuppressLint("MissingPermission")
@Composable
fun rememberVideoRecorder(
    controller: LifecycleCameraController,
    file: File,
    onDone: (Uri) -> Unit,
    onError: (String) -> Unit,
): VideoRecorder {
    val context = LocalContext.current
    var active by remember { mutableStateOf<Recording?>(null) }

    val recorder = remember(controller, file) {
        VideoRecorder(
            start = {
                if (active != null) return@VideoRecorder
                // 같은 이름에 덮어쓴다. 찍을 때마다 새 파일을 만들면 캐시가 계속 는다.
                file.delete()
                active = runCatching {
                    controller.startRecording(
                        FileOutputOptions.Builder(file).build(),
                        AudioConfig.AUDIO_DISABLED,
                        ContextCompat.getMainExecutor(context),
                    ) { event ->
                        if (event is VideoRecordEvent.Finalize) {
                            active = null
                            if (event.hasError()) {
                                onError("영상을 저장하지 못했어요.")
                            } else {
                                onDone(gaitUri(context, file))
                            }
                        }
                    }
                }.getOrElse {
                    onError("카메라를 열 수 없어요.")
                    null
                }
            },
            stop = {
                active?.stop()
                active = null
            },
            recordingProvider = { active != null },
        )
    }

    DisposableEffect(recorder) {
        onDispose {
            active?.stop()
            active = null
        }
    }
    return recorder
}

/** 찍은 파일을 뒤쪽(분석·재생)이 아는 모양(`content://`)으로 바꾼다. */
private fun gaitUri(context: Context, file: File): Uri =
    androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
