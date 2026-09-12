package com.daengs.app.walk.detail

import com.daengs.app.auth.AccountScope
import com.daengs.app.auth.Session
import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.WalkHistory
import com.daengs.app.walk.WalkSessionDetail
import com.daengs.app.walk.diary.StoryboardScene
import com.daengs.app.walk.diary.WalkDiaryReader
import com.daengs.app.walk.store.WalkDao
import com.daengs.app.walk.store.WalkEntryStore
import com.daengs.app.walk.store.WalkPhotoStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.map

/** Keeps the existing Room, publication and delivery boundaries; creates no jobs of its own. */
internal class StoredWalkDetailData(
    private val sessionId: String,
    private val account: AccountScope,
    private val currentAccount: () -> AccountScope,
    private val history: WalkHistory,
    private val dao: WalkDao,
    private val entryStore: WalkEntryStore,
    private val photos: WalkPhotoStore,
    private val startPublication: (String) -> Unit,
    private val enqueue: suspend (String) -> Unit,
    private val freshSession: suspend () -> Session?,
    private val syncSession: suspend (token: String, sessionId: String) -> Unit,
    private val refreshStoryboard: suspend (token: String, sessionId: String, remoteId: String) -> Unit,
) : WalkDetailSource, WalkDetailActions {
    private val reader = WalkDiaryReader(dao, photos) { currentAccount().ownerId.orEmpty() }
    override val changes get() = history.changes
    override val entries = entryStore.observe(sessionId).map { if (isCurrentAccount()) it else emptyList() }
    override fun isCurrentAccount() = currentAccount() == account

    override suspend fun load(): WalkSessionDetail? {
        checkActive()
        return history.sessionDetail(sessionId).also { checkActive() }
    }

    override fun observeDiary(detail: WalkSessionDetail) =
        reader.observe(listOf(detail.summary.also { require(it.sessionId == sessionId) }),
            mapOf(sessionId to detail.observations)).map { if (isCurrentAccount()) it.singleOrNull() else null }

    override suspend fun open() {
        checkActive()
        prepareDiary()
        enqueue(sessionId)
    }

    override fun prepareDiary() {
        if (isCurrentAccount()) startPublication(sessionId)
    }

    override suspend fun generateDiary() {
        checkActive()
        val auth = freshSession()
        checkActive()
        check(auth != null) { "로그인 후 일기를 만들 수 있어요." }
        check(auth.appUserId == account.ownerId) { "산책 계정이 변경되었어요." }
        syncSession(auth.accessToken, sessionId)
        checkActive()
        val row = dao.session(sessionId)
        checkActive()
        val remoteId = row?.serverWalkId ?: error("산책 동기화를 먼저 완료해 주세요.")
        check(row.ownerId == account.ownerId) { "현재 계정의 산책 기록이 아닙니다." }
        refreshStoryboard(auth.accessToken, sessionId, remoteId)
        checkActive()
    }

    override suspend fun saveEntry(entry: WalkEntry) {
        checkActive()
        require(entry.sessionId == sessionId) { "현재 산책의 기록이 아닙니다." }
        entryStore.save(entry)
        checkActive()
        enqueue(sessionId)
    }

    override suspend fun deleteEntry(id: String) {
        checkActive()
        val row = dao.entry(id) ?: return
        checkActive()
        require(row.sessionId == sessionId) { "현재 산책의 기록이 아닙니다." }
        // Pin-aware tombstone and delivery must continue through the shared deletion boundary.
        entryStore.deleteAndEnqueue(id) { deletedSession ->
            checkActive()
            enqueue(deletedSession)
        }
    }

    override suspend fun saveScene(scene: StoryboardScene, title: String, body: String) {
        checkActive()
        dao.saveDiarySceneEdit(sessionId, account.ownerId.orEmpty(), scene, title, body)
        checkActive()
    }

    override suspend fun deletePhoto(id: String) {
        checkActive()
        val row = dao.photo(id) ?: return
        checkActive()
        require(row.sessionId == sessionId) { "현재 산책의 사진이 아닙니다." }
        photos.delete(id)
    }

    private fun checkAccount() {
        if (!isCurrentAccount()) throw CancellationException("산책 계정이 변경되었어요.")
    }

    private suspend fun checkActive() {
        currentCoroutineContext().ensureActive()
        checkAccount()
    }
}
