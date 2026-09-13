# 산책 열람 시안 02 — 통합 마감

2026-09-14, #393. #387(골격), #389(장면), #390(탭 분리), #391(시간 탐색)를 합친 4단계다.
dev 기준 소비 화면을 대상으로 하며 main/운영 반영과 실제 서버 인증·업로드는 별도다.

## 발견해서 수정한 것

- 기존 1.3배 Preview/테스트는 테마가 `fontScale = 1f`로 덮어써서 실제 확대를 검사하지 않았다.
  `DiaryReviewTheme`이 요청한 배율을 테마 뒤에 주입한다. 통합 검사는 `TextLayoutResult`의
  실제 배율도 확인한다. 앱 전체의 고정 배율 정책은 그대로다.
- 시스템이 어두운 모드이면 자동 시스템 바 설정이 밝은 산책 열람 화면 위에 흰 아이콘을 그렸다.
  `DiaryReadingSystemBars`가 열람 중에는 어두운 상태/내비게이션 아이콘을 쓰고, 화면을 벗어나면
  각 바의 이전 설정을 복원한다. 화면의 색상이나 다른 화면의 정책을 바꾸지 않는다.
- 검증 Activity에 본 앱과 같은 `enableEdgeToEdge`와 시스템 바 여백을 적용했다.
  사진 창은 별도 Window이므로 캡처 전에 해당 패키지가 실제 포커스를 가진 것을 확인하고
  Window 애니메이션이 끝난 뒤 촬영한다. 지도 표식은 SDK에 실제 붙은 overlay 좌표로 터치한다.
- 이전 큰 요약 카드를 사용하던 지도 안내 테스트를 현재 앱 코드의 `compact = true` 요약과 맞췄다.
  시간 조작 아래 읽기 공간 및 뒤늦은 안내로 높이가 달라지지 않는 기존 검사는 유지했다.

## 화면 대조

작업 공간의 `design/walk-diary-v2-final-review.html`에서 원안과 앱 목록·상세·구간·재생을 나란히
볼 수 있다. 실제 기기 캡처와 폭/배율 선택이 가능한 108개 상태 렌더도 포함한다.
이미지는 `.tooling/reading-final-renders/`, `.tooling/reading-final-device/`에 있다.
이 HTML과 캡처는 작업 공간 산출물이며 APK 리소스가 아니다.

| 기준 | 확인 결과 |
| --- | --- |
| 상단/지도 도구 | 날짜·참여견/제목·요약 순서, 보행/전체 전환 옆 범례를 유지한다. 미확인 이름/날씨를 예시값으로 채우지 않는다. |
| 장면 목록/상세 | 원본 아이콘·번호·얇은 구분선, 본문과 보조 문구의 위계를 유지한다. 긴 제목/본문/주소와 사진 안내, 편집/삭제를 중간 높이에서 스크롤한다. |
| 탐색 | 모드 선택·재생 버튼·경과 시간·시간축·아래 경로 정보 순서다. 장면 제목은 탐색에 중복 표시하지 않는다. |
| 복구 상태 | 빈 목록·위치 없음·장면 로딩·갱신 오류·원격 사진·백업 재전송 표시와 조작을 확인한다. 준비 중 오류는 한 번만 표시한다. |
| 실제 지도 | 서울 합성 기록의 경로·번호/묶음·지도 도구·세 높이·사진을 확인한다. 실제 표식 터치 후 카메라와 중간 높이가 동일하다. |

시안의 가짜 지도·글자로 만든 아이콘은 앱의 Naver 지도·원본 종류 아이콘으로 대응한다.
실기기 캡처 위의 `편집 검증 · 합성 기록`은 검증 Activity의 표식이다.
사진 fixture는 32×32 회색 파일이어서 사진 내용의 미적 품질을 판단하는 자료가 아니다.
상태 렌더는 320×640, 360×800, 390×844dp에 각각 1배/1.3배, 18개 상태다.

## 재현 및 검사

