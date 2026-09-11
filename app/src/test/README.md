# 기능별 테스트 실행 지도

최초 조사 기준은 2026-09-09 `dev`의 `9bf37188bab0841ac7b682dec1714ca58c893b2f`다.
이 문서는 변경한 기능에 맞는 테스트를 고르는 지도다. 전체 통과 보고서가 아니다.
조사 시점에는 Kotlin 테스트 파일 221개에 `@Test` 선언 1,528개가 있었고,
별도 공용 파일은 `place/FacilityFixtures.kt` 1개였다. 선언 수는 실제 실행 결과와 구분한다.
2단계 공용 helper 분리는 `dev`의 `9391671`까지 반영한 뒤 진행했다.
이 시점의 테스트 파일은 226개, `@Test` 선언은 1,543개이며 분리로 추가·삭제한 테스트는 없다.

## 실행 방법

모든 명령은 **저장소 루트**에서 실행한다. 기존 Android 개발 환경을 사용하며,
JDK·SDK 준비는 [루트 README](../../../README.md)의 설치 안내를 따른다.
아래는 PowerShell 명령이다. macOS/Linux에서는 `./gradlew`로 바꾼다.

```powershell
# 한 클래스 내부 변경: 해당 클래스부터 선택한다.
.\gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.walk.store.WalkDaoTest'
# 공용 변경: --tests를 반복해 제공자와 소비자를 함께 선택한다.
.\gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.walk.pin.ActionPinEstimatorTest' --tests 'com.daengs.app.walk.pin.ActionPinReplayTest'
```

- `--tests`는 **실행 대상**을 고른다. 같은 소스셋의 다른 테스트도 컴파일되므로,
  선택하지 않은 파일의 컴파일 오류가 실행을 막을 수 있다.
- 아래 패키지 선택자는 기능 전체를 건드릴 때의 범위다. 한 동작 변경은 해당 클래스로
  좁히고, 공용 모델·저장소·fixture 변경이면 소비자를 추가한다. 표 전체를 매번 실행하지 않는다.
- `*`는 하위 패키지도 포함한다. 예를 들어 `com.daengs.app.walk.*`는 핀·일기·저장·동기화도 실행한다.
- 결과는 `app/build/test-results/testDebugUnitTest/TEST-*.xml`과
  `app/build/reports/tests/testDebugUnitTest/index.html`에서 확인한다.
  선택한 클래스·실행 수·실패·skip을 함께 기록한다. 일부 통과를 전체 통과로 적지 않는다.
- 문서만 바뀌면 명령의 클래스명·경로·링크를 대조한다. 이 작업 때문에 APK 빌드나 테스트 전체를 돌리지 않는다.

## 기능별 선택 범위

### 산책 종료·기록 공통 상세와 동선 탐색

