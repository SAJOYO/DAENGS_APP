# 동선 탐색 상태와 화면 수명 연결

5·6차가 합쳐진 dev `53ac7824`를 기준으로 APK 빌드와 열람 회귀 검증을 먼저 수행한다.
그 위에서 기존 `WalkRouteExplorer.kt`의 구현을 세 파일로 나눈다.

| 파일 | 책임 |
| --- | --- |
| WalkRouteExplorerState.kt | 단일 선택 모델, 선택 전환, 통과 분석 작업, 구간·재생 상태 |
| WalkRouteExplorer.kt | Compose 생성·복원, 경로 준비, 타이머와 lifecycle 구독·해제 |
| WalkRouteExplorerPreview.kt | 합성 경로와 320/390dp 미리보기 |

클래스·함수의 패키지, 가시성, 이름과 구현은 유지한다. 기존 시각 포맷 helper도 호출 위치를
유지하기 위해 `WalkRouteExplorer.kt`에 둔다. 상태는 Compose snapshot 값을 사용하며 순수
도메인으로 옮긴 것이 아니다. `adoptedRead`의 소유권과 일기 연결부도 그대로다.

보존할 화면 수명 계약:

- `rememberSaveable(sessionId, saver = ...)`와 구형 장면 ID/장면·배속 목록 복원 형식.
- 경로 준비의 `LaunchedEffect(detail, state)`와 취소 예외 전파.
- 재생의 `LaunchedEffect(state, state.playing, state.playbackSpeed)`, 100ms 대기와 실제 경과 시간 계산.
- lifecycle 구독의 `DisposableEffect(lifecycle, state)`, PAUSE/STOP의 일시정지.
- 이탈 시 observer 제거 후 일시정지, scope와 Effect 취소의 기존 소유권.

`WalkRouteExplorerLifecycleTest`는 실제 Compose 연결과 LifecycleRegistry를 사용해 PAUSE/STOP,
소유자 교체, 이탈·재진입에서 정지와 리스너 해제를 확인한다. 시스템 프로세스 종료나 네이티브
지도 렌더링을 검증하는 테스트는 아니다. 타이머 배율과 구간·복원은 기존 상태/화면 테스트를
함께 사용한다. 실행 명령, 기준 커밋, 개수와 검증 한계는 PR #401에 기록한다.
