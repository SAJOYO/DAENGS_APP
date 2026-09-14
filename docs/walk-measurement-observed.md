# 저장 관측 경로와 방향 — 4단계

[APP #371](https://github.com/SAJOYO/DAENGS_APP/pull/371)은 [3단계 장면 연결](walk-measurement-scenes.md)에 이어 저장 측정의 보행 외 관측과 공백을 일반 상세 지도에 공급한다. 서버·Room 스키마는 추가하지 않는다. 저장 요약과 청크를 원본·로컬 motion 엔진에 대조한 뒤, 같은 상세 읽기에서 보조선과 장면 연결을 준비한다.

## 관측 채택

`MeasurementObservedContract`는 동결된 `motion-shadow-observed-v1` 연결 정책과 최종 보행 원본 범위를 사용한다. 보행 소유 구간은 중복 보조선에서 제외한다. 나머지 인접 원본은 양 끝이 usable이고 같은 source/clock epoch이며 경과 시간이 0초 초과·20초 이하, 좌표 변위가 200m 이하일 때 연결한다. 유효하지 않은 시각·위치와 기록 경계는 연결하지 않는다. 제외 사유에 `HIGH_SPEED` 또는 `REENTRY_PENDING`이 있으면 보행거리 제외, 그 외는 보행 미확정 관측으로 분리한다.

재구성한 전체 관측 run의 원본 순서·분할과 각 보행 기여량을 전송 결과에 대조한다. 청크 해시가 맞아도 관측 경로를 줄이거나 기여량을 바꾸면 채택하지 않는다. 지원하지 않는 연결 정책도 거부한다. 이 검증은 서버와 앱의 동일 입력·정책 해석 대조이며 실제 이동의 독립적인 정답 검증은 아니다. 서버의 `experimental`, `device_result_verified=false` 상태는 유지한다.

보조 구간 ID에는 measurement ID·집계 구분·원본 시작/끝 번호가 들어간다. 장면은 보행과 보조 구간 모두에서 원본 주소를 찾는다. 공유 정점에서는 보행을 우선하고, 두 보조 구간이 만나는 곳에서는 들어오는 구간을 선택한다. 원본 없는 장면은 경쟁하는 보행/보조 방문도 함께 확인한다. 모호한 사진은 경로를 강조하지 않지만 독립적으로 확인된 사진 위치로 이동할 수 있다. 연결 정책 key는 `measurement-scene-binding-v2`다.

## 표시와 공백

`ObservedRouteReview → recordPresentationLayer`가 기존 청록/황갈색 계열 보조선·범례·설명·선택 강조를 공급한다. 장면 선택은 해당 구간의 원본 주변만 강조한다. 보행거리나 보행 경로에 관측 변위를 더하지 않는다.

방향은 원본의 인접 선분에만 붙인다. 보고된 정확도와 3m 여유보다 변위가 작은 경우에는 최대 10초의 주변 관측으로 방향 근거를 확인한다. 근거가 부족하면 선을 유지하고 화살표를 숨긴다. 주변 근거를 쓰더라도 왕복을 가로지르는 새 직선을 만들지 않는다. 선택 범위 밖의 관측에 의존하는 화살표도 숨긴다. measured 관측에서는 기기의 경과 시간을 사용하므로 wall time 보정만으로 방향을 지우지 않는다.

`MeasurementGapContexts`는 확인된 원본 사이에서 어떤 최종 관측 run도 소유하지 않는 범위를 공백으로 묶는다. 보행 정점에서 생략된 중간 관측은 원본 범위에 포함되므로 가짜 공백을 만들지 않는다. 공백은 기존 회색 `–` 표식과 목록 항목으로 공급하며, 선택했을 때만 전후 위치의 관계 점선을 표시한다. 점선은 경로·거리·방향·재생 입력이 아니다.

공백 길이는 그 범위의 원본들이 같은 source/clock epoch이고 경과 시간이 순서대로 확인될 때만 계산한다. 시계/기록 경계에서는 길이를 확정하지 않는다. 시작·종료 제어 시각과 위치 관측 시각 사이의 wall time 차이도 확정된 지속시간으로 표시하지 않는다. 측정 전체의 wall time 재생·시간으로 인접 위치 추정은 사용하지 않는다.

지도 표식 선택 시 카메라·서랍 높이 유지, 목록에서 명시적으로 위치 이동, 최초 중간 높이를 포함한 세 단계 서랍과 독립 스크롤은 기존 동작을 사용한다. Composable/지도 SDK 코드는 변경하지 않았으며 실제 생산 화면이 사용하는 자료 공급부를 연결했다.

## 검사와 남은 범위

`WalkMeasurementTest`는 기존 서버 wire 32개 사례와 600점 분할 청크, 오프라인 Room/일반 상세 재열람을 검사한다. `walk-measurement-observed-v1.json`은 DEV `ccc0d14`의 `replay_shadow()`가 같은 정밀 합성 입력에서 만든 최종 connected/non-included 구간 20개를 추출한 기대값이다. 각 사례의 measurement ID와 보조 선분의 원본 번호·제외/미확정 분류를 대조한다. 재해시한 경로 축소·기여량 변조·지원하지 않는 정책 거부도 확인한다.

`MeasurementObservedReviewTest`와 `MeasurementObservedPresentationTest`는 보행/제외/미확정/공백/보행 재개를 포함한 합성 기록으로 원본 소유, 시계 보정, 왕복 방향, 불확실한 방향 숨김, 실제 지도 자료 공급부의 선택 배타성을 확인한다. `DiaryMapNavigationTest`는 관측 장면 및 반복 시각 사진의 지도/목록 이동과 오래된 선택 차단을 검사한다.

2026-09-13: `assembleDebug` 성공. 아래 선택 범위 46개 클래스, 304개 검사 중 **302 통과·실패 0·skip 2**. skip은 비공개 원본이 없는 기존 `PrivateObservedRouteMapReplayTest`, `PrivateRecordContextReplayTest` 각 1개다. 네이티브 지도 실기기 조작·새 APK 설치는 수행하지 않았다.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --tests '*MeasurementObserved*' --tests '*MeasurementScene*' --tests '*WalkMeasurement*' --tests '*WalkDiary*' --tests '*WalkDetail*' --tests '*CompletedRouteReviewTest' --tests '*RecordContext*' --tests '*WalkReadingBaselineUiTest' --tests '*WalkRecordOverviewUiTest' --tests '*DesignLockTest' --tests '*StoredWalkDetailDataTest' --tests '*DiaryMapNavigationTest' --tests '*ActionPin*' --tests '*StoryboardObservation*' --tests '*LocalDiary*' --tests '*ServerDiary*' --tests '*ObservedRoute*' --tests '*RouteDirection*' --tests '*SessionRouteExplorerLayerStateTest' --tests '*WalkRouteExplorerStateTest'
```

시간 slice·measured 재생 시간축과 탐색 주소의 영속 복원은 후속 [5단계](walk-exploration-restore.md)에서 연결한다. 운영/main 반영과 실기록의 기기 원본 독립 대조는 별도다.