새 상세 경계는 WalkSessionDestinationTest, WalkSessionDetailUiTest,
WalkRouteExplorerStateTest로 확인한다. 지도 배치는
map.layers.completedroute.RouteDirectionLayoutTest, 단일 세션 통과·재생 계산은
walk.routeexplorer.RouteExplorerIndexTest가 담당한다.
기록 진입/편집을 바꾸면 기존 WalkRecordsRouteStateTest, ui.walk.records.WalkRecordsRouteTest,
WalkRecordsScreenTest, WalkDiaryMapScreenTest, WalkDiaryReaderTest, WalkDiaryPublicationTest를
해당 변경 경계에 맞게 추가한다. 실제 지도 SDK의 가시성은 JVM 테스트 통과와 구별한다.
[상세 계약과 기기 확인 범위](../../../docs/walk-session-detail.md)를 참고한다.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.walk.routeexplorer.RouteExplorerIndexTest' --tests 'com.daengs.app.ui.walk.WalkRouteExplorerStateTest' --tests 'com.daengs.app.map.layers.completedroute.RouteDirectionLayoutTest'
```

표의 각 선택자를 `--tests '선택자'`로 붙인다. 테스트 파일은 이 문서 아래
`java/com/daengs/app/`에 있고, 앱 코드의 같은 패키지와 연결된다.

| 기능 | 해당 기능 전체의 선택자 | 계약을 함께 확인할 소비자 |
| --- | --- | --- |
| 인증·시작·로그인 화면 | `com.daengs.app.auth.*`, `com.daengs.app.ui.startup.*`, `com.daengs.app.ui.landing.*`, `com.daengs.app.ui.nickname.*`, `com.daengs.app.ui.my.*` | 계정 전환·탈퇴 변경은 아래 계정 경계 표의 저장·전송 검증 추가 |
| 반려견 프로필·사진 | `com.daengs.app.pet.*`, `com.daengs.app.ui.pet.*` | 장소 조건은 `com.daengs.app.ui.places.PlaceDogContextTest`, `com.daengs.app.ui.places.PlaceProfilesTest`; 홈 진입은 `com.daengs.app.ui.home.PetGateTest` |
| 장소 검색·자연어 시설 검색 | `com.daengs.app.place.*`, `com.daengs.app.ui.places.*`, `com.daengs.app.map.features.places.*`, `com.daengs.app.map.layers.places.*` | 챗봇 제안은 `com.daengs.app.assistant.PlaceSuggestionsTest`, `com.daengs.app.ui.chat.PlaceSuggestionCardTest`; 경로 인계는 Journey |
| Journey·지도 인계 | `com.daengs.app.journey.*`, `com.daengs.app.map.features.journey.*` | 장소 선택·상태 복귀를 바꾸면 `com.daengs.app.ui.places.PlaceSessionCoordinatorTest` |
| 산책 코어·GPS·기록 | `com.daengs.app.walk.*`, `com.daengs.app.location.*` | 화면은 `com.daengs.app.ui.walk.*`; 지도 표현은 다음 행. 핀·사진·일기만 바꾸면 아래 좁은 묶음 사용 |
| 속도 표시 상태·GPS 표시 안정화 | `com.daengs.app.walk.display.*` | `com.daengs.app.walk.WalkSpeedServiceTest`, `com.daengs.app.ui.walk.MotionSpeedometerTest`, `com.daengs.app.ui.walk.WalkScreenPolicyTest`, `com.daengs.app.ui.walk.WalkViewModelTest`; 서비스→Room→표시, 재진입·고정 크기·기존 제어 경계 |
| 산책 지도·스타일·공통 지도 상태 | `com.daengs.app.map.style.*`, `com.daengs.app.map.shell.*`, `com.daengs.app.map.layers.trail.*`, `com.daengs.app.map.layers.completedroute.*`, `com.daengs.app.map.layers.stays.*`, `com.daengs.app.map.provider.naver.*` | 속도·GPS 안내·완료 경로는 `com.daengs.app.ui.walk.WalkSpeedometerTest`, `com.daengs.app.ui.walk.WalkGpsPresentationTest`, `com.daengs.app.ui.walk.WalkCompletedRoutePresentationTest` |
| 전봇대 점령·사진 인증·활동 | `com.daengs.app.territory.*`, `com.daengs.app.activity.*`, `com.daengs.app.map.features.territory.*`, `com.daengs.app.map.layers.territory.*` | 화면 상태는 `com.daengs.app.ui.walk.WalkViewModelTest`, `com.daengs.app.ui.walk.WalkTerritoryUiTest`, `com.daengs.app.ui.walk.Territory*` |
| 대화·어시스턴트·돌봄 | `com.daengs.app.chat.*`, `com.daengs.app.assistant.*`, `com.daengs.app.care.*`, `com.daengs.app.ui.chat.*`, `com.daengs.app.ui.storage.*` | 장소·산책 제안 계약은 해당 기능 소비자도 확인. 서버 모델의 답변 품질은 별도 범위 |
| 보행·피부 진단 응답 | `com.daengs.app.gait.*`, `com.daengs.app.screening.*` | 촬영/가이드 표현은 `com.daengs.app.ui.chat.GuideFrameScreenTest`, `com.daengs.app.ui.chat.CropGeometryTest`. 실제 진단 모델 정확도는 검증하지 않음 |
| 홈·미니룸·카드·도감·공통 UI | `com.daengs.app.miniroom.*`, `com.daengs.app.dogcard.*`, `com.daengs.app.ui.home.*`, `com.daengs.app.ui.dogcard.*`, `com.daengs.app.ui.dex.*`, `com.daengs.app.ui.theme.*`, `com.daengs.app.ui.AvatarSourceTest` | 카드 DB 변경은 `com.daengs.app.dogcard.store.CardMigrationTest`; 그림·애니메이션은 실기기 확인도 필요 |

`ExampleUnitTest`의 덧셈 예제는 제품 기능 검증으로 세지 않는다.

## GPS 이동 정책 엔진 (#294)

#325의 서버 백업/복원은 `com.daengs.app.walk.sync.WalkMotionSyncTest`가 담당한다.
Room 이관/수신 저장 변경은 `WalkMigrationTest`, `RecordingJournalTest`를,
기존 전송 연결 변경은 `WalkSyncTest`, `WalkRecordingSyncTest`, `WalkDeliveryTest`,
`WalkSpeedServiceTest`를 해당 경계에 따라 추가한다. 별도 DB나 서버 계정 없이 실행하며
공통 지문 fixture와 실제 SQLite를 쓴다. [계약·실증 범위](../../../docs/gps-motion-sync.md).

`com.daengs.app.walk.motion.*`는 Android 없는 순수 엔진의 동결 정책, 위치·속도 품질,
고속 뒤 재진입·구간 장벽, 작은 보폭 누적, 시각 역행·중복·누락, 개별/배치 재생 일치와
제한된 관측 창을 검증한다. `RecordedMotionReplayTest`는 #283 완료 epoch와 원본 번호를
대조한다. 원본/epoch 계약을 바꾸면 `RecordingJournalTest`, `RecordingCompletionTest`도
선택한다. legacy reader를 바꾸면 `TrailRecorderTest`, `WalkSummaryTest`를 추가한다.
#307의 `WalkSpeedRuntimeTest`와 `WalkSpeedServiceTest`는 운영 속도 표시 연결을 검사한다.
#319부터 새 산책의 거리·요약까지 연결한다. 이 테스트가 실기기 주행 검증은 아니다.

#313 정책 저장·비교는 위 `MotionPolicyTest`, `RecordedMotionReplayTest`,
`WalkSpeedRuntimeTest`에 더해 `RecordingJournalTest`의 실제 파일 재개방과
`WalkMigrationTest`의 15→16 이관, `WalkSpeedServiceTest`의 서비스/저장 정책 일치를
검증한다. 세션 모델·Room 변경의 기존 소비자는 `WalkDaoTest`, `WalkTrackingTest`,
`WalkSummaryTest`로 확인한다. 비교 정책의 상세 범위는
[저장 계약](../../../docs/gps-policy-persistence.md)을 따른다.

#319의 측정 연결은 위 엔진·정책·재생·runtime·service·journal·completion에 더해
`WalkSessionRouteTest`, `WalkHistoryTest`, `RoomWalkRecordsSourceTest`,
`WalkDiaryPublicationTest`, `WalkDiaryPublicationLifecycleTest`로 완료 소비자를 확인한다.
`WalkDaoTest`, `WalkTrackingTest`, `WalkSummaryTest`는 기존 저장/요약,
`WalkSyncTest`, `WalkRecordingSyncTest`는 v1과 eligibility 전송 경계다.
새 채택 마커·고속/재진입·경로 상한·STOP 시각 일치·단조 시간축·60초/50m 경계·파일
재개방을 기존 클래스에 추가했다. [적용 및 서버 후속 계약](../../../docs/gps-measurement-integration.md)을 따른다.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.walk.motion.*'
```

## 점령 게임 성적 화면·규칙 팝업

### 전봇대 북마크 (#278)

`com.daengs.app.territory.bookmarks.*`는 HTTP·목록 계약과 로그인 생애를,
`com.daengs.app.ui.game.bookmarks.*`는 중복 탭·타임아웃 재조회·목록/지도·위치 없음·작은 화면을 검증한다.
게임 진입 메뉴와 공용 별 연결을 변경하면 `TerritoryGameScreenTest`,
`ui.game.owned.OwnedTerritoryScreenTest`, `ui.walk.WalkTerritoryUiTest`를 함께 선택한다.
실기기 가상 데이터 확인은 실제 서버에 북마크가 저장됐다는 근거가 아니다.

