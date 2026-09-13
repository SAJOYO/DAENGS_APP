# 측정 동선 실기기 검증 — 6단계

[APP #374](https://github.com/SAJOYO/DAENGS_APP/pull/374)는 #372가 병합된 dev `a2ef1d6`에서 시작했다. 제품 동작을 바꾸지 않고, Android의 실제 HTTP·Room·Compose 상세와 프로세스 재시작을 반복 검사하는 opt-in 도구를 추가한다.

## 확인한 범위

2026-09-13, Galaxy S25 / Android 16(API 36), arm64 실기기에서 검증했다. 지도 검증 패키지 `com.daengs.app.locationreview`의 APK와 테스트 APK 설치 출력 `Success`를 직접 확인했다.

| 검사 | 결과 |
| --- | --- |
| 백엔드 고정 응답의 HTTP 수신 → 검증 → Room 저장 → 일반 상세 | 통과 |
| 새 프로세스에서 측정 캐시를 오프라인으로 다시 검증·열람 | 통과 |
| 장면 선택·펼친 서랍·본문 스크롤 복원 | 통과, 저장 JSON뿐 아니라 화면 스크롤 semantics 대조 |
| 선택한 시간 범위·탐색 탭 복원 | 통과, 원본 기기 시간 주소 및 실제 범위 컨트롤 확인 |
| 재생 위치·8배속 복원 | 통과, 표시 시간 대조 및 일시정지 상태 확인 |
| 기존 개발 앱 DB 사본의 Room 18→19→20 | 통과, 기존 모든 `walk_*` 테이블 행 지문 유지 및 입력 사본 불변 |
| 공유 개발 서버의 인증된 capabilities 조회 | 유효한 로그인 세션을 얻지 못해 미확인. 비인증 요청은 HTTP 401 |

4단계 UI 검사는 외부 runner가 각 단계 사이 패키지를 force-stop한다. 단계마다 다른 프로세스 UUID인지 테스트 안에서 확인하고, 검증 단계는 데이터를 다시 심거나 HTTP를 호출하지 않는다. 저장 완료 후 Activity를 닫고 프로세스를 바꾸는 검사이며, 기록 중 갑작스러운 전원 종료나 저장 전 미완료 동작의 보존을 증명하지 않는다.

기존 개발 앱에는 완료 기록 3건이 있었고, recording epoch·motion backup·precision 증거는 없었다. 이 기록을 측정 입력으로 승격하지 않았다. 개인 기록 DB는 로컬 비공개 사본으로만 업그레이드했으며 원래 앱 APK·DB는 교체하지 않았다. 좌표·계정·기기 식별자·토큰은 이 문서와 커밋에 포함하지 않는다.

## 합성 검사 실행

[`tools/walk-measurement-review.init.gradle`](../tools/walk-measurement-review.init.gradle)를 기존 지도 검증 init과 함께 사용한다. 네이버 키 설정은 [지도 검증 도구](../tools/naver-map-review/README.md)를 따른다. 별도 로그인이 필요 없다.

```powershell
.\gradlew.bat -I tools/naver-map-review.init.gradle -I tools/walk-measurement-review.init.gradle :app:assembleDebug :app:assembleDebugAndroidTest -PslimAbi=arm64-v8a --console=plain
& $adbPath -s $deviceSerial install -r app/build/outputs/apk/debug/app-debug.apk
& $adbPath -s $deviceSerial install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
uv run tools/run_measurement_device_review.py --adb $adbPath --serial $deviceSerial --output <비공개-로그-폴더>
```

설치 두 번의 `Success`, runner의 네 줄 `PASS`를 확인한다. 셸 종료 코드만 성공으로 판정하지 않는다. 첫 단계는 검증 전용 `measurement-device.db`만 초기화한다. 기존 지도 편집 검증 DB와 로그인 앱의 DB는 건드리지 않는다. 뒤 세 단계는 기존 DB와 체크포인트만 읽어 시작한다.

`high-speed-reentry`의 원본 IEEE 좌표·epoch를 기기에 저장하고, 기존 백엔드 golden의 summary/청크 바이트를 **기기 loopback HTTP 서버**로 전달한다. `WalkHttpApi`, `WalkMeasurementSync`, `StoredWalkDetailData`, `WalkDiaryMapForAccount`, 네이버 SDK는 제품 코드다. HTTP 서버는 고정 응답을 전송하므로 실제 백엔드의 기록 업로드·계산·영속화를 실행한 결과와 구별한다. 측정의 보행 제외 관측과 시간축이 실제 상세에 존재하는지도 검사한다.

합성 데이터는 opt-in 빌드에만 포함된다. 일반 앱 APK가 필요하면 init 없이 다시 빌드한다.

일반 `assembleDebug`와 `WalkExploration*`, `WalkMeasurementTest`, `WalkMigrationTest`, `DesignLockTest` **41개**도 통과했다(실패/오류/skip 0). 일반 APK의 assets와 DEX에 검증 입력 및 `MeasurementReviewActivity`가 없는 것을 확인했다.

## 기존 기록 업그레이드 검사

`LegacyMeasurementMigrationDeviceTest#copiedLegacyDatabasePreservesEveryExistingRow`는 명시적으로 공급한 `files/legacy-review-input.db`(Room 18, WAL 반영을 마친 일관된 사본)를 요구한다. `legacy-measurement-review.db`만 초기화하고 복사·업그레이드한다. 입력 사본과 기존 모든 테이블의 행 지문을 전후 대조하며 좌표/본문을 출력하지 않는다. 파일이 없으면 실패하므로 일반 네 단계 runner에는 넣지 않았다. 실제 기록을 테스트 assets에 추가하지 않는다.

## 로그인 앱의 서버 기능 조회

```powershell
# 앱 APK를 덮어쓰지 않고 테스트 APK만 빌드·설치한다.
.\gradlew.bat -I tools/walk-measurement-live-read.init.gradle :app:assembleDebugAndroidTest -PslimAbi=arm64-v8a --console=plain
& $adbPath -s $deviceSerial install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& $adbPath -s $deviceSerial shell am instrument -w -r -e class com.daengs.app.walk.sync.MeasurementCapabilitiesDeviceTest com.daengs.app.devtest.test/androidx.test.runner.AndroidJUnitRunner
```

이 검사는 로그인된 개발 앱에 저장된 유효한 access 세션과 **설치된 앱의** API 주소를 사용한다. capabilities에는 GET만 보내며 직접 토큰을 갱신하지 않는다. 만료됐다면 개발 앱을 열어 통상적인 로그인/갱신을 완료하고 다시 실행한다. 인증은 앱 안에 남고 결과의 지원 여부/버전만 출력한다. 앱과 테스트의 서명이 같아야 한다. 서명이 다르면 앱을 제거하거나 데이터를 지우지 않는다.

## 아직 확인하지 않은 범위

- 유효한 개발 로그인으로 서버 기능 협상 및 실제 측정 생성/조회 확인.
- 등록·선택된 강아지와 새 야외 산책을 기록한 뒤 원본·precision 업로드부터 측정 열람까지 종단 확인.
- 네트워크 단절 후 업로드 재개, 기록 중 프로세스 종료, 계정 전환까지 실제 서버와 이어지는 시나리오.
- 지도 가독성·손가락 조작감에 대한 사용자 판단. 이번 검사는 디자인 승인이나 모든 Android 버전 검증을 대신하지 않는다.

운영/main 반영과 공유 DB 마이그레이션은 별도 합의한다.
