package com.daengs.app.ui.dex

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * 장면의 배경음. 이 저장소의 **첫 오디오 코드**다.
 *
 * 모양은 [DeviceTilt] 와 맞췄다 — 이 저장소에서 생명주기를 다루는 코드가 그것
 * 하나뿐이라, 결을 달리하면 다음 사람이 두 방식을 다 읽어야 한다.
 *
 * ## 소리가 안 나도 화면은 산다
 *
 * 파일이 없거나 코덱이 못 열면 **조용히 아무것도 안 한다.** `rememberAssetImage` 가
 * null 을 돌려주는 것과 같은 태도다. 배경음 때문에 도감이 죽으면 안 된다.
 *
 * ## 오디오 포커스는 GAIN_TRANSIENT 다
 *
 * 이머시브는 잠깐 머무는 화면이다. `GAIN` 으로 잡으면 상대 앱은 "끝났구나" 하고
 * 스스로 돌아오지 않는다 — 도감을 닫고 나면 듣던 음악이 그냥 멈춘 채로 남는다.
 * `GAIN_TRANSIENT` 는 우리가 놓는 순간 상대가 이어서 튼다.
 *
 * @param asset `assets/` 아래 경로. null 이면 아무 일도 안 한다.
 * @param volume 0~1. **0 이면 포커스까지 놓는다** — 음소거인데 남의 음악을 계속
 *   붙잡고 있으면 껐다는 말이 거짓이 된다. 진입 연출에 맞춰 서서히 올리는
 *   페이드도 이 값으로 한다.
 */
@Composable
fun SceneMusic(asset: String?, volume: Float) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(context, owner, asset) {
        if (asset == null) return@DisposableEffect onDispose { }

        val made = runCatching {
            MediaPlayer().apply {
                context.assets.openFd(asset).use {
                    setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                isLooping = true
                // 처음엔 무음이다. 아래 [LaunchedEffect] 가 실제 값을 넣는다 —
                // 여기서 1로 두면 페이드가 시작되기 전 한 프레임이 크게 터진다.
                setVolume(0f, 0f)
                prepare()
            }
        }.getOrNull() ?: return@DisposableEffect onDispose { }

        player = made

        onDispose {
            player = null
            runCatching { made.stop() }
            made.release()
        }
    }

    // 화면이 보이는가. **상태로 들고 있어야 한다.**
    //
    // 처음엔 관찰자 안에서 곧장 pause 를 불렀는데, 홈에서 돌아왔을 때 다시 켜지지
    // 않았다 — 트는 판단은 아래 [LaunchedEffect] 가 하는데 그 키(플레이어·볼륨)가
    // 아무것도 안 바뀌어서 effect 자체가 다시 돌지 않았다. 멈추기만 하고 켜는
    // 쪽이 없는 상태였다. 생명주기를 키에 넣어야 둘이 짝이 맞는다.
    var started by remember {
        mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> started = true
                Lifecycle.Event.ON_STOP -> started = false
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    // 재생·정지·포커스를 **한 군데서** 정한다. 상태가 두 군데로 갈리면
    // "음소거인데 포커스는 쥐고 있는" 같은 어긋난 조합이 생긴다.
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val focus = remember { FocusHolder() }

    LaunchedEffect(player, volume, started, audio) {
        val p = player ?: return@LaunchedEffect
        val level = volume.coerceIn(0f, 1f)
        runCatching { p.setVolume(level, level) }
        // 안 보이거나 음소거면 소리도 포커스도 놓는다. 배경으로 내려간 채
        // 포커스를 쥐고 있으면 듣던 음악이 안 돌아온다.
        if (!started || level <= 0f) {
            runCatching { if (p.isPlaying) p.pause() }
            focus.release(audio)
        } else {
            if (focus.request(audio, p)) runCatching { if (!p.isPlaying) p.start() }
        }
    }

    DisposableEffect(focus, audio) {
        onDispose { focus.release(audio) }
    }
}

/**
 * 오디오 포커스 한 개를 들고 있는 자리.
 *
 * 요청과 반납이 짝이 맞아야 한다. 반납을 빠뜨리면 도감을 닫아도 남의 음악이
 * 안 돌아온다 — 화면에는 아무 흔적이 없어서 알아채기 어렵다.
 */
private class FocusHolder {
    private var granted: AudioFocusRequest? = null

    fun request(audio: AudioManager?, player: MediaPlayer): Boolean {
        if (audio == null) return false
        if (granted != null) return true
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setOnAudioFocusChangeListener { change ->
                // 전화가 오면 잠깐 놓고, 끝나면 다시 튼다. 영영 잃으면
                // (다른 앱이 GAIN 으로 가져가면) 멈춘 채로 둔다.
                when (change) {
                    AudioManager.AUDIOFOCUS_GAIN -> runCatching { player.start() }
                    else -> runCatching { if (player.isPlaying) player.pause() }
                }
            }
            .build()
        val ok = audio.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (ok) granted = req
        return ok
    }

    fun release(audio: AudioManager?) {
        val req = granted ?: return
        granted = null
        audio?.abandonAudioFocusRequest(req)
    }
}