`ui/game/TerritoryGameScreenTest`는 홈 진입·조회 강아지 교체·성적 상태·팝업 복귀·320dp 큰 글자를,
`ui/game/TerritoryGameRulesTest`는 전봇대를 누르는 두 로컬 예시·인증 구도·점수 차액·닫기/뒤로·상태 복원을 확인한다.
팝업은 실제 점령·카메라·GPS·서버 요청을 실행하지 않는다. 점수 조회 모델을 고치면
`TerritoryGameOverviewTest`, API/저장소를 고치면 위 activity 소비자를 추가한다.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.ui.game.TerritoryGameScreenTest' --tests 'com.daengs.app.ui.game.TerritoryGameRulesTest'
```

## 시설 검색의 적용 조건 표시·해제

### 카테고리 다중 선택과 지도 위 말풍선

`PlaceCategorySelectionTest`, `PlacePurposeSearchTest`는 다른 대분류의 종류 조합,
6개 제한과 마지막 선택 해제/늦은 응답을 확인한다.
`DogBubblePlacementTest`는 화면 끝·키보드 크기의 창에서 머리 위 배치와 꼬리 좌표를,
`PlaceDogAssistantUiTest`는 입력 → 생각 → 답변 전환과 고정된 48dp 버튼을 확인한다.
`ConnectedPlaceSearchUiTest`는 GPS 미확인/먼 검색 중심에서도 입구 유지, 반경 표시,
짧은 지도에서 안내에 가리지 않는 버튼 터치와 기존 검색 액션을 확인한다.
`ConversationUndoTest`는 서버 restore, 실패 시 현재 결과 보존, 같은 ID 재시도,
취소한 복원이 새 수동 검색을 덮지 않는지를 다룬다.
`ConnectedPlaceSearchUiTest`, `FacilityConnectedUiTest`, `FacilitySearchCoordinatorTest`는
연결 화면/기존 제안 경로의 소비자다. 실제 SDK/IME 검토 방법과 계약 경계는
[말풍선 문서](../../../docs/place-dog-bubbles.md)에 있다.

아래처럼 변경한 클래스만 선택한다. 공용 대화 저장소 수정 시에는
`FacilityConversationTest`, `ConversationFiltersTest`, `ConversationConnectedTest`도 포함한다.

```powershell
.\gradlew.bat :app:testDebugUnitTest -PfacilityConversation=true --tests 'com.daengs.app.ui.places.PlaceDogAssistantUiTest' --tests 'com.daengs.app.ui.places.DogBubblePlacementTest'
```

`ConversationFiltersTest`는 조건 ID/revision 전달, 늦은 조작 거부, 실패 시 필터 보존,
동일 요청 재시도를 확인한다. `AppliedPlaceFiltersTest`는 필수/선호·AND/OR·부정값 표시,
`ConversationConnectedTest`는 기존 ViewModel 반영, `ConnectedPlaceSearchUiTest`는 AI를
꺼도 조건 확인·해제·현재 조건 검색이 가능한지를 검증한다. `-PfacilityConversation=true`로
기존 `FacilityConversationTest`, `PlaceSearchBatchTest`, `PlaceSessionCoordinatorTest`,
`PlacesViewModelTest`와 함께 실행한다. 실제 서버/지도·실기기 검증은 별도다.

화면 테스트의 320dp 합성 렌더는 `app/build/reports/conversation/filters-screen.png`,
`filters-dialog.png`에 저장한다. 테스트 창을 그린 이미지이며 실제 폰 캡처가 아니다.

## 산책에서 함께 확인할 경계

### 측정·원본 저장

`WalkTrackingTest`, `WalkFixWriterTest`, `TrailRecorderTest`는 기록·쓰기·표시 경계를,
`WalkDaoTest`는 실제 Room 저장 결과를 확인한다. 기록 서비스의 시작·정지·권한 인계가 바뀌면
`WalkViewModelTest`와 `location` 테스트도 추가한다.
경로/집계 결과를 바꾸면 `WalkSummaryTest`, `WalkPaceTest`, `WalkSessionRouteTest`,
`WalkHistoryTest` 및 해당 지도 표현 테스트까지 확인한다.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.walk.WalkTrackingTest' --tests 'com.daengs.app.walk.WalkFixWriterTest' --tests 'com.daengs.app.walk.TrailRecorderTest' --tests 'com.daengs.app.walk.store.WalkDaoTest'
```

### 행동 핀·기록 수정

추정 입력만 바꾸면 `ActionPinEstimatorTest`와 `ActionPinReplayTest`를 선택한다.
핀 저장·확정·수정·전송 계약이 바뀌면 다음 묶음을 사용한다. `LegacyActionPinTest`와
v2 동기화의 v1 사례는 과거 기록 보존·구서버 호환 계약이다. 현재 릴리즈의 v1 생성·GPS 차단은
`LegacyActionPinTest`, 실제 서비스 버튼의 v1 저장은 `WalkSpeedServiceTest`로 확인한다.
v2 엔진의 GPS 없음·불량·정상 입력은 `ActionPinStoreTest`에서 확인한다.
동기화 진입점 변경은 `WalkEntryV2SyncTest`의 `WalkEntrySync` 연결 사례로 v2 생성·확정 재시도,
쓰기 보류와 v1/v2 혼합 산책을 확인한다. 버튼·서비스 연결을 바꾸면 `WalkTrackingTest`,
raw 업로드/finalize 이후 전송 순서는 `WalkSyncTest`도 함께 선택한다.

