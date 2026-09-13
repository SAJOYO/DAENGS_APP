package com.daengs.app.walk.sync

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.WalkSummary
import com.daengs.app.walk.diary.GeoStoryboardBundle
import com.daengs.app.walk.diary.WalkDiaryReader
import com.daengs.app.walk.diary.canonicalJson
import com.daengs.app.walk.store.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/** DEV HTTP output after normalized collection and a fake writer; no LLM quality assertion. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WalkDiarySpacePersistenceTest {
    @Test fun normalizedDiarySurvivesReopen() = assertPublicationSurvives("diary-space-board-v1.json")

    @Test fun independentCardWritingSurvivesReopen() = assertPublicationSurvives("diary-card-orchestration-v1.json")

    private fun assertPublicationSurvives(resource: String) = runBlocking {
        val response = JSONObject(javaClass.getResource("/storyboard/$resource")!!.readText())
        val board = GeoStoryboardBundle.parse(response.toString())
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "space-${UUID.randomUUID().toString().take(8)}.db"
        fun open() = Room.databaseBuilder(context, WalkDatabase::class.java, name).build()
        val owner = "normalization-owner"
        val summary = WalkSummary(board.sessionId, emptyList(), board.scenes.first().atMillis,
            board.scenes.last().atMillis, null, 100.0, 10000, emptyList(), null)
        var db = open()
        try {
            var dao = db.walkDao()
            dao.insertSession(WalkSessionRow(board.sessionId, summary.startedAtMillis,
                ownerId = owner, endedAtMillis = summary.endedAtMillis, serverWalkId = "remote"))
            val revisions = response.getJSONObject("entry_revisions")
            board.scenes.filter { it.entryReference != null }.forEach { scene ->
                val id = scene.entryReference!!.entryId
                val note = scene.diary!!.recordKind == "note"
                val entry = WalkEntry(id, board.sessionId, if (note) WalkMomentType.NOTE else WalkMomentType.SNIFFING, scene.atMillis,
                    note = scene.diary.recordText.takeIf { note }, petId = scene.entryReference.petId)
                dao.insertEntry(WalkEntryRow(id, board.sessionId, entry.toJson().toString(),
                    revisions.getInt(id), id, false))
            }
            var posts = 0
            WalkDiarySync(dao, { owner }, request = { _, path, method, request ->
                when {
                    path.endsWith("capabilities") -> JSONObject("""{"diary_formats":["walk-diary-board-v1"]}""")
                    method == "POST" -> {
                        posts++
                        assertEquals(canonicalJson(revisions), canonicalJson(request!!.getJSONObject("expected_entries")))
                        JSONObject(response.toString())
                    }
                    else -> JSONObject(response.toString()).put("status", "pending").put("bundle", JSONObject.NULL)
                }
            }).sync("test-token", board.sessionId, "remote")
            assertEquals(1, posts)
            val saved = requireNotNull(dao.sceneAnalysis(board.sessionId)).bundle
            assertEquals(board.scenes.map { it.body }, GeoStoryboardBundle.parse(saved!!).scenes.map { it.body })
            db.close()
            db = open()
            dao = db.walkDao()
            assertEquals(saved, dao.sceneAnalysis(board.sessionId)!!.bundle)
            val reader = WalkDiaryReader(dao, WalkPhotoStore(dao, File(context.cacheDir, "space-photos")) { owner }) { owner }
            val reopened = withTimeout(10000) { reader.observe(listOf(summary)).first().single() }
            assertEquals(board.scenes.map { it.body }, reopened.scenes.map { it.body })
            assertEquals(board.scenes.map { it.title }, reopened.scenes.map { it.title })
            assertEquals(board.scenes.map { it.diary?.address }, reopened.scenes.map { it.content?.address })
            assertEquals(board.scenes.map { it.diary?.publishedWriting }, reopened.scenes.map { it.content?.publishedWriting })
            assertTrue(board.scenes.any { it.entryReference == null })
            assertEquals(board.scenes.size, reopened.scenes.size)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
