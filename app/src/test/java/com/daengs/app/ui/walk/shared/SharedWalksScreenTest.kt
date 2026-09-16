package com.daengs.app.ui.walk.shared

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.daengs.app.care.CareActor
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.shared.SharedWalk
import com.daengs.app.walk.shared.SharedWalkDetail
import com.daengs.app.walk.shared.SharedWalkDetailStatus
import com.daengs.app.walk.shared.SharedWalkPoint
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

/** 목록은 산책 기록 화면에 합쳐졌다(`WalkRecordsRouteTest`). 여기는 읽기 전용 상세만 본다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedWalksScreenTest {
    @get:Rule val compose = createComposeRule()

    private val seoul = ZoneId.of("Asia/Seoul")
    private val theirs = SharedWalk(
        "w1", 1_756_681_200_000L, 1_756_683_600_000L, 2_400L, 1_234, 2_100, CareActor("u2", "키키"), false, listOf("p1"),
    )

    @Test fun `상세는 기본 정보와 경로만 있고 고치기와 지우기는 없다`() {
        val detail = SharedWalkDetail(
            theirs,
            listOf(SharedWalkPoint(1L, 37.5665, 126.978), SharedWalkPoint(2L, 37.567, 126.979)),
        )
        compose.setContent {
            DaengsTheme { SharedWalkDetailScreen(SharedWalkDetailStatus.Ready(detail), {}, {}, zone = seoul) }
        }

        compose.onNodeWithText("키키님이 다녀왔어요").assertIsDisplayed()
        compose.onNodeWithText("2025년 9월 1일 (월) 08:00").assertIsDisplayed()
        compose.onNodeWithText("거리 1.2km").assertIsDisplayed()
        compose.onNodeWithTag("shared-walk-route").assertIsDisplayed()
        listOf("삭제", "수정", "편집", "지우기", "일기").forEach { word ->
            compose.onAllNodesWithText(word, substring = true).assertCountEquals(0)
        }
    }

    @Test fun `경로가 한 점뿐이면 선 대신 안내를 둔다`() {
        val detail = SharedWalkDetail(theirs.copy(distanceM = null), listOf(SharedWalkPoint(1L, 37.5665, 126.978)))
        compose.setContent {
            DaengsTheme { SharedWalkDetailScreen(SharedWalkDetailStatus.Ready(detail), {}, {}, zone = seoul) }
        }

        compose.onNodeWithTag("shared-walk-no-route").assertIsDisplayed()
        compose.onNodeWithText("거리 측정 전").assertIsDisplayed()
    }

    @Test fun `볼 수 없는 산책은 다시 시도 없이 문장만 둔다`() {
        compose.setContent {
            DaengsTheme {
                SharedWalkDetailScreen(
                    SharedWalkDetailStatus.Unavailable("w9", "산책 기록을 찾을 수 없습니다.", retryable = false), {}, {}, zone = seoul,
                )
            }
        }

        compose.onNodeWithText("산책 기록을 찾을 수 없습니다.").assertIsDisplayed()
        compose.onAllNodesWithText("다시 시도").assertCountEquals(0)
    }

    @Test fun `못 읽은 상세는 다시 시도할 수 있다`() {
        var retried = 0
        compose.setContent {
            DaengsTheme {
                SharedWalkDetailScreen(
                    SharedWalkDetailStatus.Unavailable("w1", "서버에 닿지 못했어요.", retryable = true), {}, { retried++ }, zone = seoul,
                )
            }
        }

        compose.onNodeWithTag("shared-walk-detail-retry").performClick()
        assertEquals(1, retried)
    }
}
