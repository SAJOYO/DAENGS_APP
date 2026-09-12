package com.daengs.app.ui.chat

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 음성 인식 결과를 입력칸에 넣는 규칙과 에러 안내문.
 *
 * 인식기 자체는 기기에서만 돈다. 여기서는 **결과가 초안에 어떻게 붙는지**와
 * **에러 번호가 어떤 말이 되는지**만 잡는다 — 이 둘이 틀리면 글이 겹쳐 붙거나
 * 사용자가 "오류 7" 같은 걸 보게 된다.
 */
class VoiceInputTest {

    @Test
    fun `초안이 비어 있으면 인식한 말이 그대로 들어간다`() {
        assertEquals("사료를 안 먹어요", mergeVoiceText("", "사료를 안 먹어요"))
    }

    @Test
    fun `초안에 글이 있으면 띄어쓰기 한 칸 두고 뒤에 붙는다`() {
        assertEquals("우리 강아지가 사료를 안 먹어요", mergeVoiceText("우리 강아지가", "사료를 안 먹어요"))
    }

    @Test
    fun `초안 끝이 이미 공백이면 공백을 겹치지 않는다`() {
        assertEquals("우리 강아지가 사료를", mergeVoiceText("우리 강아지가 ", "사료를"))
        assertEquals("우리 강아지가\n사료를", mergeVoiceText("우리 강아지가\n", "사료를"))
    }

    @Test
    fun `인식 결과의 앞뒤 공백은 떼고 붙인다`() {
        assertEquals("사료를", mergeVoiceText("", "  사료를 "))
        assertEquals("우리 강아지가 사료를", mergeVoiceText("우리 강아지가", " 사료를 "))
    }

    @Test
    fun `인식 결과가 비어 있으면 초안을 건드리지 않는다`() {
        assertEquals("우리 강아지가", mergeVoiceText("우리 강아지가", ""))
        assertEquals("우리 강아지가", mergeVoiceText("우리 강아지가", "   "))
    }

    @Test
    fun `부분 결과는 시작 시점의 초안 뒤에 갈아 끼운다`() {
        // 말하는 동안 "사" → "사료" → "사료를" 로 자라는데, 매번 이어 붙이면
        // "사 사료 사료를" 이 된다. 기준 초안은 듣기 시작할 때 고정한다.
        val base = "우리 강아지가"
        assertEquals("우리 강아지가 사", mergeVoiceText(base, "사"))
        assertEquals("우리 강아지가 사료", mergeVoiceText(base, "사료"))
        assertEquals("우리 강아지가 사료를", mergeVoiceText(base, "사료를"))
    }

    @Test
    fun `말이 없어서 끝난 것은 오류가 아니다`() {
        // 마이크를 눌렀다가 아무 말도 안 한 경우. 알림을 띄우면 성가시다.
        assertNull(voiceErrorMessage(SpeechRecognizer.ERROR_NO_MATCH))
        assertNull(voiceErrorMessage(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
    }

    @Test
    fun `권한 없음과 네트워크 없음은 각각 다른 말로 안내한다`() {
        assertEquals(VOICE_DENIED, voiceErrorMessage(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
        assertEquals(
            "음성 인식에 인터넷 연결이 필요해요.",
            voiceErrorMessage(SpeechRecognizer.ERROR_NETWORK),
        )
        assertEquals(
            "음성 인식에 인터넷 연결이 필요해요.",
            voiceErrorMessage(SpeechRecognizer.ERROR_NETWORK_TIMEOUT),
        )
    }

    @Test
    fun `인식기가 바쁘면 잠시 뒤에 다시 하라고 한다`() {
        assertEquals("음성 인식기가 바빠요. 잠시 뒤 다시 눌러 주세요.", voiceErrorMessage(SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
    }

    @Test
    fun `모르는 번호는 일반 안내문으로 떨어진다`() {
        assertEquals("음성을 알아듣지 못했어요. 다시 말해 주세요.", voiceErrorMessage(9999))
        assertEquals("음성을 알아듣지 못했어요. 다시 말해 주세요.", voiceErrorMessage(SpeechRecognizer.ERROR_AUDIO))
    }

    @Test
    fun `이어 듣기는 사용자가 멈추기 전까지 토막이 끝나도 계속한다`() {
        // 자동 정지 모드: 토막이 끝나면 멈춘다.
        assertEquals(false, shouldKeepListening(holdToStop = false, userStopped = false))
        // 직접 정지 모드: 토막이 끝나도 다시 듣는다.
        assertEquals(true, shouldKeepListening(holdToStop = true, userStopped = false))
        // 직접 정지 모드라도 사용자가 눌러 멈췄으면 끝이다.
        assertEquals(false, shouldKeepListening(holdToStop = true, userStopped = true))
    }
}
