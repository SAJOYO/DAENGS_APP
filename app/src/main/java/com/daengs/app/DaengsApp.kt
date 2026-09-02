package com.daengs.app

import android.app.Application
import com.daengs.app.dogcard.CardFiles
import com.daengs.app.dogcard.CardStore
import com.daengs.app.dogcard.RoomCardStore
import com.daengs.app.dogcard.store.CardDatabase
import com.daengs.app.location.FusedLocationSource
import com.daengs.app.walk.ForegroundWalkTrackingController
import com.daengs.app.walk.WalkFixWriter
import com.daengs.app.walk.WalkHistory
import com.daengs.app.walk.sync.WalkSync
import com.daengs.app.walk.WalkRuntime
import com.daengs.app.walk.WalkTrackingStore
import com.daengs.app.walk.store.RoomWalkFixLog
import com.daengs.app.walk.store.WalkDatabase
import com.kakao.sdk.common.KakaoSdk
import com.naver.maps.map.NaverMapSdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
    lateinit var walkRuntime: WalkRuntime
        private set

    /**
     * 뽑아 놓은 카드. **산책과 DB 파일을 나눠 뒀다** — 탈퇴 때 통째로 지우는 산책과
     * 달리 카드는 나중에 파는 재화라 지우는 규칙이 정반대다 (`CardDatabase` 주석).
     */
    lateinit var cardStore: CardStore
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

        cardStore = RoomCardStore(
            dao = CardDatabase.open(this).cardDao(),
            files = CardFiles(this),
        )

        val store = WalkTrackingStore()
        val log = RoomWalkFixLog(WalkDatabase.open(this).walkDao())
        val writer = WalkFixWriter(
            log = log,
            // 저장 명령은 산책 서비스의 종료보다 오래 살아 flush까지 마쳐야 한다.
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
        walkRuntime = WalkRuntime(
            locationSource = FusedLocationSource(this),
            store = store,
            controller = ForegroundWalkTrackingController(this, store),
            writer = writer,
            log = log,
            history = WalkHistory(log),
            sync = WalkSync(log),
        )
    }
}
