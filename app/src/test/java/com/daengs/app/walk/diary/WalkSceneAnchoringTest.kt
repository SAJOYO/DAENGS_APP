package com.daengs.app.walk.diary

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.*
import com.daengs.app.walk.store.*
import com.daengs.app.walk.sync.storyboardEntryStamp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

internal fun sceneAnchorFixture(): Pair<GeoStoryboardBundle, List<RecordedFix>> {
    val data = JSONObject(WalkSceneAnchoringTest::class.java.getResource("/storyboard/v4-observations.json")!!.readText())
    val array = data.getJSONArray("observations")
    return GeoStoryboardBundle.parse(data.getJSONObject("bundle").toString()) to (0 until array.length()).map { i ->
        val p = array.getJSONObject(i)
        RecordedFix(p.getInt("client_seq"), p.getInt("chain_index"), Instant.parse(p.getString("at")).toEpochMilli(),
            p.getDouble("lat"), p.getDouble("lng"), p.getDouble("accuracy_m").toFloat(), p.getBoolean("is_mock"))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WalkSceneAnchoringTest {
    private val fixture get() = sceneAnchorFixture()
    private fun summary() = fixture.let { (b, fixes) -> WalkSummary(b.sessionId, emptyList(),
        fixes.first().atMillis, fixes.last().atMillis, null, 9999.0, 0, emptyList(), null) }

    @Test fun `shared server fixture pins observations and leaves gap unlocated regardless of route distance`() {
        val (bundle, fixes) = fixture
        val walk = summary()
        val diary = diaryWalk(walk, emptyList(), emptyList(), StoryboardDraft(),
            StoryboardAnalysisView(bundle, true, ""), fixes)
        assertEquals(7, diary.scenes.size)
        assertEquals(6, diary.scenes.count { it.point != null })
        assertEquals(diary.scenes.first().point, diary.scenes.last().point)
        assertNotEquals(diary.scenes.first().atMillis, diary.scenes.last().atMillis)
        assertNull(diary.scenes.single { it.title == "위치 기록이 비어 있는 구간" }.point)
        assertTrue(diary.scenes.all { it.entryId == null })
        assertTrue(diaryWalk(walk, emptyList(), emptyList(), StoryboardDraft(),
            StoryboardAnalysisView(bundle, true, "")).scenes.all { it.point == null })
    }

    @Test fun `same-place wrong sequence time chain and changed coordinates never borrow another fix`() {
        val (bundle, fixes) = fixture
        val anchor = bundle.scenes.first().observation!!
        val index = StoryboardObservationIndex(summary(), fixes)
        assertNotNull(index.resolve(anchor))
        for (bad in listOf(anchor.copy(clientSeq = 999), anchor.copy(chainIndex = 1),
            anchor.copy(atMillis = anchor.atMillis + 1), anchor.copy(point = GeoPoint(37.6, 127.0)),
            anchor.copy(clientSeq = fixes.last().clientSeq))) assertNull(index.resolve(bad))
        assertNull(StoryboardObservationIndex(summary(), fixes + fixes.first()).resolve(anchor))
        assertNull(StoryboardObservationIndex(summary(), fixes.drop(1)).resolve(anchor))
        assertNull(StoryboardObservationIndex(summary().copy(endedAtMillis = null), fixes).resolve(anchor))
    }

    @Test fun `invalid wire anchor is rejected and old formats remain unlocated`() {
        val (bundle, _) = fixture
        val data = JSONObject(bundle.rawJson)
        val scenes = data.getJSONArray("scenes")
        scenes.getJSONObject(1).getJSONObject("observation").put("chain_index", -1)
        assertTrue(runCatching { GeoStoryboardBundle.parse(data.toString()) }.isFailure)
        val legacy = JSONObject(bundle.rawJson).put("format", GeoStoryboardBundle.FORMAT_V3)
        val oldScenes = legacy.getJSONArray("scenes")
        (0 until oldScenes.length()).forEach { oldScenes.getJSONObject(it).remove("observation") }
        assertTrue(GeoStoryboardBundle.parse(legacy.toString()).scenes.all { it.observation == null })
    }

    @Test fun `Room history supplies original upload identities to the reactive diary reader`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(context, WalkDatabase::class.java).build()
        try {
            val dao = db.walkDao()
            val log = RoomWalkFixLog(dao)
            val (bundle, fixes) = fixture
            log.openSession(RecordedSession(id = bundle.sessionId, startedAtMillis = fixes.first().atMillis))
            fixes.forEach { log.append(bundle.sessionId, it) }
            log.closeSession(bundle.sessionId, fixes.last().atMillis)
            val detail = WalkHistory(log).sessionDetail(bundle.sessionId)!!
            assertEquals(fixes, detail.observations)
            assertTrue(detail.route.points.isNotEmpty())
            val stamp = storyboardEntryStamp(emptyList())
            assertTrue(dao.acceptSceneAnalysis(WalkSceneAnalysisRow(bundle.sessionId, 1, stamp, "fixture",
                "ready", bundle.rawJson, null), ""))
            val reader = WalkDiaryReader(dao, WalkPhotoStore(dao, File(context.cacheDir, "scene-anchor")) { "" }) { "" }
            val read = reader.observe(listOf(detail.summary), mapOf(bundle.sessionId to detail.observations)).first().single()
            assertEquals(6, read.scenes.count { it.point != null })
            assertEquals(detail.route.start!!.point, read.scenes.first().point)
            assertEquals(detail.route.end!!.point, read.scenes.last().point)
            dao.deleteSession(bundle.sessionId)
            assertTrue(reader.observe(listOf(detail.summary), mapOf(bundle.sessionId to detail.observations)).first().isEmpty())
        } finally { db.close() }
    }
}
