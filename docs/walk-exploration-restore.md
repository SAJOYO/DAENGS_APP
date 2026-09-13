# 시간 탐색과 읽던 위치 복원 — 5단계

[APP #372](https://github.com/SAJOYO/DAENGS_APP/pull/372)은 [저장 관측 표시](walk-measurement-observed.md)에 이어 일반 상세의 시간 탐색·재생과 작은 열람 주소 저장을 연결한다. 기준 브랜치는 #371이 병합된 dev `01066a0`이다.

## 시간축과 범위

`WalkMeasurementContract`가 검증한 recording epoch를 측정 상세에 유지한다. `MeasurementTimeline`은 각 epoch의 시작/종료 기기 경과 시간 차이를 더해 **기록 중 경과 시간**을 만든다. 일시정지 시간이나 다른 기기 시간 기준 사이의 길이는 추측하지 않는다. 같은 wall time의 재방문도 source epoch·clock epoch·elapsed nanos가 다르면 다른 주소다. 큰 기기 시간값은 먼저 epoch 시작값을 뺀 다음 기록 누적 시간을 더해 중간 오버플로를 피한다.

기존 동선 탐색 패널에 1/3/5분 선택과 양 끝을 조절하는 시간 범위를 추가한다. 범위와 겹치는 **원본 선분 전체**를 강조하며 끝점을 새 측정 관측으로 만들지 않는다. 보행선과 제외/미확정 보조선은 각각의 분류와 원본 구간을 유지한다. 공백을 잇거나 보행거리 합계를 바꾸지 않는다. 범위 안에 사건 시간 주소를 확인할 수 있는 장면을 나열하고 기존 장면 선택으로 이동한다. 주소를 확정할 수 없는 장면은 원래 전체 장면 목록에서 계속 읽는다.

재생은 보행 및 보행거리 제외 구간의 확인된 원본 선분에서 기기 경과 시간으로 위치를 보간한다. 보행 미확정 구간과 공백 사이에는 커서를 만들지 않는다. 다만 정확히 그 시점에 usable 원본 관측이 있으면 확인된 점을 표시한다. wall time과 기기 경과 시간이 불일치하는 선분은 표시 시각을 보간하지 않는다. 프레임 조회는 epoch별 원본 선분 이진 검색과 정확한 관측 주소 조회를 사용한다.

같은 측정의 상세 재준비는 시간 선택을 유지한다. 다른 측정으로 바뀌면 이전 시간 선택을 자동 재사용하지 않는다. 선택된 장면은 기존 읽기 전환 정책에 따라 현재 장면과 binding으로 다시 연결한다.

## 저장과 복원

Room **20**의 `walk_exploration`은 세션마다 소유자와 최대 16KiB JSON 한 개를 저장한다. 원본·측정 캐시·경로 배열을 복제하지 않는다. 세션 외래키의 삭제 연쇄로 산책 삭제/계정 정리 시 함께 제거한다. 저장은 현재 소유자의 세션이 존재할 때만 수행하고, 트랜잭션 안에서 로그인 generation을 다시 확인한다. 조회 후에도 현재 계정인지 확인한다. 서버 스키마나 API 변경은 없다.

저장하는 선택은 장면 원본 revision, 보행/관측 구간 ID, 공백/제어 사건 ID, 원본 기기 시간 주소로 표현한 범위 또는 재생 위치다. 배속, 탐색 탭, 서랍 높이, 목록의 안정적인 항목 key/스크롤, 현재 장면 본문의 스크롤을 함께 보관한다. 본문이 바뀌면 장면 사건 선택은 유지하면서 옛 본문 스크롤을 버린다. 같은 원본 주소를 확인할 수 있는 공간 통과 선택은 시간 범위로 저장하고, 주소를 확정할 수 없으면 전체 보기로 복원한다.

소유자·세션·형식 버전과 현재 읽기의 근거를 검사한다. 측정 기록은 measurement ID/result digest, 기존 기록은 원본과 요약·경로·reader의 지문을 대조한다. **다른 측정에 옛 탐색 범위를 자동 이식하지 않는다.** 저장된 측정이 아직 도착하지 않았으면 주소를 덮어쓰지 않고 기다리며, 사용자가 먼저 조작하면 그 선택을 우선한다. 삭제된 장면·다른 clock epoch·범위 밖 시점은 복원하지 않는다.

`RememberWalkExplorationPersistence`는 현재 경로·장면 묶음이 탐색 상태에 채택된 뒤에만 복원한다. 화면 재생성의 scene-only Saver 값이 먼저 들어와도 현재 원본을 사용한 시간 위치 복원을 막지 않는다. 재생은 항상 **일시정지**로 복원한다. 화면 이탈 때 마지막 표시 주소를 한 번 더 저장하며, 늦은 저장/복원은 계정 경계를 통과하지 못한다. 저장 실패가 일기 읽기를 막지는 않는다.

카메라는 기존 `DiaryMapNavigation` 정책을 사용한다. 시간 범위를 바꾸거나 늦은 주소가 도착한 것만으로 카메라 이동을 요청하지 않는다. 기존 SavedState 카메라 복원 범위를 유지하며, 이 체크포인트에 실제 지도 제스처 구도를 새로 저장하지 않는다. 서랍의 세 단계·최초 중간 높이·독립 스크롤과 지도 표식 선택 시 카메라 유지 규칙은 그대로다.

## 확인한 것

2026-09-13 최종 검사: `assembleDebug` 성공. 아래 선택 범위 **54개 클래스·340개 중 338 통과, 실패/오류 0, skip 2**. 비공개 원본이 없는 기존 `PrivateObservedRouteMapReplayTest`, `PrivateRecordContextReplayTest` 각 1개를 건너뛰었다.

- `MeasurementTimelineTest`: 보행/제외/미확정/공백, 시계 보정, 반복 시각 epoch, 큰 기기 시간, 분할 전후 거리 보존.
- `WalkMeasurementTest`: 실제 저장 응답 32개 사례의 검증된 epoch 시간축과 주소 왕복, 기존 긴 청크·오프라인 상세 재열람.
- `MeasurementTimeControlsTest`: 실제 탐색 패널의 1분 범위 선택, 범위 내 장면, 지도 자료 공급과 재생 시점 이동.
- `WalkExplorationBookmarkTest`: 새 준비 결과에서 장면/구간/공백/범위/재생 주소 해석, 소유자·측정·시계 불일치·삭제 거부, 본문 revision 변경.
- `WalkExplorationPersistenceTest`: 화면 재생성, 재진입, 마지막 위치 저장, 늦은 복원과 선행 사용자 조작, 측정 대기, 실제 읽기 화면의 본문 스크롤과 서랍 복원.
- `WalkExplorationStoreTest`, `StoredWalkDetailDataTest`, `WalkMigrationTest`: DB 닫기/재열기, 계정·삭제 경계, 19→20과 기존 버전 migration, 원본/측정 청크 보존.

새 시간 컨트롤에 `@Preview`가 있고 변경된 패널·읽기 레이아웃의 기존 Preview를 유지한다. 저장/복원 hook은 생산 상세를 사용하는 `WalkDiaryMapPreview`에서도 호출된다. 네이티브 지도 실기기 조작·APK 설치·실제 프로세스 강제 종료 검증은 수행하지 않았다. JVM의 실제 Compose/Room 재생성 검증과 구별한다.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --tests '*Measurement*' --tests '*WalkExploration*' --tests '*WalkMigrationTest' --tests '*WalkDiary*' --tests '*WalkDetail*' --tests '*CompletedRouteReviewTest' --tests '*RecordContext*' --tests '*WalkReadingBaselineUiTest' --tests '*WalkRecordOverviewUiTest' --tests '*DesignLockTest' --tests '*StoredWalkDetailDataTest' --tests '*DiaryMapNavigationTest' --tests '*ActionPin*' --tests '*StoryboardObservation*' --tests '*LocalDiary*' --tests '*ServerDiary*' --tests '*ObservedRoute*' --tests '*RouteDirection*' --tests '*SessionRouteExplorerLayerStateTest' --tests '*WalkRouteExplorerStateTest'
```

운영/main 반영은 별도 합의한다. 실기록의 기기 원본 독립 대조와 기록→동기화→열람→실제 프로세스 재시작의 종단 검증은 남아 있다.
