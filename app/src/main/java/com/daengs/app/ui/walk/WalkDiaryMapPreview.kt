package com.daengs.app.ui.walk

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.daengs.app.auth.AccountScope
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.*
import com.daengs.app.walk.detail.*
import com.daengs.app.walk.diary.*
import kotlinx.coroutines.flow.flowOf

/** The production map/drawer shell with a local, editable scene and no services. */
@Preview(showBackground = true, widthDp = 390, heightDp = 820)
@Composable
private fun WalkDiaryMapPreview() {
    val data = remember { object : WalkDetailSource, WalkDetailActions {
        val detail = readCompletedRoute(RecordedSession("preview", startedAtMillis = 0, endedAtMillis = 60_000), emptyList())
        override val changes = flowOf(Unit)
        override val entries = flowOf(emptyList<WalkEntry>())
        override fun isCurrentAccount() = true
        override suspend fun load() = detail
        override fun observeDiary(detail: WalkSessionDetail) = flowOf(DiaryWalk(detail.summary,
            listOf(DiaryScene("preview/scene", "preview", 30_000, "함께 쉬어 간 순간", "잠시 쉬었다가 다시 걸었어요.", null, "")), ""))
        override suspend fun open() = Unit
        override fun prepareDiary() = Unit
        override suspend fun generateDiary() = Unit
        override suspend fun saveEntry(entry: WalkEntry) = Unit
        override suspend fun deleteEntry(id: String) = Unit
        override suspend fun saveScene(scene: StoryboardScene, title: String, body: String) = Unit
        override suspend fun deletePhoto(id: String) = Unit
    } }
    DaengsTheme { WalkDiaryMapForAccount("preview", data, data, {}, Modifier, emptyList(),
        WalkSessionOrigin.RECORDS, AccountScope(null, 0), {}, { null }) }
}
