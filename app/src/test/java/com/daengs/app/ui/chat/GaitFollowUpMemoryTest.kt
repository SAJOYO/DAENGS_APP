package com.daengs.app.ui.chat

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 비교 참조를 **언제 기억하고 언제 버리는가** (백엔드 D-081).
 *
 * 규칙은 셋이고 전부 한 곳씩이다. 기억은 비교를 대화에 남길 때, 버리는 것은 새 보행 분석을
 * 시작할 때, 갱신은 기억하는 자리가 하나라 저절로 된다.
 *
 * **왜 화면 테스트가 아니라 소스 검사인가.** 셋 다 단일 지점의 대입 한 줄이라 로직이 없고,
 * 챗 화면 Compose 테스트는 이 저장소에서 대기 시간에 흔들린다(기존 실패 목록에 여럿 있다).
 * 대신 이 저장소가 이미 쓰는 방식(`DiaryInputBoundaryTest` · `DesignLockTest`)으로 **그 줄이
 * 그 자리에 있는지**를 본다. 자리가 옮겨지면 이 테스트가 먼저 깨진다.
 *
 * ⚠️ **규칙 4 가 깨지면 사용자가 새로 찍을 길이 막힌다.** 참조를 계속 들고 있으면 "새로 영상
 * 찍고 싶어" 라고 쳐도 서버에 비교 문맥이 있어 해설이 가로채고 등록 카드가 안 뜬다.
 * 중복을 없애려다 입구를 막는 셈이라, 이 한 줄이 이 PR 에서 제일 무겁다.
 */
class GaitFollowUpMemoryTest {

    private val source: String by lazy {
        val roots = listOf(File("src/main/java"), File("app/src/main/java"))
        val dir = roots.first { it.isDirectory }
        File(dir, "com/daengs/app/ui/chat/ChatScreen.kt").readText()
    }

    /** `{` 로 열고 짝이 맞는 `}` 까지. 람다 안만 보려고 중괄호를 센다. */
    private fun blockAfter(marker: String): String {
        val start = source.indexOf(marker)
        assertTrue("$marker 를 못 찾음 — 자리가 바뀌었으면 이 테스트부터 고칠 것", start >= 0)
        val open = source.indexOf('{', start)
        var depth = 0
        for (i in open until source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(open, i + 1)
                }
            }
        }
        throw AssertionError("$marker 의 블록이 안 닫힘")
    }

    @Test
    fun `비교를 대화에 남길 때 두 id 를 기억한다`() {
        val block = blockAfter("onSaveToChat =")
        assertTrue(
            "비교를 대화에 남기면서 참조를 기억하지 않으면 이어 묻기가 해설에 닿지 못한다",
            block.contains("lastGaitPair =") && block.contains("GaitFollowUp("),
        )
        assertTrue(
            "타이핑 턴은 신호를 보내지 않는다 — 보내면 그 대화의 모든 질문을 보행이 가로챈다",
            block.contains("explicit = false"),
        )
    }

    @Test
    fun `새 보행 분석을 시작하면 참조를 즉시 버린다`() {
        val block = blockAfter("val runGait: (Uri) -> Unit =")
        assertTrue(
            "참조를 들고 있으면 새로 찍겠다는 질문까지 해설이 가로채 등록 카드가 안 뜬다",
            block.contains("lastGaitPair = null"),
        )
    }

    @Test
    fun `기억하는 자리가 하나라 새 비교는 저절로 갱신된다`() {
        assertEquals(
            "기억하는 자리가 둘이 되면 어느 비교를 가리키는지 코드만 봐서는 알 수 없다",
            1,
            Regex("lastGaitPair = GaitFollowUp\\(").findAll(source).count(),
        )
    }

    @Test
    fun `보통 질문이 기억해 둔 참조를 싣는다`() {
        val block = blockAfter("val sendQuery: (String) -> Unit =")
        assertTrue(
            "이 줄이 없으면 서버에 비교 문맥이 안 가고 D-081 전환이 열리지 않는다",
            block.contains("lastGaitPair"),
        )
    }

    @Test
    fun `칩은 지금처럼 신호까지 보낸다`() {
        assertTrue(
            "칩은 사용자가 뜻을 밝힌 자리다 — 서버가 묻지 않고 해설로 보낸다",
            source.contains("explicit = true"),
        )
    }
}
