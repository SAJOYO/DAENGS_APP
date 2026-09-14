package com.daengs.app.ui.walk

import com.daengs.app.walk.support.diaryFixture
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.daengs.app.walk.diary.StoryboardScene
import com.daengs.app.walk.diary.sceneBody
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WalkStoryboardScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `내부 배경과 메모는 하나의 장면 본문으로 표시된다`() {
        val scene = com.daengs.app.walk.diary.GeoStoryboardBundle.parse(
            diaryFixture().toString()).scenes.first()
        compose.setContent { DiarySceneText(scene.sceneBody()) }
        compose.onNodeWithText("등록된 카페가 가까이에 있었다.   두부와 사진을 찍었다.\n다음 기록도 남김  ").assertExists()
        compose.onNodeWithText("직접 남긴 기록").assertDoesNotExist()
        compose.onNodeWithText(scene.diary!!.locationLabel).assertDoesNotExist()
    }

    @Test fun `대상 강아지 이름과 조회 부족 이유를 검토 화면에 표시한다`() {
        val before = com.daengs.app.walk.diary.GeoStoryboardBundle.parse(javaClass.getResource("/storyboard/v2-before.json")!!.readText())
        val short = com.daengs.app.walk.diary.GeoStoryboardBundle.parse(javaClass.getResource("/storyboard/v2-short.json")!!.readText())
        val scene = before.scenes.first { it.entryReference != null }
        compose.setContent {
            StoryboardContent(listOf(scene), false, null, false, false, true,
                {}, {}, {}, {}, {}, {}, selectionNotice = short.selection!!.description(), petNames = mapOf("pet-a" to "두부"))
        }
        compose.onNodeWithText("대상 강아지: 두부").assertExists()
        compose.onNodeWithText(short.selection!!.description()).assertExists()
    }

    @Test fun `분석 실패 후 이전 장면은 읽을 수 있지만 검토 완료는 막힌다`() {
        val payload = javaClass.getResource("/storyboard/pinless.json")!!.readText()
        val stamp = com.daengs.app.walk.sync.storyboardEntryStamp(emptyList())
        val source = com.daengs.app.walk.store.WalkSceneAnalysisRow("s", 2, stamp, "input", "failed", payload, "error", stamp)
        val view = com.daengs.app.walk.store.storedStoryboardAnalysisView(source, emptyList())
        compose.setContent {
            StoryboardContent(view.bundle!!.scenes.take(1), false, null, false, false, view.canReview,
                {}, {}, {}, {}, {}, {}, connectionNotice = view.notice)
        }
        compose.onNodeWithText(view.notice).assertExists()
        compose.onNodeWithText(view.bundle!!.scenes.first().title).assertExists()
        compose.onNodeWithText("이 구성 검토 완료").performScrollTo().assertIsNotEnabled()
    }

    @Test fun `geo 환경 근거를 기존 검토 화면에서 열고 숨길 수 있다`() {
        val bundle = com.daengs.app.walk.diary.GeoStoryboardBundle.parse(
            javaClass.getResource("/storyboard/pinless.json")!!.readText())
        val source = bundle.scenes.first { it.evidence.contains("commerce") }
        compose.setContent {
            var scene by remember { mutableStateOf(source) }
            StoryboardContent(listOf(scene), false, null, false, false, true,
                {}, {}, { scene = it.copy(hidden = !it.hidden) }, {}, {}, {})
        }
        compose.onNodeWithText("근거").performScrollTo().performClick()
        compose.onNodeWithText(source.evidence).assertExists()
        compose.onNodeWithText("숨기기").performScrollTo().performClick()
        compose.onNodeWithText("이번 구성에서 숨긴 장면").assertExists()
    }

    @Test fun `장면을 숨기고 복원하며 근거를 열 수 있다`() {
        compose.setContent {
            var scene by remember { mutableStateOf(StoryboardScene("entry:a", 0,
                "킁킁", "내 문구", "직접 남긴 관찰", "v1")) }
            StoryboardContent(listOf(scene), false, null, false, false, true,
                {}, {}, { scene = it.copy(hidden = !it.hidden) }, {}, {}, {})
        }
        compose.onNodeWithText("숨기기").performScrollTo().performClick()
        compose.onNodeWithText("이번 구성에서 숨긴 장면").assertExists()
        compose.onNodeWithText("복원").performScrollTo().performClick()
        compose.onNodeWithText("이번 구성에서 숨긴 장면").assertDoesNotExist()
        compose.onNodeWithText("근거").performScrollTo().performClick()
        compose.onNodeWithText("직접 남긴 관찰").assertExists()
    }

    @Test fun `기존 검토 완료는 유지하되 내부 출력 구분을 사용자 안내로 보여주지 않는다`() {
        var reviewed = false
        compose.setContent {
            StoryboardContent(emptyList(), false, null, false, false, true,
                {}, {}, {}, {}, {}, { reviewed = true })
        }
        compose.onNodeWithText("이 구성 검토 완료").performScrollTo().performClick()
        assertTrue(reviewed)
        compose.onNodeWithText("서버가 새 일기를 지원하면 배경 문장과 원본 기록을 나누어 보여줘요.").assertDoesNotExist()
    }
}
