# 기능별 테스트 실행 지도

기준은 2026-09-09 `dev`의 `9bf37188bab0841ac7b682dec1714ca58c893b2f`다.
이 문서는 변경한 기능에 맞는 테스트를 고르는 지도다. 전체 통과 보고서가 아니다.
조사 시점에는 Kotlin 테스트 파일 221개에 `@Test` 선언 1,528개가 있었고,
별도 공용 파일은 `place/FacilityFixtures.kt` 1개였다. 선언 수는 실제 실행 결과와 구분한다.

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

표의 각 선택자를 `--tests '선택자'`로 붙인다. 테스트 파일은 이 문서 아래
`java/com/daengs/app/`에 있고, 앱 코드의 같은 패키지와 연결된다.

| 기능 | 해당 기능 전체의 선택자 | 계약을 함께 확인할 소비자 |
| --- | --- | --- |
| 인증·시작·로그인 화면 | `com.daengs.app.auth.*`, `com.daengs.app.ui.startup.*`, `com.daengs.app.ui.landing.*`, `com.daengs.app.ui.nickname.*`, `com.daengs.app.ui.my.*` | 계정 전환·탈퇴 변경은 아래 계정 경계 표의 저장·전송 검증 추가 |
| 반려견 프로필·사진 | `com.daengs.app.pet.*`, `com.daengs.app.ui.pet.*` | 장소 조건은 `com.daengs.app.ui.places.PlaceDogContextTest`, `com.daengs.app.ui.places.PlaceProfilesTest`; 홈 진입은 `com.daengs.app.ui.home.PetGateTest` |
| 장소 검색·자연어 시설 검색 | `com.daengs.app.place.*`, `com.daengs.app.ui.places.*`, `com.daengs.app.map.features.places.*`, `com.daengs.app.map.layers.places.*` | 챗봇 제안은 `com.daengs.app.assistant.PlaceSuggestionsTest`, `com.daengs.app.ui.chat.PlaceSuggestionCardTest`; 경로 인계는 Journey |
| Journey·지도 인계 | `com.daengs.app.journey.*`, `com.daengs.app.map.features.journey.*` | 장소 선택·상태 복귀를 바꾸면 `com.daengs.app.ui.places.PlaceSessionCoordinatorTest` |
| 산책 코어·GPS·기록 | `com.daengs.app.walk.*`, `com.daengs.app.location.*` | 화면은 `com.daengs.app.ui.walk.*`; 지도 표현은 다음 행. 핀·사진·일기만 바꾸면 아래 좁은 묶음 사용 |
| 산책 지도·스타일·공통 지도 상태 | `com.daengs.app.map.style.*`, `com.daengs.app.map.shell.*`, `com.daengs.app.map.layers.trail.*`, `com.daengs.app.map.layers.completedroute.*`, `com.daengs.app.map.layers.stays.*` | 속도·GPS 안내·완료 경로는 `com.daengs.app.ui.walk.WalkSpeedometerTest`, `com.daengs.app.ui.walk.WalkGpsPresentationTest`, `com.daengs.app.ui.walk.WalkCompletedRoutePresentationTest` |
| 전봇대 점령·사진 인증·활동 | `com.daengs.app.territory.*`, `com.daengs.app.activity.*`, `com.daengs.app.map.features.territory.*`, `com.daengs.app.map.layers.territory.*` | 화면 상태는 `com.daengs.app.ui.walk.WalkViewModelTest`, `com.daengs.app.ui.walk.WalkTerritoryUiTest`, `com.daengs.app.ui.walk.Territory*` |
| 대화·어시스턴트·돌봄 | `com.daengs.app.chat.*`, `com.daengs.app.assistant.*`, `com.daengs.app.care.*`, `com.daengs.app.ui.chat.*`, `com.daengs.app.ui.storage.*` | 장소·산책 제안 계약은 해당 기능 소비자도 확인. 서버 모델의 답변 품질은 별도 범위 |
| 보행·피부 진단 응답 | `com.daengs.app.gait.*`, `com.daengs.app.screening.*` | 촬영/가이드 표현은 `com.daengs.app.ui.chat.GuideFrameScreenTest`, `com.daengs.app.ui.chat.CropGeometryTest`. 실제 진단 모델 정확도는 검증하지 않음 |
| 홈·미니룸·카드·도감·공통 UI | `com.daengs.app.miniroom.*`, `com.daengs.app.dogcard.*`, `com.daengs.app.ui.home.*`, `com.daengs.app.ui.dogcard.*`, `com.daengs.app.ui.dex.*`, `com.daengs.app.ui.theme.*`, `com.daengs.app.ui.AvatarSourceTest` | 카드 DB 변경은 `com.daengs.app.dogcard.store.CardMigrationTest`; 그림·애니메이션은 실기기 확인도 필요 |

