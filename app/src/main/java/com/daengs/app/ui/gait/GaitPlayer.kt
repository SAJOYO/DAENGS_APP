package com.daengs.app.ui.gait

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.daengs.app.gait.GaitRecord
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.TextMuted

/**
 * 보행 영상 재생기.
 *
 * **시스템 플레이어(`ACTION_VIEW`)로 던지지 않는다.** 그쪽이 의존성 0개로 싸지만,
 * 이 기능의 요점이 "같은 아이의 두 시점을 나란히 본다" 라서 앱 밖으로 나가면
 * 한 번에 한 편씩만 보게 되고 비교라는 일 자체가 성립하지 않는다.
 *
 * ### 소리를 끈다
 * 보행에서 볼 것은 다리가 움직이는 모양이지 소리가 아니다. 게다가 비교 화면은
 * 두 편이 동시에 도는 자리라, 소리가 살아 있으면 두 영상의 잡음이 겹쳐 나온다.
 *
 * ### 파일이 없을 때가 흔하다
 * 표본 기록은 [GaitRecord.video] 가 `null` 이다. 그때는 **재생기를 아예 안 만들고**
 * 자리표시로 물러선다 — 빈 ExoPlayer 는 검은 네모가 되어 고장으로 읽힌다.
 *
 * @param playing 밖에서 재생을 몰 때 쓴다. 비교 화면이 두 편을 한 단추로 돌리는 자리
 * @param controls 재생 컨트롤을 띄울까. 비교 화면의 작은 칸에서는 끄고 단추로 몬다
 */
@Composable
fun GaitVideoPlayer(
    record: GaitRecord,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
    controls: Boolean = true,
    loop: Boolean = true,
) {
    val video = record.video
    if (video == null) {
        // 재생할 것이 없다. 왜 없는지는 상세 화면의 요약이 한 줄로 말해 준다.
        Box(
            modifier.clip(RoundedCornerShape(14.dp)).background(PinkFaint),
            contentAlignment = Alignment.Center,
        ) {
            PawMark(Modifier.fillMaxSize().padding(24.dp), alpha = 0.30f)
            Text(
                "재생할 영상이 없어요",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            )
        }
        return
    }

    val context = LocalContext.current
    val player = remember(video) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(video))
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            // 위 주석 참고 — 보행은 눈으로 보는 것이고, 비교 화면은 두 편이 같이 돈다.
            volume = 0f
            prepare()
        }
    }

    // **놓아 주는 것을 잊으면 안 된다.** 코덱과 서피스를 쥔 채로 남으면 영상을
    // 몇 편 열고 나서 다음 재생이 조용히 실패한다.
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    // 앱이 뒤로 가면 멈춘다. 안 멈추면 화면이 없는데 디코더가 계속 돌고,
    // 다시 앞으로 왔을 때 영상이 저 혼자 앞서 있다.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(playing, player) {
        if (playing) player.play() else player.pause()
    }

    Box(modifier.clip(RoundedCornerShape(14.dp)).background(PinkFaint)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = controls
                    // 로딩 중에 검은 네모가 번쩍이지 않게 카드 색을 깔아 둔다.
                    setShutterBackgroundColor(CardWhite.toArgb())
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            update = { it.useController = controls },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
