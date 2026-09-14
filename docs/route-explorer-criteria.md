# 동선 탐색의 선택·통과·재생 기준

`RouteExplorerCriteria.kt`는 기존 `RouteExplorerIndex`의 값을 목적별로 명명한다.
설정 시스템이나 새 판정 로직을 추가하지 않는다. 같은 값이라고 서로 다른 기준을 연결하지 않는다.

| 담당 | 기준 | 기존 값/비교 |
| --- | --- | --- |
| RouteIndexGrid | 공간 인덱스 셀 한 변 | 20m |
| RouteTapCriteria | 탭에서 기준 경로까지 허용 거리 | 20m 이하 |
| RoutePassageCriteria | 분석할 인접 관측점 사이 거리 | 0.3~120m, 양 끝 포함 |
| RoutePassageCriteria | 관측 시각 간격 | 1~15,000ms, 양 끝 포함 |
| RoutePassageCriteria | 관측점 정확도 | 유한한 양수, 12m 이하 |
| RoutePassageCriteria | 기준 위치 주변 후보 공간 검색 | 30m |
| RoutePassageCriteria | 기준 방향과의 일치도 | 코사인 절댓값 0.9 이상 |
| RoutePassageCriteria | 통과 판정 구역 | 중심에서 길이 방향 ±24m, 횡방향 ±4m |
| RoutePassageCriteria | 중심 양쪽 도달 거리 | 각각 6m 이상 |
| RoutePassageCriteria | 통과 길이 방향 이동 폭 | 18m 이상 |
| RoutePassageCriteria | 통과별 횡방향 평균 위치의 최대 차이 | 2m 초과이면 불확실 |
| RouteReplayCriteria | 재생을 보간할 인접 관측의 간격 | 활동 시간과 기록 시각 각각 1~15,000ms |

후보 공간 검색은 격자에서 후보를 수집하는 범위이며 최종 허용 여부는 거리·방향·통과 구역으로
판정한다. 셀 크기와 클릭 허용 거리는 같아도 독립적인 기준이다. 통과 구역의 반폭과 여러
통과 사이의 횡방향 차이도 서로 다르다.

`RouteExplorerIndex` 내부의 `CLIP_PARALLEL_EPSILON_METERS`(0.0001m)는 선분 자르기의
수치 허용오차다. GPS 정확도 정책에 넣지 않는다. 좌표 환산 계수, 배열 순번, 이진 검색,
0..1 투영 비율 등의 산술은 이번 정책 명명과 별개로 그대로 둔다.

## 보존할 결과

- 통과 분석은 기록된 보행 경로에서 수행한다. 교차로는 재통과로 세지 않으며 평행한 길이나
  GPS 오프셋으로 구분이 어려우면 불확실로 남긴다. 순방향/역방향, 통과 ID·시각·경로도 유지한다.
- 재생은 같은 구간의 인접 관측점만 보간한다. 공백을 연결하지 않고 정확한 관측 시각의
  좌표는 그대로 읽는다. 통과 분석에 부적합한 정확도를 재생 필터로 새로 적용하지 않는다.
- 같은 15초여도 통과 분석과 재생 제한을 공유하지 않는다. 새 `MeasurementTimeline`과
  관측 원본 기반 판정은 이 레거시 경로 기준으로 통합하지 않는다.
- 선택·카메라·화면·저장 상태와 재생 속도는 변경하지 않는다.

## 검사 범위

`RouteExplorerIndexTest`의 기존 교차로·평행 경로·역방향/순방향 재통과·정지 흔들림·공백
검사에 클릭 거리, 정확도, 두 시계의 보간 간격, 중심 통과/이동 폭 경계 검사를 추가한다.
테스트 입력은 명명된 상수를 참조하지 않아 구현의 값을 바꿀 때 기대값도 함께 바뀌지 않는다.
직접 사용하는 상세 탐색 상태와 장면 대응은 `WalkRouteExplorerStateTest`,
`CompletedRouteReviewTest`로 확인한다.

```powershell
.\gradlew.bat --no-daemon :app:compileDebugKotlin :app:testDebugUnitTest `
  --tests 'com.daengs.app.walk.routeexplorer.RouteExplorerIndexTest' `
  --tests 'com.daengs.app.walk.routeexplorer.CompletedRouteReviewTest' `
  --tests 'com.daengs.app.ui.walk.WalkRouteExplorerStateTest' `
  --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' `
  '-Dorg.gradle.jvmargs=-Xmx3072m -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8' `
  -PslimAbi=arm64-v8a --console=plain
```

실행 결과와 보존 대조 근거는 PR #396에 기록한다.
