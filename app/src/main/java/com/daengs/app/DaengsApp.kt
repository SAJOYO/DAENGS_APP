package com.daengs.app

import android.app.Application
import com.kakao.sdk.common.KakaoSdk

/**
 * 카카오 SDK 를 켠다.
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
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()) {
            KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
        }
    }
}