`ExampleUnitTest`의 덧셈 예제는 제품 기능 검증으로 세지 않는다.

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
v2 동기화의 임시 v1 사례는 구서버 대응 계약이므로 v2 테스트와 이름이 비슷해도 삭제하지 않는다.
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

parser/표현 모델은 `walk.diary`, 서버 반영은 세 sync 클래스, 캐시·검색은 저장소와 reader가 담당한다.
화면만 바꾸면 해당 UI 클래스부터 선택하고, 일기 format·사진/핀 연결·무효화 규칙을 바꾸면
아래 묶음을 선택한다.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.walk.diary.*' --tests 'com.daengs.app.walk.sync.WalkDiarySyncTest' --tests 'com.daengs.app.walk.sync.WalkStoryboardSyncTest' --tests 'com.daengs.app.walk.sync.StoryboardPinSyncTest' --tests 'com.daengs.app.walk.store.WalkHistorySearchTest'
```

UI 소비자는 `com.daengs.app.ui.walk.WalkDiaryMapScreenTest`,
`com.daengs.app.ui.walk.WalkStoryboardScreenTest`, `com.daengs.app.ui.walk.WalkHistorySearchScreenTest`다.
JSON fixture·제목·검색 seed를 바꾸면 아래 공용 helper 표의 소비자도 함께 선택한다.

## Room·계정·외부 의존성

| 변경 경계 | 확인 범위와 실행 조건 |
| --- | --- |
| Walk DB migration | `WalkMigrationTest`와 바뀐 표의 Store/Dao 테스트. 실제 SQLite 파일을 옛 스키마에서 Room 최신 버전으로 열어 검증. `app/schemas/com.daengs.app.walk.store.WalkDatabase/` JSON과 생산 코드의 migration 사용 |
| 계정 전환·탈퇴 | 인증은 `auth`/`ui.startup`; 저장·삭제는 `WalkHistoryTest`, `WalkPhotoStoreTest`, `ActionPinStoreTest`; 늦은 ACK는 `WalkPhotoSyncTest`, `WalkEntryV2SyncTest`. 변경한 경계에 해당하는 클래스를 함께 선택 |
| Room 테스트의 DB | JVM의 Robolectric과 Room/SQLite를 사용. 테스트가 메모리 DB 또는 임시 DB 파일을 만들고 닫는다. 개발 서버 DB 주소·별도 PostgreSQL·Docker 설치는 필요 없음 |
| HTTP 계약 | 주입한 transport/서버 대역 또는 로컬 HTTP 서버로 요청·응답을 확인. 테스트 통과가 배포 서버의 route 존재·인증·capability 활성화를 보장하지 않음 |
| Compose | `src/test`의 Robolectric에서 실행. 현재 66개 파일이 Robolectric runner를 쓰며 이 중 29개가 Compose test rule 사용. 순수 Kotlin과 같은 Gradle task에 있으므로 필요한 UI 클래스만 선택 |
| 앱 초기화 | [robolectric.properties](resources/robolectric.properties)가 `android.app.Application`을 사용. 카카오 SDK·실제 앱 초기화가 성공했다는 뜻이 아님 |
| 실기기 | `src/androidTest`는 현재 패키지명 확인 예제 1개. GPS 수신·절전/FGS·강제 종료·Naver 지도·실제 CameraX 촬영은 해당 기능의 실기기 검증 기록 필요 |

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.walk.store.WalkMigrationTest' --tests 'com.daengs.app.walk.store.WalkDaoTest' --tests 'com.daengs.app.walk.store.WalkPhotoStoreTest'
```

