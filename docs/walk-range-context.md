# 시간 구간·장면 복귀·범위 재생 (#381)

산책 UI 개선의 3단계다. #379 읽기 화면과 #378 지도 장면 묶음을 포함한 dev 위에서 동작한다.
탐색 패널의 상단 재배치와 전용 복귀/장면 전후 버튼은 4단계에서 구성한다.

## 지금 소비하는 동작

| 조작 | 상태와 표시 |
| --- | --- |
| 시간 범위를 고른 뒤 관련 장면 열기 | 장면이 강조를 소유하고, 원래 시간 범위는 `Scene.returnRange`에 보관 |
| 그 장면에서 다른 장면 열기 | 같은 복귀 범위 유지 |
| 장면에서 기존 **동선 탐색** 탭으로 돌아가기 | 원래 범위와 탐색 스크롤로 복귀, 재생은 일시정지 |
| **장면 목록**으로 돌아가기 / 전체 동선 보기 | 기존 전체 목록/전체 동선 의미 유지, 범위 맥락 종료 |
| 범위를 고른 뒤 기존 **동선 재생** 누르기 | `Replay.range`를 유지하며 해당 범위 안에서만 재생 |
| 재생 커서 이동 / 배속 변경 | 범위 안으로 제한하고, 배속은 이후 진행량에만 적용 |
| 범위 끝 도착 / 다시 재생 | 끝에서 정지, 다시 누르면 범위의 첫 재생 가능 지점부터 시작 |

지도 강조는 선택 범위를 유지하며 범위 재생 중 전체 산책 방향을 켜지 않는다.
장면 선택의 지도/서랍 정책, 서랍 3단계와 독립 스크롤은 그대로다.
지도 묶음 ID·묶음 목록 스크롤은 탐색 스크롤과 별도로 보관한다.

## 원본 시간과 공백

`MeasurementTimeline`의 source epoch·clock epoch·기기 경과 시간 주소로 양 끝을 표현한다.
일시정지 길이를 만들어 더하거나 장면 문구·wall clock만으로 위치를 추론하지 않는다.
범위 재생에 사용하는 시간 구간은 준비된 보행선과 보행거리 제외 관측선의 검증된 원본 선분에서 계산한다.
이 계산은 시간축 준비 때 한 번 수행하고, 이후 조회는 정렬된 구간에서 이진 검색한다.

공백이나 보행 미확정 구간에 도착하면 선택 범위 안의 다음 재생 가능한 지점으로 이동한다.
범위 안에 더 진행할 근거가 없으면 끝에서 멈추고, 그 끝에 위치 근거가 없으면 커서를 숨긴다.
고립된 관측점은 수동 시점 조회에서 그대로 볼 수 있지만 이동 선분을 만들어 자동 재생하지 않는다.
범위가 선분의 끝점만 접하는 경우도 자동 재생 근거가 되지 않는다.
옛 산책/전체 산책 재생의 기존 공백 처리와 거리 계산은 변경하지 않는다.

`selectSceneNeighborhood(view, scene, beforeMillis, afterMillis)`는 현재 채택된 읽기 묶음과 장면 binding을 검사한 뒤
사건 시각의 앞뒤 범위를 선택한다. 기본은 앞뒤 30초이고 기록 경계에서 자른다.
늦은 화면의 장면, 위치 시각만 있는 장면, 시간 주소를 확인할 수 없는 장면은 선택을 바꾸지 않고 실패를 반환한다.
이 함수는 4단계의 장면 전후 버튼에서 사용할 상태 진입점이다.

## 저장과 갱신

기존 Room 20의 작은 JSON 체크포인트를 **v2**로 확장했다. 새 DB/서버 스키마는 없다.
`selection.returnRange`(장면), `selection.range`(재생), `reading.explorerOffset`을 보관한다.
v1은 범위 맥락 없는 기존 선택으로 읽는다. v2 필드를 붙인 채 형식 번호만 v1로 낮춘 값은 거부한다.

소유자·세션·측정 ID/result digest와 양 끝의 source/clock 주소를 확인한다.
재생 위치가 저장 범위 밖이면 복원하지 않는다. 모르는 형식·잘못된 범위·삭제된 장면을 복원하지 않는다.
같은 측정의 재준비는 유효한 범위를 유지하고, 다른 원본에는 옛 범위를 이식하지 않는다.
장면 사건의 원본 revision이 바뀌면 복귀 맥락을 버린다. 제목/본문만 바뀐 저장본은 재진입 시 범위를 유지하고 이전 본문 스크롤만 버린다.
재진입은 항상 일시정지로 복원하며 기존 계정 generation 경계와 늦은 복원보다 선행 사용자 조작을 우선하는 규칙을 유지한다.

탐색 패널의 스크롤은 화면을 벗어나도 `DiaryReadingMemory`에서 유지한다.
장면 화면으로 복원된 동안은 탐색 스크롤을 적용하지 않고 기다렸다가 패널이 다시 나타날 때 적용한다.
목록 높이가 달라지면 현재 스크롤 한계 안으로 제한한다.

