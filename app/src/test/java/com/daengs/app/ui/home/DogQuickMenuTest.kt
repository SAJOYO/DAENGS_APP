package com.daengs.app.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.daengs.app.ui.theme.DaengsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 강아지 퀵 메뉴가 **어디에 앉고 무엇을 부르는지**.
 *
 * 그림이 예쁜지는 여기서 못 잡는다 — 그건 실기기에서 본다. 여기가 잡는 것은 부채꼴
 * 각도와, 칸 수가 줄었을 때 다시 펴지는지, 그리고 바깥을 눌러 닫히는지다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class DogQuickMenuTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `셋이면 왼쪽 위 · 곧게 위 · 오른쪽 위로 펴진다`() {
        val deg = (0 until 3).map { fanAngleOf(it, 3) * 180.0 / PI }
        assertEquals(-150.0, deg[0], 0.5)
        assertEquals(-90.0, deg[1], 0.5)
        assertEquals(-30.0, deg[2], 0.5)
    }

    @Test
    fun `세 각도 모두 머리보다 위로 간다`() {
        // 화면 좌표는 y 가 아래로 간다. 위로 가려면 sin 이 음수여야 한다.
        for (i in 0 until 3) {
            assertTrue("칸 $i 이 머리 아래로 내려갔다", sin(fanAngleOf(i, 3)) < 0f)
        }
    }

    @Test
    fun `가운데 칸은 좌우로 안 치우친다`() {
        assertEquals(0.0, cos(fanAngleOf(1, 3)).toDouble(), 1e-6)
    }

    @Test
    fun `하나만 남으면 곧게 위로 선다`() {
        // 로그인 전이라 길이 하나뿐일 때. 부채꼴을 억지로 펴면 혼자 비스듬히 선다.
        assertEquals(-90.0, fanAngleOf(0, 1) * 180.0 / PI, 0.5)
        assertEquals(0.0, cos(fanAngleOf(0, 1)).toDouble(), 1e-6)
    }

    @Test
    fun `길이 없으면 그 칸은 아예 안 생긴다`() {
        // 눌러도 아무 일이 없는 버튼을 보여 주는 것보다 없는 편이 낫다.
        val only = dogQuickActions(onWalk = {}, onDraw = null, onAsk = null)
        assertEquals(1, only.size)
        assertEquals("산책", only[0].label)

        assertEquals(0, dogQuickActions(null, null, null).size)
        assertEquals(3, dogQuickActions({}, {}, {}).size)
    }

    @Test
    fun `산책 카드 질문 순서로 앉는다`() {
        assertEquals(
            listOf("산책", "카드", "질문"),
            dogQuickActions({}, {}, {}).map { it.label },
        )
    }

    @Test
    fun `칸을 누르면 그 길이 불린다`() {
        var walked = 0
        rule.setContent {
            DaengsTheme {
                DogQuickMenu(
                    head = Offset(400f, 600f),
                    actions = dogQuickActions(onWalk = { walked++ }, onDraw = {}, onAsk = {}),
                    onDismiss = {},
                )
            }
        }
        rule.onNodeWithTag(quickActionTag("산책")).performClick()
        assertEquals(1, walked)
    }

    @Test
    fun `바깥을 누르면 닫힌다`() {
        var dismissed = 0
        rule.setContent {
            DaengsTheme {
                DogQuickMenu(
                    head = Offset(400f, 600f),
                    actions = dogQuickActions({}, {}, {}),
                    onDismiss = { dismissed++ },
                )
            }
        }
        rule.onNodeWithTag(QUICK_MENU_SCRIM_TAG).performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun `길이 하나도 없으면 판 자체가 안 뜬다`() {
        // 판만 깔리면 방이 먹통이 된다 — 눌러도 아무것도 없는데 터치만 먹는다.
        rule.setContent {
            DaengsTheme {
                DogQuickMenu(head = Offset(400f, 600f), actions = emptyList(), onDismiss = {})
            }
        }
        rule.onNodeWithTag(QUICK_MENU_TAG).assertDoesNotExist()
    }

    @Test
    fun `벽에 붙은 아이의 메뉴도 세 칸이 다 있다`() {
        // 화면 왼쪽 끝(x=0)에 서 있어도 칸이 잘려 사라지면 안 된다.
        rule.setContent {
            DaengsTheme {
                DogQuickMenu(
                    head = Offset(0f, 600f),
                    actions = dogQuickActions({}, {}, {}),
                    onDismiss = {},
                )
            }
        }
        rule.onNodeWithTag(quickActionTag("산책")).assertIsDisplayed()
        rule.onNodeWithTag(quickActionTag("카드")).assertIsDisplayed()
        rule.onNodeWithTag(quickActionTag("질문")).assertIsDisplayed()
    }
}
