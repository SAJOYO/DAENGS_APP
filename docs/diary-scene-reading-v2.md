# 산책 열람 시안 02 — 장면 목록과 상세 (#389)

후속 [탭 역할 분리 #390](walk-tab-responsibilities.md)는 장면 목록과 장면 안내를 장면 탭으로 한정한다.

목록의 카드 테두리와 상시 수정·삭제 버튼이 제목 공간을 차지하고, 상세의 고정 조작부가 본문 높이를
줄이던 배치를 정리했다. 승인된 HTML 시안 02의 2단계이며, [화면 골격 #387](diary-reading-shell-v2.md)을 이어간다.

## 표시와 동작

- 목록은 번호 → 32dp 원본 종류 배지 → 제목/시각 순서로 배치하고 얇은 구분선으로 나눈다.
  제목은 15sp/최대 두 줄, 시각과 종류는 12sp다. 수정·삭제는 행 끝의 더보기 메뉴에서 실행한다.
  메뉴를 열거나 편집을 요청하기 위해 장면 선택·카메라 이동·서랍 펼침을 먼저 실행하지 않는다.
- 상세 상단은 목록 복귀와 필요한 경우 구간 복귀를 남긴다. 19sp 제목, 시각, 16sp/27sp 본문,
  사진과 장면 주변 탐색, 수정·삭제 및 이전/다음 순으로 읽는다. 상세 제목은 생략하지 않는다.
  편집과 전후 탐색은 본문 끝에서 함께 스크롤되며 작은 폭에서는 두 묶음이 다음 줄로 흐른다.
- 번호 없는 공백은 작은 회색 `–`와 안내 문구로 표시한다. 장면 번호에 포함하지 않는다.
  원본 종류 판정과 아이콘을 그대로 사용하며, 제목의 물·집 같은 단어로 종류를 추론하지 않는다.
- 실제 로컬 사진의 디코딩·EXIF 회전·사진 대화상자·삭제 확인을 유지한다. 원본 파일이 없는
  서버 참조는 기존 안내를 표시하며 임의 사진을 붙이지 않는다.
- 일반 목록/상세의 화면 밖 장면·생성 상태·방향 등 보조 안내는 스크롤 끝에 둔다.
  로딩·빈 장면·선택한 공백에서는 기존처럼 바로 표시한다. 동선 탐색 패널의 구조는 이번 범위가 아니다.

## 읽던 위치와 기존 연결

세 단계 서랍, 중간 높이 독립 스크롤과 지도 선택 시 카메라/높이 유지 규칙을 따른다.
목록의 기존 `scene:`/`gap:` 키는 그대로 두고 마지막 안내에 `reading-notices` 키를 추가했다.
복원 시에도 같은 키 순서를 사용하며, 안내를 읽다가 재진입한 위치를 별도 회귀 검증으로 확인한다.
상세는 기존처럼 본문 전체를 하나의 lazy 항목으로 유지해 저장된 본문 index/offset을 소비한다.
장면 왕복과 구간 복귀, 제한 재생 및 재시작 복원은 기존 상태를 재사용한다.

## 검증 (2026-09-13)

- `assembleDebug` 성공. 관련 JVM/UI 검증 17개 클래스 87개 통과(실패·오류 0).
  읽기/서랍/지도/구간 복원/공백/삭제/사진/아이콘 원본 판정과 디자인·테마 잠금을 포함한다.
  전체 저장소 테스트 실행을 뜻하지 않는다.
- 기존 동작 검증은 새 더보기 메뉴 진입과 하단 스크롤 경로에 맞췄다. 삭제 대상·확인 전 보존·
  카메라·서랍·복귀에 대한 기존 검증을 유지했고, 스크롤 끝 안내의 키 복원 검증을 추가했다.
- `DiaryReadingStyleTest`의 Compose native 렌더에서 390dp 목록/본문, 320dp·글꼴 1.3배의
  다견/긴 제목/장문을 확인했다. 목록의 얇은 행과 상세의 제목·본문·하단 액션을 시안과 대조했다.
  이 렌더의 지도 영역은 SDK 자리 표시다. 공통 장면/하단 액션 Preview도 제공한다.
- 기존 `com.daengs.app.locationreview` 패키지를 교체 설치해 측정 합성 기록의 구간/장면 복귀와
  제한 재생을 3개 앱 프로세스에서 검증했다. 별도 검증 패키지를 추가하지 않았다.
- Naver 지도와 실제 Room/편집기를 사용하는 `DiaryEditorDeviceTest` 4개 통과.
  더보기에서 수정·재열기, 저장 실패/재시도, 위치 없는 메모, 로그인 교체/세션 삭제 시 편집 종료,
  사진 삭제 취소/확정과 파일 제거를 확인했다. 사진 확인 버튼은 대화상자 범위로 지정해
  새 본문 하단의 삭제 버튼과 구분한다.
- 실기기 서울 합성 기록에서 목록 → 지도 표식 5 선택 → 상세 → 하단 액션까지 캡처했다.
  선택/본문 스크롤 전후 지도 배경 위치와 손잡이 좌표 `[0,1485][1080,1548]`가 유지됐다.

재현 명령:

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest --tests '*DiaryReadingStyleTest' --tests '*DiarySceneRemovalUiTest' --tests '*DiaryScenePresentationTest' --tests '*WalkDiaryMapScreenTest' --tests '*WalkDiaryGapListTest' --tests '*WalkSessionDetailUiTest' --tests '*WalkReadingBaselineUiTest' --tests '*WalkRecordOverviewUiTest' --tests '*WalkDiaryCompactDrawerTest' --tests '*WalkExplorationPersistenceTest' --tests '*WalkExplorerPanelUiTest' --tests '*WalkExplorerMapNoticeUiTest' --tests '*DiarySceneKindTest' --tests '*DesignLockTest' --tests '*ThemeColorLockTest' --tests '*DiaryReadingShellUiTest' --tests '*WalkDiaryPhotoUiTest'
./gradlew.bat -I tools/naver-map-review.init.gradle :app:assembleDebug :app:assembleDebugAndroidTest -PslimAbi=arm64-v8a
```

실기기 실행은 [지도 검증 도구](../tools/naver-map-review/README.md)와
`tools/run_measurement_device_review.py --range-context`를 따른다. 지도 키는 로컬 빌드 환경에만 주입한다.
렌더는 `app/build/outputs/diary-reading/`, 이번 작업 환경의 실기기 캡처와 로그는 워크스페이스
`.tooling/scenes-v2-device/`에 보관했다. 실기기도 합성 자료를 사용하는 앱 코드 검증이며 서버 로그인/업로드 검증이 아니다.

후속 3단계는 구간 선택과 재생 패널, 4단계는 전체 흐름의 화면 대조다. main/운영 반영은 별도 합의 범위다.
