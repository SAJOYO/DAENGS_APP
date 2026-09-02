package com.daengs.app.ui.dex

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

/**
 * 배경음을 껐는지 기억한다.
 *
 * [com.daengs.app.miniroom.RoomStore] 와 같은 이유로 SharedPreferences 다 —
 * 저장할 게 참/거짓 하나이고, **합성 시점에 바로 읽어야** 첫 프레임에 소리가
 * 잠깐 났다가 꺼지는 일이 없다.
 *
 * 남의 음악을 멈추면서까지 트는 이상 끄는 길이 있어야 하고, 껐으면 다음에
 * 켤 때도 꺼져 있어야 한다. 매번 다시 켜지면 끈 게 아니다.
 */
@Composable
fun rememberMuted(): MutableState<Boolean> {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.applicationContext.getSharedPreferences("dex", Context.MODE_PRIVATE)
    }
    val state = remember(prefs) { mutableStateOf(prefs.getBoolean(KEY_MUTED, false)) }

    // 바뀔 때만 쓴다. 토글에서 직접 쓰게 하면 부르는 쪽마다 잊어버릴 자리가 생긴다.
    LaunchedEffect(prefs) {
        snapshotFlow { state.value }.collect { prefs.edit().putBoolean(KEY_MUTED, it).apply() }
    }
    return state
}

private const val KEY_MUTED = "bgm_muted"
