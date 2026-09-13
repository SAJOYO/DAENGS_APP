# 탐색 패널의 실제 지도 조작과 읽기 공간 (#385)

#383을 포함한 dev `b09d494`에서 후속 검증했다. 서랍 높이와 지도 선택 정책은 그대로 둔다.

## 발견한 문제와 수정

Galaxy S25에서 화면 밖 장면 메뉴와 방향 안내가 시간 조작 위에 각각 한 줄씩 붙었다.
중간 높이 서랍의 공간을 이 안내와 고정 조작이 모두 소비해 `explorer-reading`의 실제 높이가 **0px**이었다.
장면은 semantics에 존재했지만 화면에서 읽거나 누를 수 없었다. 앞 단계의 지도 대체 Preview에는 이 두 안내가 없어서 잡지 못했다.

탐색 모드의 화면 밖 장면·방향 안내·갱신 상태·재시도를 장면 목록 아래의 독립 스크롤 영역으로 옮겼다.
시간 조작은 상단에 고정하고, 안내 버튼과 콜백은 유지한다. 장면 탭의 기존 안내 위치는 그대로다.
`WalkDiaryMapContent`가 탐색 패널에 안내 슬롯을 전달하고 `WalkRouteExplorerPanel`이 읽기 영역에 표시한다.
서랍을 강제로 펼치거나 기존 중간 높이를 늘리지 않는다.

`WalkExplorerPanelPreview.NOTICES`와 UI 회귀 검사에는 지도 보기 선택·화면 밖 장면·방향 안내·갱신 메시지·오류를 함께 넣었다.
320×640dp·글자 1.3배에서 읽기 영역이 남고, 장면·확대·재시도·화면 밖 장면에 스크롤로 접근하며,
장면에서 구간으로 돌아와도 시간 조작과 서랍 높이가 유지되는지 검사한다.

## 실기기 결과

2026-09-13, Galaxy S25 / Android 16에서 기존 `com.daengs.app.locationreview`와 테스트 APK를 갱신했다.
두 설치의 `Success`를 확인했고, 기기에서 되뽑은 APK와 이번 빌드의 SHA-256이 일치했다.
새 패키지를 추가하지 않았으며 기존 로그인 앱이나 사용자 기록은 바꾸지 않았다.

**8개 instrumentation 검사 통과**:

- 기존 4단계: 본문 스크롤·서랍, 구간, 일시정지 재생·8배속을 프로세스 교체 후 복원.
- 기존 구간 3단계: 구간→장면→구간의 스크롤·높이 유지, 장면에서 재시작 후 범위 복귀, 구간 끝 정지와 커서 복원.
- 새 `verifyExplorerActions`: 실제 범위 슬라이더 드래그, 전용 구간 복귀, 장면 앞뒤 30초, 재생 위치 터치, 구간 수정과 전체 산책 복귀.

실제 Naver SDK 화면에서 전체 경로·선택 구간·선택 장면·재생 커서를 확인했다.
합성 측정의 좌표가 (0, 0) 부근이므로 배경은 해양 지도이며, 캡처 전에 검증용 카메라만 원본 경로에 맞춘다.
사용자 산책·실제 거리 풍경이나 실시간 GPS 정확도, 서버 로그인/업로드를 검증한 결과는 아니다.
캡처는 검증 Activity에 포커스가 있을 때만 수행한다.

검증 도구의 준비 플래그가 첫 Compose 화면보다 먼저 켜지는 시작 경쟁도 수정했다.
실제 화면이 만들어질 때까지 제한 시간 안에서 기다리며 기존 저장 값·화면·프로세스 변경 검사는 유지한다.

## 재현

지도 설정을 포함한 APK 빌드/설치와 기본·구간 runner는 [기존 측정 검증 절차](walk-measurement-device-review.md#합성-검사-실행)를 따른다.
새 조작 검사는 기본/구간 검사와 별도로 초기화해서 실행한다.

```powershell
& $adbPath -s $deviceSerial shell am instrument -w -r -e class com.daengs.app.ui.walk.MeasurementDeviceTest#verifyExplorerActions com.daengs.app.locationreview.test/androidx.test.runner.AndroidJUnitRunner
```

종료 코드만 보지 않고 `OK (1 test)`를 확인한다.
검증 앱 external files의 `explorer-whole.png`, `explorer-range.png`, `explorer-scene-actions.png`, `explorer-cursor.png`가 실제 SDK 캡처다.
앱·좌표·설정·PNG 등 로컬 산출물은 저장소에 넣지 않는다.
JVM 회귀 검사와 일반 debug 빌드는 [탐색 패널 검사 명령](walk-explorer-panel.md#검증)을 사용한다.
관련 27개 클래스의 139개 테스트와 일반 debug 빌드가 통과했으며 실패·오류·건너뜀은 0이다.
이번 결과를 운영 main 반영이나 실계정 서버 검증으로 취급하지 않는다.
