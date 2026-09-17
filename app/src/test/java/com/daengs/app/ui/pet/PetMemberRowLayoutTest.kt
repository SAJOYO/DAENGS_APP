package com.daengs.app.ui.pet

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.daengs.app.pet.PetMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 보호자 목록 한 줄의 배치.
 *
 * **역할 배지는 이름 바로 뒤, 「내보내기」는 오른쪽 끝이다.** 배지를 오른쪽 끝에 두면
 * 「내보내기」가 있는 줄만 배지가 안쪽으로 밀려 줄마다 배지 자리가 달라 보였다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
// **글자 폭을 실제로 잰다.** 기본(LEGACY) 그래픽에서는 글자 폭이 몇 px 로 나와 겹침을 못 잡는다.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PetMemberRowLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    private val longName = "이름이아주아주아주길어서한줄에는절대로다안들어가는보호자이름"

    private fun screen(members: List<PetMember>) {
        compose.setContent {
            PetMembersScreen(
                members = members,
                petName = "노을이",
                currentUserId = "u1",
                isGroupOwner = true,
                onOpenInvites = {},
                onRemove = {},
            )
        }
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
        return results.single()
    }

    private fun DpRect.overlaps(o: DpRect) = left < o.right && o.left < right && top < o.bottom && o.top < bottom

    @Test
    fun `배지는 이름 바로 뒤에 붙고 내보내기는 오른쪽 끝에 있다`() {
        screen(listOf(PetMember("u1", "아빠", isOwner = true), PetMember("u2", "가연", isOwner = false)))

        // 내보내기가 있는 줄: 이름 → 배지 → (빈칸) → 내보내기
        val name = bounds("member-name-u2")
        val badge = bounds("role-u2")
        val remove = bounds("remove-u2")
        val row = bounds("member-u2")
        assertTrue("배지가 이름 뒤에 온다", badge.left >= name.right)
        assertTrue("배지가 이름에 바로 붙는다(빈칸이 벌어지지 않는다)", badge.left - name.right <= 12.dp)
        assertTrue("내보내기가 배지보다 오른쪽이다", remove.left >= badge.right)
        assertTrue("내보내기가 줄 오른쪽 끝에 붙는다", row.right - remove.right <= 16.dp)

        // 자기 줄(내보내기 없음): 이름 · 나 → 배지. 배지가 오른쪽 끝으로 밀려나지 않는다.
        val ownerName = bounds("member-name-u1")
        val ownerBadge = bounds("role-u1")
        val ownerRow = bounds("member-u1")
        assertTrue(ownerBadge.left > ownerName.right)
        assertTrue("「나」 하나만큼만 떨어진다", ownerBadge.left - ownerName.right <= 40.dp)
        assertTrue("내보내기가 없어도 배지가 오른쪽 끝으로 가지 않는다", ownerRow.right - ownerBadge.right > 40.dp)

        // 버튼이 있는 줄만 높아지면 목록이 들쭉날쭉하다.
        assertEquals("내보내기 유무로 줄 높이가 달라진다", ownerRow.bottom - ownerRow.top, row.bottom - row.top)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun `긴 이름은 이름만 말줄임되고 배지와 내보내기는 겹치지 않고 보인다`() {
        screen(listOf(PetMember("u1", "아빠", isOwner = true), PetMember("u2", longName, isOwner = false)))

        val nameNode = compose.onNodeWithTag("member-name-u2", useUnmergedTree = true)
        val layout = nameNode.layout()
        assertEquals("이름은 한 줄", 1, layout.lineCount)
        assertTrue("넘친 이름은 잘린다", layout.hasVisualOverflow)

        compose.onNodeWithTag("role-u2", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("remove-u2", useUnmergedTree = true).assertIsDisplayed()
        val name = bounds("member-name-u2")
        val badge = bounds("role-u2")
        val remove = bounds("remove-u2")
        val row = bounds("member-u2")
        assertFalse("이름과 배지가 겹친다", name.overlaps(badge))
        assertFalse("배지와 내보내기가 겹친다", badge.overlaps(remove))
        assertTrue("내보내기가 줄 밖으로 나갔다", remove.right <= row.right)
        assertTrue("배지가 줄 밖으로 나갔다", badge.right <= row.right)
    }
}
