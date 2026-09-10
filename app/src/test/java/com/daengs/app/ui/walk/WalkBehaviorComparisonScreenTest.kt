package com.daengs.app.ui.walk

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.daengs.app.pet.Pet
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.diary.*
import com.daengs.app.walk.support.behaviorComparisonFixture
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h900dp", application = Application::class)
class WalkBehaviorComparisonScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    @Config(qualifiers = "w320dp-h900dp")
    fun `an oversized query can recover with a shorter period on a narrow screen`() {
        val view = WalkBehaviorComparison.parse(behaviorComparisonFixture())
        val pet = Pet(id = view.query.spatial.petId, name = "댕이", breed = "믹스",
            sex = null, neutered = null, weightKg = null, birthDate = null,
            birthDateKind = null, isPrimary = true)
        val today = LocalDate.of(2026, 9, 2)
        val requested = mutableListOf<WalkBehaviorComparisonQuery>()
        val tooLarge = "결과 Cell은 최대 5000개입니다. 필터를 좁혀 주세요."
        compose.setContent { DaengsTheme {
            WalkBehaviorComparisonBrowser(listOf(pet), {}, {}, today = today,
                load = { query ->
                    requested += query
                    if (requireNotNull(query.spatial.since).isBefore(LocalDate.of(2026, 8, 27))) {
                        throw SpatialDiaryHttpException(413, "spatial_diary_result_cell_limit", tooLarge)
                    }
                    LoadedBehaviorComparison(view.copy(query = query))
                },
                map = { _, _, modifier -> Box(modifier) { Text("비교 지도") } })
        } }
        compose.onNodeWithText(tooLarge).assertExists()
        compose.onNodeWithText("최근 30일").performScrollTo().assertIsSelected()
        compose.onNodeWithText("최근 90일").performScrollTo().performClick()
        compose.onNodeWithText(tooLarge).assertExists()
        // The refresh action remains outside the horizontally scrolling period choices.
        compose.onNodeWithText("새로고침").assertIsDisplayed()

        compose.onNodeWithText("최근 7일").performScrollTo().performClick()
        compose.onNodeWithText("최근 7일").assertIsSelected()
        compose.onNodeWithText(tooLarge).assertDoesNotExist()
        compose.onNodeWithText("기록 2건 · 서로 다른 1일").assertExists()
        compose.onNodeWithText("최근 1일").performScrollTo().performClick()
        compose.onNodeWithText("최근 1일").assertIsSelected()
        compose.onNodeWithText("기록 2건 · 서로 다른 1일").assertExists()
        compose.runOnIdle {
            assertEquals(listOf("2026-08-04", "2026-06-05", "2026-08-27", "2026-09-02"),
                requested.map { it.spatial.since.toString() })
            assertTrue(requested.all { it.spatial.until == today &&
                it.spatial.petId == pet.id && it.behavior == view.query.behavior })
        }
    }

    @Test fun `server fixture reaches both map selections and unlocated evidence without inventing a local walk`() {
        val view = WalkBehaviorComparison.parse(behaviorComparisonFixture())
        var matching = false
        compose.setContent { DaengsTheme {
            WalkBehaviorComparisonContent(view, false, null, {}, {}, modifier = Modifier.fillMaxSize(),
                map = { _, subset, modifier -> matching = subset; Box(modifier) { Text(if (subset) "지도 B" else "지도 A") } })
        } }
        compose.onNodeWithText("비교 가능한 산책 ${view.baseline.walkIds.size}회 중 ${view.matching.walkIds.size}회에서 ${view.query.behavior.label} 기록을 남겼어요.").assertExists()
        compose.onNodeWithTag("behavior-matching").performScrollTo().performClick()
        compose.onNodeWithText("지도 B").assertExists()
        compose.runOnIdle { assertTrue(matching) }
        compose.onNodeWithTag("behavior-baseline").performScrollTo().performClick()
        compose.runOnIdle { assertFalse(matching) }
        val label = view.evidence.first { it.point == null }.locationLabel
        compose.onNodeWithText(label).performScrollTo().assertExists()
        compose.onAllNodesWithText("이 산책 보기").assertCountEquals(0)
    }

    @Test fun `empty and identical groups are explained while errors have a real retry`() {
        val view = WalkBehaviorComparison.parse(behaviorComparisonFixture())
        val empty = view.copy(matching = BehaviorWalkGroup(emptyList(), view.matching.field.copy(denominator = 0.0, cells = emptyList())),
            evidence = emptyList(), summary = view.summary.copy(entryCount = 0, recordedDayCount = 0, unlocatedEntryCount = 0))
        val current = mutableStateOf<WalkBehaviorComparison?>(empty)
        val error = mutableStateOf<String?>(null)
        var retries = 0
        compose.setContent { DaengsTheme {
            WalkBehaviorComparisonContent(current.value, false, error.value, { retries++ }, {}, modifier = Modifier.fillMaxSize(),
                map = { _, _, modifier -> Box(modifier) { Text("지도") } })
        } }
        compose.onNodeWithText("이 기간에는 ${view.query.behavior.label} 기록이 있는 산책이 없어요.").performScrollTo().assertExists()
        val identical = view.copy(baseline = view.matching,
            summary = view.summary.copy(selectedWalkCount = view.matching.walkIds.size, excludedEmptyWalkCount = 0))
        compose.runOnIdle { current.value = identical }
        compose.onNodeWithText("모든 비교 산책에 이 행동 기록이 있어 두 분포가 같아요.").performScrollTo().assertExists()
        compose.runOnIdle { current.value = null; error.value = "행동 기록 비교를 준비 중이에요." }
        compose.onNodeWithText("다시 시도").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun `a locally available evidence walk opens its client session`() {
        val view = WalkBehaviorComparison.parse(behaviorComparisonFixture())
        val evidence = view.evidence.first().copy(clientSessionId = "local-walk")
        val withLocal = view.copy(evidence = view.evidence.mapIndexed { index, row -> if (index == 0) evidence else row })
        var opened: String? = null
        compose.setContent { DaengsTheme {
            WalkBehaviorComparisonContent(withLocal, false, null, {}, { opened = it }, setOf("local-walk"), Modifier.fillMaxSize(),
                map = { _, _, modifier -> Box(modifier) })
        } }
        compose.onNodeWithText("이 산책 보기").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("local-walk", opened) }
    }
}
