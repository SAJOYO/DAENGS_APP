package com.daengs.app.ui.walk

import com.daengs.app.walk.*
import com.daengs.app.walk.detail.WalkDetailActions
import com.daengs.app.walk.detail.WalkDetailSource
import com.daengs.app.walk.diary.*
import com.daengs.app.walk.records.WalkRecord
import kotlinx.coroutines.flow.*

/** In-memory fixture adapter for the production session screen; never touches Room or a server. */
internal class WalkRecordsLabDetailData(private val record: WalkRecord) : WalkDetailSource, WalkDetailActions {
    override val entries = MutableStateFlow(record.entries)
    private val draft = MutableStateFlow(StoryboardDraft())
    private val revision = MutableStateFlow(0)
    override val changes = revision.map { Unit }
    override fun isCurrentAccount() = true
    override suspend fun load(): WalkSessionDetail {
        val summary = WalkRecordsLabFixture.loadRoute(record)
        return WalkSessionDetail(summary, summary.toSessionRoute(), emptyList())
    }
    override fun observeDiary(detail: WalkSessionDetail) = combine(entries, draft) { current, edits ->
        diaryWalk(detail.summary, current, emptyList(), edits,
            StoryboardAnalysisView(null, false, "가상 산책 · 변경은 이 화면을 나가면 초기화돼요."))
            .copy(title = record.title, sourceEntries = current)
            .withBoundaryScenes(edits)
    }
    override suspend fun open() = Unit
    override fun prepareDiary() { revision.value++ }
    override suspend fun generateDiary() { revision.value++ }
    override suspend fun saveEntry(entry: WalkEntry) {
        require(entry.sessionId == record.summary.sessionId)
        entry.validate()
        entries.value = entries.value.filterNot { it.id == entry.id } + entry
    }
    override suspend fun deleteEntry(id: String) { entries.value = entries.value.filterNot { it.id == id } }
    override suspend fun saveScene(scene: StoryboardScene, title: String, body: String) {
        draft.value = draft.value.edit(scene, title, body, acknowledge = true, bodyScope = SceneBodyScope.SCENE)
    }
    override suspend fun deleteScene(scene: StoryboardScene) { draft.value = draft.value.hide(scene) }
    override suspend fun deletePhoto(id: String) { error("가상 산책에는 저장된 사진이 없어요.") }
}
