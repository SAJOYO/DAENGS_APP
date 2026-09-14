package com.daengs.app.ui.walk.review

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.daengs.app.BuildConfig
import com.daengs.app.map.provider.naver.LocalWalkMapDiagnostics
import com.daengs.app.map.provider.naver.WalkMapDiagnostics
import com.daengs.app.auth.AccountScope
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.walk.WalkDiaryMapForAccount
import com.daengs.app.ui.walk.WalkSessionOrigin
import com.daengs.app.walk.*
import com.daengs.app.walk.detail.StoredWalkDetailData
import com.daengs.app.walk.detail.WalkDetailActions
import com.daengs.app.walk.store.*
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

/** Opt-in fixture app only: real Room/readers/editors/SDK, with no auth or server delivery. */
class DiaryEditorReviewActivity : ComponentActivity() {
    internal val mapDiagnostics = WalkMapDiagnostics()
    private lateinit var db: WalkDatabase
    internal lateinit var dao: WalkDao
    private lateinit var history: WalkHistory
    private lateinit var photos: WalkPhotoStore
    private lateinit var entries: WalkEntryStore
    private var account by mutableStateOf(AccountScope("editor-review", 1))
    private var ready by mutableStateOf(false)
    private var opened by mutableStateOf(true)
    var failNextEntrySave = false
    var failNextDelivery = false
    var deliveryAttempts = 0; private set
    private var observedView: MapView? = null
    private var observedMap: NaverMap? = null
    private val persistent get() = intent.getBooleanExtra("persistent", false)
    val photoFile get() = File(if (persistent) filesDir else cacheDir, "editor-review/$PHOTO.jpg")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        check(BuildConfig.APPLICATION_ID.endsWith(".locationreview"))
        account = AccountScope(intent.getStringExtra("owner") ?: "editor-review", 1)
        val reset = persistent && savedInstanceState == null && intent.getBooleanExtra("reset", false)
        // This package is isolated; resets are explicit and never run in the verify/reopen phase.
        if (reset) { deleteDatabase(WalkDatabase.NAME); photoFile.delete() }
        db = if (persistent) WalkDatabase.open(this)
            else Room.inMemoryDatabaseBuilder(this, WalkDatabase::class.java).build()
        dao = db.walkDao()
        val log = RoomWalkFixLog(dao) { account.ownerId.orEmpty() }
        history = WalkHistory(log)
        photos = WalkPhotoStore(dao, photoFile.parentFile!!) { account.ownerId.orEmpty() }
        entries = WalkEntryStore(dao) { account.ownerId.orEmpty() }
        lifecycleScope.launch {
            if (!persistent || reset) withContext(Dispatchers.IO) { seed(log, intent.getBooleanExtra("route", true)) }
            ready = true
        }
        setContent { CompositionLocalProvider(LocalWalkMapDiagnostics provides mapDiagnostics) { DaengsTheme {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                ReviewLabel()
                if (ready && opened) key(account) {
                    val expected = account
                    val data = remember(expected) {
                        StoredWalkDetailData(SESSION, expected, { account }, history, dao, entries, photos,
                            {}, {
                                deliveryAttempts++
                                if (failNextDelivery) { failNextDelivery = false; error("검증용 전달 예약 실패") }
                            }, { null }, { _, _ -> }, { _, _, _ -> })
                    }
                    val actions = remember(data) { object : WalkDetailActions by data {
                        override suspend fun saveEntry(entry: WalkEntry) {
                            if (failNextEntrySave) {
                                failNextEntrySave = false
                                error("검증용 저장 실패")
                            }
                            data.saveEntry(entry)
                        }
                    } }
                    WalkDiaryMapForAccount(SESSION, data, actions, { opened = false }, Modifier.weight(1f),
                        emptyList(), WalkSessionOrigin.RECORDS, expected, {}, { null })
                } else if (ready) TextButton(onClick = { opened = true }) { Text("검증 기록 다시 열기") }
                else Text("검증 기록 준비 중")
            }
        } } }
    }

    fun replaceLogin() { account = account.copy(generation = account.generation + 1) }
    fun removeSession() { lifecycleScope.launch { dao.deleteSession(SESSION) } }
    suspend fun savedEntries(): List<WalkEntry> = dao.entries(SESSION).mapNotNull { it.entry() }

    fun map(): NaverMap? {
        val view = descendants(window.decorView).filterIsInstance<MapView>().firstOrNull()
        if (view !== observedView) {
            observedView = view; observedMap = null
            view?.getMapAsync { if (observedView === view) observedMap = it }
        }
        return observedMap
    }

    fun screenPoint(): Pair<Float, Float>? {
        val map = map() ?: return null
        val view = observedView ?: return null
        val pixel = map.projection.toScreenLocation(LatLng(37.56661, 126.978388 + 8 * 0.0002))
        val location = IntArray(2); view.getLocationOnScreen(location)
        return pixel.x + location[0] to pixel.y + location[1]
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }

    private suspend fun seed(log: RoomWalkFixLog, withRoute: Boolean) {
        log.openSession(RecordedSession(SESSION, dogIds = listOf("review-dog"), startedAtMillis = START))
        if (withRoute) (0..36).forEach { seq ->
            val step = if (seq <= 18) seq else 36 - seq
            log.append(SESSION, RecordedFix(seq, 0, START + seq * 20000,
                37.56661, 126.978388 + step * 0.0002, 3f, false))
        }
        val note = WalkEntry("original-note", SESSION, WalkMomentType.NOTE, START + 20000, note = "원래 남긴 검증 메모")
        dao.insertEntry(WalkEntryRow(note.id, SESSION, note.toJson().toString(), 1, "fixture", false))
        photoFile.parentFile!!.mkdirs()
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.LTGRAY)
        photoFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        dao.insertPhoto(WalkPhotoRow(PHOTO, SESSION, account.ownerId!!, START + 60000, START + 60000,
            37.56661, 126.978388, 3f))
        log.closeSession(SESSION, START + 740000)
        val board = requireNotNull(dao.prepareLocalDiary(SESSION, account.ownerId!!))
        dao.publishDiaryBase(SESSION, board.deadlineAtMillis)
    }

    override fun onDestroy() {
        lifecycleScope.cancel()
        super.onDestroy()
        db.close()
    }

    companion object {
        val PROCESS_INSTANCE = UUID.randomUUID().toString()
        const val SESSION = "editor-review-session"
        const val PHOTO = "c0000000-0000-4000-8000-000000000001"
        const val START = 1789200000000L
    }
}

@Preview(showBackground = true)
@Composable
private fun ReviewLabel() { Text("편집 검증 · 합성 기록", Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