- 일반 debug 빌드와 관련 25개 클래스 118개 검사 통과(실패·오류·skip 0).
- 108개 상태 렌더를 생성했고, 비교 HTML의 네 원안 프레임과 이미지 전환을 브라우저에서 확인했다.
- 실기기 구간/장면 복귀·제한 재생을 세 프로세스에서 검사하고 시간축/배속/커서 터치 검사도 통과했다. 총 9개 instrumentation 검사이며 실제 서버를 호출하지 않는다.
- 실기기 서울 지도/편집 5개 검사 통과. 상태 표시줄의 실제 어두운 아이콘, 지도 표식 선택, 사진 창을 최종 APK에서 재촬영했다.

`DiaryReadingMatrixTest`가 108개 이미지를 생성하고 배율·탭 분리·복구 조작·본문 스크롤을 확인한다.
기본 경로는 `app/build/outputs/diary-reading-matrix`; `DAENGS_READING_REVIEW_DIR`로 변경할 수 있다.

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest --tests '*DiaryReadingMatrixTest' --tests '*DiaryReadingSystemBarsTest' --tests '*DiaryReadingStyleTest' --tests '*WalkExplorerPanelUiTest' --tests '*WalkExplorerMapNoticeUiTest' --tests '*MeasurementTimeControlsTest' --tests '*WalkRangeContextUiTest' --tests '*WalkRouteExplorerStateTest' --tests '*RecordContextPresentationTest' --tests '*WalkExplorationPersistenceTest' --tests '*WalkReadingBaselineUiTest' --tests '*WalkDiaryCompactDrawerTest' --tests '*WalkRecordOverviewUiTest' --tests '*DesignLockTest' --tests '*ThemeColorLockTest' --tests '*DiarySceneRemovalUiTest' --tests '*DiaryScenePresentationTest' --tests '*WalkDiaryMapScreenTest' --tests '*WalkDiaryGapListTest' --tests '*WalkSessionDetailUiTest' --tests '*DiarySceneKindTest' --tests '*DiaryReadingShellUiTest' --tests '*WalkDiaryPhotoUiTest' --tests '*WalkRouteBackup*Test'
```

실기기는 기존 `com.daengs.app.locationreview`와 `.test`만 교체 설치한다.
두 init script를 함께 쓰면 서울 지도/편집 fixture와 measurement fixture를 동일 패키지에서 검증한다.
일반 앱이나 devtest 앱을 설치하지 않는다.

```powershell
./gradlew.bat -I tools/naver-map-review.init.gradle -I tools/walk-measurement-review.init.gradle :app:assembleDebug :app:assembleDebugAndroidTest -PslimAbi=arm64-v8a
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class com.daengs.app.ui.walk.review.DiaryEditorDeviceTest com.daengs.app.locationreview.test/androidx.test.runner.AndroidJUnitRunner
uv run tools/run_measurement_device_review.py --adb <adb경로> --serial <연결기기> --output <결과폴더> --range-context
adb shell am instrument -w -r -e class com.daengs.app.ui.walk.MeasurementDeviceTest#verifyExplorerActions com.daengs.app.locationreview.test/androidx.test.runner.AndroidJUnitRunner
```

Naver 키는 기존 로컬 설정/환경변수로 주입한다. 설치는 각각 `Success`, instrumentation은
JUnit `OK`를 확인해야 한다. `am instrument`의 프로세스 종료 코드만으로 성공을 판단하지 않는다.
measurement의 고정 입력은 (0, 0) 근처라 지도 배경이 바다다. 서울 fixture의 지도 대조와
분리해 구간 손잡이·커서·배속·복귀·세 프로세스 복원만 검증한다.

## 범위의 한계

실제 사용자 산책/로그인/서버 전달을 검증한 결과는 아니다. Room과 실제 앱 소비 경로에 넣은
합성 기록으로 확인했다. 본 앱은 여전히 시스템 글자 배율을 고정한다. 이번 1.3배 통과는
열람 구성요소의 확대 내구성 근거이며 앱 전체 접근성 지원 완료를 뜻하지 않는다.