GPS 기록 구분의 전송·복원·보완은 `WalkRecordingSyncTest`를 함께 선택한다. 이 클래스의
`production serializers agree with the shared server contract and preserve unknown` 사례는
공통 fixture와 실제 WalkApi/PinPending 직렬화를 대조하고 `app/build/outputs/contracts/gps-recording-v1.json`을
생성한다. 서버 #441의 계약 테스트에 이 파일을 넣어 양쪽 구현을 연결해 검증할 수 있다.
`WalkSyncTest`는 LOCAL_ONLY/RAW_UPLOADED/DERIVED의 관문, `WalkEntryV2SyncTest`는
Room outbox·기존 오류의 제한된 재시도·ACK 유실과 원본 지문 동결을 담당한다.
v1 릴리즈 정책을 바꾸면 앞의 두 클래스와 `WalkRecordingSyncTest`로 구형 서버의 전송 지속,
지원 서버의 실제 메타데이터 보완, 426 이후 기존 v2 자료 복구를 함께 확인한다.
핀의 일기 반영은 `StoryboardPinSyncTest`, `WalkRecordProfileTest`, `WalkDiaryTest`도 연결된다.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.walk.pin.*' --tests 'com.daengs.app.walk.store.WalkEntryStoreTest' --tests 'com.daengs.app.walk.sync.WalkEntryV2SyncTest' --tests 'com.daengs.app.walk.sync.StoryboardPinSyncTest'
```

### 사진·분석 완료·재전송

`WalkDao.sessionsPendingAnalysis()`는 계산이 남은 세션을 조회한다.
`RoomWalkFixLog.sessionsPendingAnalysis()`는 여기에 **기록·사진 미전송 세션**도 합친다.
따라서 `DERIVED`여도 사진 목록 ACK 전에는 앱의 재전송 대상일 수 있다.
이 차이를 지우면 사진이 유실되거나 테스트가 옛 기대값 때문에 실패한다.

사진 전송 변경에는 아래 묶음을 사용한다. `WalkDaoTest`가 두 목록의 차이를,
`WalkPhotoSyncTest`가 빈 사진 목록도 ACK 뒤에만 대기열에서 빠지는 것을 확인한다.
`WalkSyncTest`의 메모리 대역은 Room의 사진/기록 대기열을 구현하지 않으므로
그 테스트만으로 실제 대기열 검증을 대신하지 않는다.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.walk.store.WalkDaoTest' --tests 'com.daengs.app.walk.store.WalkPhotoStoreTest' --tests 'com.daengs.app.walk.sync.WalkPhotoSyncTest' --tests 'com.daengs.app.walk.sync.WalkSyncTest' --tests 'com.daengs.app.walk.sync.WalkDeliveryTest'
```

촬영 입력을 바꾸면 `com.daengs.app.walk.WalkPhotoCaptureTest`,
`com.daengs.app.ui.walk.WalkDiaryPhotoUiTest`를 추가한다.
전송본을 일기 생성에 연결하는 변경은 `com.daengs.app.walk.sync.WalkDiarySyncTest`도 확인한다.
DB 버전·초기 행 생성·기존 사진 게시자 이관은 아래 마이그레이션 묶음에 해당한다.
상세 계약은 [사진 동기화](../../../docs/walk-photo-sync.md)에 있다.

### 일기·스토리보드·기록 검색

종료 후 일기 공개 시점·서버 후보 채택을 바꾸면 아래 묶음부터 선택한다.
`WalkDiaryPublicationTest`는 DAO 계약, `WalkDiaryPublicationLifecycleTest`는 실제 조정기의
타이머·동기화→Room→Reader 흐름, `WalkDiaryBoardSyncTest`는 상태별 HTTP 경계를 확인한다.
준비 중·만료 후 미공개·공개 완료와 공개 행이 없는 기존 산책을 서로 대신 쓰지 않는다.
`WalkHistorySearchTest`는 기존 분석 제목 무효화와 공개 보드 제목 보존을 구분한다.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.walk.diary.WalkDiaryPublicationTest' --tests 'com.daengs.app.walk.diary.WalkDiaryPublicationLifecycleTest' --tests 'com.daengs.app.walk.sync.WalkDiaryBoardSyncTest' --tests 'com.daengs.app.walk.diary.WalkDiaryReaderTest' --tests 'com.daengs.app.walk.store.WalkHistorySearchTest'
```

parser/표현 모델은 `walk.diary`, 서버 반영은 세 sync 클래스, 캐시·검색은 저장소와 reader가 담당한다.
화면만 바꾸면 해당 UI 클래스부터 선택하고, 일기 format·사진/핀 연결·무효화 규칙을 바꾸면
아래 묶음을 선택한다.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.walk.diary.*' --tests 'com.daengs.app.walk.sync.WalkDiarySyncTest' --tests 'com.daengs.app.walk.sync.WalkStoryboardSyncTest' --tests 'com.daengs.app.walk.sync.StoryboardPinSyncTest' --tests 'com.daengs.app.walk.store.WalkHistorySearchTest'
```

UI 소비자는 `com.daengs.app.ui.walk.WalkDiaryMapScreenTest`,
`com.daengs.app.ui.walk.WalkStoryboardScreenTest`, `com.daengs.app.ui.walk.WalkHistorySearchScreenTest`다.
JSON fixture·제목·검색 seed를 바꾸면 아래 공용 helper 표의 소비자도 함께 선택한다.

### 행동 기록으로 산책 비교

서버 응답의 A/B·근거 정합성은 `WalkBehaviorComparisonTest`, HTTP·세션 경계는
`WalkBehaviorComparisonApiTest`, 지도 선택·빈 결과·위치 없는 근거는
`WalkBehaviorComparisonScreenTest`가 검증한다. 이 화면 테스트는 320dp에서 413 오류 뒤
7일·1일 선택이 실제 조회 기간과 성공 화면에 반영되는 흐름도 검증한다. 서버에서 직렬화한 응답 fixture를
공용 `walk/support/BehaviorComparisonFixtures.kt`를 통해 함께 읽는다.
실제 Naver 지도 렌더링·서버 데이터 적재는 이 테스트의 범위가 아니다.
공용 지도 정책을 바꾸면 `com.daengs.app.map.shell.MapScenePolicyTest`도 함께 선택한다.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.walk.diary.WalkBehaviorComparisonTest' --tests 'com.daengs.app.walk.diary.WalkBehaviorComparisonApiTest' --tests 'com.daengs.app.ui.walk.WalkBehaviorComparisonScreenTest'
```

## 산책 기록의 일반 진입·상세 복귀

홈·산책 화면의 기록 진입점은 `WalkRecordsRoute`에서 실제 공급부와 기존
`WalkDiaryMapScreen`을 연결한다. 화면 연결만 바꾸면 아래 네 클래스를 선택한다.

- `ui/walk/WalkRecordsRouteStateTest`: 기록 화면을 실제로 내린 뒤 상태 복원,
  Activity 저장 시점, 같은 회원 재로그인·다른 계정·프로세스 경계의 초기화.
- `ui/walk/records/WalkRecordsRouteTest`: 기록·모아보기·행동 보기의 상세 왕복,
  홈으로 나갔다 복귀, nullable 프로필 로딩 중 조건 유지, 미로그인 안내,
  계정 변경 중 늦은 응답 배제와 동기화 오류에도 로컬 기록 유지.
- `ui/walk/WalkRecordsScreenTest`: 공통 조건·페이지·지도 선택·행동 보기의 기존 소비자.
- `ui/walk/WalkDiaryMapScreenTest`: 기존 상세 표시·뒤로 동작의 소비자.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.ui.walk.WalkRecordsRouteStateTest' --tests 'com.daengs.app.ui.walk.records.WalkRecordsRouteTest' --tests 'com.daengs.app.ui.walk.WalkRecordsScreenTest' --tests 'com.daengs.app.ui.walk.WalkDiaryMapScreenTest' -PslimAbi=x86_64 --console=plain
```

