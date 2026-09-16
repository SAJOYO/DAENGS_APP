# 산책 일기 외부 방향 표시

PR #419는 완료 산책의 방향 표시만 변경한다. 저장 GPS, 속도 색상, 통과 판정,
일기 선택·서랍·재생 상태는 바꾸지 않는다. 확대 전에도 후보가 있으면 표시한다.

## 책임

- `map.layers.completedroute.RouteDirectionPolicy`: 화면 크기·간격·곡률·개수 기준.
- `RouteDirectionCandidates`: 연속 경로에서 화면상 접선과 후보 생성. SDK 의존 없음.
- `RouteDirectionLayout`: 후보 우선순위, 왕복·교차 모호성, 핀·다른 경로·화면과의 충돌 검사.
- `NaverSessionRouteExplorer`: 좌표 투영과 SDK 마커 수명. 이전 후보의 원본 간선 ID·비율을
  지도/경로 범위에서 보관한다. 현재 화면 좌표를 저장하거나 일기 원본에 기록하지 않는다.

## 배치

1. 연속된 간선을 묶는다. 세그먼트 변경·좌표 공백·잘못된 좌표를 건너 연결하지 않는다.
   화면에서 1px보다 작은 GPS 간선도 유지한다.
2. 화면 안에 들어오는 길이를 먼저 구하고 기본 12dp 간격, 최대 160개 후보로 제한한다.
   화면 밖의 긴 동선이 후보 예산을 차지하지 않게 한다.
3. 후보 앞뒤 14dp의 경로로 접선을 계산한다. 직선거리/경로거리 비율이 .985 미만이거나
   중간 관측점의 이탈이 2dp를 넘으면 급커브 후보로 생략한다. 곧은 후보를 먼저 검사한다.
4. 이전 후보는 같은 원본 간선의 비율을 새 투영에 적용하여 우선 검사한다.
   핀·경로·화면 경계와 새로 충돌하거나 충분히 곧지 않으면 유지하지 않는다.
5. 진행 방향 오른쪽 16dp 한 곳에만 놓는다. 반대편이나 48dp 바깥으로 옮기지 않는다.
   간격은 100dp 이상, 최대 5개다. 최대 개수를 채우기 위해 충돌 조건을 풀지 않는다.
6. 전체 보기에서 7dp 안의 교차·역방향 관측은 기존 기준으로 모호하게 처리한다.
   선택한 통과는 별도 방향을 표시하되 선택 밖의 경로와도 충돌 검사한다.

아이콘은 18dp 상자 안의 짧고 통통한 화살표와 흰 외곽선이다. 둥근 끝과 꺾임을 유지하며
몸통은 3.2dp, 외곽선 포함 굵기는 5.2dp다. 실제 픽셀 크기와 충돌 반경은
같은 정책에서 계산한다. 카메라 이동 중 숨기고 멈춘 뒤 재배치하는 수명은 유지한다.
원본 경로나 선택 대상이 바뀌면 이전 후보를 폐기한다. 지도 크기·핀 경계 변경에는
후보를 재검사하며, 재생 커서만 바뀌는 경우 방향 마커를 재생성하지 않는다.

네이버 기본 지도 라벨의 경계 상자는 입력하지 않으므로 지명·상호 전체의 충돌 회피를
보장하지 않는다. 16dp 옆이 막힌 짧은 구간에는 화살표가 없을 수 있다.

## 검증 및 재현

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.map.layers.completedroute.RouteDirectionLayoutTest' --tests 'com.daengs.app.map.layers.completedroute.SessionRouteExplorerLayerStateTest' --tests 'com.daengs.app.map.provider.naver.NaverSessionRouteExplorerTest' --tests 'com.daengs.app.DesignLockTest'
.\gradlew.bat -I tools/walk-direction-review.init.gradle :app:assembleDebug -PslimAbi=arm64-v8a
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.daengs.app.directionreview/com.daengs.app.ui.walk.WalkDirectionLabActivity
```

`WalkDirectionLabActivity`는 메모리 안의 예시 경로만 사용하며 기록·계정 저장소에 쓰지 않는다.
곡선 / 왕복 / 통과 선택과 핀 숨김·표시, 지도 확대·축소·회전·이동·백그라운드 복귀를 비교한다.
SDK 키는 기존 로컬 설정 또는 `DAENGS_NAVER_NCP_KEY_ID` 환경변수로 주입한다.
키가 없거나 별도 검토 패키지의 인증이 허용되지 않으면 타일은 표시되지 않을 수 있다.

JVM 검사는 배치와 SDK 호출 수명을 검사한다. 실제 SDK 렌더링·읽기 쉬움의 판정을 대신하지 않는다.