Room 준비 코드를 정리할 때는 계정·시계·세션·임시 파일과 `@Before`/`@After`의 수명을 유지한다.
메모리 DB 테스트를 서버 DB 테스트로 바꾸지 않는다.

## 공용 helper 제공자와 소비자

현재 테스트 파일 7곳이 다른 테스트에 helper를 제공한다. 아래 경로는
`java/com/daengs/app/` 기준이며 제공 파일 자체의 테스트도 검증 범위에 포함한다.
Kotlin은 같은 패키지의 함수를 import 없이 참조할 수 있으므로 import 문 검색만으로는 부족하다.

| 현재 제공 파일 / 공유 항목 | 다른 소비 테스트 |
| --- | --- |
| `walk/pin/ActionPinEstimatorTest.kt` — `PIN_TAP`, `pinRequest`, `pinPoint`, `pinFix` | `walk/pin/ActionPinReplayTest.kt` |
| `walk/diary/ServerDiaryBundleTest.kt` — `diaryFixture` | `walk/diary/WalkDiaryReaderTest.kt`, `walk/sync/WalkDiarySyncTest.kt` |
| `walk/diary/WalkDiaryTitleTest.kt` — `titledDiaryFixture` | `walk/diary/WalkDiaryReaderTest.kt`, `walk/store/WalkHistorySearchTest.kt` |
| `walk/store/WalkHistorySearchTest.kt` — `seedSearchWalk` | `ui/walk/WalkHistorySearchScreenTest.kt` |
| `territory/TerritoryActionSyncTest.kt` — 식별자 상수, `MemoryActions`, `ClaimServer` | `territory/ServerTerritoryPhotosTest.kt`, `territory/CertifiedTerritoryPresentationTest.kt`, `territory/TerritoryActionApiTest.kt`, `territory/TerritoryActionStoreTest.kt`, `territory/TerritoryPhotoApiTest.kt`, `ui/walk/WalkTerritoryUiTest.kt`, `ui/walk/TerritoryRangePresentationTest.kt` |
| `territory/ServerTerritoryPhotosTest.kt` — `PhotoServer` | `territory/CertifiedTerritoryPresentationTest.kt`, `territory/TerritoryActionStoreTest.kt` |
| `activity/ActivityApiTest.kt` — `ActivityFixtures` | `activity/ActivityRepositoryTest.kt` |
| 이미 분리된 `place/FacilityFixtures.kt` — `facilityQuery`, `facilityJson`, `facilityResponse` | `place/FacilityApiTest.kt`, `ui/places/FacilityViewModelTest.kt`, `ui/places/FacilitySearchCoordinatorTest.kt`, `ui/places/FacilitySearchPanelTest.kt`, `ui/places/FacilityConnectedUiTest.kt` |

2단계는 위 7곳의 공용 준비 코드 분리다. 입력을 만드는 방법은 공유하고,
시나리오·assertion·기대값은 각 테스트가 소유한다. 이름이 비슷하다는 이유로 검사를 합치거나
삭제하지 않는다. 각 함수의 인자·기본값·반환값과 기존 호출부를 보존한다.

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

지도 작성 단계에서는 Markdown만 변경하고 링크·경로·선택자를 정적으로 확인한다.
레포에 Actions workflow는 없으며 이 문서는 새 CI나 실행기를 추가하지 않는다.

## 갱신 기준

- 테스트 또는 helper를 추가·이동하면 기능별 선택 범위와 제공자/소비자 표도 갱신한다.
- 저장소 동작을 바꾸면 이를 단순화한 fake가 어디까지 검증하는지 다시 확인한다.
  실제 저장·복구·전송 계약은 관련 Room 테스트와 묶는다.
- 결과에는 기준 커밋·명령·실행 수·실패·skip·미검증 범위를 적는다.
- 대응 테스트가 없으면 검증 공백으로 남긴다. 전체 실행이나 새로운 설치 도구로 대신하지 않는다.