이 절은 실행 지도이며 통과 결과가 아니다. Route 테스트는 실제 기록 화면을 unmount하는
상세 대역과 inspection 지도를 사용한다. MainActivity 초기화·native Naver 지도·배포 서버·
사용자 폰 검증을 대신하지 않는다. 인증·Room·원판 조회 계약까지 바꿀 때는 아래 해당
경계의 테스트만 추가하고, 화면 연결 때문에 전체 테스트를 실행하지 않는다.

## 산책 기록의 공통 상단·독립 필터

상단·선택창 변경은 `ui.walk.records.WalkRecordsFiltersTest`(5마리 복수 선택, 취소,
빈 부분집합 금지, 기간과 독립 적용, 복원, 검색 접기, 320dp/큰 글자)와
`WalkRecordsScreenTest`, `WalkRecordsRouteTest`, `WalkRecordsRouteStateTest`로 좁힌다.
강아지 집합 조회를 변경하면 `WalkRecordsSelectionTest`, `RoomWalkRecordsSourceTest`,
`TraceLoadingWalkRecordsSourceTest`를 더해 중복 없는 OR 선택·행동 귀속·조회 경계를 확인한다.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.ui.walk.records.WalkRecordsFiltersTest' --tests 'com.daengs.app.ui.walk.records.WalkRecordsRouteTest' --tests 'com.daengs.app.ui.walk.WalkRecordsScreenTest' --tests 'com.daengs.app.ui.walk.WalkRecordsRouteStateTest' --tests 'com.daengs.app.walk.records.WalkRecordsSelectionTest' --tests 'com.daengs.app.walk.records.RoomWalkRecordsSourceTest' --tests 'com.daengs.app.walk.records.TraceLoadingWalkRecordsSourceTest' -PslimAbi=x86_64 --console=plain
```

합성 화면 렌더는 `app/build/outputs/records-filters/`에 저장한다. 실제 강아지 사진·
네이버 지도·사용자 폰 검증을 대신하지 않는다. 위 명령은 실행 지도이며 통과 기록이 아니다.

## 산책 기록의 날짜별 목록

`WalkRecordsListTest`는 시작 날짜의 시간대·연도/자정 경계, 같은 날짜 머리글,
카드별 상세 진입, 제목/경로/프로필 누락, 큰 글자·5마리, 페이지·탭·스크롤 복원을 확인한다.
`WalkRecordsFiltersTest`는 프로필 로딩과 선택한 강아지의 일부/전체 삭제도 확인한다.
새 목록의 소비자는 `WalkRecordsScreenTest`, `WalkRecordsRouteTest`, `WalkRecordsRouteStateTest`다.
공통 상세 연결을 함께 갱신하면 `WalkSessionDestinationTest`, `WalkSessionDetailUiTest`를 추가한다.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.ui.walk.records.WalkRecordsListTest' --tests 'com.daengs.app.ui.walk.records.WalkRecordsFiltersTest' --tests 'com.daengs.app.ui.walk.records.WalkRecordsRouteTest' --tests 'com.daengs.app.ui.walk.WalkRecordsScreenTest' --tests 'com.daengs.app.ui.walk.WalkRecordsRouteStateTest' -PslimAbi=x86_64 --console=plain
```

합성 기록의 렌더는 `app/build/outputs/records-list/`에 생성한다. 실제 지도·사용자 기록의
실기기 검증과는 별개다. 이 변경은 기존 `WalkHistoryBrowser`의 카드·조회에는 적용하지 않는다.

## 지도 중심 모아보기와 행동별 겹침

`WalkRecordsMapFrameTest`는 접힌 지도 면적, 손잡이 드래그, 탭·저장 복원의 펼침 상태,
행동 관련 산책만의 겹침 계산, 숨김 후 원래 집계 유지, 위치 없는 행동 복귀와 320dp/큰 글자를 확인한다.
`WalkRecordsScreenTest`, `WalkRecordsRouteTest`, `WalkRecordsRefreshTest`는 선택·숨김·상세·갱신 소비자이며,
`WalkRecordsFiltersTest`는 상단 조건 아이콘과 독립 선택창을 확인한다.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.ui.walk.records.WalkRecordsMapFrameTest' --tests 'com.daengs.app.ui.walk.records.WalkRecordsFiltersTest' --tests 'com.daengs.app.ui.walk.records.WalkRecordsRouteTest' --tests 'com.daengs.app.ui.walk.WalkRecordsScreenTest' --tests 'com.daengs.app.ui.walk.WalkRecordsRefreshTest' -PslimAbi=x86_64 --console=plain
```

합성 UI 렌더는 `app/build/outputs/records-map/`에 저장한다. 지도 SDK 카메라·타일·핀의 실제 표시와
손가락 조작은 실기기에서 확인해야 하며, JVM 렌더 결과와 구별한다.

## 산책 기록의 실제 원판 조회

`walk/records/WalkRecordSheetsTest`는 DEV 직렬화 fixture의 산책 매핑·원판 정책·셀 계약과
빈 결과를, `WalkRecordSheetsApiTest`는 인증된 batch HTTP 요청과 미배포/인증 실패를 확인한다.
`TraceLoadingWalkRecordsSourceTest`는 조회 지연·실패·재시도·계정/기록 변경 중 늦은 응답을,
`RoomWalkRecordsSourceTest`는 실제 SQLite의 업로드 ID 전달과 보드 확정 시 목록 갱신,
준비 중·늦은 AI 제목의 검색 제외를 확인한다.
브러시 연결/겹침 정책은 `WalkRecordsTracesTest`, 화면 소비자는
`ui/walk/WalkRecordsScreenTest`로 좁혀 실행한다. 실제 서버·DB 접속은 필요하지 않다.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.walk.records.WalkRecordSheetsTest' --tests 'com.daengs.app.walk.records.WalkRecordSheetsApiTest' --tests 'com.daengs.app.walk.records.TraceLoadingWalkRecordsSourceTest' --tests 'com.daengs.app.walk.records.RoomWalkRecordsSourceTest' --tests 'com.daengs.app.walk.records.WalkRecordsTracesTest' --tests 'com.daengs.app.walk.records.WalkRecordsSelectionTest' --tests 'com.daengs.app.ui.walk.WalkRecordsScreenTest' --console=plain
```

