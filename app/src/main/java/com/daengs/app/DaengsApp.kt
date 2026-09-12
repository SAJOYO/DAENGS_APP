package com.daengs.app

import android.app.Application
import com.daengs.app.dogcard.CardFiles
import com.daengs.app.dogcard.CardStore
import com.daengs.app.dogcard.RoomCardStore
import com.daengs.app.dogcard.store.CardDatabase
import com.daengs.app.location.FusedLocationSource
import com.daengs.app.auth.SessionProvider
import com.daengs.app.auth.TokenStore
import com.daengs.app.walk.ForegroundWalkTrackingController
import com.daengs.app.walk.WalkFixWriter
import com.daengs.app.walk.WalkHistory
import com.daengs.app.walk.sync.WalkSync
import com.daengs.app.walk.sync.WorkManagerWalkDeliveryScheduler
import com.daengs.app.walk.WalkRuntime
import com.daengs.app.walk.WalkTrackingStore
import com.daengs.app.walk.store.RoomWalkFixLog
import com.daengs.app.walk.store.WalkDatabase
import com.kakao.sdk.common.KakaoSdk
import com.naver.maps.map.NaverMapSdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.distinctUntilChangedBy
import com.daengs.app.territory.*

/**
 * 프로세스 공용 SDK와 산책 기록 런타임을 초기화한다.
 *
 * **왜 Application 인가.** 로그인이 끝나면 카카오가 우리 앱의
 * `AuthCodeHandlerActivity` 를 부르는데, 그 사이에 프로세스가 죽었다가 다시 뜰 수 있다.
 * 그때는 [MainActivity] 를 안 거치고 그 액티비티부터 시작하므로, `MainActivity.onCreate`
 * 에서 초기화하면 SDK 가 안 켜진 채로 콜백을 받는다.
 *
 * 앱 키는 [BuildConfig] 를 통해 들어온다. 값은 `local.properties` 에 있고 저장소에는
 * 없다 — 넣는 법은 `README.md` 참고. **키가 없어도 앱은 켜진다.** 그 상태로는
 * 로그인 버튼만 막히고 "둘러보기" 로 방까지 들어가진다.
 */
class DaengsApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val facilityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val facilityConversationLazy = lazy {
        com.daengs.app.place.FacilityConversationRepository(
            com.daengs.app.place.ConversationApi { BuildConfig.API_BASE_URL },
            com.daengs.app.place.PlaceRepository(com.daengs.app.place.PlaceApi(baseUrl = { BuildConfig.API_BASE_URL })),
            sessionProvider::freshSession, tokenStore::load,
            { sessionProvider.accountScope.value },
        )
    }
    val facilityConversation get() = facilityConversationLazy.value
    val facilityAssistant by lazy {
        com.daengs.app.assistant.FacilityAssistant(facilityConversation,
            captureBookmarks = { placeBookmarks().captureTurn() },
            onSearchApplied = { placeBookmarks().returnToSearch() },
        )
    }
    private var facilityBookmarkAccount: com.daengs.app.auth.AccountScope? = null
    private var facilityBookmarks: com.daengs.app.ui.places.PlaceBookmarkController? = null

    /** Map and assistant commands share the same account and per-place write sequence. */
    fun placeBookmarks(): com.daengs.app.ui.places.PlaceBookmarkController {
        val account = sessionProvider.accountScope.value
        if (facilityBookmarkAccount != account || facilityBookmarks == null) {
            facilityBookmarks?.close()
            facilityBookmarkAccount = account
            facilityBookmarks = com.daengs.app.ui.places.PlaceBookmarkController(facilityScope,
                com.daengs.app.place.bookmarks.PlaceBookmarkRepository(
                    com.daengs.app.place.bookmarks.PlaceBookmarkApi(), sessionProvider::freshSession,
                    { sessionProvider.accountScope.value },
                ), account)
        }
        return requireNotNull(facilityBookmarks)
    }

    lateinit var tokenStore: TokenStore
        private set

    lateinit var sessionProvider: SessionProvider
        private set

    lateinit var activityRepository: com.daengs.app.activity.ActivityRepository
        private set

    lateinit var ownedTerritoryRepository: com.daengs.app.territory.owned.OwnedTerritoryRepository
        private set

    lateinit var walkRuntime: WalkRuntime
        private set

    var territoryActions: TerritoryActionSync? = null
        private set

    /**
     * 뽑아 놓은 카드. **산책과 DB 파일을 나눠 뒀다** — 탈퇴 때 통째로 지우는 산책과
     * 달리 카드는 나중에 파는 재화라 지우는 규칙이 정반대다 (`CardDatabase` 주석).
     */
    lateinit var cardStore: CardStore
        private set

    lateinit var actionPins: com.daengs.app.walk.pin.ActionPinStore
    lateinit var actionPinScheduler: com.daengs.app.walk.pin.ActionPinScheduler
    lateinit var walkEntries: com.daengs.app.walk.store.WalkEntryStore
        private set
    lateinit var walkPhotos: com.daengs.app.walk.store.WalkPhotoStore
        private set
    lateinit var walkStoryboardSync: com.daengs.app.walk.sync.WalkDiarySync
        private set
    lateinit var walkEntryDao: com.daengs.app.walk.store.WalkDao
        private set
    lateinit var walkDiaryPublication: com.daengs.app.walk.diary.WalkDiaryPublication
        private set
    private lateinit var walkDatabase: WalkDatabase

    /** Keep the returned source for this login; request a new one after accountScope changes. */
    fun walkRecordsSource(): com.daengs.app.walk.records.WalkRecordsSource? =
        com.daengs.app.walk.records.accountWalkRecordsSource(walkDatabase, sessionProvider)

    fun routeBackupSource(scope: com.daengs.app.auth.AccountScope): com.daengs.app.walk.sync.WalkRouteBackupSource? {
        val owner = scope.ownerId?.takeIf { it.isNotBlank() } ?: return null
        if (sessionProvider.accountScope.value != scope) return null
        return com.daengs.app.walk.sync.WalkRouteBackupSource(walkDatabase, owner,
            isCurrentAccount = { sessionProvider.accountScope.value == scope },
            enqueue = walkRuntime.delivery::enqueue)
    }

    /** CameraX 완료 뒤 저장은 화면 회전/이탈보다 오래 살아야 한다. */
    fun saveWalkPhoto(capture: com.daengs.app.walk.WalkPhotoCapture, file: java.io.File) = applicationScope.async {
        try {
            walkRuntime.writer.flush()
            walkPhotos.save(capture, file)
        } finally { file.delete() }
    }

    /**
     * 카드 얼굴 그림. **동기화가 이걸 읽어 올린다** — `CardStore` 는 그림을 밖으로
     * 안 꺼내 주는데(그럴 이유가 없었다), 서버에 올리려면 바이트가 필요하다.
     */
    lateinit var cardFiles: CardFiles
        private set
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()) {
            KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
        }
        // 지도도 같은 철학이다 — 키가 없으면 초기화를 건너뛰고, 지도 화면만 빈 타일이 된다.
        if (BuildConfig.NAVER_MAP_NCP_KEY_ID.isNotBlank()) {
            NaverMapSdk.getInstance(this).client =
                NaverMapSdk.NcpKeyClient(BuildConfig.NAVER_MAP_NCP_KEY_ID)
        }

        tokenStore = TokenStore(this)
        sessionProvider = SessionProvider(tokenStore)
        facilityScope.launch {
            var previous = sessionProvider.accountScope.value
            sessionProvider.accountScope.collect { account ->
                if (account != previous) {
                    if (facilityConversationLazy.isInitialized()) facilityConversation.invalidate()
                    facilityBookmarks?.close()
                    facilityBookmarks = null
                    facilityBookmarkAccount = null
                    previous = account
                }
            }
        }
        activityRepository = com.daengs.app.activity.ActivityRepository(
            com.daengs.app.activity.ActivityApi(), sessionProvider::freshSession, tokenStore::load,
        )
        ownedTerritoryRepository = com.daengs.app.territory.owned.OwnedTerritoryRepository(
            com.daengs.app.territory.owned.OwnedTerritoryApi(), sessionProvider::freshSession, tokenStore::load,
        )

        cardFiles = CardFiles(this)
        cardStore = RoomCardStore(
            dao = CardDatabase.open(this).cardDao(),
            files = cardFiles,
        )

        val store = WalkTrackingStore()
        walkDatabase = WalkDatabase.open(this)
        val dao = walkDatabase.walkDao()
        walkPhotos = com.daengs.app.walk.store.WalkPhotoStore(dao, java.io.File(filesDir, "walk-photos"),
            onChanged = { sessionId -> applicationScope.launch {
                runCatching { walkRuntime.delivery.enqueue(sessionId) }
            } }) {
            tokenStore.load()?.appUserId.orEmpty()
        }
        val log = RoomWalkFixLog(dao, prunePhotos = walkPhotos::prune) { tokenStore.load()?.appUserId.orEmpty() }
        applicationScope.launch { walkPhotos.prune() }
        walkEntries = com.daengs.app.walk.store.WalkEntryStore(dao) { tokenStore.load()?.appUserId.orEmpty() }
        walkEntryDao = dao
        actionPins = com.daengs.app.walk.pin.ActionPinStore(dao, { tokenStore.load()?.appUserId.orEmpty() },
            activeSessionId = { store.state.value.activeSessionId })
        actionPinScheduler = com.daengs.app.walk.pin.ActionPinScheduler(this, applicationScope)
        walkStoryboardSync = com.daengs.app.walk.sync.WalkDiarySync(dao, { tokenStore.load()?.appUserId.orEmpty() })
        val writer = WalkFixWriter(
            log = log,
            // 저장 명령은 산책 서비스의 종료보다 오래 살아 flush까지 마쳐야 한다.
            scope = applicationScope,
        )
        val delivery = WorkManagerWalkDeliveryScheduler(this, log)
        val photoSync = com.daengs.app.walk.sync.WalkPhotoSync(dao, { tokenStore.load()?.appUserId.orEmpty() })
        walkRuntime = WalkRuntime(
            recordingScope = applicationScope,
            locationSource = FusedLocationSource(this),
            store = store,
            controller = ForegroundWalkTrackingController(this, store),
            writer = writer,
            log = log,
            history = WalkHistory(log),
            sync = WalkSync(log, entrySync = com.daengs.app.walk.sync.WalkEntrySync(dao,
                preferLegacy = com.daengs.app.walk.pin.ActionPinRollout.legacyCreation,
                owner = { tokenStore.load()?.appUserId.orEmpty() },
                v2 = com.daengs.app.walk.sync.WalkEntryV2Sync(dao, { tokenStore.load()?.appUserId.orEmpty() })),
                recording = com.daengs.app.walk.sync.WalkRecordingSync(),
                motion = com.daengs.app.walk.sync.WalkMotionSync(walkDatabase, { tokenStore.load()?.appUserId.orEmpty() },
                    precision = com.daengs.app.walk.sync.WalkMotionPrecisionSync(walkDatabase, { tokenStore.load()?.appUserId.orEmpty() }),
                    restorationGuard = log::restoringForOwner),
                requireRecordingSupport = !com.daengs.app.walk.pin.ActionPinRollout.legacyCreation,
                photoSync = photoSync::sync,
                storyboardSync = { token, sessionId, remoteId -> walkStoryboardSync.sync(token, sessionId, remoteId) }),
            delivery = delivery,
        )
        // close와 enqueue 사이에서 프로세스가 죽어도 다음 시작에서 다시 발견한다.
        // Queue recovery before the service can enqueue a new session/action.
        walkDiaryPublication = com.daengs.app.walk.diary.WalkDiaryPublication(dao,
            { tokenStore.load()?.appUserId.orEmpty() }, applicationScope, sync = { id ->
                sessionProvider.freshSession()?.let { auth ->
                    walkRuntime.sync.syncPendingSession(auth.accessToken, id)
                }
            })
        val recoveredPins = writer.ordered {
            actionPins.recover()
            com.daengs.app.walk.recoverDrainedRecordings(log) { id, cutoff -> actionPins.finishSession(id, cutoff) }
        }
        applicationScope.launch {
            recoveredPins.await()
            walkDiaryPublication.recover()
            delivery.enqueuePending()
        }
        if (BuildConfig.DEBUG && BuildConfig.TERRITORY_SERVER_ACTIONS) {
            val actions = TerritoryActionSync(TerritoryActionDatabase.open(this).actions(),
                TerritoryActionApi { BuildConfig.API_BASE_URL }, sessionProvider::freshSession,
                { tokenStore.load()?.appUserId }, { store.state.value }, applicationScope,
                { enqueueTerritoryActions(this) })
            territoryActions = actions
            actions.photos = ServerTerritoryPhotos(actions, TerritoryActionApi { BuildConfig.API_BASE_URL },
                HttpTerritoryPhotoUploader(), java.io.File(noBackupFilesDir, "territory-photos"),
                sessionProvider::freshSession, { tokenStore.load()?.appUserId }, { store.state.value },
                applicationScope, { enqueueTerritoryActions(this) })
            applicationScope.launch {
                actions.recover()
                store.state.distinctUntilChangedBy { Triple(it.ownerId, it.activeSessionId, it.trail.state) }
                    .collect { actions.syncTracking() }
            }
        }
    }
}
