package com.daengs.app.ui.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * 채팅 입력칸의 음성 인식.
 *
 * 안드로이드 내장 [SpeechRecognizer] 를 그대로 쓴다 — 서버에 STT 가 없고, 지금 쓰임새는
 * 한국어 짧은 질문이라 기기 인식으로 충분하다. 인식한 글은 **입력칸에 넣기만** 하고
 * 보내는 건 사용자 몫이다 (설정으로 바로 보내게 할 수 있다 — [rememberVoiceAutoSend]).
 *
 * **구글 인식기는 침묵이 오면 스스로 멈추고, 이걸 끌 수 없다.** 침묵 길이를 조절하는
 * extra 가 있지만 구글 구현이 대체로 무시한다. 그래서 "정지를 누를 때까지 듣기"
 * ([rememberVoiceHoldToStop]) 는 한 토막이 끝나면 곧바로 다시 `startListening` 하는
 * 루프다. 토막 사이에 반 초쯤 못 듣는 틈이 있다 — 다른 앱들도 같은 방법을 쓴다.
 *
 * 이벤트는 셋이다.
 * - [onPartial] 말하는 동안 자라는 글. 매번 **듣기 시작할 때의 초안** 뒤에 갈아 끼운다
 *   ([mergeVoiceText] 참고). 이어 붙이면 "사 사료 사료를" 이 된다.
 * - [onSegment] 한 토막이 끝난 글. 이걸 붙인 것이 다음 토막의 기준 초안이 된다.
 * - [onFinished] 듣기가 끝났다 (어느 모드든). 바로 보내기는 여기서 판단한다.
 */
@Stable
class VoiceInputController internal constructor(
    private val context: Context,
    private val holdToStop: () -> Boolean,
    private val onPartial: (String) -> Unit,
    private val onSegment: (String) -> Unit,
    private val onFinished: () -> Unit,
    private val onError: (String) -> Unit,
) {
    /** 듣는 중인가. 입력줄이 아이콘 색과 안내문을 바꾸는 근거다. */
    var listening by mutableStateOf(false)
        private set

    private var recognizer: SpeechRecognizer? = null

    /** 사용자가 마이크를 다시 눌러 멈추는 중. 이어 듣기 루프를 끊는 신호다. */
    private var userStopped = false

    /**
     * 듣기 시작. 권한은 부르는 쪽이 먼저 받아 둔다 — 여기서는 없으면 안내만 한다.
     * 인식기가 없는 기기(구글 앱 없음, 일부 에뮬레이터)도 여기서 걸러진다.
     */
    fun start() {
        if (listening) return
        if (!hasMicPermission(context)) {
            onError(VOICE_DENIED)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError(VOICE_UNAVAILABLE)
            return
        }
        userStopped = false
        listening = true
        listen()
    }

    /**
     * 사용자가 멈춤. 지금까지 인식한 것으로 마무리한다 — `stopListening` 이 마지막
     * 토막을 [RecognitionListener.onResults] 로 돌려주고, 아무 말도 없었으면
     * `ERROR_NO_MATCH` 로 온다. 둘 다 [finish] 로 모인다.
     */
    fun stop() {
        if (!listening) return
        userStopped = true
        recognizer?.stopListening()
    }

    private fun listen() {
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        r.startListening(recognizeIntent(context))
    }

    private fun finish() {
        listening = false
        onFinished()
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        // ⚠️ 세 콜백 다 `listening` 을 먼저 본다. `destroy()`·`finish()` 뒤에도 메인
        // 스레드 큐에 이미 올라간 콜백은 온다 — 가드가 없으면 끝난 컨트롤러가
        // 인식기를 새로 만들어 마이크를 다시 열거나, 비운 입력칸에 옛 글이 다시 찬다.
        override fun onPartialResults(partialResults: Bundle?) {
            if (!listening) return
            val text = partialResults.firstResult() ?: return
            if (text.isNotBlank()) onPartial(text)
        }

        override fun onResults(results: Bundle?) {
            if (!listening) return
            results.firstResult()?.takeIf { it.isNotBlank() }?.let(onSegment)
            if (shouldKeepListening(holdToStop(), userStopped)) listen() else finish()
        }

        override fun onError(error: Int) {
            if (!listening) return
            val silent = error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            // 이어 듣기 중의 침묵은 오류가 아니다 — 다시 듣는다.
            if (silent && shouldKeepListening(holdToStop(), userStopped)) {
                listen()
                return
            }
            // 멈추라고 한 뒤 오는 ERROR_CLIENT 는 취소의 메아리다. 안내하지 않는다.
            val message = if (userStopped) null else voiceErrorMessage(error)
            finish()
            message?.let(onError)
        }
    }

    internal fun destroy() {
        recognizer?.destroy()
        recognizer = null
        listening = false
    }
}

