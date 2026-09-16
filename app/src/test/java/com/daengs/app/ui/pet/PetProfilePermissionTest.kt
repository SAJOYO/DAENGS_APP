package com.daengs.app.ui.pet

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.pet.Pet
import com.daengs.app.pet.PetDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 강아지 프로필에서 **누가 무엇을 고칠 수 있나.**
 *
 * | 역할 | 이름·사진 | 공통 정보 |
 * | --- | --- | --- |
 * | 그룹 주보호자 | 고침 | 고침 |
 * | 연결한 공동 보호자 (`isOwner && !isGroupOwner`) | 고침 (내 목록에서만) | 못 고침 |
 * | 연결 없이 참여한 돌보미 (`!isOwner`) | 못 고침 | 못 고침 |
 *
 * **못 고치는 칸은 잠그는 것이 아니라 안 그린다.** 눌리지 않는 입력 칸을 띄워 두면
 * 화면이 그 이유를 설명해야 하고, 그 설명(서버가 이 사람의 PUT 을 409 로 막는다)은
 * 사용자가 알 바 아니다. 대신 값만 줄로 보여 준다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PetProfilePermissionTest {

    @get:Rule
    val compose = createComposeRule()

    private fun pet(isOwner: Boolean, isGroupOwner: Boolean) = Pet(
        id = "p1",
        name = "노을이",
        breed = DogBreed.BEAGLE.id,
        sex = Pet.Sex.FEMALE,
        neutered = true,
        weightKg = 5f,
        birthDate = LocalDate.of(2022, 3, 2),
        birthDateKind = Pet.BirthDateKind.FAMILY_DAY,
        isPrimary = false,
        isOwner = isOwner,
        isGroupOwner = isGroupOwner,
        healthConditions = "슬개골 탈구",
        medications = "관절 영양제",
    )

    private val groupOwner = pet(isOwner = true, isGroupOwner = true)
    private val linkedCarer = pet(isOwner = true, isGroupOwner = false)
    private val joinedCarer = pet(isOwner = false, isGroupOwner = false)

    /** `MainActivity` 가 넘기는 것과 **같은 셈**으로 깃발을 만든다. */
    private fun profile(target: Pet, onSubmit: (PetDraft) -> Unit = {}) {
        compose.setContent {
            PetFormScreen(
                initial = target,
                onSubmit = { draft, _ -> onSubmit(draft) },
                onCancel = {},
                busy = false,
                error = null,
                canEditIdentity = target.isOwner,
                canEditCommon = target.isGroupOwner,
            )
        }
    }

    private fun nameInputs() = compose.onAllNodesWithContentDescription("이름").fetchSemanticsNodes().size
    private fun weightInputs() = compose.onAllNodesWithContentDescription("몸무게 (kg)").fetchSemanticsNodes().size
    private fun photoActions() = compose.onAllNodesWithText("사진 올리기").fetchSemanticsNodes().size +
        compose.onAllNodesWithText("사진 바꾸기").fetchSemanticsNodes().size

    // -- ① 그룹 주보호자: 전부 고친다 -------------------------------------------------

    @Test
    fun `주보호자는 이름과 사진을 고칠 수 있다`() {
        profile(groupOwner)

        assertEquals(1, nameInputs())
        assertEquals(1, photoActions())
    }

    @Test
    fun `주보호자는 공통 정보도 고칠 수 있다`() {
        profile(groupOwner)

        assertEquals(1, weightInputs())
        compose.onNodeWithContentDescription("앓는 병").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("저장하기").performScrollTo().assertIsDisplayed()
    }

    // -- ② 연결한 공동 보호자: 이름·사진만 -------------------------------------------

    @Test
    fun `연결한 돌보미는 이름과 사진을 고칠 수 있다`() {
        profile(linkedCarer)

        assertEquals(1, nameInputs())
        assertEquals(1, photoActions())
    }

    @Test
    fun `연결한 돌보미에게 공통 정보 입력 칸이 없다`() {
        profile(linkedCarer)

        assertEquals(0, weightInputs())
        compose.onAllNodesWithContentDescription("앓는 병").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("먹는 약").assertCountEquals(0)
    }

    /** 못 고치더라도 **볼 수는 있어야 한다** — 함께 돌보려면 먹는 약을 알아야 한다. */
    @Test
    fun `연결한 돌보미도 공통 정보를 값으로는 본다`() {
        profile(linkedCarer)

        compose.onNodeWithTag("fact-견종").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("슬개골 탈구").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("관절 영양제").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `연결한 돌보미의 저장은 고친 이름을 싣는다`() {
        var sent: PetDraft? = null
        profile(linkedCarer, onSubmit = { sent = it })

        compose.onNodeWithContentDescription("이름").performTextReplacement("노을")
        compose.onNodeWithText("저장하기").performScrollTo().performClick()

        assertEquals("노을", sent?.name)
    }

    // -- ③ 연결 없이 참여한 돌보미: 읽기만 -------------------------------------------

    /** 프로필에 **들어갈 수는 있다.** 못 들어가면 그 아이의 정보를 볼 길이 아예 없다. */
    @Test
    fun `참여한 돌보미도 프로필을 본다`() {
        profile(joinedCarer)

        compose.onNodeWithText("강아지 정보").assertIsDisplayed()
        compose.onNodeWithTag("fact-이름").assertIsDisplayed()
        compose.onNodeWithText("노을이").assertIsDisplayed()
        compose.onNodeWithTag("fact-견종").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `참여한 돌보미에게 이름과 사진 편집이 안 보인다`() {
        profile(joinedCarer)

        assertEquals("이름 입력 칸이 없어야 한다", 0, nameInputs())
        assertEquals("사진 바꾸기 줄이 없어야 한다", 0, photoActions())
    }

    /** 저장 자리가 없으니 남의 이름·사진을 바꿀 길도 없다. */
    @Test
    fun `참여한 돌보미에게는 저장 자리가 없다`() {
        var sent: PetDraft? = null
        profile(joinedCarer, onSubmit = { sent = it })

        compose.onAllNodesWithText("저장하기").assertCountEquals(0)
        compose.onAllNodesWithText("등록하기").assertCountEquals(0)
        assertNull(sent)
    }

    /** 왜 못 고치는지를 서버 말로 설명하지 않는다. */
    @Test
    fun `읽기 전용 화면에 기술 용어가 없다`() {
        profile(joinedCarer)

        for (word in listOf("409", "PUT", "is_group_owner", "권한")) {
            compose.onAllNodesWithText(word, substring = true).assertCountEquals(0)
        }
    }

    // -- ④ 새로 등록할 때는 전부 열려 있다 -------------------------------------------

    @Test
    fun `새로 등록하는 화면은 예전 그대로다`() {
        compose.setContent {
            PetFormScreen(onSubmit = { _, _ -> }, onCancel = {}, busy = false, error = null)
        }

        assertEquals(1, nameInputs())
        assertEquals(1, weightInputs())
        compose.onNodeWithText("등록하기").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithTag("fact-견종").assertCountEquals(0)
    }
}
