# 장면과 동선 탐색의 역할 (#390)

2026-09-13 사용자가 정정한 기준이다. 두 탭에서 같은 장면 목록을 제공하면 장면 탭의 목적이
없어진다. 기존 #383의 전체/관련 장면 목록 정책을 폐기하고 다음처럼 분리한다.

| 탭 | 표시하는 내용 |
| --- | --- |
| 장면 | 전체 장면 목록, 본문·사진·편집/삭제, 장면 로딩/갱신 안내, 화면 밖 장면 메뉴 |
| 동선 탐색 | 시간/구간 선택, 재생, 이동 근거·기록 공백·경로 정보, 지도 방향 안내 |

동선 탐색에는 전체/구간/재생 어느 상태에서도 장면 목록이나 장면 수·원본 종류·장면 로딩 안내를
넣지 않는다. `WalkRouteExplorerPanel`에서 장면 데이터와 선택 콜백 자체를 제거했다.
장면 시각의 미확정 여부도 탐색 목록을 만드는 조건으로 사용하지 않는다. 장면은 장면 탭에서 읽는다.

지도 표식을 누르면 장면 탭에서 해당 본문을 연다. 카메라와 서랍 높이를 유지하는 기존 규칙을 따른다.
구간을 고른 뒤 장면 탭으로 옮기면 재생을 멈추고 선택 범위를 보존한다. 장면을 읽은 후
`구간 복귀` 또는 동선 탐색 탭으로 돌아오면 그 범위를 다시 보여준다. 장면 상세의
`이 장면 앞뒤 30초 보기`는 실제 시간 근거가 있을 때 동선 탐색으로 이동하는 연결이다.
시간 범위가 없는 기존 구간/전후 관계 선택의 탭 종료 동작은 그대로 따른다.

탐색 스크롤 저장 버전은 3이다. 이전 화면은 장면 목록까지 포함했으므로 기존 탐색 offset만 0으로
옮기고, 선택 범위·장면·장면 목록/본문 위치·서랍·배속과 경로 정보 펼침 상태는 보존한다.

## 검증

- debug 빌드와 관련 13개 클래스 53개 검증 통과(실패·오류·skip 0).
- 전체/구간/재생/기록 공백/측정 없는 과거 산책에서 장면 중복이 없는지 확인했다.
  장면 탭으로 옮겼을 때 목록/화면 밖 메뉴/재시도 동작은 계속 접근할 수 있다.
- 320dp·글꼴 1.3배에서 탭 → 장면 → 구간 복귀와 재생 정지를 확인했다.
  재진입 시 탐색 스크롤과 범위, 장면 선택의 복원도 검증했다.
- 기존 읽기·세 단계 서랍·지도 선택·디자인/테마 잠금 검증을 포함한다.
  이전의 중복 목록 접근 검증은 사용자 정정에 맞춰 탭 이동 경로로 변경했다.
- `WalkExplorerPanelPreview`와 Compose native 렌더의 390dp/320dp 전체 구조를 확인했다.
  렌더의 지도는 자리 표시이며, 작업 환경의 결과는 `.tooling/tabs-explorer-renders/`에 있다.
- 기존 실기기 패키지 `com.daengs.app.locationreview`를 교체 설치하고 두 APK의 `Success`를 확인했다.
  구간 → 장면 탭 → 본문 → 구간 복귀와 제한 재생을 3개 앱 프로세스에서 검증했다.
  `MeasurementDeviceTest#verifyExplorerActions`도 통과했으며 실제 슬라이더 드래그·구간 왕복·재생을 확인했다.
  Naver SDK 화면의 전체 동선/구간/장면 캡처는 작업 환경 `.tooling/tabs-device/`에 있다.
  고정 합성 기록의 좌표가 (0, 0) 부근이어서 배경은 바다이며, 실제 사용자 산책·서버 검증은 아니다.

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest --tests '*WalkExplorerPanelUiTest' --tests '*WalkExplorerMapNoticeUiTest' --tests '*MeasurementTimeControlsTest' --tests '*WalkRangeContextUiTest' --tests '*DiaryScenePresentationTest' --tests '*WalkRouteExplorerStateTest' --tests '*RecordContextPresentationTest' --tests '*WalkExplorationPersistenceTest' --tests '*WalkReadingBaselineUiTest' --tests '*WalkDiaryCompactDrawerTest' --tests '*WalkRecordOverviewUiTest' --tests '*DesignLockTest' --tests '*ThemeColorLockTest'
```

이번 변경은 탭 역할의 수정이다. 시간 조작부의 크기·간격을 정리하는 시안 02의 3단계는 별도 작업이다.