@Composable
fun rememberVoiceInput(
    holdToStop: Boolean,
    onPartial: (String) -> Unit,
    onSegment: (String) -> Unit,
    onFinished: () -> Unit,
    onError: (String) -> Unit,
): VoiceInputController {
    val context = LocalContext.current
    val hold by rememberUpdatedState(holdToStop)
    val partial by rememberUpdatedState(onPartial)
    val segment by rememberUpdatedState(onSegment)
    val finished by rememberUpdatedState(onFinished)
    val error by rememberUpdatedState(onError)
    val controller = remember(context) {
        VoiceInputController(
            context = context.applicationContext,
            holdToStop = { hold },
            onPartial = { partial(it) },
            onSegment = { segment(it) },
            onFinished = { finished() },
            onError = { error(it) },
        )
    }
    DisposableEffect(controller) { onDispose { controller.destroy() } }
    return controller
}

/**
 * 인식 요청. 언어는 `ko-KR` 고정이다.
 *
 * Android 13 부터는 "이 언어들 사이에서 알아서 바꿔라" 를 덧붙일 수 있다. 한국어 말투에
 * 낀 영어 단어는 `ko-KR` 만으로도 받지만, 문장째 영어로 넘어가면 이 옵션이 있어야
 * 따로 받는다. 지원 안 하는 기기는 조용히 무시하므로 넣어서 손해가 없다.
 */
private fun recognizeIntent(context: Context): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, VOICE_LANGUAGE)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, VOICE_LANGUAGE)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
            putStringArrayListExtra(
                RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES,
                arrayListOf(VOICE_LANGUAGE, "en-US"),
            )
        }
    }

private fun Bundle?.firstResult(): String? =
    this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

fun hasMicPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

// ── 순수 규칙. 테스트가 잡는 것은 여기부터다 ([VoiceInputTest]) ──────────────────

/**
 * 인식한 [spoken] 을 [base] 뒤에 붙인다.
 *
 * 초안이 비어 있으면 그대로, 글이 있으면 띄어쓰기 한 칸을 두고 뒤에. 초안 끝이 이미
 * 공백이나 줄바꿈이면 겹치지 않는다. 인식 결과의 앞뒤 공백은 뗀다 — 인식기가 앞에
 * 공백을 붙여 주는 일이 있다.
 *
 * [base] 는 **듣기 시작할 때 고정한** 초안이어야 한다. 부분 결과가 자라는 동안 매번
 * 같은 기준에 갈아 끼워야 "사 사료 사료를" 이 안 된다.
 */
fun mergeVoiceText(base: String, spoken: String): String {
    val text = spoken.trim()
    if (text.isEmpty()) return base
    if (base.isEmpty()) return text
    return if (base.last().isWhitespace()) base + text else "$base $text"
}

/** 토막이 끝났을 때 계속 들을까. 직접 정지 모드에서 사용자가 아직 안 멈췄을 때만. */
fun shouldKeepListening(holdToStop: Boolean, userStopped: Boolean): Boolean =
    holdToStop && !userStopped

/**
 * 인식기 에러 번호 → 안내문. `null` 이면 알리지 않는다.
 *
 * 말이 없어서 끝난 것(NO_MATCH · SPEECH_TIMEOUT)은 오류가 아니다 — 마이크를 눌렀다가
 * 아무 말도 안 한 사람에게 알림을 띄우면 성가시다.
 */
fun voiceErrorMessage(error: Int): String? = when (error) {
    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> null
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VOICE_DENIED
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
        "음성 인식에 인터넷 연결이 필요해요."
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "음성 인식기가 바빠요. 잠시 뒤 다시 눌러 주세요."
    else -> "음성을 알아듣지 못했어요. 다시 말해 주세요."
}

private const val VOICE_LANGUAGE = "ko-KR"

const val VOICE_DENIED = "마이크 권한이 필요해요. 거절했다면 앱 설정에서 허용해 주세요."

const val VOICE_UNAVAILABLE = "이 기기에서는 음성 인식을 쓸 수 없어요."