## 검증

2026-09-13: #378을 포함한 dev `dcb098d` 병합 후 아래 **23개 클래스·119개 테스트 통과**(실패·오류·skip 0),
`assembleDebug` 성공. 변경된 패널의 기존 Compose Preview를 유지했다. 전체 저장소 테스트 실행을 뜻하지 않는다.

- `WalkRangeContextTest`: 구간/장면 왕복, 범위 제한·공백·배속, 지도 강조, 원본/사건 변경과 삭제, 장면 전후 원본 시각, v1/v2와 잘못된 복원 값.
- `MeasurementRangePlaybackTest`: 공백 뒤의 확인된 선분, 범위 경계, 시계 보정/다른 recording epoch, 소수 밀리초 원본의 올림 처리.
- `WalkRangeContextUiTest`: 실제 읽기 레이아웃과 탐색 패널에서 탭 왕복·같은 서랍 높이·재진입 후 스크롤과 일시정지 커서 복원·공백 범위 재생 불가 안내.
- 기존 열람·지도 선택·묶음·삭제·복원·계정/DB 검사와 잠긴 디자인 검사를 함께 실행한다. 기존 잠금 검사는 수정하지 않았다.

```powershell
./gradlew.bat :app:testDebugUnitTest --tests '*WalkRangeContextTest' --tests '*WalkRangeContextUiTest' --tests '*MeasurementRangePlaybackTest' --tests '*MeasurementTimelineTest' --tests '*WalkRouteExplorerStateTest' --tests '*WalkExploration*Test' --tests '*MeasurementTimeControlsTest' --tests '*WalkDiaryReadViewTest' --tests '*WalkDetailStateTest' --tests '*WalkDiaryMapScreenTest' --tests '*DiaryMapNavigationTest' --tests '*ObservedRoutePresentationTest' --tests '*SessionRouteExplorerLayerStateTest' --tests '*WalkDiaryCompactDrawerTest' --tests '*WalkReadingBaselineUiTest' --tests '*WalkRecordOverviewUiTest' --tests '*WalkDiaryGapListTest' --tests '*DiarySceneRemovalUiTest' --tests '*DiaryReadingStyleTest' --tests '*DesignLockTest' --tests '*NaverGroupedMomentLayerTest' :app:assembleDebug
```

검증 데이터는 합성 원본이며 JVM/Compose/Room에서 실행한다.
#381 구현 당시에는 앱을 설치하지 않았다. 실제 Naver 지도·기기 재시작·서버 로그인/업로드 검증은 위 결과에 포함하지 않는다.

### 병합 후 실기기 확인 (#384)

2026-09-13, #381·#382가 병합된 dev `15bf75c`에서 Galaxy S25 / Android 16으로 확인했다.
별도 `com.daengs.app.locationreview` 앱의 기존 4단계와 새 구간 3단계, 총 **7개 instrumentation 테스트 통과**.
각 단계 사이 외부 runner가 앱을 force-stop하며, 복원 단계에서는 프로세스 UUID가 달라졌는지도 검사한다.

- 0–17초 선택 → 원본 시각이 연결된 메모 열람 → 동선 탐색 복귀: 실제 슬라이더 양 끝, 탐색 스크롤, 서랍 높이 유지.
- 메모를 연 상태로 저장 후 프로세스 교체: 장면과 복귀 범위 유지, 탐색 탭에서 기존 범위·스크롤 복원.
- 8배속 범위 재생: 17초 끝에서 정지하고 `00:17 / 00:17` 표시. 다시 재생하면 범위 안에서 시작.
- 6초로 커서를 옮겨 저장 후 프로세스 교체: `00:06 / 00:17`, 8배속, 범위와 일시정지 상태 복원.
- 기존 장면 본문 스크롤·서랍 확대, 5–17초 구간, 일시정지 재생 복원 4단계도 통과.

설치한 APK를 기기에서 다시 받아 빌드 산출물과 SHA-256이 같은지 확인했다.
Naver SDK 화면 캡처에서 장면 강조와 17초 끝/복원된 6초 커서를 확인했다.
합성 원본은 (0, 0) 부근이므로 캡처 시 검증 앱의 카메라 범위 제한을 해제하고 원본 좌표에 맞춘다.
이 카메라 조작은 왕복·스크롤 검사와 체크포인트 저장이 끝난 뒤에만 한다. 카메라에 따라 방향 안내 높이가 달라질 수 있다.
제품 화면·지도 정책과 원본 좌표/측정 바이트는 바꾸지 않았다.

재현 명령과 검증 범위는 [측정 동선 실기기 검증](walk-measurement-device-review.md#구간-맥락-검사-384)을 따른다.
이 결과는 저장 완료 후 앱 프로세스 재시작 검사다. 기기 재부팅·실제 회원 기록·서버 로그인/업로드는 검증하지 않았다.
