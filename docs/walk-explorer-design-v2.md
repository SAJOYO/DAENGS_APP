# 산책 열람 시안 02 — 동선 탐색 디자인 (#391)

> 4단계 정정 (2026-09-14): 당시 1.3배 설정은 `DaengsTheme`에서 1배로 덮어써졌다. 이전 확대 통과 기록은 실제 글꼴 확대의 근거로 쓰지 않는다. 테마 뒤에 배율을 주입하고 텍스트 레이아웃의 배율을 검사한 재검증 결과는 [통합 마감 기록](diary-reading-final-review.md)을 따른다.

전체/구간 선택과 재생이 비슷한 버튼으로 경쟁하고, 굵은 기본 슬라이더 옆에 시간 값이 작게
붙어 있던 배치를 정리했다. 시안 02의 3단계이며 [탭 역할 분리 #390](walk-tab-responsibilities.md)를 따른다.
동선 탐색에 장면 목록이나 장면 안내를 추가하지 않는다.

## 화면과 조작

- 전체 산책/구간 고르기는 옅은 바탕의 한 묶음으로 표시한다. 선택한 항목은 흰 바탕과 굵기로
  구분하고 접근성 선택 상태를 제공한다. 각 모드는 48dp 높이의 조작 영역을 사용한다.
- 재생은 별도의 분홍 버튼이다. 재생 중에는 구간 수정/재생 닫기, 배속, 일시정지를 우선 배치한다.
  기존 전체 재생·제한 재생·구간 복귀 동작을 그대로 사용한다.
- `경과` 표기와 17sp 시간 값을 시간축 위에 놓는다. 구간 모드에는 선택 길이도 표시한다.
  긴 시간과 글꼴 확대에서 길이 표시는 필요한 경우 다음 줄로 흐르며 시간 숫자를 생략하지 않는다.
- 범위/재생 슬라이더는 내용 전체 폭, 4dp 선, 18dp 원형 손잡이를 사용한다. 실제 조작 영역은
  최소 48dp 높이다. Material3의 드래그·접근성·키보드 입력 처리는 유지하고 모양만 바꾼다.
  시간축은 선택/재생 시간을 나타내며 위치 근거가 없는 경로를 새로 연결하지 않는다.
- 1·3·5분 빠른 선택과 범위 길이 메뉴는 시간축 아래 스크롤 영역에 둔다. 선택 범위 시작으로
  이동하는 액션도 같은 줄에 둔다. 길이 변경은 선택 시작을 유지하고 기록 끝에서 자른다.
- 경과 시간 설명과 전후 관계는 `경로 정보`에서 펼친다. 동선/관측/전후 관계 선택은 얇은
  구분선과 선택 배경으로 표시하고 원래 액션에 연결한다. 지도 방향 안내는 같은 좌우 기준에 정렬한다.
- 공백 범위, 재생 위치 미확정, 측정 시간축이 없는 과거 산책과 오류는 기존 근거와 문구로 안내한다.
  서랍 높이와 지도 카메라 정책은 바꾸지 않는다.

탐색 스크롤 저장 버전은 4다. 조작과 설명의 순서가 바뀌었으므로 이전 탐색 offset만 초기화한다.
선택 범위·장면 목록/본문·배속·서랍과 경로 정보 펼침 상태는 보존한다.

## 검증 (2026-09-13)

- debug 빌드, 관련 12개 클래스 51개 검사 통과(실패·오류·skip 0).
  시간 조작, 탭 분리, 구간/장면 왕복과 복원, 기존 지도/서랍/읽기 및 디자인·테마 잠금을 포함한다.
- 시간축의 전체 폭/48dp 높이와 두 범위 손잡이, 시간 값의 위쪽 배치를 검증했다.
  320dp·글꼴 1.3배의 긴 시간 표시는 끝자리·생략 여부·화면 경계를 확인한다.
  길이 메뉴가 시작점을 유지하고 기록 끝에서 잘리는 것도 확인했다.
- `WalkExplorerPanelPreview`와 부품 Preview를 제공한다. 390dp/320dp·글꼴 확대의 전체/구간/재생,
  긴 시간 및 경로 안내 렌더를 시안과 대조했다. Compose native 렌더의 지도는 자리 표시다.
  작업 환경의 결과는 `.tooling/explorer-v2-renders/`에 보관했다.
- 기존 실기기 패키지 `com.daengs.app.locationreview`를 교체 설치하고 두 APK의 `Success`를 확인했다.
  구간/장면 복귀와 제한 재생을 3개 앱 프로세스에서 검증했고 `verifyExplorerActions`도 통과했다.
  양쪽 범위 손잡이 조작, 재생 커서 터치, 배속과 복귀를 실제 Naver SDK 화면에서 확인했다.
  커서 검증은 이전 저장값으로 통과하지 않도록 실제 UI 값과 해당 저장값의 일치를 기다린다.
- 전체/구간/재생 실기기 캡처는 `.tooling/explorer-v2-device/`에 보관했다. 고정 합성 기록의
  좌표가 (0, 0) 부근이어서 지도 배경은 바다다. 실제 사용자 기록이나 서버 로그인/업로드 검증은 아니다.

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest --tests '*WalkExplorerPanelUiTest' --tests '*WalkExplorerMapNoticeUiTest' --tests '*MeasurementTimeControlsTest' --tests '*WalkRangeContextUiTest' --tests '*WalkRouteExplorerStateTest' --tests '*RecordContextPresentationTest' --tests '*WalkExplorationPersistenceTest' --tests '*WalkReadingBaselineUiTest' --tests '*WalkDiaryCompactDrawerTest' --tests '*WalkRecordOverviewUiTest' --tests '*DesignLockTest' --tests '*ThemeColorLockTest'
```

다음 4단계는 전체 화면의 통합 대조와 마감이다. 이번 변경은 main/운영 반영을 포함하지 않는다.
