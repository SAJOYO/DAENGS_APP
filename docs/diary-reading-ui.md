# 단일 산책 읽기 화면 (#379)

> 4단계 정정 (2026-09-14): 당시 1.3배 설정은 `DaengsTheme`에서 1배로 덮어써졌다. 이전 확대 통과 기록은 실제 글꼴 확대의 근거로 쓰지 않는다. 테마 뒤에 배율을 주입하고 텍스트 레이아웃의 배율을 검사한 재검증 결과는 [통합 마감 기록](diary-reading-final-review.md)을 따른다.

승인된 시안의 2단계다. #377의 원본 기반 장면 종류를 실제 산책 상세의 헤더·요약·목록·본문에 적용한다.

상단·지도 도구·탭의 후속 시안 02 적용은 [화면 골격 #387](diary-reading-shell-v2.md)을 참고한다.
장면 목록·본문의 후속 정리는 [장면 읽기 #389](diary-scene-reading-v2.md)을 참고한다.
아래는 #379 당시의 적용과 검증 기록이다.

## 표시와 데이터

- 헤더는 해당 `WalkSummary.dogIds`에 참여한 강아지만 표시한다. 현재 대표견을 대신 붙이지 않는다.
  한 마리는 등록 견종 그림, 여러 마리나 견종 미확인은 기존 발바닥 그림을 사용한다.
  중복 참여 ID는 한 번만 세고, 이름을 확인할 수 없는 참여견은 미확인 마릿수로 표시한다.
- 날짜·제목·날씨·활동 시간·거리·평균 속도는 기존 상세 데이터와 포맷 함수를 그대로 사용한다.
  시간은 일시정지를 포함한 경과 시간이 아닌 기존 `activeDurationMillis`다.
- 목록은 기존 테마의 둥근 카드로 구분하고, 원본 종류 배지·시각·장면 번호를 분리한다.
  제목의 물·집 같은 단어로 새 아이콘을 추론하지 않는다. 공백 안내는 기존 회색 `–`를 유지한다.
- 본문은 제목·시각·문단의 크기와 간격을 정리했다. 시각을 본문에 반복하지 않는다.
  사진은 선택 장면의 실제 로컬 파일만 최대 800px로 읽고 EXIF 회전을 적용한다.
  사진이나 `사진 보기`를 누르면 같은 사진의 기존 대화상자를 연다.
  다른 사진으로 바뀌면 이전 미리보기를 지우며, 읽기 실패는 안내를 표시한다.
  서버에 사진 참조만 있는 장면은 촬영 기기에서 볼 수 있다는 기존 안내를 유지한다.
- 목록·본문의 수정/삭제와 추가 기록·생성/갱신·백업·사진 보기의 기존 연결을 유지한다.

## 서랍과 접근성

3단계 높이, 중간 높이의 독립 스크롤, 지도 선택 시 카메라/높이 유지와 재진입 복원은 기존 계약을 따른다.
탭 없는 기존 읽기 화면도 목록을 실제 보이는 높이로 측정한다. 이전에는 펼침 높이로 측정해
화면 밖 장면을 이미 보이는 항목으로 취급했고, 새 카드 간격에서 공백 안내 → 목록 → 장면 선택이 실패했다.
높이 기준점이나 선택 시 펼침 규칙을 바꾸지 않고 이 측정만 고쳤다. 기존 검증은 수정하지 않았다.

320dp·글꼴 1.3배에서 다견 이름·탭을 확인하고 긴 제목/장문의 끝까지 독립 스크롤할 수 있는지 검사한다.
제목은 상단 최대 두 줄·목록 최대 두 줄이며 상세 제목은 생략하지 않는다.
요약의 접근성 설명은 기존 걸은 시간·이동 거리·평균 속도 문구를 보존한다.

## 확인 방법

2026-09-13: 위 변경의 대상 14개 클래스, 중복을 제외한 72개 테스트가 통과했다(실패·오류·skip 0).
기존 회귀 포함 64개를 실행한 뒤, 긴 제목 검사를 추가한 스타일 5개와 공통 부품 소비자 7개를 따로 확인했다.
`assembleDebug`와 4종 native 렌더도 확인했다. 전체 저장소 테스트 실행을 뜻하지 않는다.

`DiaryReadingStylePreview`는 390×844, 320×640/글꼴 1.3배에 목록·본문·긴 제목·다견·사진 참조만 있는 장면·빈 상태·로딩·오류를 제공한다.
데이터는 명시적인 합성 예시이고 지도 영역은 SDK 자리 표시다. 실제 Naver 지도 렌더 검증으로 세지 않는다.

```powershell
./gradlew.bat :app:testDebugUnitTest --tests '*DiaryReadingStyleTest' --tests '*DiaryScenePresentationTest' --tests '*DiarySceneRemovalUiTest' --tests '*WalkDiaryCompactDrawerTest' --tests '*WalkDiaryMapScreenTest' --tests '*WalkSessionDetailUiTest' --tests '*WalkDiaryPhotoUiTest' --tests '*WalkDiaryGapListTest' --tests '*WalkExplorationPersistenceTest' --tests '*DesignLockTest' --tests '*WalkReadingBaselineUiTest' --tests '*WalkRecordOverviewUiTest' --tests '*MeasurementTimeControlsTest' --tests '*WalkDiaryReadViewTest' :app:assembleDebug
```

`DiaryReadingStyleTest`의 5개 검사는 실제 Compose 목록/본문·수정/삭제 접근·다견 정보·작은 화면의 장문 스크롤·로딩/오류 복구와 로컬 파일 디코딩/교체를 확인한다.
Robolectric native 렌더 결과는 `app/build/outputs/diary-reading/`에 생성한다:
`reading-list-390.png`, `reading-body-390.png`, `reading-multiple-320.png`, `reading-long-title-320.png`.
실제 사용자 사진이나 지도/서버 데이터를 테스트 fixture로 넣지 않았다.

## 후속 범위

구간에서 장면으로 갔다 돌아오는 맥락과 제한 재생은 3단계, 동선 탐색 패널 배치는 4단계다.
실기기 최종 화면과 Naver 지도 통합 확인은 후속이다. 이번 작업에서는 폰에 앱을 설치하지 않았다.
서버 로그인·업로드 확인의 기존 미완료 상태를 UI 합성 검증으로 완료 처리하지 않는다.
