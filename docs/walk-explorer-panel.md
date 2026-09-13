# 산책 시간 탐색 패널 (#383)

UI 개선 4단계다. #381의 원본 시간 주소·구간 복귀·범위 재생을 실제 읽기 화면의 조작으로 연결한다.

## 화면과 조작

| 상태 | 상단 고정 조작 | 아래 독립 스크롤 |
| --- | --- | --- |
| 전체 산책 | 전체 산책 / 구간 고르기 / 동선 재생, 1·3·5분 빠른 선택 | 전체 장면과 경로 정보 |
| 시간 구간 | 같은 모드 선택, 시작·끝 시간과 범위 슬라이더, 길이 메뉴 | 원본 시간으로 확인한 관련 장면과 미확정 장면 안내 |
| 재생 | 구간 수정 또는 재생 닫기, 배속·재생/정지, 커서 슬라이더 | 관련 장면과 현재 위치 근거 안내 |
| 측정 시간축 없는 옛 산책 | 기존 전체 동선·배속·재생 | 전체 장면과 기존 동선/관측/전후 관계 |

재생 중에는 범위 슬라이더를 함께 표시하지 않는다. 구간 수정은 같은 범위를 다시 열며 재생을 멈춘다.
범위의 장면 번호는 전체 장면 순서와 같다. 원본 시각을 확인하지 못한 장면을 시간이나 문구로 추정해 끼워 넣지 않는다.
빈 범위·장면 로딩·재생할 이동 근거가 없는 범위는 각각 안내한다.

측정 기록의 기존 보행선·관측 경로·전후 관계는 **경로 정보**에서 펼친다.
지도에서 구간이나 공백·통과를 선택하면 해당 상세와 선택 버튼을 바로 읽을 수 있다.
측정 시간축 없는 기록은 기존 경로 정보에 바로 접근할 수 있다.

장면 상단의 **구간 복귀**는 원래 범위와 탐색 스크롤로 돌아간다.
본문의 **이 장면 앞뒤 30초 보기**는 현재 채택된 원본 스냅샷의 시간 주소가 있을 때만 나타난다.
산책 양 끝을 넘는 범위는 잘라내며, 오래된 클릭이나 원본이 바뀐 장면은 선택하지 않는다.
원래 장면 목록 복귀와 수정·삭제·사진 열람은 그대로 사용한다.

## 열람과 복원

서랍의 3단계 높이·첫 중간 높이·장면 선택 시 높이 유지와 지도 카메라 정책은 바꾸지 않는다.
상단 시간 조작을 고정하고 아래 읽기 영역만 스크롤한다.
`DiaryReadingMemory`가 탐색 스크롤과 경로 정보 펼침을 유지하므로 장면 왕복과 재진입에도 사용한다.

새 체크포인트의 읽기 자료에는 `explorerLayout: 2`를 기록한다.
이전 탐색 오프셋은 시간 조작과 경로 목록까지 포함하므로 새 읽기 영역에 같은 픽셀을 적용하지 않고 0으로 옮긴다.
기존 v1/v2 선택·구간·커서·배속·서랍·장면 본문·지도 묶음 복원은 그대로 검증한다.

## 검증

`WalkExplorerPanelUiTest`는 실제 읽기 레이아웃과 패널에서 고정 조작부, 독립 스크롤,
범위/커서 슬라이더 배타성, 구간 복귀와 장면 전후 선택, 옛 기록·공백·로딩·미확정 시각,
관측 경로/전후 관계 접근과 읽기 자료 이전을 검사한다.
`MeasurementTimeControlsTest`, `DiaryScenePresentationTest`는 분리된 실제 패널 진입점으로 검사한다.
고정 버튼을 찾는 기존 테스트는 스크롤 동작 대신 화면에 보이는지를 확인하고 클릭한다.
잠금 테스트와 기존 높이·선택·복원 단언은 변경하지 않았다.

```powershell
./gradlew.bat :app:testDebugUnitTest --tests '*WalkExplorerPanelUiTest' --tests '*WalkRangeContextTest' --tests '*WalkRangeContextUiTest' --tests '*MeasurementRangePlaybackTest' --tests '*MeasurementTimelineTest' --tests '*WalkRouteExplorerStateTest' --tests '*WalkExploration*Test' --tests '*MeasurementTimeControlsTest' --tests '*WalkDiaryReadViewTest' --tests '*WalkDetailStateTest' --tests '*WalkDiaryMapScreenTest' --tests '*DiaryMapNavigationTest' --tests '*ObservedRoutePresentationTest' --tests '*SessionRouteExplorerLayerStateTest' --tests '*WalkDiaryCompactDrawerTest' --tests '*WalkReadingBaselineUiTest' --tests '*WalkRecordOverviewUiTest' --tests '*WalkDiaryGapListTest' --tests '*DiarySceneRemovalUiTest' --tests '*DiaryReadingStyleTest' --tests '*DiaryScenePresentationTest' --tests '*WalkDiaryPhotoUiTest' --tests '*WalkSessionDetailUiTest' --tests '*DesignLockTest' --tests '*NaverGroupedMomentLayerTest' :app:assembleDebug
```

`WalkExplorerPanelPreview`는 합성 측정 원본으로 전체/구간/재생/공백/옛 기록/로딩을 그린다.
390×844dp와 320×640dp·글자 1.3배 Preview를 제공한다.
`DAENGS_EXPLORER_PREVIEW_DIR`에 출력 경로를 지정하고 UI 검사를 실행하면 실제 Compose native 렌더 PNG를 저장한다.
기본 검증 결과물은 `app/build/outputs/explorer-panel/`에 두고 저장소에는 넣지 않는다.

이번 패널은 관련 27개 클래스의 138개 JVM/Compose/Room 테스트와 debug 빌드를 통과했다. 앱을 설치하지 않았다.
기존 `MeasurementDeviceTest`의 고정 버튼·분리된 재생 시각·읽기 스크롤 조회도 새 패널에 맞췄다.
별도 지도/측정 검증 APK와 instrumentation APK는 빌드만 확인하며, #384의 실기기 통과 수를 새 UI의 실행 결과로 사용하지 않는다.
지도 부분은 미리보기 대체 화면이며 새 패널의 실제 Naver 지도 통합·실기기 조작은 후속이다.
#384에서 기록한 3단계 실기기 결과는 [시간 구간 검증](walk-range-context.md)에 별도로 남아 있다.
서버 로그인/업로드 및 운영 main 반영은 이번 검증 결과에 포함하지 않는다.