## Room·계정·외부 의존성

| 변경 경계 | 확인 범위와 실행 조건 |
| --- | --- |
| Walk DB migration | `WalkMigrationTest`와 바뀐 표의 Store/Dao 테스트. 실제 SQLite 파일을 옛 스키마에서 Room 최신 버전으로 열어 검증. `app/schemas/com.daengs.app.walk.store.WalkDatabase/` JSON과 생산 코드의 migration 사용 |
| 계정 전환·탈퇴 | 인증은 `auth`/`ui.startup`; 저장·삭제는 `WalkHistoryTest`, `WalkPhotoStoreTest`, `ActionPinStoreTest`; 늦은 ACK는 `WalkPhotoSyncTest`, `WalkEntryV2SyncTest`. 변경한 경계에 해당하는 클래스를 함께 선택 |
| Room 테스트의 DB | JVM의 Robolectric과 Room/SQLite를 사용. 테스트가 메모리 DB 또는 임시 DB 파일을 만들고 닫는다. 개발 서버 DB 주소·별도 PostgreSQL·Docker 설치는 필요 없음 |
| HTTP 계약 | 주입한 transport/서버 대역 또는 로컬 HTTP 서버로 요청·응답을 확인. 테스트 통과가 배포 서버의 route 존재·인증·capability 활성화를 보장하지 않음 |
| Compose | `src/test`의 Robolectric에서 실행. 최초 조사 시점에 66개 파일이 Robolectric runner를 쓰며 이 중 29개가 Compose test rule 사용. 순수 Kotlin과 같은 Gradle task에 있으므로 필요한 UI 클래스만 선택 |
| 앱 초기화 | [robolectric.properties](resources/robolectric.properties)가 `android.app.Application`을 사용. 카카오 SDK·실제 앱 초기화가 성공했다는 뜻이 아님 |
| 실기기 | `src/androidTest`는 현재 패키지명 확인 예제 1개. GPS 수신·절전/FGS·강제 종료·Naver 지도·실제 CameraX 촬영은 해당 기능의 실기기 검증 기록 필요 |

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.walk.store.WalkMigrationTest' --tests 'com.daengs.app.walk.store.WalkDaoTest' --tests 'com.daengs.app.walk.store.WalkPhotoStoreTest'
```

Room 준비 코드를 정리할 때는 계정·시계·세션·임시 파일과 `@Before`/`@After`의 수명을 유지한다.
메모리 DB 테스트를 서버 DB 테스트로 바꾸지 않는다.

## 공용 helper 제공자와 소비자

2단계에서는 테스트 파일 7곳의 공용 코드를 기능별 `support` 파일 6개로 분리했다.
이후 추가 정리에서 대화 응답·장면 위치 근거·내 점령지·찜 fixture도 테스트 파일에서 분리했다.
아래 표는 이 공용 코드와 기존 시설 fixture의 현재 참조 범위다. 경로는
`java/com/daengs/app/` 기준이며 원래 제공 파일의 테스트도 소비자로 포함한다.
Kotlin은 같은 패키지의 함수를 import 없이 참조할 수 있으므로 import 문 검색만으로는 부족하다.

| 공용 파일 / 공유 항목 | 소비 테스트 |
| --- | --- |
| [walk/support/PinFixtures.kt](java/com/daengs/app/walk/support/PinFixtures.kt) — `PIN_TAP`, `pinRequest`, `pinPoint`, `pinFix` | `walk/pin/ActionPinEstimatorTest.kt`, `walk/pin/ActionPinReplayTest.kt` |
| [walk/support/DiaryFixtures.kt](java/com/daengs/app/walk/support/DiaryFixtures.kt) — `diaryFixture` | `walk/diary/ServerDiaryBundleTest.kt`, `walk/diary/WalkDiaryReaderTest.kt`, `walk/sync/WalkDiarySyncTest.kt`, `ui/walk/WalkStoryboardScreenTest.kt` |
| 같은 `DiaryFixtures.kt` — `titledDiaryFixture` | `walk/diary/WalkDiaryTitleTest.kt`, `walk/diary/WalkDiaryReaderTest.kt`, `walk/store/WalkHistorySearchTest.kt`, `walk/sync/WalkStoryboardSyncTest.kt` |
| 같은 `DiaryFixtures.kt` — `sceneAnchorFixture` | `walk/diary/WalkSceneAnchoringTest.kt`, `walk/sync/WalkStoryboardSyncTest.kt`, `ui/walk/WalkDiaryMapScreenTest.kt` |
| [walk/support/WalkHistoryFixtures.kt](java/com/daengs/app/walk/support/WalkHistoryFixtures.kt) — `seedSearchWalk` | `walk/store/WalkHistorySearchTest.kt`, `ui/walk/WalkHistorySearchScreenTest.kt` |
| [territory/support/TerritoryFixtures.kt](java/com/daengs/app/territory/support/TerritoryFixtures.kt) — 식별자 상수, `MemoryActions`, `ClaimServer` | `territory/TerritoryActionSyncTest.kt`, `territory/ServerTerritoryPhotosTest.kt`, `territory/CertifiedTerritoryPresentationTest.kt`, `territory/TerritoryActionApiTest.kt`, `territory/TerritoryActionStoreTest.kt`, `territory/TerritoryPhotoApiTest.kt`; 공용 `territory/support/PhotoServer.kt`도 참조 |
| [territory/support/PhotoServer.kt](java/com/daengs/app/territory/support/PhotoServer.kt) — `PhotoServer` | `territory/ServerTerritoryPhotosTest.kt`, `territory/CertifiedTerritoryPresentationTest.kt`, `territory/TerritoryActionStoreTest.kt` |
| [territory/support/OwnedTerritoryFixtures.kt](java/com/daengs/app/territory/support/OwnedTerritoryFixtures.kt) — `OWNED_PET`, `OWNED_SITE`, `ownedJson` | `territory/owned/OwnedTerritoryApiTest.kt`, `territory/owned/OwnedTerritoryRepositoryTest.kt` |
| [territory/support/BookmarkFixtures.kt](java/com/daengs/app/territory/support/BookmarkFixtures.kt) — `BOOKMARK_SITE`, `bookmarkJson`, `mutationJson` | `territory/bookmarks/TerritoryBookmarkApiTest.kt`, `territory/bookmarks/TerritoryBookmarkRepositoryTest.kt`, `ui/game/bookmarks/TerritoryBookmarkActionTest.kt`, `ui/game/bookmarks/TerritoryBookmarkControllerTest.kt`, `ui/game/bookmarks/TerritoryBookmarksScreenTest.kt` |
| [activity/support/ActivityFixtures.kt](java/com/daengs/app/activity/support/ActivityFixtures.kt) — `ActivityFixtures` | `activity/ActivityApiTest.kt`, `activity/ActivityRepositoryTest.kt` |
| 이미 분리된 `place/FacilityFixtures.kt` — `facilityQuery`, `facilityJson`, `facilityResponse` | `place/FacilityApiTest.kt`, `ui/places/FacilityViewModelTest.kt`, `ui/places/FacilitySearchCoordinatorTest.kt`, `ui/places/FacilitySearchPanelTest.kt`, `ui/places/FacilityConnectedUiTest.kt` |
| [place/support/ConversationFixtures.kt](java/com/daengs/app/place/support/ConversationFixtures.kt) — `conversationFixture` | `place/FacilityConversationTest.kt`, `place/ConversationFiltersTest.kt`, `place/ConversationUndoTest.kt`, `ui/places/ConversationConnectedTest.kt`, `ui/places/ConnectedPlaceSearchUiTest.kt`; 같은 파일의 `filteredConversationFixture`도 참조 |
| 같은 `ConversationFixtures.kt` — `filteredConversationFixture` | `place/ConversationFiltersTest.kt`, `place/ConversationUndoTest.kt`, `ui/places/AppliedPlaceFiltersTest.kt`, `ui/places/ConversationConnectedTest.kt`, `ui/places/ConnectedPlaceSearchUiTest.kt` |

각 helper의 이름·가시성·인자·기본값·반환값과 fake 동작을 보존하고 소비자는 `support`에서 import한다.
일기 JSON은 테스트 클래스 대신 공용 파일의 `DiaryResources`를 기준으로 같은 절대 리소스 경로를 읽는다.
새 전역 가변 상태나 공통 테스트 부모 클래스는 만들지 않는다.

시나리오·기대값·DB/파일 정리 절차는 기존 테스트에 남아 있다. `PhotoServer`가 이미 수행하던
업로드 헤더·바이트 검증은 fake와 함께 보존했다. 핀 오차 검증 `assertNear`는 해당 테스트만 쓰므로
`ActionPinEstimatorTest.kt`에 남겼다. 이름이 비슷하다는 이유로 검사를 합치거나 삭제하지 않는다.

참조를 대조하면서 누락된 일기 소비자 `WalkStoryboardScreenTest`, `WalkStoryboardSyncTest`를 추가했다.
기존 표의 `WalkTerritoryUiTest`, `TerritoryRangePresentationTest`는 공유 식별자가 아니라
`MapPurpose.WALK`를 사용하므로 이 helper 검증 범위에서 제외했다.

### 추가 분리한 fixture의 검증 범위

대화 응답·장면 위치 근거·내 점령지·찜 fixture를 함께 변경할 때는 아래 16개 클래스를 선택한다.
`AppliedPlaceFiltersTest`는 `filteredConversationFixture`를 통해 대화 응답을 간접 사용하므로 포함한다.
한 묶음만 변경하면 해당 제공자 행의 소비자를 선택하되, 다른 helper를 거친 소비자까지 포함한다.
대화 연결 화면도 검증하므로 기존 기능 플래그 `-PfacilityConversation=true`를 사용한다.

```powershell
$fixtureTests = @(
    'com.daengs.app.place.FacilityConversationTest'
    'com.daengs.app.place.ConversationFiltersTest'
    'com.daengs.app.place.ConversationUndoTest'
    'com.daengs.app.ui.places.AppliedPlaceFiltersTest'
    'com.daengs.app.ui.places.ConversationConnectedTest'
    'com.daengs.app.ui.places.ConnectedPlaceSearchUiTest'
    'com.daengs.app.walk.diary.WalkSceneAnchoringTest'
    'com.daengs.app.walk.sync.WalkStoryboardSyncTest'
    'com.daengs.app.ui.walk.WalkDiaryMapScreenTest'
    'com.daengs.app.territory.owned.OwnedTerritoryApiTest'
    'com.daengs.app.territory.owned.OwnedTerritoryRepositoryTest'
    'com.daengs.app.territory.bookmarks.TerritoryBookmarkApiTest'
    'com.daengs.app.territory.bookmarks.TerritoryBookmarkRepositoryTest'
    'com.daengs.app.ui.game.bookmarks.TerritoryBookmarkActionTest'
    'com.daengs.app.ui.game.bookmarks.TerritoryBookmarkControllerTest'
    'com.daengs.app.ui.game.bookmarks.TerritoryBookmarksScreenTest'
)
$testArgs = $fixtureTests | ForEach-Object { '--tests'; $_ }
.\gradlew.bat :app:testDebugUnitTest -PfacilityConversation=true @testArgs --no-daemon --max-workers=1 --console=plain
```

## 기준 시점에 확인한 결과

이 지도 작성에 앞선 2026-09-09 리뷰에서 `9bf3718`을 checkout하고 다음 명령을 실행했다.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.walk.store.WalkDaoTest' --tests 'com.daengs.app.walk.store.WalkMigrationTest' --tests 'com.daengs.app.walk.store.WalkPhotoStoreTest' --tests 'com.daengs.app.walk.sync.WalkPhotoSyncTest' --tests 'com.daengs.app.walk.sync.WalkEntryV2SyncTest' --tests 'com.daengs.app.walk.pin.ActionPinEstimatorTest' --tests 'com.daengs.app.walk.pin.ActionPinReplayTest'
```

