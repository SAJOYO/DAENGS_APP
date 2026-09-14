# 일기 읽기 준비와 탐색 연결 경계

`ui.walk.detail.WalkDiaryReadView`는 준비된 경로와 장면을 하나의 읽기 결과로 묶는다.
같은 파일의 `walkDiaryReadUpdates`는 주입받은 조회·관찰 함수로 읽기를 준비한다.
이 패키지는 상위 산책 화면·탐색 상태나 Room/동기화 구현을 직접 참조하지 않는다.

`ui.walk.DiaryRouteAdoption`은 준비된 읽기를 탐색 상태에 연결한다.
`adopt`, `selectSceneNeighborhood`, `sceneNeighborhood`는 기존 구현 그대로이며,
탐색 상태와 읽기 모델을 모두 아는 연결부다. 의존 방향은 다음과 같다.

```text
상세 상태 / 지도 / 복원 / 탐색 연결부
    → ui.walk.detail 읽기 모델·준비
        → 산책·일기·경로 계산 모델과 주입받은 조회·관찰 함수
```

다음 소유권과 조건은 이번 분리에서 바꾸지 않는다.

- `adoptedRead`는 기존 `WalkRouteExplorerState`가 보관하며 동일 인스턴스 비교를 유지한다.
- 읽기와 선택 반영은 기존 `WalkDetailState`의 같은 Compose snapshot에서 이루어진다.
- `mapLatest`/`transformLatest` 취소, 계정 세대 확인과 이전 정상 읽기 유지 조건을 보존한다.
- 준비 중 장면 보존, 삭제 확정 시 선택 해제, 사건 변경 시 복귀 구간 무효화를 유지한다.
- 복원 주소, 재생 속도·중지, 서랍 높이·카메라 명령의 소유권을 이동하지 않는다.

새 상태 객체나 Gradle 모듈은 없다. `DiaryReadBoundaryTest`는 패키지의 직접 소스 참조를
검사한다. 순수 도메인 계층이나 모든 전이 의존의 격리를 보장하는 것은 아니다.
실행한 회귀 범위와 결과는 PR #400에 기록한다.
