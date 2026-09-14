# 네이버 지도 첫 그리기 회귀 검사

`NaverMapSurface`를 실제 SDK로 실행하는 선택적 검증 앱이다. 새 지도 뷰를 코드로 교체하거나
제거 후 다시 추가할 때 사용자 입력 없이 Surface와 지도 준비가 완료되는지 검사한다.
버튼 ripple·타이머 invalidate·강제 layout은 이 실패를 가릴 수 있어 사용하지 않는다.
화면의 `Box` 안에서 지도만 제거/재생성하는 구조도 유지한다. 단순화해 지도를 Column에
직접 넣은 대조 화면은 수정 전에도 통과했지만, Box 안 재진입은 Surface 대기를 재현했다.

정식 앱과 다른 `com.daengs.app.locationreview` 패키지와 SDK만 초기화하는 Application을 사용한다.
서울시청 합성 좌표를 넣으며 인증·산책 런타임·DB·업로드를 시작하지 않는다.
일반 Debug/Release 빌드에는 이 디렉터리의 Activity와 테스트가 포함되지 않는다.

## 빌드와 실행

Android SDK/JDK가 준비된 저장소 루트에서 실행한다. 기존 `local.properties`의
`daengs.naverMapClientId` 또는 `DAENGS_NAVER_NCP_KEY_ID` 환경변수에 검증용 Naver 키가 필요하다.
키·로컬 설정·APK는 커밋하지 않는다. 스타일 ID가 없으면 기본 네이버 지도를 사용한다.

```powershell
.\gradlew.bat --no-daemon -I tools/naver-map-review.init.gradle :app:assembleDebug :app:assembleDebugAndroidTest -PslimAbi=arm64-v8a --max-workers=2 --console=plain

# $adbPath와 $deviceSerial은 사용 중인 adb 경로와 검증 기기의 serial.
& $adbPath -s $deviceSerial install -r app/build/outputs/apk/debug/app-debug.apk
& $adbPath -s $deviceSerial install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& $adbPath -s $deviceSerial shell am instrument -w -r -e class com.daengs.app.map.review.MapFirstFrameTest com.daengs.app.locationreview.test/androidx.test.runner.AndroidJUnitRunner
```

두 설치의 `Success`와 runner의 `OK (2 tests)`를 직접 확인한다. `am instrument`의 셸 종료 코드만으로
성공을 판정하지 않는다. 실패 로그에는 지도 세대, Surface 유효성, 지도 준비 여부가 나온다.
표시가 멈췄을 때 화면을 누르거나 새 draw를 요청해서 테스트를 통과시키지 않는다.

검증 빌드는 `app-debug.apk` 출력을 사용한다. 일반 앱 APK가 필요하면 init script 없이 다시 빌드한다.

## 검사 범위

- `replacementAndReentryRenderWithoutAdditionalInput`: 새 지도 교체와 화면 제거/재진입을 각각 3회,
  실제 Surface·NaverMap·위치 표시 준비와 이전 지도 파괴 확인.
- `resumeRotationAndLocationKeepWorking`: Activity 중지/재개, 동일 지도 유지, 135° 회전 보정,
  위치 숨김/복귀, 추적 카메라 이동, 얼굴 규격 확인.

이 문제는 Surface의 실제 생성에 달려 있어 Robolectric shadow 검사만으로 판정하지 않는다.
야외 GPS, 서버 동기화, 프로세스 종료 복원, 모든 Android 버전의 동작은 이 검사 범위에 없다.

## 수정 원리

새 MapView가 처음 배치되면 `doOnLayout`에서 루트 뷰의 다음 그리기를 한 번 요청한다.
새 AndroidView의 배치 시점에 SurfaceView의 pre-draw 단계가 이미 지난 경우에도 다음 단계를 진행한다.
배치 콜백은 한 번 실행되고 제거되며 좌표 갱신이나 재구성 때마다 반복하지 않는다.
SDK lifecycle·카메라·얼굴 Effect는 그대로 유지한다.

Android의 [SurfaceView 구현](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/SurfaceView.java)은
pre-draw에서 크기와 Surface를 갱신한다. 이번 수정은 해당 단계가 실행되지 않던 기기 재현과
추가 draw 한 번으로 같은 지도 객체가 준비된 실험에 근거한다.

실기기 실행 결과와 수정 전후 비교는 PR #355에 기록한다.

## 일반 행동·사진 핀 회귀 검사

`MomentReviewActivity`는 같은 검증 Application 아래서 실제 `NaverMapSurface`에 짖기·킁킁·
배설·메모·사진·누락 사진의 여섯 핀과 경로를 표시한다. 사진은 앱 캐시에 만드는 단색
합성 PNG이며 사용자 사진·계정·산책 저장소를 사용하지 않는다. 빌드와 설치 명령은 위와 같다.

```powershell
& $adbPath -s $deviceSerial shell am instrument -w -r -e class com.daengs.app.map.review.MomentLayerDeviceTest com.daengs.app.locationreview.test/androidx.test.runner.AndroidJUnitRunner
```

- `nativeTapPhotoReplacementAndFallbackKeepRouteAndCamera`: 실제 터치 입력으로 새 콜백과
  선택을 확인한다. 사진 교체 픽셀과 누락 사진의 기본 핀 클릭을 검사하고, 같은 경로 객체와
  카메라가 유지되는지 확인한다.
- `backgroundMapReplacementAndReentryReleaseOldPins`: Activity 중지/복귀, 지도 객체 교체,
  지도 제거/재진입에서 이전 핀의 연결 해제와 새 지도 준비를 확인한다.

그림 확인용 진입은 `am start -n com.daengs.app.locationreview/com.daengs.app.map.review.MomentReviewActivity`다.
스크린샷은 검증 Activity가 전면인지 확인한 다음 촬영한다. runner 결과와 실제 화면 확인은
구분해서 기록하며, 이 검사는 야외 GPS·서버 저장·성능 측정을 포함하지 않는다. PR #394에 결과를 기록한다.
