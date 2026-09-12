package com.daengs.app.ui.walk.review

import android.app.Application
import com.daengs.app.BuildConfig
import com.daengs.app.auth.SessionProvider
import com.daengs.app.auth.TokenStore
import com.kakao.sdk.common.KakaoSdk
import com.naver.maps.map.NaverMapSdk
import java.net.URI
import java.io.File

/** Dedicated application: no Room, WalkRuntime, delivery, GPS or diary generation initialization. */
class WalkRecordReviewApplication : Application() {
    lateinit var sessions: SessionProvider
        private set

    override fun onCreate() {
        super.onCreate()
        require(BuildConfig.DEBUG && BuildConfig.APPLICATION_ID.endsWith(".walkreview"))
        val base = URI(BuildConfig.API_BASE_URL)
        require(base.scheme == "https" && base.host != null && base.rawUserInfo == null)
        val origin = File(noBackupFilesDir, "review-origin.txt")
        if (origin.exists()) require(origin.readText() == BuildConfig.API_BASE_URL) {
            "검토 앱의 서버가 바뀌었어요. 저장된 인증을 다른 서버에 사용하지 않습니다."
        } else origin.writeText(BuildConfig.API_BASE_URL)
        KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
        if (BuildConfig.NAVER_MAP_NCP_KEY_ID.isNotBlank())
            NaverMapSdk.getInstance(this).client = NaverMapSdk.NcpKeyClient(BuildConfig.NAVER_MAP_NCP_KEY_ID)
        sessions = SessionProvider(TokenStore(this))
    }
}
