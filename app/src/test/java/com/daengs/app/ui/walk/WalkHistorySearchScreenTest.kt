package com.daengs.app.ui.walk

import android.app.Application
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.WalkDiaryReader
import com.daengs.app.walk.store.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WalkHistorySearchScreenTest {
    private val compose = createComposeRule()
    @get:Rule val resources: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() = setup()
        // Compose must dispose its Room collectors before the database is closed.
        override fun after() = db.close()
    }).around(compose)
    private lateinit var db: WalkDatabase
    private lateinit var log: RoomWalkFixLog
    private lateinit var reader: WalkDiaryReader
    private var rendered: View? = null
    private fun setup() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, WalkDatabase::class.java).build()
        log = RoomWalkFixLog(db.walkDao())
        reader = WalkDiaryReader(db.walkDao(), WalkPhotoStore(db.walkDao(), File(context.cacheDir, "search-test")) { "" }) { "" }
    }
    private fun waitText(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    @Composable private fun Capture() { val view = LocalView.current; SideEffect { rendered = view.rootView } }
    private fun capture(name: String) {
        val file = File("build/outputs/$name.png"); file.parentFile!!.mkdirs()
        compose.runOnIdle {
            val view = requireNotNull(rendered)
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        }
    }

    @Test fun `empty real store exposes interactive filters and range picker`() {
        compose.setContent { DaengsTheme { Capture(); WalkHistoryBrowser(WalkHistory(log), reader, {}, {}) } }
        waitText("아직 산책 기록이 없어요.")
        compose.onNodeWithTag("history-search").assertIsDisplayed()
        compose.onNodeWithText("계절 ▾").performClick()
        compose.onNodeWithText("가을").performClick()
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithText("출발 날씨 ▾").performClick()
        compose.onNodeWithText("정보 없음").performClick()
        compose.onNodeWithText("닫기").performClick()
        waitText("아직 산책 기록이 없어요.")
        compose.onNodeWithText("가을 · 정보 없음").assertExists()
        capture("walk-history-empty")
        compose.onNodeWithTag("history-period").performClick()
        compose.onNodeWithText("산책 날짜 범위").assertExists()
        compose.onNodeWithText("적용").assertIsNotEnabled()
        compose.onNodeWithText("취소").performClick()
        compose.onNodeWithText("초기화").performClick()
        compose.onNodeWithText("계절 ▾").assertExists()
        compose.onNodeWithText("출발 날씨 ▾").assertExists()
    }

    @Test fun `search pages survive detail return and state restore while changing query resets page`() {
        runBlocking { for (day in 1..8) {
            seedSearchWalk(log, "s-$day", day)
            WalkEntryStore(db.walkDao()).save(WalkEntry(sessionId = "s-$day", type = WalkMomentType.NOTE,
                recordedAtMillis = 1, note = "호수 산책"))
        } }
        val history = WalkHistory(log)
        val restore = StateRestorationTester(compose)
        restore.setContent { DaengsTheme {
            Capture()
            val holder = rememberSaveableStateHolder()
            var opened by remember { mutableStateOf<String?>(null) }
            if (opened == null) holder.SaveableStateProvider("history") {
                WalkHistoryBrowser(history, reader, {}, { opened = it })
            } else TextButton(onClick = { opened = null }) { Text("상세에서 돌아가기") }
        } }
        waitText("1 페이지")
        compose.onNodeWithTag("history-search").performTextInput("호수")
        waitText("1 페이지")
        compose.onNodeWithText("다음 ›").performClick()
        waitText("2 페이지")
        capture("walk-history-results")
        // The displayed date is the title fallback of the first row on page 2 (September 3).
        val at = java.time.LocalDate.of(2026,9,3).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        compose.onNodeWithText("${formatWalkDay(at)} 산책").performClick()
        compose.onNodeWithText("상세에서 돌아가기").performClick()
        waitText("2 페이지")
        compose.onNodeWithTag("history-search").assertTextContains("호수")
        restore.emulateSavedInstanceStateRestore()
        waitText("2 페이지")
        compose.onNodeWithTag("history-search").assertTextContains("호수")
        compose.onNodeWithTag("history-search").performTextReplacement("없는 검색어")
        waitText("조건에 맞는 산책이 없어요.")
        capture("walk-history-no-results")
        compose.onNodeWithText("전체 기록 보기").performClick()
        waitText("1 페이지")
        compose.onNodeWithText("‹ 이전").assertIsNotEnabled()
    }

    @Test fun `loading and errors keep search controls and retry keeps conditions`() {
        var retry = false
        var error by mutableStateOf<String?>(null)
        compose.setContent { DaengsTheme {
            WalkHistorySearchLayout(WalkHistoryFilter(keyword = "호수"), {}, true, error, false, false,
                { retry = true }, {}, Modifier.fillMaxSize())
        } }
        compose.onNodeWithTag("history-search").assertIsDisplayed()
        compose.onNodeWithText("산책 기록을 찾고 있어요.").assertExists()
        compose.runOnIdle { error = "불러오기 실패" }
        compose.onNodeWithText("다시 시도").performClick()
        assertTrue(retry)
        compose.onNodeWithTag("history-search").assertTextContains("호수")
    }
}
