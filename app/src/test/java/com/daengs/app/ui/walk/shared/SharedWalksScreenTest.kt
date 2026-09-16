package com.daengs.app.ui.walk.shared

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.daengs.app.care.CareActor
import com.daengs.app.pet.Pet
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.walk.formatWalkDuration
import com.daengs.app.walk.shared.SharedWalk
import com.daengs.app.walk.shared.SharedWalkDetail
import com.daengs.app.walk.shared.SharedWalkDetailStatus
import com.daengs.app.walk.shared.SharedWalkPoint
import com.daengs.app.walk.shared.SharedWalksStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedWalksScreenTest {
    @get:Rule val compose = createComposeRule()

    private val seoul = ZoneId.of("Asia/Seoul")
    private val pets = listOf(Pet("p1", "두부", "maltese", null, null, null, null, null, isPrimary = true))
    private val theirs = SharedWalk(
        "w1", 1_756_681_200_000L, 1_756_683_600_000L, 2_400L, 1_234, 2_100, CareActor("u2", "키키"), false, listOf("p1"),
    )
    private val former = theirs.copy(id = "w2", actor = CareActor("u3", null), distanceM = null)

    private fun list(
        status: SharedWalksStatus = SharedWalksStatus.Ready,
        walks: List<SharedWalk> = listOf(theirs, former),
        hasMore: Boolean = false,
        onOpen: (String) -> Unit = {},
        onLoadMore: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) = compose.setContent {
        DaengsTheme {
            SharedWalksScreen(pets, "p1", status, walks, hasMore, {}, onLoadMore, onRetry, onOpen, {}, zone = seoul)
        }
    }

    @Test fun `목록 줄에 누가 언제 얼마나 걸었는지 보이고 고치기와 지우기는 없다`() {
        list()

        compose.onNodeWithText("키키님이 다녀왔어요").assertIsDisplayed()
        compose.onNodeWithText("이전 보호자가 다녀왔어요").assertIsDisplayed()
        compose.onAllNodesWithText("2025년 9월 1일 (월) 08:00").assertCountEquals(2)
        compose.onAllNodesWithText("시간 ${formatWalkDuration(2_400_000L)}").assertCountEquals(2)
        compose.onNodeWithText("거리 1.2km").assertIsDisplayed()
        compose.onNodeWithText("거리 측정 전").assertIsDisplayed()
        listOf("삭제", "수정", "편집", "지우기").forEach { word ->
            compose.onAllNodesWithText(word, substring = true).assertCountEquals(0)
        }
    }

    @Test fun `누르면 그 산책 id 로 상세를 연다`() {
        var opened: String? = null
        list(onOpen = { opened = it })

        compose.onNodeWithTag("shared-walk-w1").performClick()

        assertEquals("w1", opened)
    }

    @Test fun `다음 페이지가 있으면 더 보기로 이어 읽는다`() {
        var more = 0
        list(hasMore = true, onLoadMore = { more++ })

        compose.onNodeWithTag("shared-walks-more").performClick()

        assertEquals(1, more)
    }

    @Test fun `다녀온 산책이 없으면 빈 목록이라고 말한다`() {
        list(walks = emptyList())

        compose.onNodeWithTag("shared-walks-empty").assertIsDisplayed()
    }

    @Test fun `공동 조회가 없는 서버면 쓸 수 없다고만 말한다`() {
        list(status = SharedWalksStatus.Unsupported, walks = emptyList())

        compose.onNodeWithTag("shared-walks-unsupported").assertIsDisplayed()
    }

    @Test fun `못 읽었으면 빈 목록이 아니라 문장과 다시 시도다`() {
        var retried = 0
        list(status = SharedWalksStatus.Failed("서버에 닿지 못했어요."), walks = emptyList(), onRetry = { retried++ })

        compose.onNodeWithText("서버에 닿지 못했어요.").assertIsDisplayed()
        compose.onAllNodesWithText("다른 보호자가 다녀온 산책이 아직 없어요").assertCountEquals(0)
        compose.onNodeWithTag("shared-walks-retry").performClick()
        assertEquals(1, retried)
    }

    @Test fun `상세는 기본 정보와 경로만 있고 고치기와 지우기는 없다`() {
        val detail = SharedWalkDetail(
            theirs,
            listOf(SharedWalkPoint(1L, 37.5665, 126.978), SharedWalkPoint(2L, 37.567, 126.979)),
        )
        compose.setContent {
            DaengsTheme { SharedWalkDetailScreen(SharedWalkDetailStatus.Ready(detail), {}, {}, zone = seoul) }
        }

        compose.onNodeWithText("키키님이 다녀왔어요").assertIsDisplayed()
        compose.onNodeWithTag("shared-walk-route").assertIsDisplayed()
        listOf("삭제", "수정", "편집", "지우기", "일기").forEach { word ->
            compose.onAllNodesWithText(word, substring = true).assertCountEquals(0)
        }
    }

    @Test fun `경로가 한 점뿐이면 선 대신 안내를 둔다`() {
        val detail = SharedWalkDetail(theirs, listOf(SharedWalkPoint(1L, 37.5665, 126.978)))
        compose.setContent {
            DaengsTheme { SharedWalkDetailScreen(SharedWalkDetailStatus.Ready(detail), {}, {}, zone = seoul) }
        }

        compose.onNodeWithTag("shared-walk-no-route").assertIsDisplayed()
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
}