| 클래스 | 실행 수 |
| --- | ---: |
| `WalkDaoTest` | 19 |
| `WalkMigrationTest` | 10 |
| `WalkPhotoStoreTest` | 7 |
| `WalkPhotoSyncTest` | 17 |
| `WalkEntryV2SyncTest` | 14 |
| `ActionPinEstimatorTest` | 29 |
| `ActionPinReplayTest` | 2 |

**합계 98 passed / 0 failed / 0 skipped.** Gradle의 Kotlin/Room KSP 컴파일도 성공했다.
컴파일에는 선택 밖 `WalkDiaryMapScreenTest`의 nullable `File.parentFile` 접근 경고가 있었다.
이 결과는 해당 7개 클래스의 검증이다. 위 기능별 선택표 전체·실기기·배포 서버 검증 결과가 아니다.

1단계 지도 작성에서는 Markdown만 변경하고 링크·경로·선택자를 정적으로 확인했다.
레포에 Actions workflow는 없으며 이 문서는 새 CI나 실행기를 추가하지 않는다.

## 2단계 공용 helper 분리 결과

2026-09-09, `dev`의 `9391671`과 1단계 문서를 합친 `9c8738e` 위에서 helper를 분리하고 검증했다.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.activity.ActivityApiTest' --tests 'com.daengs.app.activity.ActivityRepositoryTest' --tests 'com.daengs.app.territory.CertifiedTerritoryPresentationTest' --tests 'com.daengs.app.territory.ServerTerritoryPhotosTest' --tests 'com.daengs.app.territory.TerritoryActionApiTest' --tests 'com.daengs.app.territory.TerritoryActionStoreTest' --tests 'com.daengs.app.territory.TerritoryActionSyncTest' --tests 'com.daengs.app.territory.TerritoryPhotoApiTest' --tests 'com.daengs.app.ui.walk.WalkHistorySearchScreenTest' --tests 'com.daengs.app.ui.walk.WalkStoryboardScreenTest' --tests 'com.daengs.app.walk.diary.ServerDiaryBundleTest' --tests 'com.daengs.app.walk.diary.WalkDiaryReaderTest' --tests 'com.daengs.app.walk.diary.WalkDiaryTitleTest' --tests 'com.daengs.app.walk.pin.ActionPinEstimatorTest' --tests 'com.daengs.app.walk.pin.ActionPinReplayTest' --tests 'com.daengs.app.walk.store.WalkHistorySearchTest' --tests 'com.daengs.app.walk.sync.WalkDiarySyncTest' --tests 'com.daengs.app.walk.sync.WalkStoryboardSyncTest' --console=plain
```

| 범위 | 클래스 수 | 통과 수 |
| --- | ---: | ---: |
| 핀·일기·검색과 소비 화면 | 10 | 78 |
| Territory 동기화·저장·HTTP·표현 | 6 | 32 |
| Activity HTTP·저장소 | 2 | 18 |
| 합계 | 18 | 128 |

**128 passed / 0 failed / 0 skipped.** XML 결과의 클래스 목록이 위 선택 목록과 정확히 일치한다.
Kotlin/Room KSP 컴파일도 성공했다. 변경 전후의 18개 테스트 본문과 7개 공용 코드 블록을
대조해 입력·기대값·fake 동작·정리 절차가 보존됐음을 확인했다. 바뀐 참조는 import/호출 경로와
일기 리소스 조회 기준 클래스다. JSON 리소스·앱 실행 코드·Gradle 설정은 바꾸지 않았다.

컴파일에는 기존 `ChatScreen`의 deprecated clipboard API와 `WalkDiaryMapScreenTest`의
nullable `File.parentFile` 경고가 있었다. 전체 테스트·APK·실기기·배포 서버 검증은 이번 범위에 없다.

## 갱신 기준

- 테스트 또는 helper를 추가·이동하면 기능별 선택 범위와 제공자/소비자 표도 갱신한다.
- 저장소 동작을 바꾸면 이를 단순화한 fake가 어디까지 검증하는지 다시 확인한다.
  실제 저장·복구·전송 계약은 관련 Room 테스트와 묶는다.
- 결과에는 기준 커밋·명령·실행 수·실패·skip·미검증 범위를 적는다.
- 대응 테스트가 없으면 검증 공백으로 남긴다. 전체 실행이나 새로운 설치 도구로 대신하지 않는다.

## 전봇대 객체 재사용

`map/provider/naver/TerritoryOverlayStoreTest`는 ID별 본체 유지·변경분 반영·지도 수명 정리와 효과 취소를 SDK handle 대역으로 검증한다. 이 경계를 바꾸면 `TerritoryPoleArtTest`와 `TerritoryBoardPresentationTest`를 함께 선택한다. 실제 네이버 SDK의 클릭·범위 원·네 가지 색상은 Debug `TerritoryPerformanceLabActivity`와 `TerritoryPoleLabActivity`에서 확인한다. 가상 장소 측정 방법과 지표 한계는 [전봇대 성능 검증](../../../docs/territory-overlay-performance.md)에 있다. 전체 테스트로 확대하지 않는다.

### 전봇대 효과 대상 갱신 (2단계)

store의 현재/이전 대상·새 범위 원 초기화·프레임 목록 재사용을 바꾸면 `TerritoryOverlayStoreTest`와 유한 애니메이션 소비자인 `ui.walk.TerritoryFeedbackUiTest`만 선택한다. 이미지와 회원 소유 매핑 변경이 없으면 1단계의 이미지·보드 테스트를 반복할 필요가 없다. Debug 비교의 handleFrames는 FPS가 아닌 처리 대상 호출 수다.
