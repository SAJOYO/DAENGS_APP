# 산책 상세 통합 검토 (#367)

기준: `dev@0bda173a` (#365 포함). 데이터 연결(#358), 진행 상태(#361), 일기 조립(#362),
편집 대상/대화상자(#363)를 열람·복원 기준(#365)과 함께 검토했다.

## 확인한 결함과 수정

새 메모를 Room에 저장한 뒤 전달 작업 예약이 실패하면, 기존 코드는 편집창에 저장 실패를
표시했다. 같은 입력으로 다시 저장하면 이미 만들어진 행과 이전 입력의 버전이 맞지 않아
충돌할 수 있었다. 실제 `StoredWalkDetailData`·Room·`WalkDetailState`를 연결한 회귀 검사에서
DB에 메모가 존재하는데 편집 완료 콜백이 0회인 것을 먼저 재현했다.

`WalkDetailDeliveryPending`은 변경이 DB에 확정된 뒤 예약만 실패했을 때 발생한다.
상태 담당은 현재 계정·작업 세대를 검사한 뒤 편집을 완료하고, 기존 화면 오류/재시도로
전달 예약을 다시 실행한다. 저장 내용·mutation·삭제 표식을 재작성하지 않는다.
저장 자체의 실패/편집 충돌은 기존처럼 초안을 유지하며, 취소나 이전 로그인 작업은
완료로 취급하지 않는다. 사진/장면 저장과 서버 계약은 변경하지 않았다.
산책 삭제가 확인되면 남은 전달 안내도 지운다. 재시도 중 뒤늦게 발생한 예약 실패가
삭제/접근 불가 화면을 다시 오류 화면으로 바꾸지 않도록 현재 읽기의 존재를 검사한다.

## 검토한 경계

| 경계 | 확인한 보호 |
|---|---|
| 화면 진입 | 산책 ID와 로그인 generation으로 composition과 데이터 수명을 구분 |
| 조회와 탐색 | 준비된 읽기와 explorer 채택을 같은 Snapshot에서 반영, 늦은 조회/장면 결과 차단 |
| 삭제와 작업 완료 | 작업 epoch 증가와 취소, 이전 완료 콜백 및 열린 편집창 해제 |
| 일기 조회와 조립 | 계정/종료/존재 검사는 Reader, 공개 정책과 사용자 수정 계산은 Assembly |
| 기록 편집 | 저장 전 동시 요청 차단, 원본 위치/방문 시각과 편집 버전 검사 유지 |
| 재생과 복원 | #365의 현재 범위 유지; 재생 시간 주소나 영속 열람 위치를 새로 구현하지 않음 |

## 로컬 검증

관련 22개 클래스 145개 테스트 통과(실패/오류/건너뜀 0).
저장/삭제의 예약 실패, 재시도 시 동일 행 유지, 취소와 로그인 교체 회귀를 포함한다.
기존 조립/Reader/공개 수명/장면 위치/화면 상태/열람 조합/지도 이동/디자인 잠금 검사를 함께 실행했다.
마지막 삭제 안내 보완 뒤 상태·저장소·상세 UI·읽기·디자인 잠금 6개 클래스 49개를 다시 실행해
모두 통과했다. 새 삭제 사례 2개를 포함하여 중복을 제외한 검증 케이스는 147개다.

## 파일 DB와 실기기 검증 방법

`tools/naver-map-review.init.gradle`의 `.locationreview` 앱에서만 파일 DB 모드를 사용한다.
`DiaryEditorReviewActivity`의 `persistent=true`는 제품 `WalkDatabase.open()`을 사용한다.
합성 기록 초기화는 첫 준비 단계의 명시적 `reset=true`에만 실행한다. 이후 재개방·계정 교체·
기록 삭제·프로세스 재시작에는 초기화하지 않는다. 사진도 이 패키지의 files 디렉터리에 보관한다.

`DiaryPersistenceDeviceTest`는 두 단계로 실행한다. 첫 단계는 실제 편집기로 메모/장면 저장과
예약 실패 재시도를 실행하고, Activity 종료로 Room을 닫은 뒤 새로 열어 저장 내용과 사진 바이트를
대조한다. 사진을 삭제한 뒤 체크포인트를 남긴다. 외부에서 앱을 강제 종료하고 두 번째 단계를
실행하면 프로세스 식별자가 달라야 하며, 같은 메모·mutation·장면과 사진 삭제 상태가 보여야 한다.
다른 계정에서의 비공개, 화면 재생성 시 편집창 해제, 삭제 후 재개방도 검사한다.

```powershell
adb shell am instrument -w -e class com.daengs.app.ui.walk.review.DiaryEditorDeviceTest com.daengs.app.locationreview.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class com.daengs.app.ui.walk.review.DiaryPersistenceDeviceTest#prepareEditsAndReopenDatabase com.daengs.app.locationreview.test/androidx.test.runner.AndroidJUnitRunner
adb shell am force-stop com.daengs.app.locationreview
adb shell am instrument -w -e class com.daengs.app.ui.walk.review.DiaryPersistenceDeviceTest#verifyEditsAfterProcessRestart com.daengs.app.locationreview.test/androidx.test.runner.AndroidJUnitRunner
```

## 로그인된 앱과 서버 검증 방법

사용자가 허용한 기존 개발용 앱 `.devtest`의 서명·공개 클라이언트 설정을 확인하고 로그인 상태를
유지한다. `tools/walk-detail-live-review.init.gradle`은 이 패키지에만 검증 소스를 추가한다.
API·카카오·지도 설정은 로컬 환경 변수로 주입하며 저장소에 넣지 않는다. 토큰은 앱의
`SessionProvider` 안에서만 사용한다.

`WalkDetailLiveDeviceTest`는 `allowLiveNote=true`가 있어야 실행된다. 새 산책을 만들지 않고,
기존 완료 산책에 UUID로 식별되는 임시 메모 한 건만 추가한다. 별도 메모리 Room에
`StoredWalkDetailData`와 실제 `WalkEntrySync`를 연결하여 예약 실패 → 재시도 → 서버 ACK →
HTTP 재조회 → 새 로컬 DB로 복원 → 서버 삭제를 검증한다. 기존 앱 DB의 메모는 수정하지 않는다.
실패 시에도 해당 메모만 정리하며, 정리가 끝나지 않으면 앱 안의 cleanup ticket을 유지한다.
이는 WorkManager의 실제 지연·재기동 검증과 구별한다.

필요한 로컬 환경 변수는 `DAENGS_REVIEW_API_BASE_URL`, `DAENGS_REVIEW_KAKAO_KEY`,
`DAENGS_NAVER_NCP_KEY_ID`와 기존 앱에 설정된 경우 `DAENGS_REVIEW_GAIT_URL`,
`DAENGS_REVIEW_MAP_STYLE`이다. 기존 개발 앱과 동일한 설정을 사용한다.

```powershell
.\gradlew.bat -I tools/walk-detail-live-review.init.gradle :app:assembleDebug :app:assembleDebugAndroidTest -PslimAbi=arm64-v8a
adb shell am instrument -w -e allowLiveNote true -e class com.daengs.app.walk.detail.WalkDetailLiveDeviceTest com.daengs.app.devtest.test/androidx.test.runner.AndroidJUnitRunner
```

## 실행 결과와 한계

- Samsung SM-S931N / Android 16(API 36), USB ADB 정상 인증.
- `.locationreview`: 기존 편집 시나리오 4개와 파일 DB 준비/재개방 1개 통과(16.614초).
  외부 force-stop으로 PID가 사라진 것을 확인하고 새 프로세스에서 복원/삭제 검사 1개 통과(6.011초).
- `.devtest`: 기존 설치와 인증서가 같은 것을 확인한 뒤 `install -r -d`로 설치했다.
  새 소스 빌드의 기본 versionCode(1)가 기존 검토판(6)보다 작아 개발 패키지에만 `-d`를 사용했다.
  일반 배포 앱 `.app`은 교체하지 않았고 개발 앱의 로그인/기존 기록은 유지됐다.
- 실제 서버 왕복 1개 검사 통과(1.298초). UUID 임시 메모의 ACK·HTTP 내용 재조회·새 DB로 복원·
  삭제 응답과 삭제 후 재조회를 확인했다. cleanup ticket도 제거됐다.
- 최신 개발 앱의 일반 진입에서 기존 산책 3건과 실제 네이버 지도·장면·서랍을 확인했다.
  16배속 재생 중 백그라운드 왕복 뒤 03:56/05:11 시점에서 배속을 유지한 일시정지 상태를 확인했다.
- 두 설치 APK 모두 기기에서 다시 꺼내 로컬 APK와 SHA-256을 대조했다.
  파일 DB 검증 APK: `BF25F26241CD6D014B9DB52B5836911ECB43466C8424DB0B4B5BBEDEDC56741B`.
  최종 제품 코드와 서버 검증 APK: `F9D9D475A527FBC21867D071D181D73B45C7F66C83D90A45EF63EE80D1FB4E82`.
- Debug APK/AndroidTest 빌드와 최종 Release Kotlin 컴파일 통과. Release는 컴파일 확인이며
  서명된 배포 APK나 출시 서버 설정 검증을 뜻하지 않는다.
- 파일 DB 검증 뒤 추가한 삭제 시 안내 해제는 최종 개발 앱에 포함했다. 관련 상태·저장소·상세 UI
  등 로컬 49개 검사를 추가 실행해 통과했다. 앞의 실기기 6건을 이 마지막 보완 뒤 재실행한 것으로 보고하지 않는다.
- WorkManager의 실제 네트워크 대기/프로세스 재기동, 영속 열람 주소 복원, 긴 실측 산책의 모든
  공백/왕복 유형은 이번 결과에 포함하지 않는다. 제품 코드 변경은 위 저장/예약 실패 경계에 한정한다.
