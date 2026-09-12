package com.daengs.app.ui.chat

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext

/**
 * 음성 입력 설정 둘. 둘 다 기본 꺼짐이다.
 *
 * [com.daengs.app.ui.dex.rememberMuted] 와 같은 꼴의 SharedPreferences 다 — 값이
 * 참/거짓 하나씩이고, 채팅 화면이 **합성 시점에 바로 읽어야** 마이크를 누른 첫
 * 순간부터 맞는 모드로 돈다. 채팅 화면과 마이 탭이 각자 부르므로 `MainActivity`
 * 배선이 필요 없다.
 */

/** 말이 끝나면 확인 없이 질문으로 보낼까. 꺼져 있으면 입력칸에 넣기만 한다. */
@Composable
fun rememberVoiceAutoSend(): MutableState<Boolean> = rememberVoicePref(KEY_AUTO_SEND)

/**
 * 마이크를 다시 누를 때까지 계속 들을까. 꺼져 있으면 말이 끊기는 순간 인식기가
 * 스스로 멈춘다 — 그 동작을 끌 수 없어서 이 설정은 "토막이 끝나면 다시 듣기" 다
 * ([VoiceInputController] 참고).
 */
@Composable
fun rememberVoiceHoldToStop(): MutableState<Boolean> = rememberVoicePref(KEY_HOLD_TO_STOP)

@Composable
private fun rememberVoicePref(key: String): MutableState<Boolean> {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
    val state = remember(prefs, key) { mutableStateOf(prefs.getBoolean(key, false)) }
    // 바뀔 때만 쓴다. 토글에서 직접 쓰게 하면 부르는 쪽마다 잊어버릴 자리가 생긴다.
    LaunchedEffect(prefs, key) {
        snapshotFlow { state.value }.collect { prefs.edit().putBoolean(key, it).apply() }
    }
    return state
}

private const val PREFS = "chat-voice"
private const val KEY_AUTO_SEND = "auto_send"
private const val KEY_HOLD_TO_STOP = "hold_to_stop"
