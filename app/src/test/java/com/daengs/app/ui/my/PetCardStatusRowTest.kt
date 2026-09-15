package com.daengs.app.ui.my

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.pet.Pet
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
 * 카드 ③ 상태·액션 줄의 배치.
 *
 * **뱃지 유무로 카드 모양이 달라지면 안 된다.** 목록에서 뱃지 있는 카드만 한 줄 높거나 액션이
 * 옆으로 밀려 있으면, 같은 버튼을 누르려고 카드마다 손 위치를 다시 찾아야 한다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
// **글자 폭을 실제로 잰다.** 기본(LEGACY) 그래픽에서는 글자 폭이 몇 px 로 나와 겹침을 못 잡는다.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PetCardStatusRowTest {

    @get:Rule
    val compose = createComposeRule()

    private fun pet(
        id: String,
        name: String = "몽몽이",
        isOwner: Boolean = true,
        isGroupOwner: Boolean = isOwner,
        hasOtherCarers: Boolean = false,
    ) = Pet(
        id = id,
        name = name,
        breed = DogBreed.BEAGLE.id,
        sex = Pet.Sex.MALE,
        neutered = null,
        weightKg = 5.0f,
        birthDate = null,
        birthDateKind = null,
        isPrimary = false,
        isOwner = isOwner,
        isGroupOwner = isGroupOwner,
        hasOtherCarers = hasOtherCarers,
    )

    private val coCare get() = pet("co", hasOtherCarers = true)
    private val solo get() = pet("solo")
    private val linked get() = pet("linked", isOwner = true, isGroupOwner = false)

    private fun screen(pets: List<Pet>, fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MyScreen(
                    breed = DogBreed.BEAGLE,
                    nickname = "네옹집사",
                    pets = pets,
                    onToggleRoomPet = {},
                    canAddMore = true,
                    onAddPet = {},
                    onEditPet = {},
                    onPickPrimary = {},
                    onDeletePet = {},
                    onOpenMembers = {},
                    onRenamePet = { _, _ -> },
                    deleteBusy = false,
                    deleteError = null,
                    onDismissDelete = {},
                    signedIn = true,
                    onSignIn = {},
                    onSignOut = {},
                    onWithdraw = {},
                    withdrawBusy = false,
                    withdrawError = null,
                    onDismissWithdraw = {},
                )
            }
        }
    }

    private fun inCard(id: String, matcher: SemanticsMatcher): SemanticsNodeInteraction =
        compose.onNode(matcher and hasAnyAncestor(hasTestTag("pet-card-$id")), useUnmergedTree = true)

    private fun card(id: String) = compose.onNode(hasTestTag("pet-card-$id"), useUnmergedTree = true)
    private fun status(id: String) = compose.onNode(hasTestTag("pet-card-status-$id"), useUnmergedTree = true)
    private fun text(id: String, label: String) = inCard(id, hasText(label))

    private fun SemanticsNodeInteraction.lineCount(): Int {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
        return results.single().lineCount
    }

    private fun exists(id: String, label: String) =
        compose.onAllNodes(hasText(label) and hasAnyAncestor(hasTestTag("pet-card-$id")), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    private fun DpRect.overlaps(o: DpRect) = left < o.right && o.left < right && top < o.bottom && o.top < bottom

    // -- 뱃지 유무로 모양이 같다 ----------------------------------------------------------

    @Test
    fun `뱃지가 있든 없든 액션 줄 높이와 카드 높이와 오른쪽 액션 위치가 같다`() {
        screen(listOf(coCare, solo))
        compose.onAllNodesWithText("공동 돌봄", useUnmergedTree = true).assertCountEquals(1)

        assertSameShape("co", "solo")
    }

    private fun assertSameShape(a: String, b: String) {
        val sa = status(a).getUnclippedBoundsInRoot()
        val sb = status(b).getUnclippedBoundsInRoot()
        assertEquals("액션 줄 높이", sa.bottom - sa.top, sb.bottom - sb.top)
        val ca = card(a).getUnclippedBoundsInRoot()
        val cb = card(b).getUnclippedBoundsInRoot()
        assertEquals("카드 높이", ca.bottom - ca.top, cb.bottom - cb.top)
        for (label in listOf("대표로", "방에서 빼기", "삭제")) {
            val ra = text(a, label).getUnclippedBoundsInRoot()
            val rb = text(b, label).getUnclippedBoundsInRoot()
            assertEquals("$label 왼쪽", ra.left, rb.left)
            assertEquals("$label 오른쪽", ra.right, rb.right)
            assertEquals("$label 카드 안 세로 위치", ra.top - ca.top, rb.top - cb.top)
        }
    }

    // -- 이름 --------------------------------------------------------------------------

    @Test
    fun `긴 이름은 한 줄로 말줄임되고 카드 높이를 바꾸지 않는다`() {
        val long = pet("long", name = "이름이아주아주아주길어서한줄에는절대로다안들어가는강아지이름", hasOtherCarers = true)
        screen(listOf(long, coCare))

        val name = text("long", long.name)
        assertEquals(1, name.lineCount())
        val results = mutableListOf<TextLayoutResult>()
        name.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
        assertTrue("넘친 이름은 잘려야 한다(말줄임)", results.single().hasVisualOverflow)

        val cl = card("long").getUnclippedBoundsInRoot()
        val cc = card("co").getUnclippedBoundsInRoot()
        assertEquals("카드 높이", cc.bottom - cc.top, cl.bottom - cl.top)
        // 이름이 오른쪽 카드 밖으로 밀려나지 않는다
        assertTrue(name.getUnclippedBoundsInRoot().right <= cl.right)
    }

    // -- 좁은 화면 · 큰 글씨 ----------------------------------------------------------------

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun `좁은 화면 글씨 1_5배에서 글자가 접히지 않고 겹치지 않는다`() = assertNoOverlap(1.5f)

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun `좁은 화면 글씨 2배에서 글자가 접히지 않고 겹치지 않는다`() = assertNoOverlap(2.0f)

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun `좁은 화면 기본 글씨에서도 뱃지 유무로 모양이 같다`() {
        screen(listOf(coCare, solo, linked))
        assertSameShape("co", "solo")
        assertNoOverlapIn("co")
        assertNoOverlapIn("linked")
    }

    private fun assertNoOverlap(fontScale: Float) {
        screen(listOf(coCare, solo, linked), fontScale)
        assertSameShape("co", "solo")
        for (id in listOf("co", "solo", "linked")) assertNoOverlapIn(id)
        // 버튼은 빠지지 않는다 — 조건대로 다 떠 있다
        assertTrue(exists("linked", "이름 변경"))
        assertFalse("연결한 공동 보호자 카드에 삭제는 원래 없다", exists("linked", "삭제"))
        assertTrue(exists("co", "삭제"))
    }

    private fun assertNoOverlapIn(id: String) {
        val cardRect = card(id).getUnclippedBoundsInRoot()
        val rects = listOf("공동 돌봄", "대표로", "방에서 빼기", "삭제", "이름 변경")
            .filter { exists(id, it) }
            .map { label ->
                val node = text(id, label)
                assertEquals("$label 은 한 줄이어야 한다", 1, node.lineCount())
                val r = node.getUnclippedBoundsInRoot()
                assertTrue("$label 이 카드 오른쪽 밖으로 나갔다: $r / $cardRect", r.right <= cardRect.right)
                assertTrue("$label 이 카드 왼쪽 밖으로 나갔다", r.left >= cardRect.left)
                label to r
            }
        for (i in rects.indices) for (j in i + 1 until rects.size) {
            assertFalse(
                "${rects[i].first} 와 ${rects[j].first} 가 겹친다: ${rects[i].second} / ${rects[j].second}",
                rects[i].second.overlaps(rects[j].second),
            )
        }
    }
}
